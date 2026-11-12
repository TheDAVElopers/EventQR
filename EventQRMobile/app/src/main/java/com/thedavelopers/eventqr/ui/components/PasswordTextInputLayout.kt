package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.textfield.TextInputLayout
import com.thedavelopers.eventqr.ui.icons.AppIcons

/**
 * The password field's end-icon host (§5.2, §6.1). Material owns the toggle: it swaps the input
 * type, flips the end icon's `checked` state and exposes its own show/hide description, so the
 * eye is a real 48dp target instead of a touch zone on the text field.
 *
 * The drawable is a state list built from [AppIcons] rather than an XML selector, because the
 * icon contract allows `@drawable/ic_ui_*` references only from layouts, menus and styles
 * (`tools/check-tokens.ps1`, icons) - a second copy of either eye path would be a second address.
 */
class PasswordTextInputLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextInputLayout(context, attrs, defStyleAttr) {

    init {
        val toggle = StateListDrawable().apply {
            // Checked is "password revealed": the crossed eye is what the next tap will do.
            addState(intArrayOf(android.R.attr.state_checked), AppCompatResources.getDrawable(context, AppIcons.VisibilityOff))
            addState(IntArray(0), AppCompatResources.getDrawable(context, AppIcons.Visibility))
        }
        setEndIconDrawable(toggle)
    }
}
