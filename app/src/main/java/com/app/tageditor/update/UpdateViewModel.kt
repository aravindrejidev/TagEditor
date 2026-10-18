package com.app.tageditor.update

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class UpdatePhase { IDLE, AVAILABLE, DOWNLOADING, READY, NEEDS_PERMISSION, FAILED }

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val info: UpdateInfo? = null,
    val file: File? = null,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val message: String? = null
)

/**
 * Looks for a newer GitHub release every time the app starts and drives the update pop-up:
 * available -> downloading -> installer.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = UpdateRepository(application)
    private val prefs = application.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val currentVersion: String = repo.currentVersion

    private var downloadJob: Job? = null

    init {
        repo.cleanup()
        checkForUpdate(manual = false)
    }

    private fun toast(text: String) {
        messageChannel.trySend(text)
    }

    /** [manual] = the user tapped the version chip: always answer, ignore "skip this version". */
    fun checkForUpdate(manual: Boolean) {
        if (!repo.isConfigured) {
            if (manual) toast("Update check is not set up for this build")
            return
        }
        if (_checking.value || _state.value.phase != UpdatePhase.IDLE) return
        viewModelScope.launch {
            _checking.value = true
            val result = runCatching { repo.fetchLatest() }
            _checking.value = false

            val latest = result.getOrNull()
            if (result.isFailure) {
                if (manual) toast("Could not check for updates. Are you online?")
            } else if (latest == null || !repo.isNewer(latest.version, repo.currentVersion)) {
                if (manual) toast("You have the latest version (v${repo.currentVersion})")
            } else if (!manual && prefs.getString(KEY_SKIPPED, "") == latest.version) {
                // The user chose to skip this version.
            } else {
                _state.value = UpdateUiState(phase = UpdatePhase.AVAILABLE, info = latest)
            }
        }
    }

    /** The big button of the pop-up: update, install or retry depending on the phase. */
    fun onPrimaryAction() {
        when (_state.value.phase) {
            UpdatePhase.AVAILABLE, UpdatePhase.FAILED -> startDownload()
            UpdatePhase.READY, UpdatePhase.NEEDS_PERMISSION -> install()
            else -> Unit
        }
    }

    private fun startDownload() {
        val info = _state.value.info ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _state.value = UpdateUiState(UpdatePhase.DOWNLOADING, info, null, 0L, info.apkSize)
            try {
                val file = repo.download(info) { done, total ->
                    _state.update { it.copy(downloaded = done, total = total) }
                }
                _state.value = UpdateUiState(UpdatePhase.READY, info, file)
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateUiState(
                    phase = UpdatePhase.FAILED,
                    info = info,
                    message = e.message ?: "The download failed"
                )
            }
        }
    }

    /** Hands the downloaded APK to the system installer (after the user allowed installs). */
    fun install() {
        val current = _state.value
        val info = current.info ?: return
        val file = current.file ?: return
        if (!repo.canInstallPackages()) {
            _state.value = UpdateUiState(UpdatePhase.NEEDS_PERMISSION, info, file)
            startSafely(repo.unknownSourcesIntent())
            return
        }
        _state.value = UpdateUiState(UpdatePhase.READY, info, file)
        try {
            getApplication<Application>().startActivity(repo.installIntent(file))
        } catch (e: Exception) {
            _state.value = UpdateUiState(
                phase = UpdatePhase.FAILED,
                info = info,
                message = "Could not open the installer: ${e.message ?: "unknown error"}"
            )
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            toast("Could not open the settings screen")
        }
    }

    /** "Later": hide the pop-up until the next app start. */
    fun later() {
        downloadJob?.cancel()
        _state.value = UpdateUiState()
    }

    /** "Skip this version": do not show the pop-up again for this release. */
    fun skipVersion() {
        val info = _state.value.info
        if (info != null) prefs.edit().putString(KEY_SKIPPED, info.version).apply()
        later()
    }

    fun openReleasePage() {
        val url = _state.value.info?.pageUrl ?: return
        if (url.isNotBlank()) startSafely(repo.pageIntent(url))
    }

    private companion object {
        const val KEY_SKIPPED = "skipped_version"
    }
}
