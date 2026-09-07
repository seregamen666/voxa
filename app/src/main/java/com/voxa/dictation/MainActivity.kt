package com.voxa.dictation

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
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

    private fun imeComponentName(): ComponentName =
        ComponentName(this, DictationInputMethodService::class.java)

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val target = imeComponentName()
        return imm.enabledInputMethodList.any {
            it.packageName == target.packageName && it.serviceName == target.className
        }
    }

    private fun isImeSelected(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return false
        // Система может хранить короткую форму ("pkg/.ClassName"), поэтому
        // сравниваем разобранные ComponentName, а не сырые строки.
        return ComponentName.unflattenFromString(current) == imeComponentName()
    }

    private fun refreshActivationState() {
        val activated = License.isActivated(this)
        binding.activationGroup.visibility = if (activated) View.GONE else View.VISIBLE
        binding.stepsGroup.visibility = if (activated) View.VISIBLE else View.GONE

        if (activated &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        if (activated) refreshStepsState()
    }

    private fun refreshStepsState() {
        val enabled = isImeEnabled()
        val selected = enabled && isImeSelected()

        markStep(
            done = enabled,
            number = "1",
            badge = binding.badgeStep1,
            subtitle = binding.subtitleStep1,
            doneText = getString(R.string.step1_subtitle_done),
            todoText = getString(R.string.step1_subtitle),
        )
        markStep(
            done = selected,
            number = "2",
            badge = binding.badgeStep2,
            subtitle = binding.subtitleStep2,
            doneText = getString(R.string.step2_subtitle_done),
            todoText = getString(R.string.step2_subtitle),
        )
    }

    private fun markStep(
        done: Boolean,
        number: String,
        badge: android.widget.TextView,
        subtitle: android.widget.TextView,
        doneText: String,
        todoText: String,
    ) {
        if (done) {
            badge.text = "✓"
            badge.setBackgroundResource(R.drawable.bg_step_done)
            subtitle.text = doneText
            subtitle.setTextColor(ContextCompat.getColor(this, R.color.good))
        } else {
            badge.text = number
            badge.setBackgroundResource(R.drawable.bg_step_number)
            subtitle.text = todoText
            subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun tryActivate() {
        val key = binding.etLicenseKey.text.toString()
        binding.tvActivationError.visibility = View.GONE
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
        binding.tvActivationError.visibility = View.VISIBLE
    }
}
