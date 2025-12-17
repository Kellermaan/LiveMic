package com.example.livemic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/**
 * Main UI for controlling audio streaming and showing runtime status.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var bluetoothStatusText: TextView
    private lateinit var recordingStatusText: TextView
    private lateinit var recordingTimer: TextView
    private lateinit var outputFilePath: TextView
    private lateinit var bluetoothIndicator: View
    private lateinit var recordingIndicator: View

    private val requiredPermissions = arrayOf(Manifest.permission.RECORD_AUDIO).let {
        if (Build.VERSION.SDK_INT >= 31) it + arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else it
    }

    private var isRecording = false
    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    @Volatile private var monitorBluetooth = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupClickListeners()
        startBluetoothMonitor()
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorBluetooth = false
        stopTimer()
    }

    private fun initializeViews() {
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        bluetoothStatusText = findViewById(R.id.bluetoothStatusText)
        recordingStatusText = findViewById(R.id.recordingStatusText)
        recordingTimer = findViewById(R.id.recordingTimer)
        outputFilePath = findViewById(R.id.outputFilePath)
        bluetoothIndicator = findViewById(R.id.bluetoothStatusIndicator)
        recordingIndicator = findViewById(R.id.recordingStatusIndicator)

        // Initial UI state
        recordingStatusText.text = "Idle"
        recordingTimer.text = "00:00:00"
        outputFilePath.text = "No active recording"
        setRecordingIndicator(false)
    }

    private fun setupClickListeners() {
        startBtn.setOnClickListener { handleStartClick() }
        stopBtn.setOnClickListener { handleStopClick() }
    }

    private fun handleStartClick() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CODE_PERMISSIONS)
            return
        }

        val btInfo = checkBluetoothConnection()
        if (!btInfo.isConnected) {
            bluetoothStatusText.text = "No Bluetooth device connected"
            setBluetoothIndicator(false)
            return
        }

        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        recordingStatusText.text = "Recording"
        outputFilePath.text = "Recording to app external files/Records"
        startBtn.isEnabled = false
        setRecordingIndicator(true)

        startService(Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_START })
        startTimer()
    }

    private fun handleStopClick() {
        isRecording = false
        recordingStatusText.text = "Idle"
        startBtn.isEnabled = true
        stopService(Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_STOP })
        stopTimer()
        setRecordingIndicator(false)
        outputFilePath.text = "No active recording"
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    val seconds = (elapsed / 1000) % 60
                    val minutes = (elapsed / 60000) % 60
                    val hours = elapsed / 3600000
                    recordingTimer.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        recordingTimer.text = "00:00:00"
    }

    private fun hasPermissions(): Boolean {
        for (p in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission not granted: $p")
                return false
            }
        }
        return true
    }

    private data class BluetoothInfo(val isConnected: Boolean, val status: String)

    private fun checkBluetoothConnection(): BluetoothInfo {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return BluetoothInfo(false, "Bluetooth unavailable")
        if (!adapter.isEnabled) return BluetoothInfo(false, "Bluetooth disabled")

        val headsetState = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
        val a2dpState = adapter.getProfileConnectionState(BluetoothProfile.A2DP)

        return when {
            headsetState == BluetoothProfile.STATE_CONNECTED -> BluetoothInfo(true, "Connected (SCO)")
            a2dpState == BluetoothProfile.STATE_CONNECTED -> BluetoothInfo(true, "Connected (A2DP)")
            else -> BluetoothInfo(false, "No device connected")
        }
    }

    private fun startBluetoothMonitor() {
        thread {
            while (monitorBluetooth) {
                val info = checkBluetoothConnection()
                runOnUiThread {
                    bluetoothStatusText.text = info.status
                    setBluetoothIndicator(info.isConnected)
                }
                Thread.sleep(2000)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            handleStartClick()
        }
    }

    private fun setBluetoothIndicator(connected: Boolean) {
        val color = if (connected) android.R.color.holo_green_light else android.R.color.holo_red_light
        bluetoothIndicator.setBackgroundColor(ContextCompat.getColor(this, color))
    }

    private fun setRecordingIndicator(active: Boolean) {
        val color = if (active) android.R.color.holo_red_light else android.R.color.holo_green_light
        recordingIndicator.setBackgroundColor(ContextCompat.getColor(this, color))
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }
}
