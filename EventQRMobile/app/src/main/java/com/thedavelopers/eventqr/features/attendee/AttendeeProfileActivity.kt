package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse
import kotlinx.coroutines.launch
import java.util.Base64
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AttendeeProfile"

open class AttendeeProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileRole: TextView
    private lateinit var txtProfileEmail: TextView
    private lateinit var txtPhone: TextView
    private lateinit var imgProfileAvatar: ImageView
    private lateinit var imgProfileAvatarPlaceholder: View
    private lateinit var progressProfileLoading: ProgressBar
    private lateinit var txtProfileError: TextView
    private lateinit var btnProfileRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

        txtProfileName = findViewById(R.id.txtProfileName)
        txtProfileRole = findViewById(R.id.txtProfileRole)
        txtProfileEmail = findViewById(R.id.txtProfileEmail)
        txtPhone = findViewById(R.id.txtPhone)
        imgProfileAvatar = findViewById(R.id.imgProfileAvatar)
        imgProfileAvatarPlaceholder = findViewById(R.id.imgProfileAvatarPlaceholder)
        progressProfileLoading = findViewById(R.id.progressProfileLoading)
        txtProfileError = findViewById(R.id.txtProfileError)
        btnProfileRetry = findViewById(R.id.btnProfileRetry)

        btnProfileRetry.setOnClickListener { loadProfile() }
        
        findViewById<View>(R.id.cardEditProfile).setOnClickListener {
            startActivity(Intent(this, AttendeeEditProfileActivity::class.java))
        }
        findViewById<View>(R.id.cardMyEvents).setOnClickListener {
            startActivity(Intent(this, RegisteredEventsActivity::class.java))
        }
        findViewById<View>(R.id.cardTransactionHistory).setOnClickListener {
            startActivity(Intent(this, AttendeeTransactionsActivity::class.java))
        }
        findViewById<View>(R.id.cardClaimedRewards).setOnClickListener {
            startActivity(Intent(this, ClaimedRewardsActivity::class.java))
        }
        findViewById<View>(R.id.cardMyEventRequests).setOnClickListener {
            startActivity(Intent(this, MyEventRequestsActivity::class.java))
        }
        findViewById<View>(R.id.cardNotifications).setOnClickListener {
            startActivity(Intent(this, AttendeeNotificationsActivity::class.java))
        }

        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {            sessionManager.clearSession()
            startActivity(
                Intent(this, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }

        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        setLoadingState(true)
        clearErrorState()

        // Initial sync from session
        renderProfile(null)

        // Fresh fetch from backend
        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is com.thedavelopers.eventqr.core.api.NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber, user.email)
                    sessionManager.saveRole(user.role)
                    sessionManager.saveAvatarFileId(user.avatarFileId)
                    renderProfile(user)
                    renderAvatar(
                        imgProfileAvatar,
                        imgProfileAvatarPlaceholder,
                        loadAvatarPreview(repository, filesDir, user.userId.toString(), user.avatarFileId)
                    )
                    clearErrorState()
                }
                is com.thedavelopers.eventqr.core.api.NetworkResult.Error -> {
                    showErrorState(result.message.ifBlank { "Unable to load profile." })
                }
                else -> Unit
            }

            setLoadingState(false)
        }
    }

    private fun renderProfile(user: UserResponse? = null) {
        txtProfileName.text = user?.fullName ?: sessionManager.getFullName().orEmpty()
        txtProfileRole.text = (user?.role?.name ?: sessionManager.getUserRole())
            ?.takeIf { it.isNotBlank() }
            ?.let { RoleMapper.getDisplayName(it) }
            .orEmpty()
        txtProfileEmail.text = user?.email ?: sessionManager.getEmail().orEmpty()
        txtPhone.text = user?.phoneNumber ?: sessionManager.getPhone().orEmpty()
    }

    private fun setLoadingState(loading: Boolean) {
        progressProfileLoading.visibility = if (loading) View.VISIBLE else View.GONE
        btnProfileRetry.visibility = View.GONE
        txtProfileError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        txtProfileError.text = message
        txtProfileError.visibility = View.VISIBLE
        btnProfileRetry.visibility = View.VISIBLE
    }

    private fun clearErrorState() {
        txtProfileError.visibility = View.GONE
        btnProfileRetry.visibility = View.GONE
    }
}

