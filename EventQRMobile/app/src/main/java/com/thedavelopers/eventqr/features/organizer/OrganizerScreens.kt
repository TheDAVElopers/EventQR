package com.thedavelopers.eventqr.features.organizer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.EventAdapter
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.notifications.NotificationAdapter
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationRequest
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSnapshot
import com.thedavelopers.eventqr.features.rewards.RewardAdapter
import com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.scanpurposes.ScanPurposeAdapter
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest
import com.thedavelopers.eventqr.features.users.UserAdapter
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ManageEventsPresenter(
    private var view: ManageEventsContract.View?,
    private val repository: OrganizerRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load() {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> view?.renderEvents(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun create(event: EventRequest) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.createEvent(event)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Event submitted")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            load()
        }
    }

    fun review(eventId: String, approved: Boolean, reviewerUserId: String, rejectionReason: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.reviewEvent(eventId, EventApprovalRequest(approved, UUID.fromString(reviewerUserId), rejectionReason.ifBlank { null }))) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Event reviewed")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            load()
        }
    }

    fun activate(eventId: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.activateEvent(eventId)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Event activated")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            load()
        }
    }
}

interface ManageEventsContract {
    interface View {
        fun renderEvents(items: List<com.thedavelopers.eventqr.features.events.model.dto.EventResponse>)
        fun showMessage(message: String)
    }
}

open class ManageEventsActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity(), ManageEventsContract.View {
    private lateinit var presenter: ManageEventsPresenter
    private lateinit var adapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_events)

        presenter = ManageEventsPresenter(this, OrganizerRepository(this))
        adapter = EventAdapter { event ->
            findViewById<EditText>(R.id.edtManageEventId).setText(event.eventId.toString())
        }

        findViewById<RecyclerView>(R.id.recyclerManageEvents).apply {
            layoutManager = LinearLayoutManager(this@ManageEventsActivity)
            adapter = this@ManageEventsActivity.adapter
        }

        findViewById<Button>(R.id.btnLoadManageEvents).setOnClickListener { presenter.load() }
        findViewById<Button>(R.id.btnSubmitManageEvent).setOnClickListener {
            presenter.create(readEventRequest())
        }
        findViewById<Button>(R.id.btnReviewManageEvent).setOnClickListener {
            presenter.review(
                findViewById<EditText>(R.id.edtManageEventId).text.toString(),
                findViewById<CheckBox>(R.id.chkManageEventApproved).isChecked,
                findViewById<EditText>(R.id.edtManageEventReviewerId).text.toString(),
                findViewById<EditText>(R.id.edtManageEventRejectionReason).text.toString(),
            )
        }
        findViewById<Button>(R.id.btnActivateManageEvent).setOnClickListener {
            presenter.activate(findViewById<EditText>(R.id.edtManageEventId).text.toString())
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_events)

        presenter.load()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    private fun readEventRequest(): EventRequest {
        fun parseInstant(text: String): Instant? = runCatching { Instant.parse(text.trim()) }.getOrNull()
        return EventRequest(
            title = findViewById<EditText>(R.id.edtManageEventTitle).text.toString(),
            description = findViewById<EditText>(R.id.edtManageEventDescription).text.toString().ifBlank { null },
            location = findViewById<EditText>(R.id.edtManageEventLocation).text.toString().ifBlank { null },
            registrationOpenAt = parseInstant(findViewById<EditText>(R.id.edtManageEventRegOpen).text.toString()),
            registrationCloseAt = parseInstant(findViewById<EditText>(R.id.edtManageEventRegClose).text.toString()),
            eventStartAt = parseInstant(findViewById<EditText>(R.id.edtManageEventStart).text.toString()),
            eventEndAt = parseInstant(findViewById<EditText>(R.id.edtManageEventEnd).text.toString()),
            capacity = findViewById<EditText>(R.id.edtManageEventCapacity).text.toString().toIntOrNull() ?: 0,
            rewardsEnabled = findViewById<CheckBox>(R.id.chkManageEventRewards).isChecked,
            organizerUserId = UUID.fromString(findViewById<EditText>(R.id.edtManageEventOrganizerId).text.toString()),
        )
    }

    override fun renderEvents(items: List<com.thedavelopers.eventqr.features.events.model.dto.EventResponse>) {
        adapter.submitItems(items)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class ManageUsersPresenter(
    private var view: ManageUsersContract.View?,
    private val repository: OrganizerRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load() {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getUsers()) {
                is NetworkResult.Success -> view?.renderUsers(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun create(request: UserRequest) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.createUser(request)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "User created")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            load()
        }
    }

    fun changeRole(userId: String, role: AccountRole) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.changeUserRole(userId, role)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Role updated")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            load()
        }
    }
}

