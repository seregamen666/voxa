package com.voxa.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.voxa.dictation.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат виден в логах системы */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding.btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        binding.btnSwitchKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }
}
