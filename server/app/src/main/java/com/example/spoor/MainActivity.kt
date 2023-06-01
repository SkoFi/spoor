package com.example.spoor

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.Button
import android.widget.TextView


class MainActivity : AppCompatActivity() {
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private var isRecording = false
    private lateinit var audioRecord: AudioRecord
    private lateinit var outputBuffer: ByteArray
    private lateinit var audioRecordValueTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioRecordValueTextView = findViewById(R.id.audioRecordValueTextView)

        // Request runtime permission for recording audio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    // Start recording
    private fun startRecording() {
        // Create AudioRecord instance and start recording
        val bufferSize = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        audioRecord.startRecording()

        // Start capturing audio data and processing it
        isRecording = true
        val buffer = ShortArray(bufferSize / 2)
        outputBuffer = ByteArray(bufferSize * 2) // Each short occupies 2 bytes
        Thread {
            while (isRecording) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)

                // Copy the short buffer to the output byte buffer
                for (i in 0 until bytesRead) {
                    val sample = buffer[i]
                    outputBuffer[i * 2] = (sample.toInt() and 0xFF).toByte() // Least significant byte
                    outputBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte() // Most significant byte
                }

                // Process the audio data as needed
            }
        }.start()
    }


    // Stop recording
    private fun stopRecording() {
        isRecording = false
        audioRecord.stop()
        audioRecord.release()
    }

    // Handle the result of permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start recording
                startRecording()
            } else {
                // Permission denied, handle accordingly
            }
        }
    }

    // Callbacks
    fun onStartButtonClick(view: View) {
        startRecording()

        // Clear the TextView text
        audioRecordValueTextView.text = ""

        // Disable start button and enable stop button
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        startButton.isEnabled = false
        stopButton.isEnabled = true
    }

    fun onStopButtonClick(view: View) {
        stopRecording()

        // Display the captured value
        audioRecordValueTextView.text = outputBuffer.contentToString()
    }
}
