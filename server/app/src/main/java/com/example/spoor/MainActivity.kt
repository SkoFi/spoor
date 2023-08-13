package com.example.spoor

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter


open class MainActivity : AppCompatActivity(), SessionService.ActivityCallback {
    private val PERMISSIONS_REQ_CODE = 1
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAPTURE_AUDIO_OUTPUT
    )
    private val SPOTIFY_AUTH_REQ_CODE = 1000
    private val TAG: String = "MAIN"
    private var selectedRecorder = "Mic"
    private lateinit var captureAudioResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var boundService: SessionService
    private lateinit var sessionServiceIntent: Intent
    private var isBound = false
    // UI Stuff
    private lateinit var btnMic: Button
    private lateinit var btnPhoneOutput: Button
    private lateinit var btnUsb: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvCurrentlyPlaying: TextView
    private lateinit var teSessionName: EditText
    private lateinit var swPlaylist: Switch
    // Initialize Recorders
    private lateinit var recorder: RecorderClass
    private lateinit var micRecorder: MicRecorder
    private lateinit var usbRecorder: UsbRecorder
    private lateinit var phoneOutputRecorder: PhoneOutputRecorder
    // Spotify Stuff
    private val spotifyApi = SpotifyApi()
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Prefs File
        prefs = getSharedPreferences("com.example.recscollector", MODE_PRIVATE)
        editor = prefs.edit()

        initUi()

        sessionServiceIntent = Intent(this, SessionService::class.java)

        // Handler for Audio Capture request
        captureAudioResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data is Intent) {
                // Success
                Log.d(TAG, "Audio Capture Request Granted!")

                boundService.setCredentials(result.resultCode, result.data!!)

                GlobalScope.launch {
                    boundService.startSession(spotifyApi)
                }
            } else {
                throw Exception("Screen Capture Request denied!")
            }
        }

        // Launch request for required permissions
        requestPermissions(REQUIRED_PERMISSIONS, PERMISSIONS_REQ_CODE)

        Log.d(TAG, "About to run handler for Spotify login")
        // Handler for Spotify Login
        val spotifyLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            Log.d(TAG, "Checking result from activityresult register: ${result.resultCode.toString() + " " + result.data.toString()}")
            if (result.resultCode == RESULT_OK && result.data is Intent) {
                // Get return from Auth Activity
                val returnedResult = result.data.toString()
                Log.d(TAG, "Returned from Auth. Result: $returnedResult")

                // Load User ID and Access Token
                val userId = prefs.getString("SPOTIFY_USER_ID", null)
                val accessToken = prefs.getString("SPOTIFY_ACCESS_TOKEN", null)
                Log.d(TAG, "userID: $userId")
                Log.d(TAG, "accesToken: $accessToken")
                if (userId != null && accessToken != null) {
                    spotifyApi.setTokens(userId, accessToken)
                    Log.d(TAG, "set Tokens successful")
                } else {
                    throw Exception("Failed to get userId and/or AccessToken: $userId $accessToken")
                }
            } else {
                Log.d(TAG, "Something went wrong with activityresult register: ${result.resultCode.toString() + " " + result.data.toString()}")
            }
        }
        // Login to Spotify
        val spotifyLogin = Intent(this, SpotifyAuthActivity::class.java)
        spotifyLoginHandler.launch(spotifyLogin)
    }

    // Handle the result of permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Verify all required permissions were granted
        Log.d(TAG, "Permission request handler: $grantResults")
        if (true) { //(requestCode == PERMISSIONS_REQ_CODE && grantResults.isNotEmpty()) {
            if (true) {
                Log.d(TAG, "Permissions Granted!")
            } else {
                throw Exception("Permissions denied!")
            }
        }
    }

    // Handler for Service Connection
    private val serviceConn = object: ServiceConnection {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onServiceConnected(name: ComponentName, ibinder: IBinder) {
            Log.d(TAG, "Service Connected")
            isBound = true

            val binder = ibinder as SessionService.SessionBinder
            // Get Bound Service instance (now have access to Service methods)
            boundService = binder.getService()
            // Set callback to the Service (grants Service access to its callback)
            boundService.setCallback(this@MainActivity)

            // Register this Activity with the Bound service (enables callbacks)
            //(boundService as MicRecorder).registerClient(this@MainActivity)

            val captureReqNeeded = boundService.initialize(selectedRecorder, captureAudioResultLauncher)

            if (!captureReqNeeded) {
                GlobalScope.launch {
                    boundService.startSession(spotifyApi)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    //// Custom Functions
    private fun initUi() {
        setContentView(R.layout.activity_main)

        // Get UI elements
        btnMic = findViewById(R.id.micSelectButton)
        btnPhoneOutput = findViewById(R.id.phoneOutputSelectButton)
        btnUsb = findViewById(R.id.usbSelectButton)
        btnStart = findViewById(R.id.startButton)
        btnStop = findViewById(R.id.stopButton)
        teSessionName = findViewById(R.id.editTextSessionName)
        tvCurrentlyPlaying = findViewById(R.id.tvCurrentlyPlaying)
        swPlaylist = findViewById(R.id.playlistSwitch)

        // Initialize UI
        // Note: this should be consistent with the selectedRecorder variable
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        btnMic.isEnabled = false
        btnPhoneOutput.isEnabled = true
        btnUsb.isEnabled = true
        swPlaylist.isChecked = false
        teSessionName.isEnabled = false

        SharedVariables.createPlaylist = swPlaylist.isChecked
    }
    private fun permissionsGranted(): Boolean {
        /*
        Check if total set of permissions have been granted for this object

        val activity = context as? AppCompatActivity
        return activity?.let {
        */
        var allPermissionsGranted = true
        for (permission in REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
            }
        }
        return allPermissionsGranted
        //} ?: false
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun switchRecorder(selectedRecorder: String = "Mic") {
        /*
        Selects Audio Source to use for recording based on selection
        Input:
        - audioCapToken (opt): Only for Phone_Output--Token received from Audio Capture request
        - audioCapCode (opt): Only for Phone_Output--Code received from Audio Capture request
        Parameter:
        - recorder: Intent to start Service for selected recorder
        - boundService: Service bound to this activity (used to call functions)
        - serviceConn: Service Connection to the Recorder
        Return:
        - None
        */
        Log.d(TAG, "Configuring to $selectedRecorder")
        if (selectedRecorder == "Mic") {
            recorder = micRecorder
        } else if (selectedRecorder == "Phone_Output") {
            recorder = phoneOutputRecorder
        } else if (selectedRecorder == "USB") {
            recorder = usbRecorder
        } else {
            throw Exception("Unexpected Audio Source Selection!")
        }
    }

    // Callbacks
    override fun getCurrentSong(songText: String){
        runOnUiThread {
            Log.d(TAG, "Updating text to $songText")
            tvCurrentlyPlaying.text = songText
        }
    }

    fun onMicSelButtonClick(view: View) {
        selectedRecorder = "Mic"

        // Enable Phone Output button, disable Mic Button
        btnMic.isEnabled = false
        btnPhoneOutput.isEnabled = true
        btnUsb.isEnabled = true
    }

    fun onPhoneOutputSelButtonClick(view: View) {
        selectedRecorder = "Phone_Output"

        // Disable Phone Output button, enable Mic Button
        btnMic.isEnabled = true
        btnPhoneOutput.isEnabled = false
        btnUsb.isEnabled = true
    }

    fun onUsbSelButtonClick(view: View) {
        selectedRecorder = "USB"

        // Disable Phone Output button, enable Mic Button
        btnMic.isEnabled = true
        btnPhoneOutput.isEnabled = true
        btnUsb.isEnabled = false
    }

    open fun showAlertDialog(view: View?, title: String, message: String) {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)

        // Set the title and message
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(message)

        // Set positive button
        alertDialogBuilder.setPositiveButton("OK",
            DialogInterface.OnClickListener { dialog, which ->
                // Do something when the "OK" button is clicked
                dialog.dismiss() // Close the dialog
            })

        // Create and show the AlertDialog
        val alertDialog: AlertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun generateFullSessionName(rawName: String): String {
        val currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formattedDate = currentDate.format(formatter)

        return "[Spoor] $rawName $formattedDate"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onStartButtonClick(view: View) {
        Log.d(TAG, "Start button clicked")
        if (swPlaylist.isChecked && teSessionName.text.toString() == "") {
            showAlertDialog(view, "Session Name Uninitialized", "Enter name for session!")
            return
        }
        // Save the entered Session name
        SharedVariables.sessionName = generateFullSessionName(teSessionName.text.toString())

        bindService(sessionServiceIntent, serviceConn, Context.BIND_AUTO_CREATE)

        // Clear text, switch around the Start/Stop buttons
        tvCurrentlyPlaying.text = ""
        teSessionName.isEnabled = false
        btnStart.isEnabled = false
        btnStop.isEnabled = true
    }

    fun onStopButtonClick(view: View) {
        // Print the captured value
        unbindService(serviceConn)

        // Enable start button and disable stop button
        teSessionName.setText("")
        teSessionName.isEnabled = true
        btnStart.isEnabled = true
        btnStop.isEnabled = false
    }

    fun onPlaylistSwitchToggle(view: View) {
        teSessionName.isEnabled = swPlaylist.isChecked
        SharedVariables.createPlaylist = swPlaylist.isChecked
        if (!swPlaylist.isChecked) {
            teSessionName.setText("")
        }
    }
}
