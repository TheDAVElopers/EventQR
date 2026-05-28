package com.thedavelopers.eventqr.features.auth.changepassword

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.auth.login.LoginActivity

open class ChangePasswordActivity : AppCompatActivity(), ChangePasswordContract.View {
    private lateinit var presenter: ChangePasswordPresenter
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var resetButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        presenter = ChangePasswordPresenter(this)
        presenter.attach(this)

        currentPasswordInput = findViewById(R.id.edtCurrentPass)
        newPasswordInput = findViewById(R.id.edtNewPass)
        confirmPasswordInput = findViewById(R.id.edtConfirmPass)
        resetButton = findViewById(R.id.btnResetPass)

        resetButton.setOnClickListener {
            presenter.submitChange(
                currentPasswordInput.text.toString(),
                newPasswordInput.text.toString(),
                confirmPasswordInput.text.toString(),
            )
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        resetButton.isEnabled = !isLoading
        resetButton.text = if (isLoading) "Resetting..." else "Reset Password"
    }

    override fun showCurrentPasswordError(message: String?) {
        currentPasswordInput.error = message
    }

    override fun showNewPasswordError(message: String?) {
        newPasswordInput.error = message
    }

    override fun showConfirmPasswordError(message: String?) {
        confirmPasswordInput.error = message
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateBackToSignIn() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}