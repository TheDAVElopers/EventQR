package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.thedavelopers.eventqr.Dashboard
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.events.EventAdapter
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.notifications.NotificationAdapter
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.RegisteredEventAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.RewardAdapter
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

interface AttendeeView {
    fun showLoading(isLoading: Boolean)
    fun showMessage(message: String)
}

class EventsPresenter(
    private var view: EventsContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadEvents() {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showEvents(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface EventsContract {
    interface View : AttendeeView {
        fun showEvents(items: List<EventResponse>)
        fun showError(message: String)
    }
}

open class AttendeeEventsActivity : AppCompatActivity(), EventsContract.View {
    private lateinit var presenter: EventsPresenter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var loadingView: TextView
    private lateinit var adapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)
        configureAttendeeBottomNav(AttendeeBottomNavItem.EVENTS)

        presenter = EventsPresenter(this, AttendeeRepository(this))
        recyclerView = findViewById(R.id.recyclerEvents)
        emptyView = findViewById(R.id.txtEventsEmpty)
        loadingView = findViewById(R.id.txtEventsLoading)
        adapter = EventAdapter { event -> openEventDetail(event) }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnRefreshEvents).setOnClickListener { presenter.loadEvents() }
        presenter.loadEvents()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    private fun openEventDetail(event: EventResponse) {
        startActivity(
            Intent(this, EventDetailActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, event.eventId.toString())
                .putExtra(EXTRA_EVENT_TITLE, event.title)
                .putExtra(EXTRA_EVENT_LOCATION, event.location ?: "")
                .putExtra(EXTRA_EVENT_DESCRIPTION, event.description ?: "")
                .putExtra(EXTRA_EVENT_START, DateFormatters.formatInstant(event.eventStartAt))
                .putExtra(EXTRA_EVENT_END, DateFormatters.formatInstant(event.eventEndAt))
                .putExtra(EXTRA_EVENT_STATUS, event.status.name)
                .putExtra(EXTRA_EVENT_CAPACITY, event.capacity.toString())
                .putExtra(EXTRA_EVENT_COUNT, event.currentAttendeeCount.toString())
        )
    }

    override fun showLoading(isLoading: Boolean) {
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showEvents(items: List<EventResponse>) {
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitItems(items)
    }

    override fun showError(message: String) {
        emptyView.visibility = View.VISIBLE
        emptyView.text = message
    }
}

class EventDetailPresenter(
    private var view: EventDetailContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun registerForEvent(eventId: String, eventTitle: String) {
        val sessionManager = SessionManager((view as? AppCompatActivity) ?: return)
        val email = sessionManager.getEmail().orEmpty()
        val fullName = sessionManager.getFullName().orEmpty()
        if (!Validators.isValidEmail(email) || !Validators.isNonEmpty(fullName)) {
            view?.showMessage("Open registration to enter attendee details")
            view?.openRegistration(eventId, eventTitle, email, fullName)
            return
        }
        view?.openRegistration(eventId, eventTitle, email, fullName)
    }
}

interface EventDetailContract {
    interface View : AttendeeView {
        fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String)
    }
}

open class EventDetailActivity : AppCompatActivity(), EventDetailContract.View {
    private lateinit var presenter: EventDetailPresenter
    private lateinit var eventId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        presenter = EventDetailPresenter(this, AttendeeRepository(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<TextView>(R.id.txtDetailTitle).text = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        findViewById<TextView>(R.id.txtDetailDescription).text = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION).orEmpty()
        findViewById<TextView>(R.id.txtDetailVenue).text = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty().ifBlank { "Location not set" }
        findViewById<TextView>(R.id.txtDetailStart).text = intent.getStringExtra(EXTRA_EVENT_START).orEmpty()
        findViewById<TextView>(R.id.txtDetailEnd).text = intent.getStringExtra(EXTRA_EVENT_END).orEmpty()
        findViewById<TextView>(R.id.txtDetailStatus).text = intent.getStringExtra(EXTRA_EVENT_STATUS).orEmpty()
        findViewById<TextView>(R.id.txtDetailCapacity).text = "${intent.getStringExtra(EXTRA_EVENT_COUNT).orEmpty()}/${intent.getStringExtra(EXTRA_EVENT_CAPACITY).orEmpty()}"

        findViewById<Button>(R.id.btnRegisterForEvent).setOnClickListener {
            presenter.registerForEvent(eventId, intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty())
        }
        findViewById<Button>(R.id.btnViewTransactions).setOnClickListener {
            startActivity(Intent(this, AttendeeTransactionsActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId))
        }
        findViewById<Button>(R.id.btnViewRewards).setOnClickListener {
            startActivity(Intent(this, AttendeeRewardsActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId))
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) = Unit

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String) {
        startActivity(
            Intent(this, AttendeeRegistrationActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_EVENT_TITLE, eventTitle)
                .putExtra(EXTRA_PREFILL_EMAIL, email)
                .putExtra(EXTRA_PREFILL_FULL_NAME, fullName)
        )
    }
}

class RegistrationPresenter(
    private var view: RegistrationContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun submit(eventId: String, fullName: String, email: String, phoneNumber: String) {
        if (!Validators.isValidEmail(email)) {
            view?.showFieldError("email", "Enter a valid email address")
            return
        }
        if (!Validators.isNonEmpty(fullName)) {
            view?.showFieldError("fullName", "Full name is required")
            return
        }
        view?.showFieldError("email", null)
        view?.showFieldError("fullName", null)
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.createRegistration(
                RegistrationRequest(
                    eventId = UUID.fromString(eventId),
                    email = email,
                    fullName = fullName,
                    phoneNumber = phoneNumber.ifBlank { null },
                )
            )) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: "Registration completed")
                    view?.openQr(result.data.registrationId.toString())
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface RegistrationContract {
    interface View : AttendeeView {
        fun showFieldError(field: String, message: String?)
        fun openQr(registrationId: String)
    }
}

open class AttendeeRegistrationActivity : AppCompatActivity(), RegistrationContract.View {
    private lateinit var presenter: RegistrationPresenter
    private lateinit var eventId: String
    private lateinit var fullNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var submitButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_registration)

        presenter = RegistrationPresenter(this, AttendeeRepository(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        fullNameInput = findViewById(R.id.edtRegistrationFullName)
        emailInput = findViewById(R.id.edtRegistrationEmail)
        phoneInput = findViewById(R.id.edtRegistrationPhone)
        submitButton = findViewById(R.id.btnSubmitRegistration)

        fullNameInput.setText(intent.getStringExtra(EXTRA_PREFILL_FULL_NAME).orEmpty())
        emailInput.setText(intent.getStringExtra(EXTRA_PREFILL_EMAIL).orEmpty())

        submitButton.setOnClickListener {
            presenter.submit(
                eventId,
                fullNameInput.text.toString(),
                emailInput.text.toString(),
                phoneInput.text.toString(),
            )
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        submitButton.isEnabled = !isLoading
        submitButton.text = if (isLoading) "Submitting..." else "Register"
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showFieldError(field: String, message: String?) {
        when (field) {
            "fullName" -> fullNameInput.error = message
            "email" -> emailInput.error = message
        }
    }

    override fun openQr(registrationId: String) {
        startActivity(Intent(this, AttendeeQrCredentialActivity::class.java).putExtra(EXTRA_REGISTRATION_ID, registrationId))
        finish()
    }
}

class QrCredentialPresenter(
    private var view: QrCredentialContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(registrationId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getQrCredentialByRegistration(registrationId)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.renderQr(result.data)
                    result.data.qrCredentialId.toString().also { repository.markQrDisplayed(it) }
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun markDownloaded(qrCredentialId: String) {
        job = kotlinx.coroutines.MainScope().launch { repository.markQrDownloaded(qrCredentialId) }
    }
}

interface QrCredentialContract {
    interface View : AttendeeView {
        fun renderQr(snapshot: QrCredentialSnapshot)
    }
}

open class AttendeeQrCredentialActivity : AppCompatActivity(), QrCredentialContract.View {
    private lateinit var presenter: QrCredentialPresenter
    private lateinit var qrImage: ImageView
    private lateinit var qrText: TextView
    private lateinit var loadingText: TextView
    private lateinit var markDownloadedButton: Button
    private var currentQrCredentialId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_credential)

        presenter = QrCredentialPresenter(this, AttendeeRepository(this))
        qrImage = findViewById(R.id.imgQrCode)
        qrText = findViewById(R.id.txtQrValue)
        loadingText = findViewById(R.id.txtQrLoading)
        markDownloadedButton = findViewById(R.id.btnMarkQrDownloaded)

        findViewById<Button>(R.id.btnLoadQr).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtQrRegistrationId).text.toString())
        }

        val registrationId = intent.getStringExtra(EXTRA_REGISTRATION_ID).orEmpty()
        if (registrationId.isNotBlank()) {
            findViewById<EditText>(R.id.edtQrRegistrationId).setText(registrationId)
            presenter.load(registrationId)
        }

        markDownloadedButton.setOnClickListener {
            currentQrCredentialId?.let { presenter.markDownloaded(it) }
            Toast.makeText(this, "QR download marked", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun renderQr(snapshot: QrCredentialSnapshot) {
        currentQrCredentialId = snapshot.qrCredentialId.toString()
        qrText.text = snapshot.qrValue
        qrImage.setImageBitmap(renderQrBitmap(snapshot.qrValue))
    }

    private fun renderQrBitmap(value: String): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

open class RegisteredEventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registered_events)
        findViewById<TextView>(R.id.txtRegisteredEventsMessage).text = "My registered events endpoint is not available yet, so this screen is a foundation only."
    }
}

