package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.thedavelopers.eventqr.BuildConfig
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionLogout
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.MetricRowStacking
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.asButtonRole
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Attendee profile (§6.2) - retires Gen A.
 *
 * A compact pass head (title `Profile`, no eyebrow, no count) owns the brand band and the
 * status-bar inset, the same wiring as the attendee Rewards screen and the admin Accounts /
 * Requests screens. Everything below the tear scrolls, identity block included. The identity is
 * one TalkBack node (its name is the heading) and the two sections are `list_row_icon` rows bound
 * through [ListRowBinder]: ACCOUNT first with Edit profile alone, then ACTIVITY. Every role
 * reaches this screen, so it holds no portal switch - the dashboard role tabs do that.
 *
 * Transaction history shows no points balance: `PointBalanceResponse` is per (event, attendee) - the
 * Rewards screen reads it for the event it has selected - and there is no account-wide balance
 * endpoint, so the row keeps its chevron instead of printing an event's balance under a page-wide
 * label.
 */
class AttendeeProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository
    private lateinit var headerProfile: View
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileEmail: TextView
    private lateinit var skeletonLoading: View
    private lateinit var swipeRefresh: com.thedavelopers.eventqr.ui.components.EventQrSwipeRefreshLayout
    private lateinit var layoutProfileSections: View
    private lateinit var errorProfileBlock: View
    private lateinit var txtProfileError: TextView
    private lateinit var btnProfileRetry: TextView
    private var loadJob: Job? = null

    // False until the role guard passed and every view above is inflated. A denied session
    // finishes in onCreate without inflating anything, so onResume must not touch them.
    private var uiReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.ATTENDEE)) {
            Toast.makeText(this, this.getString(R.string.attendee_profile_access_denied), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_attendee_profile)
        // Transparent status bar paints the head band behind it; the icons adapt to the band's
        // luminance, exactly like the other pass-head screens.
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        bindPassHead()

        headerProfile = findViewById(R.id.headerProfile)

        txtProfileName = findViewById(R.id.txtProfileName)
        txtProfileEmail = findViewById(R.id.txtProfileEmail)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        swipeRefresh = findViewById(R.id.swipeRefreshProfile)
        layoutProfileSections = findViewById(R.id.layoutProfileSections)
        errorProfileBlock = findViewById(R.id.errorProfileBlock)
        txtProfileError = findViewById(R.id.txtProfileError)
        btnProfileRetry = findViewById(R.id.btnProfileRetry)

        findViewById<TextView>(R.id.txtProfileVersion).text = getString(
            R.string.ui_attendee_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )

        swipeRefresh.setOnRefreshListener { loadProfile() }

        btnProfileRetry.setOnClickListener { loadProfile() }

        bindActivityRows()

        // A styled TextView, so it needs the Button role explicitly (§9.5).
        findViewById<View>(R.id.cardSignOut).apply {
            asButtonRole()
            setOnClickListener { showSignOutConfirmation() }
        }

        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)
        uiReady = true
    }

    /**
     * The shared compact pass head: flat `ui_primary` band, tear as its bottom edge, status-bar
     * inset on `passHeadIdentity` so the fill runs behind the system bar. No eyebrow (the role
     * eyebrow is retired, v1.40), no count (a profile has no result set) and no back arrow (the
     * bottom nav owns this destination).
     */
    private fun bindPassHead() {
        val head = findViewById<View>(R.id.passHead)
        head.findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        PassHeadBinder.bindCompact(
            root = head,
            title = getString(R.string.ui_attendee_profile_title),
        )
    }

    private fun bindActivityRows() {
        bindRow(
            R.id.rowTransactionHistory,
            title = getString(R.string.ui_attendee_transaction_history),
            meta = getString(R.string.ui_attendee_profile_meta_history),
            icon = AppIcons.Logs,
        ) { startActivity(Intent(this, AttendeeTransactionsActivity::class.java)) }

        bindRow(
            R.id.rowClaimedRewards,
            title = getString(R.string.ui_attendee_claimed_rewards),
            meta = getString(R.string.ui_attendee_profile_meta_rewards),
            icon = AppIcons.Reward,
        ) { startActivity(Intent(this, ClaimedRewardsActivity::class.java)) }

        bindRow(
            R.id.rowMyEventRequests,
            title = getString(R.string.ui_attendee_my_event_requests),
            meta = getString(R.string.ui_attendee_profile_meta_requests),
            icon = AppIcons.Request,
            showDivider = false,
        ) { startActivity(Intent(this, MyEventRequestsActivity::class.java)) }
    }

    private fun bindRow(
        rowId: Int,
        title: String,
        meta: String?,
        @androidx.annotation.DrawableRes icon: Int,
        showDivider: Boolean = true,
        onClick: () -> Unit,
    ) {
        val row = findViewById<View>(rowId)
        ListRowBinder.bind(
            row = row,
            title = title,
            meta = meta,
            trailing = ListRowBinder.Trailing.CHEVRON,
            showDivider = showDivider,
            onClick = onClick,
        )
        row.findViewById<android.widget.ImageView>(R.id.listRowIcon)?.setImageResource(icon)
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.common_sign_out))
            .setMessage(getString(R.string.attendee_profile_are_you_sure_you_want_to_sign_out))
            .setPositiveButton(getString(R.string.common_sign_out)) { dialog, _ ->
                dialog.dismiss()
                performSignOut()
            }
            .setNegativeButton(getString(R.string.request_event_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performSignOut() {
        loadJob?.cancel()
        findViewById<View>(R.id.cardSignOut).isEnabled = false
        lifecycleScope.launch {
            // Revokes the token on the server first, then clears local session state.
            SessionLogout.signOut(this@AttendeeProfileActivity)
            startActivities(com.thedavelopers.eventqr.core.navigation.AppNavigator.signedOutStack(this@AttendeeProfileActivity))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!uiReady) return
        loadProfile()
    }

    private fun loadProfile() {
        setLoadingState(true)
        clearErrorState()

        renderProfile(null)

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber, user.email)
                    sessionManager.saveRole(user.role)
                    renderProfile(user)
                    clearErrorState()
                }
                is NetworkResult.Error -> showErrorState(
                    result.message.ifBlank { getString(R.string.ui_attendee_profile_load_failed) },
                )
                else -> Unit
            }

            setLoadingState(false)
        }
    }

    private fun renderProfile(user: UserResponse? = null) {
        // The pre-load pass has no DTO yet, so fall back to the cached session values instead of
        // flashing an empty name or email before the request resolves.
        val name = user?.fullName ?: sessionManager.getFullName().orEmpty()
        val email = user?.email ?: sessionManager.getEmail().orEmpty()
        txtProfileName.text = name
        txtProfileEmail.text = email
        txtProfileEmail.maxLines = if (MetricRowStacking.stacks(resources.configuration.fontScale)) 2 else 1

        findViewById<TextView>(R.id.txtProfileInitial).text =
            name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        // One TalkBack node for the block, the name first; the avatar and both labels are
        // importantForAccessibility="no" so nothing is read twice (§9.5).
        headerProfile.contentDescription = listOf(name, email).filter { it.isNotBlank() }.joinToString(", ")

        // Edit profile always prints a meta line: the real number when there is one, else what the
        // row edits. A meta that vanished with the data would change the row's height mid-load.
        val phone = (if (user != null) user.phoneNumber else sessionManager.getPhone())
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.ui_attendee_profile_meta_edit)
        findViewById<View>(R.id.rowEditProfile).let { row ->
            ListRowBinder.bind(
                row = row,
                title = getString(R.string.ui_attendee_edit_profile),
                meta = phone,
                trailing = ListRowBinder.Trailing.CHEVRON,
                showDivider = false,
            ) { startActivity(Intent(this, AttendeeEditProfileActivity::class.java)) }
            row.findViewById<android.widget.ImageView>(R.id.listRowIcon)?.setImageResource(AppIcons.Profile)
        }
    }

    private fun setLoadingState(loading: Boolean) {
        val showSkeleton = loading && !swipeRefresh.isRefreshing
        skeletonLoading.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        if (loading) {
            if (showSkeleton) layoutProfileSections.visibility = View.GONE
            errorProfileBlock.visibility = View.GONE
        } else {
            swipeRefresh.isRefreshing = false
            layoutProfileSections.visibility = View.VISIBLE
        }
    }

    private fun showErrorState(message: String) {
        skeletonLoading.visibility = View.GONE
        layoutProfileSections.visibility = View.VISIBLE
        errorProfileBlock.visibility = View.VISIBLE
        txtProfileError.text = message
    }

    private fun clearErrorState() {
        errorProfileBlock.visibility = View.GONE
    }
}
