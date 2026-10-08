package com.thedavelopers.eventqr.features.auth.forgotpassword

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.auth.login.LoginActivity

open class ForgotPasswordActivity : AppCompatActivity(), ForgotPasswordContract.View {
    private lateinit var presenter: ForgotPasswordPresenter
    private lateinit var emailInput: EditText
    private lateinit var sendButton: Button
    private lateinit var backButton: android.widget.ImageButton
    private lateinit var formLayout: LinearLayout
    private lateinit var confirmationLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        presenter = ForgotPasswordPresenter()
        emailInput = findViewById(R.id.editEmail)
        sendButton = findViewById(R.id.btnSendResetLink)
        backButton = findViewById(R.id.btnBackToSignIn)
        formLayout = findViewById(R.id.layoutForm)
        confirmationLayout = findViewById(R.id.layoutConfirmation)
        presenter.attach(this, this)

        sendButton.setOnClickListener {
            presenter.submitRequest(emailInput.text.toString())
        }

        backButton.setOnClickListener {
            presenter.backToSignIn()
        }

        findViewById<Button>(R.id.btnBackToSignInConfirmation).setOnClickListener {
            presenter.backToSignIn()
        }

        findViewById<View>(R.id.tvBackToSignIn).setOnClickListener {
            presenter.backToSignIn()
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        sendButton.isEnabled = !isLoading
        sendButton.text = getString(if (isLoading) R.string.forgot_password_sending else R.string.forgot_password_send_reset_link)
    }

    override fun showEmailError(message: String?) {
        emailInput.error = message
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showConfirmation() {
        formLayout.visibility = View.GONE
        confirmationLayout.visibility = View.VISIBLE
    }

    override fun navigateBackToSignIn() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
