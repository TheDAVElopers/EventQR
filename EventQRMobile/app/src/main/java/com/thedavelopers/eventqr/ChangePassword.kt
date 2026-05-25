package com.thedavelopers.eventqr

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ChangePassword : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val edtCurrentPassowrd = findViewById<EditText>(R.id.edtCurrentPass)
        val edtNewPassword = findViewById<EditText>(R.id.edtNewPass)
        val edtConfirmPassword = findViewById<EditText>(R.id.edtNewPass)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPass)

        btnResetPassword.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
        }
    }
}