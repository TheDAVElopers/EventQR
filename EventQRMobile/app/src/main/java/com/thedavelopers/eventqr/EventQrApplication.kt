package com.thedavelopers.eventqr

import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import java.util.Locale

class EventQrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppBackButtonStyler())
    }

    private class AppBackButtonStyler : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            styleBackButtons(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            styleBackButtons(activity)
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        private fun styleBackButtons(activity: Activity) {
            val root = activity.window?.decorView ?: return
            root.post {
                applyBackButtonStyle(activity, root)
            }
        }

        private fun applyBackButtonStyle(activity: Activity, view: View) {
            if (isBackView(activity, view)) {
                when (view) {
                    is ImageButton -> styleBackImage(activity, view)
                    is ImageView -> styleBackImage(activity, view)
                }
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    applyBackButtonStyle(activity, view.getChildAt(index))
                }
            }
        }

        private fun styleBackImage(activity: Activity, view: ImageView) {
            view.setImageResource(R.drawable.back_btn)
            view.imageTintList = ColorStateList.valueOf(Color.parseColor("#111827"))
            view.background = null
            view.setBackgroundResource(resolveBorderlessRipple(activity))
            view.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10))
            view.minimumWidth = dp(activity, 40)
            view.minimumHeight = dp(activity, 40)

            val params = view.layoutParams
            if (params != null) {
                params.width = dp(activity, 40)
                params.height = dp(activity, 40)
                view.layoutParams = params
            }

            val parent = view.parent as? View
            if (isBackView(activity, parent)) {
                parent.background = null
                parent.setBackgroundResource(resolveBorderlessRipple(activity))
                parent.minimumWidth = dp(activity, 40)
                parent.minimumHeight = dp(activity, 40)
            }
        }

        private fun isBackView(activity: Activity, view: View?): Boolean {
            if (view == null) return false
            val description = view.contentDescription?.toString()?.trim()?.lowercase(Locale.ENGLISH)
            if (description == "back") return true
            val entryName = runCatching {
                if (view.id != View.NO_ID) activity.resources.getResourceEntryName(view.id) else ""
            }.getOrDefault("").lowercase(Locale.ENGLISH)
            return entryName.contains("back") && (view is ImageButton || view is ImageView)
        }

        private fun resolveBorderlessRipple(activity: Activity): Int {
            val value = TypedValue()
            return if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)) {
                value.resourceId
            } else {
                android.R.color.transparent
            }
        }

        private fun dp(activity: Activity, value: Int): Int {
            return (value * activity.resources.displayMetrics.density).toInt()
        }
    }
}