class TransactionHistoryPresenter(
    private var view: TransactionHistoryContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getTransactionsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.renderTransactions(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface TransactionHistoryContract {
    interface View : AttendeeView {
        fun renderTransactions(items: List<TransactionResponse>)
    }
}

open class AttendeeTransactionsActivity : AppCompatActivity(), TransactionHistoryContract.View {
    private lateinit var presenter: TransactionHistoryPresenter
    private lateinit var adapter: TransactionAdapter
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)

        presenter = TransactionHistoryPresenter(this, AttendeeRepository(this))
        adapter = TransactionAdapter()
        loadingText = findViewById(R.id.txtTransactionsLoading)
        emptyText = findViewById(R.id.txtTransactionsEmpty)

        findViewById<RecyclerView>(R.id.recyclerTransactions).apply {
            layoutManager = LinearLayoutManager(this@AttendeeTransactionsActivity)
            adapter = this@AttendeeTransactionsActivity.adapter
        }

        findViewById<Button>(R.id.btnLoadTransactions).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtTransactionsEventId).text.toString())
        }

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        if (eventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtTransactionsEventId).setText(eventId)
            presenter.load(eventId)
        } else {
            emptyText.text = "Select an event to view transaction history."
            emptyText.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun renderTransactions(items: List<TransactionResponse>) {
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (items.isEmpty()) "No transactions found for this event." else emptyText.text
        adapter.submitItems(items)
    }
}

