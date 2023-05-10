package com.industry.med

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat


interface BiometricAuthListener {
    fun onBiometricAuthenticateError(error: Int,errMsg: String)
    fun onBiometricAuthenticateSuccess(result: BiometricPrompt.AuthenticationResult)
}

object BiometricUtils {
    private fun hasBiometricCapability(context: Context): Int {
        return BiometricManager.from(context).canAuthenticate(DEVICE_CREDENTIAL or BIOMETRIC_STRONG or BIOMETRIC_WEAK)
    }

    fun isBiometricReady(context: Context): Boolean {
        return hasBiometricCapability(context) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun setBiometricPromptInfo(): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle("Вход")
            .setDescription("Используйте свой отпечаток пальца или Face ID")
            .setAllowedAuthenticators(DEVICE_CREDENTIAL or BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .build()
    }

    private fun initBiometricPrompt(
        activity: AppCompatActivity,
        listener: BiometricAuthListener
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                listener.onBiometricAuthenticateError(errorCode, errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                listener.onBiometricAuthenticateSuccess(result)
            }
        }

        return BiometricPrompt(activity, executor, callback)
    }

    fun showBiometricPrompt(
        activity: AppCompatActivity,
        listener: BiometricAuthListener,
        cryptoObject: BiometricPrompt.CryptoObject? = null,
    ) {
        val promptInfo = setBiometricPromptInfo()

        val biometricPrompt = initBiometricPrompt(activity, listener)

        biometricPrompt.apply {
            if (cryptoObject == null) authenticate(promptInfo)
            else authenticate(promptInfo, cryptoObject)
        }
    }
}