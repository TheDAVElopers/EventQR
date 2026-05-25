package com.thedavelopers.eventqr

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SignIn : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val etEmail = findViewById<EditText>(R.id.edtEmail)
        val etPassword = findViewById<EditText>(R.id.edtPassword)

        btnSignIn.setOnClickListener {
            val intent = Intent(this, Dashboard::class.java)
            startActivity(intent)
        }

        btnRegister.setOnClickListener {
            val intent = Intent(this, Registration::class.java)
            startActivity(intent)
        }
    }
}