open class AttendeeEditProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AttendeeRepository

    private lateinit var btnBack: ImageButton
    private lateinit var edtFullName: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPhone: EditText
    private lateinit var imgAvatar: ImageView
    private lateinit var imgAvatarPlaceholder: ImageView
    private lateinit var txtChangePhoto: TextView
    private lateinit var txtApiError: TextView
    private lateinit var btnRetryProfileLoad: Button
    private lateinit var progressLoading: ProgressBar
    private lateinit var btnSaveChanges: Button

    private var initialFullName: String = ""
    private var initialEmail: String = ""
    private var initialPhone: String = ""
    private var initialAvatarFileId: String? = null

    private var selectedAvatarFile: File? = null
    private var avatarChanged: Boolean = false
    private var isLoadingProfile: Boolean = false
    private var isSavingProfile: Boolean = false

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            return@registerForActivityResult
        }
        clearApiError()
        val avatarFile = cacheSelectedAvatar(uri)
        if (avatarFile == null) {
            showApiError("Unable to read selected photo. Please try a different image.")
            return@registerForActivityResult
        }

        selectedAvatarFile = avatarFile
        avatarChanged = true
        renderAvatar(imgAvatar, imgAvatarPlaceholder, avatarFile)
        updateSaveButtonState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        sessionManager = SessionManager(this)
        repository = AttendeeRepository(this)

        bindViews()
        bindActions()
        prefillFromSession()
        loadCurrentProfile()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBackImage)
        edtFullName = findViewById(R.id.edtFullName)
        edtEmail = findViewById(R.id.edtEmail)
        edtPhone = findViewById(R.id.edtPhone)
        imgAvatar = findViewById(R.id.imgAvatar)
        imgAvatarPlaceholder = findViewById(R.id.imgAvatarPlaceholder)
        txtChangePhoto = findViewById(R.id.txtChangePhoto)
        txtApiError = findViewById(R.id.txtApiError)
        btnRetryProfileLoad = findViewById(R.id.btnRetryProfileLoad)
        progressLoading = findViewById(R.id.progressLoading)
        btnSaveChanges = findViewById(R.id.btnSaveChanges)
    }

    private fun bindActions() {
        btnBack.setOnClickListener { finish() }
        btnRetryProfileLoad.setOnClickListener { loadCurrentProfile() }
        txtChangePhoto.setOnClickListener {
            if (isLoadingProfile || isSavingProfile) {
                return@setOnClickListener
            }
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSaveChanges.setOnClickListener {
            attemptSave()
        }

        val formWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                clearApiError()
                clearFieldErrors()
                updateSaveButtonState()
            }
        }

        edtFullName.addTextChangedListener(formWatcher)
        edtEmail.addTextChangedListener(formWatcher)
        edtPhone.addTextChangedListener(formWatcher)
    }

    private fun prefillFromSession() {
        edtFullName.setText(sessionManager.getFullName().orEmpty())
        edtEmail.setText(sessionManager.getEmail().orEmpty())
        edtPhone.setText(sessionManager.getPhone().orEmpty())

        initialAvatarFileId = sessionManager.getAvatarFileId()
        resolveAvatarCacheFile(filesDir, sessionManager.getUserId(), initialAvatarFileId)
            ?.takeIf { it.exists() }
            ?.let { renderAvatar(imgAvatar, imgAvatarPlaceholder, it) }

        if (initialAvatarFileId.isNullOrBlank()) {
            imgAvatar.setImageDrawable(null)
            imgAvatarPlaceholder.visibility = View.VISIBLE
        }

        captureInitialFormSnapshot()
        updateSaveButtonState()
    }

    private fun loadCurrentProfile() {
        setLoadingState(true)
        lifecycleScope.launch {
            when (val profileResult = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    val user = profileResult.data
                    edtFullName.setText(user.fullName)
                    edtEmail.setText(user.email)
                    edtPhone.setText(user.phoneNumber.orEmpty())

                    sessionManager.updateProfile(
                        fullName = user.fullName,
                        phone = user.phoneNumber,
                        email = user.email
                    )
                    sessionManager.saveRole(user.role)
                    sessionManager.saveAvatarFileId(user.avatarFileId)
                    initialAvatarFileId = user.avatarFileId

                    renderAvatar(
                        imgAvatar,
                        imgAvatarPlaceholder,
                        loadAvatarPreview(repository, filesDir, user.userId.toString(), user.avatarFileId)
                    )
                }

                is NetworkResult.Error -> {
                    showApiError(profileResult.message)
                }

                else -> Unit
            }

            captureInitialFormSnapshot()
            setLoadingState(false)
        }
    }

    private fun attemptSave() {
        clearApiError()
        clearFieldErrors()

        if (!validateForm()) {
            return
        }

        if (sanitizeEmail() != initialEmail) {
            edtEmail.error = "Email updates are not supported."
            showApiError("Email updates are not supported by this account endpoint.")
            return
        }

        if (!hasChanges()) {
            return
        }

        isSavingProfile = true
        updateSaveButtonState()

        val fullName = sanitizeName()
        val phone = sanitizePhone().ifBlank { null }

        lifecycleScope.launch {
            when (val updateResult = repository.updateProfile(fullName, phone)) {
                is NetworkResult.Success -> {
                    if (avatarChanged) {
                        val avatarFile = selectedAvatarFile
                        if (avatarFile == null || !avatarFile.exists()) {
                            showApiError("Selected photo is unavailable. Please choose the image again.")
                            isSavingProfile = false
                            updateSaveButtonState()
                            return@launch
                        }
                        when (val avatarResult = repository.uploadAvatar(avatarFile)) {
                            is NetworkResult.Error -> {
                                showApiError(avatarResult.message)
                                isSavingProfile = false
                                updateSaveButtonState()
                                return@launch
                            }

                            is NetworkResult.Success -> {
                                val uploadedAvatar = avatarResult.data
                                val userId = sessionManager.getUserId()
                                val avatarPreview = loadAvatarPreview(repository, filesDir, userId, uploadedAvatar.fileId.toString())
                                selectedAvatarFile = avatarPreview?.cachedFile ?: selectedAvatarFile
                                sessionManager.saveAvatarFileId(uploadedAvatar.fileId.toString())
                                initialAvatarFileId = uploadedAvatar.fileId.toString()
                                avatarChanged = false
                                renderAvatar(imgAvatar, imgAvatarPlaceholder, avatarPreview)

                                if (avatarPreview == null) {
                                    Log.w(TAG, "Avatar uploaded, but backend preview data could not be rendered immediately.")
                                }
                            }

                            else -> Unit
                        }
                    }

                    refreshProfileStateFromBackend()

                    sessionManager.updateProfile(fullName, phone, initialEmail)
                    sessionManager.saveAvatarFileId(initialAvatarFileId)
                    captureInitialFormSnapshot()
                    Toast.makeText(this@AttendeeEditProfileActivity, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }

                is NetworkResult.Error -> {
                    showApiError(updateResult.message)
                }

                else -> Unit
            }

            isSavingProfile = false
            updateSaveButtonState()
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        if (sanitizeName().isBlank()) {
            edtFullName.error = "Full name is required."
            isValid = false
        }

        val email = sanitizeEmail()
        if (email.isBlank()) {
            edtEmail.error = "Email address is required."
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.error = "Enter a valid email address."
            isValid = false
        }

        val phone = sanitizePhone()
        if (phone.isBlank()) {
            edtPhone.error = "Phone number is required."
            isValid = false
        } else if (!isPhoneValid(phone)) {
            edtPhone.error = "Enter a valid phone number."
            isValid = false
        }

        return isValid
    }

    private fun isPhoneValid(phone: String): Boolean {
        val pattern = Regex("^[+]?[-0-9()\\s]{7,20}$")
        val digits = phone.count { it.isDigit() }
        return pattern.matches(phone) && digits in 7..15
    }

    private fun hasChanges(): Boolean {
        return sanitizeName() != initialFullName ||
            sanitizeEmail() != initialEmail ||
            sanitizePhone() != initialPhone ||
            avatarChanged
    }

    private fun captureInitialFormSnapshot() {
        initialFullName = sanitizeName()
        initialEmail = sanitizeEmail()
        initialPhone = sanitizePhone()
    }

    private fun sanitizeName(): String = edtFullName.text.toString().trim()

    private fun sanitizeEmail(): String = edtEmail.text.toString().trim()

    private fun sanitizePhone(): String = edtPhone.text.toString().trim()

    private fun clearFieldErrors() {
        edtFullName.error = null
        edtEmail.error = null
        edtPhone.error = null
    }

    private fun showApiError(message: String) {
        txtApiError.text = message
        txtApiError.visibility = View.VISIBLE
        btnRetryProfileLoad.visibility = View.VISIBLE
    }

    private fun clearApiError() {
        txtApiError.text = ""
        txtApiError.visibility = View.GONE
        btnRetryProfileLoad.visibility = View.GONE
    }

    private fun setLoadingState(loading: Boolean) {
        isLoadingProfile = loading
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE

        edtFullName.isEnabled = !loading
        edtEmail.isEnabled = !loading
        edtPhone.isEnabled = !loading
        txtChangePhoto.isEnabled = !loading

        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        val canSave = !isLoadingProfile && !isSavingProfile && hasChanges()
        btnSaveChanges.isEnabled = canSave
        btnSaveChanges.text = if (isSavingProfile) "Saving..." else "Save Changes"
    }

    private fun cacheSelectedAvatar(uri: Uri): File? {
        return runCatching {
            val avatarDirectory = File(filesDir, "avatars")
            if (!avatarDirectory.exists()) {
                avatarDirectory.mkdirs()
            }

            val userKey = sessionManager.getUserId()?.takeIf { it.isNotBlank() } ?: "current"
            val targetFile = File(avatarDirectory, "avatar_$userKey.jpg")

            contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    return null
                }
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        }.getOrNull()
    }

    private suspend fun refreshProfileStateFromBackend() {
        when (val result = repository.getMyProfile()) {
            is NetworkResult.Success -> {
                val user = result.data
                sessionManager.updateProfile(user.fullName, user.phoneNumber, user.email)
                sessionManager.saveRole(user.role)
                sessionManager.saveAvatarFileId(user.avatarFileId)
                initialAvatarFileId = user.avatarFileId

                val avatarPreview = loadAvatarPreview(repository, filesDir, user.userId.toString(), user.avatarFileId)
                renderAvatar(imgAvatar, imgAvatarPlaceholder, avatarPreview)
                if (avatarPreview == null) {
                    Log.w(TAG, "Profile refresh succeeded, but avatar preview data was not available.")
                }
            }

            is NetworkResult.Error -> {
                Log.w(TAG, "Profile refresh after save failed: ${result.message}")
            }

            else -> Unit
        }
    }
}

