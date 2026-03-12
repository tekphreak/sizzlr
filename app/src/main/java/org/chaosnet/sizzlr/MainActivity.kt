package org.chaosnet.sizzlr

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import org.chaosnet.sizzlr.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private val handler = Handler(Looper.getMainLooper())
    private var timerSeconds = 0
    private var timerRunnable: Runnable? = null

    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                "Camera and microphone access are required to record auditions.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setupButtons()
        showIdleUI()
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Prefer front camera; fall back to back if front unavailable
            val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
                binding.btnRecord.isEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val vc = videoCapture ?: return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "Sizzlr_$timestamp"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Sizzlr")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        recording = vc.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        showRecordingUI()
                        startTimer()
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopTimer()
                        if (!event.hasError()) {
                            showReviewUI()
                        } else {
                            Log.e(TAG, "Recording error: ${event.error}")
                            Toast.makeText(this, "Recording failed. Please try again.", Toast.LENGTH_SHORT).show()
                            recording?.close()
                            recording = null
                            showIdleUI()
                        }
                    }
                    else -> Unit
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun setupButtons() {
        binding.btnRecord.setOnClickListener { startRecording() }
        binding.btnStop.setOnClickListener { stopRecording() }
        binding.btnRetake.setOnClickListener { showIdleUI() }
        binding.btnSubmit.setOnClickListener {
            Toast.makeText(this, "Tape saved to your gallery (Movies/Sizzlr).", Toast.LENGTH_SHORT).show()
            showSuccessUI()
        }
        binding.btnNewClip.setOnClickListener { showIdleUI() }
    }

    // ── UI state machine ──────────────────────────────────────────────────────

    private fun showIdleUI() {
        binding.btnRecord.visibility = View.VISIBLE
        binding.btnStop.visibility = View.GONE
        binding.actionButtons.visibility = View.GONE
        binding.successMsg.visibility = View.GONE
        binding.recIndicator.visibility = View.GONE
        binding.faceGuide.visibility = View.VISIBLE
    }

    private fun showRecordingUI() {
        binding.btnRecord.visibility = View.GONE
        binding.btnStop.visibility = View.VISIBLE
        binding.actionButtons.visibility = View.GONE
        binding.successMsg.visibility = View.GONE
        binding.recIndicator.visibility = View.VISIBLE
        binding.faceGuide.visibility = View.GONE
    }

    private fun showReviewUI() {
        binding.btnRecord.visibility = View.GONE
        binding.btnStop.visibility = View.GONE
        binding.actionButtons.visibility = View.VISIBLE
        binding.successMsg.visibility = View.GONE
        binding.recIndicator.visibility = View.GONE
        binding.faceGuide.visibility = View.GONE
    }

    private fun showSuccessUI() {
        binding.btnRecord.visibility = View.GONE
        binding.btnStop.visibility = View.GONE
        binding.actionButtons.visibility = View.GONE
        binding.successMsg.visibility = View.VISIBLE
        binding.recIndicator.visibility = View.GONE
        binding.faceGuide.visibility = View.VISIBLE
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private fun startTimer() {
        timerSeconds = 0
        timerRunnable = object : Runnable {
            override fun run() {
                val m = timerSeconds / 60
                val s = timerSeconds % 60
                binding.timer.text = String.format("%02d:%02d", m, s)
                timerSeconds++
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopTimer()
    }

    companion object {
        private const val TAG = "Sizzlr"
    }
}
