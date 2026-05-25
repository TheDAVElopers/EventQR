package com.thedavelopers.eventqr

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class Landing : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Keep the splash screen on-screen while we set up our custom gradient
        splashScreen.setKeepOnScreenCondition { true }
        
        // Show the custom gradient layout
        setContentView(R.layout.activity_splash_modern)
        enableEdgeToEdge()

        // Transition to main content after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            showLandingContent()
            splashScreen.setKeepOnScreenCondition { false }
        }, 2000)
    }

    private fun showLandingContent() {
        setContentView(R.layout.activity_landing)
        
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)

        btnSignIn.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
            finish()
        }

        btnCreateAccount.setOnClickListener {
            val intent = Intent(this, Registration::class.java)
            startActivity(intent)
            finish()
        }
    }
}