private fun resolveAvatarCacheFile(filesDir: File, userId: String?, avatarFileId: String?): File? {
    if (userId.isNullOrBlank() || avatarFileId.isNullOrBlank()) {
        return null
    }

    val avatarDirectory = File(filesDir, "avatars")
    if (!avatarDirectory.exists()) {
        avatarDirectory.mkdirs()
    }

    return File(avatarDirectory, "avatar_${userId}_$avatarFileId.jpg")
}

private data class AvatarPreview(
    val cachedFile: File? = null,
    val storedFile: StoredFileResponse? = null,
)

private suspend fun loadAvatarPreview(
    repository: AttendeeRepository,
    filesDir: File,
    userId: String?,
    avatarFileId: String?,
): AvatarPreview? {
    val cacheFile = resolveAvatarCacheFile(filesDir, userId, avatarFileId)
    if (cacheFile?.exists() == true) {
        return AvatarPreview(cachedFile = cacheFile)
    }

    val fileId = avatarFileId ?: return null
    return when (val result = repository.getStoredFile(fileId)) {
        is NetworkResult.Success -> {
            val storedFile = result.data
            val cachedFile = cacheAvatarPayload(filesDir, userId, storedFile)
            AvatarPreview(cachedFile = cachedFile, storedFile = storedFile)
        }
        else -> null
    }
}