interface ManageUsersContract {
    interface View {
        fun renderUsers(items: List<UserResponse>)
        fun showMessage(message: String)
    }
}

open class ManageUsersActivity : AppCompatActivity(), ManageUsersContract.View {
    private lateinit var presenter: ManageUsersPresenter
    private lateinit var adapter: UserAdapter
    private lateinit var roleSpinner: Spinner

    private var selectedUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_users)

        presenter = ManageUsersPresenter(this, OrganizerRepository(this))
        adapter = UserAdapter { user -> selectedUserId = user.userId.toString() }
        roleSpinner = findViewById(R.id.spnUserRole)

        findViewById<RecyclerView>(R.id.recyclerManageUsers).apply {
            layoutManager = LinearLayoutManager(this@ManageUsersActivity)
            adapter = this@ManageUsersActivity.adapter
        }

        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, AccountRole.values().map { it.name })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        findViewById<Button>(R.id.btnLoadManageUsers).setOnClickListener { presenter.load() }
        findViewById<Button>(R.id.btnCreateUser).setOnClickListener { presenter.create(readUserRequest()) }
        findViewById<Button>(R.id.btnChangeRole).setOnClickListener {
            val userId = selectedUserId ?: findViewById<EditText>(R.id.edtTargetUserId).text.toString()
            presenter.changeRole(userId, AccountRole.valueOf(roleSpinner.selectedItem.toString()))
        }

        presenter.load()
    }

    private fun readUserRequest(): UserRequest {
        return UserRequest(
            email = findViewById<EditText>(R.id.edtUserEmail).text.toString(),
            fullName = findViewById<EditText>(R.id.edtUserFullName).text.toString(),
            phoneNumber = findViewById<EditText>(R.id.edtUserPhone).text.toString().ifBlank { null },
            password = findViewById<EditText>(R.id.edtUserPassword).text.toString(),
            role = AccountRole.valueOf(roleSpinner.selectedItem.toString()),
        )
    }

    override fun renderUsers(items: List<UserResponse>) {
        adapter.submitItems(items)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class ManageScanPurposesPresenter(
    private var view: ManageScanPurposesContract.View?,
    private val repository: OrganizerRepository,
) {
    private var job: Job? = null

    fun detach() { job?.cancel(); view = null }

    fun load(eventId: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getScanPurposesByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderPurposes(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun create(request: ScanPurposeRequest) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.createScanPurpose(request)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Scan purpose created")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface ManageScanPurposesContract {
    interface View {
        fun renderPurposes(items: List<com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse>)
        fun showMessage(message: String)
    }
}

open class ManageScanPurposesActivity : AppCompatActivity(), ManageScanPurposesContract.View {
    private lateinit var presenter: ManageScanPurposesPresenter
    private lateinit var adapter: ScanPurposeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_scan_purposes)

        presenter = ManageScanPurposesPresenter(this, OrganizerRepository(this))
        adapter = ScanPurposeAdapter()

        findViewById<RecyclerView>(R.id.recyclerManageScanPurposes).apply {
            layoutManager = LinearLayoutManager(this@ManageScanPurposesActivity)
            adapter = this@ManageScanPurposesActivity.adapter
        }

        findViewById<Button>(R.id.btnLoadScanPurposes).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtScanPurposeEventId).text.toString())
        }
        findViewById<Button>(R.id.btnCreateScanPurpose).setOnClickListener {
            presenter.create(readRequest())
        }
    }

    private fun readRequest(): ScanPurposeRequest {
        val codeSpinner = findViewById<Spinner>(R.id.spnScanPurposeCode)
        return ScanPurposeRequest(
            eventId = UUID.fromString(findViewById<EditText>(R.id.edtScanPurposeEventId).text.toString()),
            name = findViewById<EditText>(R.id.edtScanPurposeName).text.toString(),
            code = com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.valueOf(codeSpinner.selectedItem.toString()),
            active = findViewById<CheckBox>(R.id.chkScanPurposeActive).isChecked,
            trackingOnly = findViewById<CheckBox>(R.id.chkScanPurposeTrackingOnly).isChecked,
            description = findViewById<EditText>(R.id.edtScanPurposeDescription).text.toString().ifBlank { null },
        )
    }

    override fun renderPurposes(items: List<com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse>) {
        adapter.submitItems(items)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class ManageRewardsPresenter(
    private var view: ManageRewardsContract.View?,
    private val repository: OrganizerRepository,
) {
    private var job: Job? = null

    fun detach() { job?.cancel(); view = null }

    fun load(eventId: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val rewards = repository.getRewardsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderRewards(rewards.data)
                is NetworkResult.Error -> view?.showMessage(rewards.message)
                NetworkResult.Loading -> Unit
            }
            when (val rules = repository.getPointRules(eventId)) {
                is NetworkResult.Success -> view?.renderPointRules(rules.data)
                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
            when (val redemptions = repository.getRewardRedemptions(eventId)) {
                is NetworkResult.Success -> view?.renderRedemptions(redemptions.data)
                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun createReward(request: RewardRequest) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.saveReward(request)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Reward saved")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun createPointRule(request: PointRuleRequest) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.savePointRule(request)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Point rule saved")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface ManageRewardsContract {
    interface View {
        fun renderRewards(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse>)
        fun renderPointRules(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleResponse>)
        fun renderRedemptions(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse>)
        fun showMessage(message: String)
    }
}

open class ManageRewardsActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity(), ManageRewardsContract.View {
    private lateinit var presenter: ManageRewardsPresenter
    private lateinit var adapter: RewardAdapter
    private lateinit var rewardsText: TextView
    private lateinit var rulesText: TextView
    private lateinit var redemptionsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_rewards)

        presenter = ManageRewardsPresenter(this, OrganizerRepository(this))
        adapter = RewardAdapter { }
        rewardsText = findViewById(R.id.txtRewardsList)
        rulesText = findViewById(R.id.txtPointRulesList)
        redemptionsText = findViewById(R.id.txtRedemptionsList)

        findViewById<Button>(R.id.btnLoadManageRewards).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtRewardsEventId).text.toString())
        }
        findViewById<Button>(R.id.btnCreateReward).setOnClickListener { presenter.createReward(readRewardRequest()) }
        findViewById<Button>(R.id.btnCreatePointRule).setOnClickListener { presenter.createPointRule(readPointRuleRequest()) }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_rewards)
    }

    private fun readRewardRequest(): RewardRequest {
        return RewardRequest(
            eventId = UUID.fromString(findViewById<EditText>(R.id.edtRewardsEventId).text.toString()),
            name = findViewById<EditText>(R.id.edtRewardName).text.toString(),
            pointsRequired = findViewById<EditText>(R.id.edtRewardPointsRequired).text.toString().toIntOrNull() ?: 0,
            stockQuantity = findViewById<EditText>(R.id.edtRewardStock).text.toString().toIntOrNull(),
        )
    }

    private fun readPointRuleRequest(): PointRuleRequest {
        return PointRuleRequest(
            eventId = UUID.fromString(findViewById<EditText>(R.id.edtRewardsEventId).text.toString()),
            scanPurposeId = UUID.fromString(findViewById<EditText>(R.id.edtPointRuleScanPurposeId).text.toString()),
            points = findViewById<EditText>(R.id.edtPointRulePoints).text.toString().toIntOrNull() ?: 0,
            active = findViewById<CheckBox>(R.id.chkPointRuleActive).isChecked,
        )
    }

    override fun renderRewards(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse>) {
        rewardsText.text = items.joinToString("\n\n") { "${it.name} - ${it.pointsRequired} pts - ${it.status.name}" }
    }

    override fun renderPointRules(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleResponse>) {
        rulesText.text = items.joinToString("\n\n") { "Purpose ${it.scanPurposeId} - ${it.points} pts - ${if (it.active) "Active" else "Inactive"}" }
    }

    override fun renderRedemptions(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse>) {
        redemptionsText.text = items.joinToString("\n\n") { "${it.rewardId} - ${it.status.name} - ${it.pointsSpent} pts" }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class ReportsPresenter(
    private var view: ReportsContract.View?,
    private val repository: OrganizerRepository,
) {
    private var job: Job? = null
    fun detach() { job?.cancel(); view = null }
    fun load(eventId: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEventReport(eventId)) {
                is NetworkResult.Success -> view?.renderReport(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface ReportsContract {
    interface View {
        fun renderReport(snapshot: EventReportSnapshot)
        fun showMessage(message: String)
    }
}

open class ReportsActivity : AppCompatActivity(), ReportsContract.View {
    private lateinit var presenter: ReportsPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        presenter = ReportsPresenter(this, OrganizerRepository(this))
        findViewById<Button>(R.id.btnLoadReport).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtReportEventId).text.toString())
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderReport(snapshot: EventReportSnapshot) {
        findViewById<TextView>(R.id.txtReportSummary).text = buildString {
            append("Total attendees: ${snapshot.totalAttendees}\n")
            append("Registered: ${snapshot.registeredCount}\n")
            append("Entered: ${snapshot.enteredCount}\n")
            append("Exited: ${snapshot.exitedCount}\n")
            append("No show: ${snapshot.noShowCount}\n")
            append("Attendance scans: ${snapshot.attendanceCount}\n")
            append("Claims: ${snapshot.claimsCount}\n")
            append("Booth/session visits: ${snapshot.boothSessionVisits}\n")
            append("Rewards redeemed: ${snapshot.rewardsRedeemed}\n")
            append("Total points: ${snapshot.totalPointsEarned}\n")
            append("Approved tx: ${snapshot.approvedTransactions}\n")
            append("Rejected tx: ${snapshot.rejectedTransactions}")
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class NotificationManagementPresenter(
    private var view: NotificationManagementContract.View?,
    private val repository: OrganizerRepository,
) {
    private var job: Job? = null
    fun detach() { job?.cancel(); view = null }
    fun load(eventId: String) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getNotificationsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderNotifications(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
    fun create(request: NotificationRequest) {
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.createNotification(request)) {
                is NetworkResult.Success -> view?.showMessage(result.message ?: "Notification created")
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface NotificationManagementContract {
    interface View {
        fun renderNotifications(items: List<com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse>)
        fun showMessage(message: String)
    }
}

open class NotificationManagementActivity : AppCompatActivity(), NotificationManagementContract.View {
    private lateinit var presenter: NotificationManagementPresenter
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_management)

        presenter = NotificationManagementPresenter(this, OrganizerRepository(this))
        adapter = NotificationAdapter()

        findViewById<RecyclerView>(R.id.recyclerNotificationManagement).apply {
            layoutManager = LinearLayoutManager(this@NotificationManagementActivity)
            adapter = this@NotificationManagementActivity.adapter
        }

        findViewById<Button>(R.id.btnLoadNotificationManagement).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtNotificationEventId).text.toString())
        }
        findViewById<Button>(R.id.btnCreateNotification).setOnClickListener { presenter.create(readRequest()) }
    }

    private fun readRequest(): NotificationRequest {
        return NotificationRequest(
            eventId = UUID.fromString(findViewById<EditText>(R.id.edtNotificationEventId).text.toString()),
            recipientUserId = UUID.fromString(findViewById<EditText>(R.id.edtNotificationRecipientId).text.toString()),
            title = findViewById<EditText>(R.id.edtNotificationTitle).text.toString(),
            message = findViewById<EditText>(R.id.edtNotificationMessage).text.toString(),
        )
    }

    override fun renderNotifications(items: List<com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse>) {
        adapter.submitItems(items)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}