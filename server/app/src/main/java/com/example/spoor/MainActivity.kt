package com.example.spoor

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.spoor.RecorderClass.NoPermissions

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_REQ_CODE = 1
    private val SCREEN_CAP_REQ_CODE = 2
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAPTURE_AUDIO_OUTPUT
    )
    private val TAG: String = "MAIN"
    private var selectedRecorder = "Mic"
    private lateinit var recorder: Intent
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var captureAudioResultLauncher: ActivityResultLauncher<Intent>
    private var boundService: RecorderClass? = null
    private lateinit var serviceConn: ServiceConnection
    private var isBound = false

    private fun permissionsGranted(): Boolean {
        // Check if total set of permissions have been granted for this object
        //val activity = context as? AppCompatActivity
        //return activity?.let {
        var allPermissionsGranted = true
        for (permission in REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
            }
        }
        return allPermissionsGranted
        //} ?: false
    }
    private fun switchRecorder(audioCapToken: Intent? = null,
                               audioCapCode: Int = 0) {
        // Selects Audio Source to use for recording based on selection
        // Input:
        // - audioCapToken (opt): Only for Phone_Output--Token received from Audio Capture request
        // - audioCapCode (opt): Only for Phone_Output--Code received from Audio Capture request
        // Parameter:
        // - recorder: Intent to start Service for selected recorder
        // - boundService: Service bound to this activity (used to call functions)
        // - serviceConn: Service Connection to the Recorder
        // Return:
        // - None
        if (selectedRecorder == "Mic") {
            Log.d(TAG, "Configuring Mic Recorder")
            recorder = Intent(this, MicRecorder::class.java)

            serviceConn = object: ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val binderBridge = service as MicRecorder.RecBinder
                    boundService = binderBridge.getService()
                    isBound = true
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    isBound = false
                    boundService = null
                }
            }
        } else if (selectedRecorder == "Phone_Output") {
            recorder = Intent(this, PhoneOutputRecorder::class.java)
            // Pass extracted Audio Capture Token, Code to Service to initialize Media Projection
            recorder.putExtra("AUDIO_CAP_TOKEN", audioCapToken)
            recorder.putExtra("AUDIO_CAP_CODE", audioCapCode)

            serviceConn = object: ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val binderBridge = service as PhoneOutputRecorder.RecBinder
                    boundService = binderBridge.getService()
                    isBound = true
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    isBound = false
                    boundService = null
                }
            }
        } else if (selectedRecorder == "USB") {
            recorder = Intent(this, UsbRecorder::class.java)
        } else {
            throw Exception("Unexpected Audio Source Selection!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the view
        setContentView(R.layout.activity_main)
        // Enable Phone Output button, disable Mic Button. Note: this should b consistent with
        // the selectedRecorder variable
        findViewById<Button>(R.id.micSelectButton).isEnabled = false
        findViewById<Button>(R.id.phoneOutputSelectButton).isEnabled = true

        // Handler for Audio Capture request
        captureAudioResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data is Intent) {
                // Success
                Log.d(TAG, "Audio Capture Request Granted!")

                // Initialize default recorder and start it
                switchRecorder(audioCapCode=result.resultCode, audioCapToken=result.data)
                Log.d(TAG, "Starting Service")
                bindService(recorder, serviceConn, Context.BIND_AUTO_CREATE)
            } else {
                throw Exception("Screen Capture Request denied!")
            }
        }

        // Launch request for required permissions
        requestPermissions(REQUIRED_PERMISSIONS, PERMISSIONS_REQ_CODE)
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

    // Callbacks
    fun onStartButtonClick(view: View) {
        try {
            Log.d(TAG, "Start button clicked")
            // Request approval for Screen/Audio Capture (Note: this returns to handler in onCreate)
            mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            captureAudioResultLauncher.launch(captureIntent)
        } catch (e: NoPermissions) {
            throw Exception("Audio Capture permission request rejected!")
        }

        // Disable start button and enable stop button
        findViewById<Button>(R.id.startButton).isEnabled = false
        findViewById<Button>(R.id.stopButton).isEnabled = true
    }

    fun onStopButtonClick(view: View) {
        // Print the captured value
        Log.d(TAG, "Buffer Data: ${boundService?.getBufferData().contentToString()}")
        unbindService(serviceConn)

        // Enable start button and disable stop button
        findViewById<Button>(R.id.startButton).isEnabled = true
        findViewById<Button>(R.id.stopButton).isEnabled = false
    }

    fun onMicSelButtonClick(view: View) {
        selectedRecorder = "Mic"

        // Enable Phone Output button, disable Mic Button
        findViewById<Button>(R.id.micSelectButton).isEnabled = false
        findViewById<Button>(R.id.phoneOutputSelectButton).isEnabled = true
    }

    fun onPhoneOutputSelButtonClick(view: View) {
        selectedRecorder = "Phone_Output"

        // Disable Phone Output button, enable Mic Button
        findViewById<Button>(R.id.micSelectButton).isEnabled = true
        findViewById<Button>(R.id.phoneOutputSelectButton).isEnabled = false
    }
}
