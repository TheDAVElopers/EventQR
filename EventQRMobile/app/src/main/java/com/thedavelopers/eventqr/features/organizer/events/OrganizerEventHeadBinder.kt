package com.thedavelopers.eventqr.features.organizer.events

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.features.organizer.bindEventBadge

/**
 * Binder for [R.layout.include_organizer_event_head] (§4.6, component inventory §1a) - the one
 * identity head shared by the organizer event view and the event editor.
 *
 *     OrganizerEventHeadBinder.bind(
 *         findViewById(R.id.oeHead),
 *         title = event.title.orEmpty(),
 *         status = status,
 *         whenText = whenLine,
 *         whereText = event.venue,
 *     )
 *
 * Both meta lines are optional and `GONE` when blank, so a head with no venue shrinks to the title
 * row instead of rendering a placeholder. `whenText` / `whereText` are named for the Kotlin keyword.
 *
 * Reflow: at `fontScale > 1.3` the badge leaves the title row and becomes the meta block's last
 * line (`space_8` above, no start margin). Beside a 3-line 28sp title at 2.0 a trailing badge leaves
 * roughly 90dp for the name, so the badge moves rather than squeezing the title.
 *
 * TalkBack (§9.5): the name and status are one node (`{name}, {status}`), the badge's tone is never
 * read, and each meta line is its own node.
 */
object OrganizerEventHeadBinder {

    /** Above this font scale the badge cannot share the title's line without starving the title. */
    private const val BADGE_REFLOW_FONT_SCALE = 1.3f

    fun bind(
        root: View,
        title: CharSequence,
        status: EventStatus? = null,
        whenText: CharSequence? = null,
        whereText: CharSequence? = null,
    ) {
        val titleRow = root.findViewById<ViewGroup>(R.id.oeHeadTitleRow)
        val metaBlock = root.findViewById<ViewGroup>(R.id.oeHeadMeta)
        val titleView = root.findViewById<TextView>(R.id.oeHeadTitle)
        val badge = root.findViewById<TextView>(R.id.oeHeadStatus)
        val context = root.context

        titleView.text = title
        if (status != null) {
            badge.visibility = View.VISIBLE
            badge.bindEventBadge(status)
        } else {
            badge.visibility = View.GONE
        }

        // One node for the name plus status; the badge's text is already in that description.
        val statusText = badge.text?.toString().orEmpty()
        titleRow.contentDescription =
            if (badge.visibility == View.VISIBLE && statusText.isNotBlank()) {
                "${title}, $statusText"
            } else {
                title.toString()
            }
        titleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        badge.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        ViewCompat.setScreenReaderFocusable(titleRow, true)

        val reflow = root.resources.configuration.fontScale > BADGE_REFLOW_FONT_SCALE
        if (reflow && badge.visibility == View.VISIBLE) {
            moveBadge(
                badge = badge,
                into = metaBlock,
                topGap = context.resources.getDimensionPixelSize(R.dimen.space_8),
                startGap = 0,
                gravity = Gravity.START,
            )
        } else {
            moveBadge(
                badge = badge,
                into = titleRow,
                topGap = context.resources.getDimensionPixelSize(R.dimen.space_4),
                startGap = context.resources.getDimensionPixelSize(R.dimen.space_12),
                gravity = Gravity.TOP or Gravity.START,
            )
        }

        bindMeta(root.findViewById(R.id.oeHeadWhen), whenText)
        bindMeta(root.findViewById(R.id.oeHeadWhere), whereText)
    }

    private fun bindMeta(view: TextView, text: CharSequence?) {
        view.text = text
        view.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** Idempotent: rebinding never stacks a second badge in the same parent. */
    private fun moveBadge(
        badge: TextView,
        into: ViewGroup,
        topGap: Int,
        startGap: Int,
        gravity: Int,
    ) {
        if (badge.parent !== into) {
            (badge.parent as? ViewGroup)?.removeView(badge)
            into.addView(badge)
        }
        badge.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.gravity = gravity
            topMargin = topGap
            marginStart = startGap
        }
    }
}