private fun cacheAvatarPayload(
    filesDir: File,
    userId: String?,
    storedFile: StoredFileResponse,
): File? {
    val cacheFile = resolveAvatarCacheFile(filesDir, userId, storedFile.fileId.toString()) ?: return null
    val encodedContent = storedFile.contentBase64?.takeIf { it.isNotBlank() } ?: return null

    return runCatching {
        val decoded = Base64.getDecoder().decode(encodedContent)
        FileOutputStream(cacheFile).use { output ->
            output.write(decoded)
        }
        cacheFile
    }.getOrNull()
}

private fun renderAvatar(imageView: ImageView, placeholder: View, preview: AvatarPreview?) {
    val cachedFile = preview?.cachedFile
    if (cachedFile?.exists() == true) {
        imageView.setImageURI(Uri.fromFile(cachedFile))
        imageView.visibility = View.VISIBLE
        placeholder.visibility = View.GONE
        return
    }

    val encodedContent = preview?.storedFile?.contentBase64?.takeIf { it.isNotBlank() }
    if (!encodedContent.isNullOrBlank()) {
        val bitmap = runCatching {
            val bytes = Base64.getDecoder().decode(encodedContent)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.onFailure {
            Log.w(TAG, "Unable to decode avatar preview from backend payload.", it)
        }.getOrNull()

        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
            return
        }
    }

    if (cachedFile == null || !cachedFile.exists()) {
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        placeholder.visibility = View.VISIBLE
    }
}

private fun renderAvatar(imageView: ImageView, placeholder: View, file: File?) {
    renderAvatar(imageView, placeholder, file?.let { AvatarPreview(cachedFile = it) })
}