class RewardsPresenter(
    private var view: RewardsContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String, attendeeUserId: String?) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val rewardsResult = repository.getRewardsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    val balanceResult = attendeeUserId?.takeIf { it.isNotBlank() }?.let { repository.getRewardBalance(eventId, it) }
                    view?.showLoading(false)
                    view?.renderRewards(rewardsResult.data)
                    if (balanceResult is NetworkResult.Success) {
                        view?.showBalance(balanceResult.data)
                    }
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(rewardsResult.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun redeem(eventId: String, attendeeUserId: String?, rewardId: String) {
        val userId = attendeeUserId.orEmpty()
        if (userId.isBlank()) {
            view?.showMessage("Attendee user ID is required to redeem rewards")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.redeemReward(
                RewardRedemptionRequest(
                    eventId = UUID.fromString(eventId),
                    attendeeUserId = UUID.fromString(userId),
                    rewardId = UUID.fromString(rewardId),
                )
            )) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: "Reward redeemed")
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface RewardsContract {
    interface View : AttendeeView {
        fun showBalance(balance: com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse)
        fun renderRewards(items: List<RewardResponse>)
    }
}

open class AttendeeRewardsActivity : AppCompatActivity(), RewardsContract.View {
    private lateinit var presenter: RewardsPresenter
    private lateinit var adapter: RewardAdapter
    private lateinit var loadingText: TextView
    private lateinit var balanceText: TextView
    private var eventId: String = ""
    private var attendeeUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rewards)
        configureAttendeeBottomNav(AttendeeBottomNavItem.REWARDS)

        presenter = RewardsPresenter(this, AttendeeRepository(this))
        adapter = RewardAdapter { reward -> presenter.redeem(eventId, attendeeUserId, reward.rewardId.toString()) }
        loadingText = findViewById(R.id.txtRewardsLoading)
        balanceText = findViewById(R.id.txtRewardsBalance)

        findViewById<RecyclerView>(R.id.recyclerRewards).apply {
            layoutManager = LinearLayoutManager(this@AttendeeRewardsActivity)
            adapter = this@AttendeeRewardsActivity.adapter
        }

        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        attendeeUserId = SessionManager(this).getUserId()
        findViewById<Button>(R.id.btnLoadRewards).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtRewardsEventId).text.toString(), attendeeUserId)
        }

        if (eventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtRewardsEventId).setText(eventId)
            presenter.load(eventId, attendeeUserId)
        } else {
            balanceText.text = "Choose an event to see reward balance and available rewards."
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showBalance(balance: com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse) {
        balanceText.text = "Balance: ${balance.pointsBalance} points"
    }

    override fun renderRewards(items: List<RewardResponse>) {
        adapter.submitItems(items)
        if (items.isEmpty()) {
            findViewById<TextView>(R.id.txtRewardsEmpty).visibility = View.VISIBLE
        }
    }
}

