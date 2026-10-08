package com.thedavelopers.eventqr.features.organizer.notifications

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class NotificationManagementActivityTest {

    @Test
    fun rightAfterOpening_onlyTheSkeletonIsVisible_notTheEmptyMessageToo() {
        val activity = Robolectric.buildActivity(NotificationManagementActivity::class.java).create().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.skeletonLoading).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutNotificationEmpty).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutNotificationError).visibility)
    }
}
