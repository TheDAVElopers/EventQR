package com.thedavelopers.eventqr.features.transactions

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The staff transaction list keys its row title by scan purpose (§6.3, B2): a row must never fall
 * back to a raw enum name such as "Id print", and no two purposes share one label.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TransactionTitleLabelTest {
    @Test
    fun everyScanTypeHasItsOwnLabel_andNoneFallsBackToTheEnumName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = TextView(context)

        val labels = TransactionType.values().associateWith { mapTransactionTitle(view, it) }
        assertEquals("Entry", labels[TransactionType.ENTRY])
        assertEquals("ID Print", labels[TransactionType.ID_PRINT])
        assertEquals("Benefit Claim", labels[TransactionType.BENEFIT_CLAIM])
        assertEquals("Registration", labels[TransactionType.REGISTRATION])

        labels.forEach { (type, label) ->
            assertNotEquals("$type fell back to its enum name", type.name, label)
        }
    }
}
