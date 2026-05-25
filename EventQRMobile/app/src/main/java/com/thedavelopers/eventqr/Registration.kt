package com.thedavelopers.eventqr

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class Registration : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val edtFirstName = findViewById<EditText>(R.id.edtFirstName)
        val edtLastName = findViewById<EditText>(R.id.edtLastName)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtConfirmPassword = findViewById<EditText>(R.id.edtConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)

        btnRegister.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
        }

        btnSignIn.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
        }
    }
}