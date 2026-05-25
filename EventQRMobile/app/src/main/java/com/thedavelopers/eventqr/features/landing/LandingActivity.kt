package com.thedavelopers.eventqr.features.landing

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.Registration
import com.thedavelopers.eventqr.SignIn

open class LandingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash_modern)
        enableEdgeToEdge()

        Handler(Looper.getMainLooper()).postDelayed({
            showLandingContent()
        }, 2000)
    }

    private fun showLandingContent() {
        setContentView(R.layout.activity_landing)

        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)

        btnSignIn.setOnClickListener {
            startActivity(Intent(this, SignIn::class.java))
            finish()
        }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, Registration::class.java))
            finish()
        }
    }
}
