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

    private lateinit var binding: ActivityMainBinding

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат виден в логах системы */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        binding.btnSwitchKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        binding.btnActivate.setOnClickListener { tryActivate() }

        refreshActivationState()
    }

    override fun onResume() {
        super.onResume()
        refreshActivationState()
    }

    private fun refreshActivationState() {
        val activated = License.isActivated(this)
        binding.activationGroup.visibility = if (activated) android.view.View.GONE else android.view.View.VISIBLE
        binding.stepsGroup.visibility = if (activated) android.view.View.VISIBLE else android.view.View.GONE

        if (activated &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun tryActivate() {
        val key = binding.etLicenseKey.text.toString()
        binding.tvActivationError.visibility = android.view.View.GONE
        binding.btnActivate.isEnabled = false
        binding.btnActivate.text = getString(R.string.activation_checking)

        License.activateAsync(this, key) { result ->
            binding.btnActivate.isEnabled = true
            binding.btnActivate.text = getString(R.string.activation_button)

            when (result) {
                is ActivationResult.Success -> refreshActivationState()
                is ActivationResult.InvalidKey -> showActivationError(R.string.activation_error)
                is ActivationResult.DeviceLimitReached -> showActivationError(R.string.activation_error_limit)
                is ActivationResult.NetworkError -> showActivationError(R.string.activation_error_network)
            }
        }
    }

    private fun showActivationError(resId: Int) {
        binding.tvActivationError.text = getString(resId)
        binding.tvActivationError.visibility = android.view.View.VISIBLE
    }
}
