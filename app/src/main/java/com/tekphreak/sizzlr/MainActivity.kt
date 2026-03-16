package com.tekphreak.sizzlr

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
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
import com.tekphreak.sizzlr.SettingsActivity.Companion.DEFAULT_BLACK_ON_WHITE
import com.tekphreak.sizzlr.SettingsActivity.Companion.DEFAULT_EMAIL
import com.tekphreak.sizzlr.SettingsActivity.Companion.DEFAULT_PROMPT_TOP
import com.tekphreak.sizzlr.SettingsActivity.Companion.KEY_PROMPT_TOP
import com.tekphreak.sizzlr.SettingsActivity.Companion.DEFAULT_FONT_SIZE
import com.tekphreak.sizzlr.SettingsActivity.Companion.DEFAULT_SPEED
import com.tekphreak.sizzlr.SettingsActivity.Companion.KEY_BLACK_ON_WHITE
import com.tekphreak.sizzlr.SettingsActivity.Companion.KEY_EMAIL
import com.tekphreak.sizzlr.SettingsActivity.Companion.KEY_FONT_SIZE
import com.tekphreak.sizzlr.SettingsActivity.Companion.KEY_SCRIPT
import com.tekphreak.sizzlr.SettingsActivity.Companion.KEY_SPEED
import com.tekphreak.sizzlr.SettingsActivity.Companion.PREFS_NAME
import com.tekphreak.sizzlr.databinding.ActivityMainBinding
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
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())
    private var timerSeconds = 0
    private var timerRunnable: Runnable? = null

    private var lastVideoUri: Uri? = null
    private var scrollAnimator: ValueAnimator? = null
    private var isCountingDown = false

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

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }

        setupButtons()
        showIdleUI()
    }

    override fun onResume() {
        super.onResume()
        binding.teleprompterText.text = prefs.getString(KEY_SCRIPT, "").orEmpty()
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

    // ── Countdown → Record ────────────────────────────────────────────────────

    private fun startCountdown() {
        isCountingDown = true
        binding.btnRecord.isEnabled = false
        val toneGen = try { ToneGenerator(AudioManager.STREAM_MUSIC, 90) } catch (e: Exception) { null }

        fun tick(count: Int) {
            if (!isCountingDown) {
                toneGen?.release()
                binding.countdownText.visibility = View.GONE
                binding.btnRecord.isEnabled = true
                return
            }
            if (count == 0) {
                binding.countdownText.visibility = View.GONE
                toneGen?.release()
                isCountingDown = false
                binding.btnRecord.isEnabled = true
                startRecording()
                return
            }
            binding.countdownText.text = count.toString()
            binding.countdownText.visibility = View.VISIBLE
            if (count >= 2) toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            handler.postDelayed({ tick(count - 1) }, 1000)
        }

        tick(5)
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
                            lastVideoUri = event.outputResults.outputUri
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

    private fun submitTape() {
        val uri = lastVideoUri
        val email = prefs.getString(KEY_EMAIL, DEFAULT_EMAIL) ?: DEFAULT_EMAIL

        if (uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "Sizzlr Audition Tape")
                putExtra(Intent.EXTRA_TEXT, "Please find my audition tape attached.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Send audition tape via…"))
        } else {
            Toast.makeText(this, "Tape saved to your gallery (Movies/Sizzlr).", Toast.LENGTH_SHORT).show()
        }

        showSuccessUI()
    }

    private fun setupButtons() {
        binding.btnRecord.setOnClickListener { startCountdown() }
        binding.btnStop.setOnClickListener { stopRecording() }
        binding.btnRetake.setOnClickListener { showIdleUI() }
        binding.btnSubmit.setOnClickListener { submitTape() }
        binding.btnNewClip.setOnClickListener { showIdleUI() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ── UI state machine ──────────────────────────────────────────────────────

    private fun showIdleUI() {
        isCountingDown = false
        stopTeleprompterScroll()
        resetTeleprompterPosition()
        binding.countdownText.visibility = View.GONE
        binding.teleprompterScroll.visibility = View.GONE
        binding.controlsInner.visibility = View.VISIBLE
        binding.scriptBox.visibility = View.VISIBLE
        binding.btnRecord.visibility = View.VISIBLE
        binding.btnRecord.isEnabled = videoCapture != null
        binding.btnStop.visibility = View.GONE
        binding.btnRetake.visibility = View.GONE
        binding.btnSubmit.visibility = View.GONE
        binding.successMsg.visibility = View.GONE
        binding.recIndicator.visibility = View.GONE
        binding.faceGuide.visibility = View.VISIBLE
    }

    private fun showRecordingUI() {
        val script = prefs.getString(KEY_SCRIPT, "").orEmpty()
        binding.countdownText.visibility = View.GONE
        binding.controlsInner.visibility = View.GONE

        if (script.isNotEmpty()) {
            // Apply font size
            val fontSize = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).toFloat()
            binding.teleprompterText.textSize = fontSize

            // Apply colour theme
            val blackOnWhite = prefs.getBoolean(KEY_BLACK_ON_WHITE, DEFAULT_BLACK_ON_WHITE)
            if (blackOnWhite) {
                binding.teleprompterScroll.setBackgroundColor(Color.WHITE)
                binding.teleprompterText.setTextColor(Color.BLACK)
            } else {
                binding.teleprompterScroll.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.zinc_900)
                )
                binding.teleprompterText.setTextColor(
                    ContextCompat.getColor(this, R.color.zinc_200)
                )
            }

            binding.teleprompterText.text = script

            // Apply position (top/bottom) before making visible so layout is correct
            val promptAtTop = prefs.getBoolean(KEY_PROMPT_TOP, DEFAULT_PROMPT_TOP)
            applyTeleprompterPosition(promptAtTop)

            binding.teleprompterScroll.visibility = View.VISIBLE
            startTeleprompterScroll()
        } else {
            binding.teleprompterScroll.visibility = View.GONE
        }

        binding.btnRecord.visibility = View.GONE
        binding.btnStop.visibility = View.VISIBLE
        binding.btnRetake.visibility = View.GONE
        binding.btnSubmit.visibility = View.GONE
        binding.successMsg.visibility = View.GONE
        binding.recIndicator.visibility = View.VISIBLE
        binding.faceGuide.visibility = View.GONE
    }

    private fun showReviewUI() {
        stopTeleprompterScroll()
        resetTeleprompterPosition()
        binding.teleprompterScroll.visibility = View.GONE
        binding.controlsInner.visibility = View.VISIBLE
        binding.scriptBox.visibility = View.VISIBLE
        binding.btnRecord.visibility = View.GONE
        binding.btnStop.visibility = View.GONE
        binding.btnRetake.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.VISIBLE
        binding.successMsg.visibility = View.GONE
        binding.recIndicator.visibility = View.GONE
        binding.faceGuide.visibility = View.GONE
    }

    private fun showSuccessUI() {
        binding.teleprompterScroll.visibility = View.GONE
        binding.controlsInner.visibility = View.VISIBLE
        binding.btnRecord.visibility = View.GONE
        binding.btnStop.visibility = View.GONE
        binding.btnRetake.visibility = View.GONE
        binding.btnSubmit.visibility = View.GONE
        binding.successMsg.visibility = View.VISIBLE
        binding.recIndicator.visibility = View.GONE
        binding.faceGuide.visibility = View.VISIBLE
    }

    // ── Teleprompter position (ConstraintSet swap) ────────────────────────────

    private fun applyTeleprompterPosition(atTop: Boolean) {
        val cs = ConstraintSet()
        cs.clone(binding.root)
        if (atTop) {
            // Teleprompter fills space between header and viewport
            cs.connect(R.id.teleprompterScroll, ConstraintSet.TOP, R.id.header, ConstraintSet.BOTTOM)
            cs.connect(R.id.teleprompterScroll, ConstraintSet.BOTTOM, R.id.viewportContainer, ConstraintSet.TOP)
            // Viewport moves below the teleprompter
            cs.connect(R.id.viewportContainer, ConstraintSet.TOP, R.id.teleprompterScroll, ConstraintSet.BOTTOM)
        } else {
            // Teleprompter fills space between viewport and bottom of screen
            cs.connect(R.id.teleprompterScroll, ConstraintSet.TOP, R.id.viewportContainer, ConstraintSet.BOTTOM)
            cs.connect(R.id.teleprompterScroll, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            // Viewport sits below header
            cs.connect(R.id.viewportContainer, ConstraintSet.TOP, R.id.header, ConstraintSet.BOTTOM)
        }
        cs.applyTo(binding.root)
    }

    private fun resetTeleprompterPosition() {
        val cs = ConstraintSet()
        cs.clone(binding.root)
        cs.connect(R.id.viewportContainer, ConstraintSet.TOP, R.id.header, ConstraintSet.BOTTOM)
        cs.connect(R.id.teleprompterScroll, ConstraintSet.TOP, R.id.viewportContainer, ConstraintSet.BOTTOM)
        cs.connect(R.id.teleprompterScroll, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        cs.applyTo(binding.root)
    }

    // ── Teleprompter scroll ───────────────────────────────────────────────────

    private fun startTeleprompterScroll() {
        val scrollView = binding.teleprompterScroll
        scrollView.scrollTo(0, 0)

        // Wait for the view to be fully laid out before measuring scroll range
        scrollView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    val content = scrollView.getChildAt(0) ?: return
                    val scrollRange = content.height - scrollView.height
                    if (scrollRange <= 0) return

                    val speed = prefs.getInt(KEY_SPEED, DEFAULT_SPEED)
                    val pixelsPerSecond = speed * 20   // 1–10 → 20–200 px/s
                    val durationMs = scrollRange * 1000L / pixelsPerSecond

                    scrollAnimator = ValueAnimator.ofInt(0, scrollRange).apply {
                        duration = durationMs
                        interpolator = LinearInterpolator()
                        addUpdateListener { anim ->
                            scrollView.scrollTo(0, anim.animatedValue as Int)
                        }
                        start()
                    }
                }
            }
        )
    }

    private fun stopTeleprompterScroll() {
        scrollAnimator?.cancel()
        scrollAnimator = null
        binding.teleprompterScroll.scrollTo(0, 0)
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
        isCountingDown = false
        cameraExecutor.shutdown()
        stopTimer()
        stopTeleprompterScroll()
    }

    companion object {
        private const val TAG = "Sizzlr"
    }
}
