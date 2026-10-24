package com.thedavelopers.eventqr.features.terms

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color

object TermsLink {
    fun intent(context: Context): Intent = Intent(context, TermsActivity::class.java)

    /** Makes the first occurrence of [linkText] inside [target]'s text open the terms page; the rest keeps its normal behaviour. */
    fun apply(target: TextView, linkText: String) {
        val full = target.text.toString()
        val start = full.indexOf(linkText, ignoreCase = true)
        if (start < 0) return
        val span = SpannableString(full)
        span.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                widget.context.startActivity(intent(widget.context))
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = ContextCompat.getColor(target.context, R.color.primary)
                ds.isUnderlineText = true
            }
        }, start, start + linkText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        target.text = span
        target.movementMethod = LinkMovementMethod.getInstance()
        target.highlightColor = target.context.color(R.color.transparent)
    }
}
