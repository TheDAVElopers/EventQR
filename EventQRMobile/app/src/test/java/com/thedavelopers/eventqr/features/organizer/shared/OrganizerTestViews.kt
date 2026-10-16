package com.thedavelopers.eventqr.features.organizer

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.util.UUID

/** Every visible TextView text under [this] (buttons included), for asserting on code-built organizer screens. */
internal fun View.allTexts(): List<String> {
    if (visibility != View.VISIBLE) return emptyList()
    val own = (this as? TextView)?.text?.toString()?.let { listOf(it) }.orEmpty()
    val children = (this as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { group.getChildAt(it).allTexts() } }.orEmpty()
    return own + children
}

internal fun View.findButton(label: String): Button? {
    if (this is Button && text.toString() == label) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findButton(label)?.let { return it }
    return null
}

internal fun testOrganizerEvent(title: String = "Fest", status: String = "Approved") = OrganizerMvpEvent(
    id = UUID.randomUUID().toString(), title = title, organizerName = "Org", dateTime = "-", shortDate = "-",
    venue = "Hall", status = status, submittedDate = "-", adminRemarks = "", additionalOrganizers = emptyList(),
    registeredCount = 0, enteredCount = 0, attendedCount = 0, exitedCount = 0, noShowCount = 0,
    totalTransactions = 0, successfulScans = 0, rejectedScans = 0, benefitClaims = 0, boothSessionVisits = 0,
    rewardRedemptions = 0, totalPointsAwarded = 0, idTemplateStatus = "-", rewardsStatus = "Enabled",
    staffCount = 0, scanPurposesCount = 0,
)