class NotificationsPresenter(
    private var view: NotificationsContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(recipientUserId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getNotificationsByRecipient(recipientUserId)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.renderNotifications(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}

interface NotificationsContract {
    interface View : AttendeeView {
        fun renderNotifications(items: List<NotificationResponse>)
    }
}

open class AttendeeNotificationsActivity : AppCompatActivity(), NotificationsContract.View {
    private lateinit var presenter: NotificationsPresenter
    private lateinit var adapter: NotificationAdapter
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        presenter = NotificationsPresenter(this, AttendeeRepository(this))
        adapter = NotificationAdapter()
        loadingText = findViewById(R.id.txtNotificationsLoading)
        emptyText = findViewById(R.id.txtNotificationsEmpty)

        findViewById<RecyclerView>(R.id.recyclerNotifications).apply {
            layoutManager = LinearLayoutManager(this@AttendeeNotificationsActivity)
            adapter = this@AttendeeNotificationsActivity.adapter
        }

        val recipientUserId = SessionManager(this).getUserId().orEmpty()
        if (recipientUserId.isBlank()) {
            emptyText.text = "Sign in again to see your notifications."
            emptyText.visibility = View.VISIBLE
        } else {
            presenter.load(recipientUserId)
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun renderNotifications(items: List<NotificationResponse>) {
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (items.isEmpty()) "No notifications available." else emptyText.text
        adapter.submitItems(items)
    }
}

private const val EXTRA_EVENT_ID = "extra_event_id"
private const val EXTRA_EVENT_TITLE = "extra_event_title"
private const val EXTRA_EVENT_LOCATION = "extra_event_location"
private const val EXTRA_EVENT_DESCRIPTION = "extra_event_description"
private const val EXTRA_EVENT_START = "extra_event_start"
private const val EXTRA_EVENT_END = "extra_event_end"
private const val EXTRA_EVENT_STATUS = "extra_event_status"
private const val EXTRA_EVENT_CAPACITY = "extra_event_capacity"
private const val EXTRA_EVENT_COUNT = "extra_event_count"
private const val EXTRA_PREFILL_EMAIL = "extra_prefill_email"
private const val EXTRA_PREFILL_FULL_NAME = "extra_prefill_full_name"
private const val EXTRA_REGISTRATION_ID = "extra_registration_id"
