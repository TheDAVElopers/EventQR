package com.thedavelopers.eventqr.features.terms

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding

/** Static Terms & Conditions page, opened from the sign-up and event registration agreement text. */
class TermsActivity : AppCompatActivity() {
    private val sections = listOf(
        R.string.terms_s1_title to R.string.terms_s1_body,
        R.string.terms_s2_title to R.string.terms_s2_body,
        R.string.terms_s3_title to R.string.terms_s3_body,
        R.string.terms_s4_title to R.string.terms_s4_body,
        R.string.terms_s5_title to R.string.terms_s5_body,
        R.string.terms_s6_title to R.string.terms_s6_body,
        R.string.terms_s7_title to R.string.terms_s7_body,
        R.string.terms_s8_title to R.string.terms_s8_body,
        R.string.terms_s9_title to R.string.terms_s9_body,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        val content = findViewById<LinearLayout>(R.id.layoutTermsContent)
        val inflater = LayoutInflater.from(this)
        sections.forEachIndexed { index, (titleRes, bodyRes) ->
            val card = inflater.inflate(R.layout.item_terms_section, content, false)
            val title = getString(titleRes)
            card.findViewById<TextView>(R.id.txtTermsNumber).text = (index + 1).toString()
            card.findViewById<TextView>(R.id.txtTermsSectionTitle).text = title
            card.findViewById<TextView>(R.id.txtTermsSectionBody).setText(bodyRes)
            // One TalkBack stop for number + title instead of two.
            card.findViewById<LinearLayout>(R.id.layoutTermsTitleRow).contentDescription =
                getString(R.string.terms_section_content_description, index + 1, title)
            content.addView(card)
        }

        // Keep the last card clear of the gesture bar.
        content.applyEventQrBottomInsetPadding()
    }
}
