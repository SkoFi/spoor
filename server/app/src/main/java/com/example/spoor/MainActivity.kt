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
    private lateinit var audioRecordValueTextView: TextView
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
    private fun switchRecorder(audio_source: String = "Phone_Output", audio_cap_token: Intent? = null,
                               audio_cap_code: Int = 0) {
        if (audio_source == "Mic") {
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
        } else if (audio_source == "Phone_Output") {
            recorder = Intent(this, PhoneOutputRecorder::class.java)
            // Pass extracted Audio Capture Token, Code to Service to initialize Media Projection
            recorder.putExtra("AUDIO_CAP_TOKEN", audio_cap_token)
            recorder.putExtra("AUDIO_CAP_CODE", audio_cap_code)

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
        } else if (audio_source == "USB") {
            recorder = Intent(this, UsbRecorder::class.java)
        } else {
            throw Exception("Unexpected Audio Source Selection!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        audioRecordValueTextView = findViewById(R.id.audioRecordValueTextView)

        // Handle request permissions for Audio Capture
        captureAudioResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data is Intent) {
                // Success
                Log.d(TAG, "Audio Capture Request Granted!")

                // Initialize default recorder and start it
                switchRecorder(audio_cap_code=result.resultCode, audio_cap_token=result.data)
                Log.d(TAG, "Starting Service")
                // startService(recorder)
                bindService(recorder, serviceConn, Context.BIND_AUTO_CREATE)
            } else {
                throw Exception("Screen Capture Request denied!")
            }
        }
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
            Log.d(TAG, "In start")
            // Request Media Projection
            mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            // Request approval for Screen/Audio Capture
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            captureAudioResultLauncher.launch(captureIntent)

        } catch (e: NoPermissions) {
            Log.d(TAG, "In catch block")
            //.requestPermissions(this, recorder.REQUIRED_PERMISSIONS, PERMISSIONS_REQ_CODE)
            Log.d(TAG, "Post request")
            return
        }

        // Clear the TextView text
        audioRecordValueTextView.text = ""

        // Disable start button and enable stop button
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        startButton.isEnabled = false
        stopButton.isEnabled = true
    }

    fun onStopButtonClick(view: View) {
        // Display the captured value
        audioRecordValueTextView.text = boundService?.getBufferData().toString()
        unbindService(serviceConn)

        // Disable start button and enable stop button
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }
}

