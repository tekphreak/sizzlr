package com.tekphreak.sizzlr

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tekphreak.sizzlr.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            binding.scriptInput.setText(text)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read file.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Email
        binding.emailInput.setText(prefs.getString(KEY_EMAIL, DEFAULT_EMAIL))

        // Scroll speed (SeekBar 0–9 → display 1–10)
        val savedSpeed = prefs.getInt(KEY_SPEED, DEFAULT_SPEED)
        binding.speedSeekBar.progress = savedSpeed - 1
        binding.speedLabel.text = "SCROLL SPEED: $savedSpeed"
        binding.speedSeekBar.setOnSeekBarChangeListener(seekBarLabel(binding.speedLabel, "SCROLL SPEED") { it + 1 })

        // Font size (SeekBar 0–20 → display 16–36 sp)
        val savedFontSize = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        binding.fontSizeSeekBar.progress = savedFontSize - 16
        binding.fontSizeLabel.text = "FONT SIZE: $savedFontSize"
        binding.fontSizeSeekBar.setOnSeekBarChangeListener(seekBarLabel(binding.fontSizeLabel, "FONT SIZE") { it + 16 })

        // Text theme
        binding.switchBlackOnWhite.isChecked = prefs.getBoolean(KEY_BLACK_ON_WHITE, DEFAULT_BLACK_ON_WHITE)

        // Teleprompter position
        binding.switchPromptTop.isChecked = prefs.getBoolean(KEY_PROMPT_TOP, DEFAULT_PROMPT_TOP)

        // Script
        binding.scriptInput.setText(prefs.getString(KEY_SCRIPT, ""))

        binding.btnLoadFile.setOnClickListener { filePicker.launch("*/*") }
        binding.btnSave.setOnClickListener { saveAndExit() }
        binding.btnBack.setOnClickListener { finish() }
    }

    /** Helper that wires a SeekBar to update a label using [display] to map progress → value. */
    private fun seekBarLabel(
        label: android.widget.TextView,
        prefix: String,
        display: (Int) -> Int
    ) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            label.text = "$prefix: ${display(progress)}"
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun saveAndExit() {
        val email = binding.emailInput.text.toString().trim()
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInput.error = "Enter a valid email address"
            return
        }
        prefs.edit()
            .putString(KEY_EMAIL, email.ifEmpty { DEFAULT_EMAIL })
            .putInt(KEY_SPEED, binding.speedSeekBar.progress + 1)
            .putInt(KEY_FONT_SIZE, binding.fontSizeSeekBar.progress + 16)
            .putBoolean(KEY_BLACK_ON_WHITE, binding.switchBlackOnWhite.isChecked)
            .putBoolean(KEY_PROMPT_TOP, binding.switchPromptTop.isChecked)
            .putString(KEY_SCRIPT, binding.scriptInput.text.toString())
            .apply()
        Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val PREFS_NAME = "sizzlr_prefs"
        const val KEY_EMAIL = "email_address"
        const val KEY_SPEED = "scroll_speed"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_BLACK_ON_WHITE = "black_on_white"
        const val KEY_PROMPT_TOP = "prompt_top"
        const val KEY_SCRIPT = "script_text"
        const val DEFAULT_EMAIL = "michaelrhogan@gmail.com"
        const val DEFAULT_SPEED = 3
        const val DEFAULT_FONT_SIZE = 22
        const val DEFAULT_BLACK_ON_WHITE = false
        const val DEFAULT_PROMPT_TOP = false
    }
}
