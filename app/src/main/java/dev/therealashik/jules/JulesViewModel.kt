package dev.therealashik.jules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface AppScreen {
    data object Home : AppScreen
    data object NewTask : AppScreen
    data class Detail(val sessionId: String) : AppScreen
    data object Settings : AppScreen
    data object Gallery : AppScreen
}
enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class AppUiState(
    val screen: AppScreen = AppScreen.Home,
    val galleryReturnToNewTask: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val sessions: List<Session> = emptyList(), val nextSessionsToken: String? = null,
    val sources: List<Source> = emptyList(), val session: Session? = null, val activities: List<Activity> = emptyList(),
    val nextActivitiesToken: String? = null, val prompts: List<SavedPrompt> = emptyList(), val promptSeed: String = "",
    val theme: ThemePreference = ThemePreference.SYSTEM, val pageSize: Int = 30,
    val isLoadingSessions: Boolean = false, val isLoadingMoreSessions: Boolean = false,
    val isLoadingSources: Boolean = false, val isRefreshingDetail: Boolean = false,
    val isLoadingMoreActivities: Boolean = false, val isCreating: Boolean = false,
    val isSending: Boolean = false, val isApproving: Boolean = false, val isDeleting: Boolean = false,
    val isValidatingKey: Boolean = false, val error: String? = null, val sourceError: String? = null
)

class JulesViewModel(
    private val repository: JulesRepository,
    private val securePrefs: SecurePrefs,
    private val preferences: AppPreferences
) : ViewModel() {
    private val detailMutex = Mutex()
    private var sessionsJob: Job? = null
    private val _state = MutableStateFlow(AppUiState(
        apiKeyConfigured = repository.hasKey,
        screen = if (repository.hasKey) AppScreen.Home else AppScreen.Settings,
        theme = runCatching { ThemePreference.valueOf(preferences.theme()) }.getOrDefault(ThemePreference.SYSTEM),
        pageSize = preferences.pageSize(), prompts = preferences.prompts()
    ))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init { if (repository.hasKey) { refreshSessions(); loadSources() } }
    fun navigate(screen: AppScreen) {
        _state.update { it.copy(screen = screen, error = null) }
        when (screen) { AppScreen.Home -> refreshSessions(); AppScreen.NewTask -> loadSources(); AppScreen.Gallery -> refreshPrompts(); else -> Unit }
    }
    fun openGallery(fromCreate: Boolean) { _state.update { it.copy(screen = AppScreen.Gallery, galleryReturnToNewTask = fromCreate, error = null) }; refreshPrompts() }
    fun returnFromGallery() { val target = if (_state.value.galleryReturnToNewTask) AppScreen.NewTask else AppScreen.Settings; _state.update { it.copy(screen = target, galleryReturnToNewTask = false) } }
    fun retrySources() = loadSources()
    fun openSession(session: Session) {
        val id = session.name.ifBlank { session.id }.substringAfterLast('/')
        _state.update { it.copy(screen = AppScreen.Detail(id), session = session, activities = emptyList(), nextActivitiesToken = null, error = null) }
    }
    fun refreshSessions() {
        if (_state.value.isLoadingSessions || _state.value.isLoadingMoreSessions) return
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingSessions = true, error = null, nextSessionsToken = null) }
            try {
                val page = repository.listSessions(_state.value.pageSize, null)
                _state.update { it.copy(sessions = page.sessions, nextSessionsToken = page.nextPageToken, isLoadingSessions = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.update { it.copy(isLoadingSessions = false, error = friendlyError(e)) }
            }
        }
    }
    fun loadMoreSessions() {
        val token = _state.value.nextSessionsToken ?: return
        if (_state.value.isLoadingMoreSessions) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMoreSessions = true, error = null) }
            try {
                val page = repository.listSessions(_state.value.pageSize, token)
                _state.update { old -> old.copy(sessions = (old.sessions + page.sessions).distinctBy { it.name.ifBlank { it.id } }, nextSessionsToken = page.nextPageToken, isLoadingMoreSessions = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.update { it.copy(isLoadingMoreSessions = false, error = friendlyError(e)) }
            }
        }
    }
    private fun loadSources() {
        if (!_state.value.apiKeyConfigured || _state.value.isLoadingSources) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSources = true, sourceError = null) }
            try {
                val all = mutableListOf<Source>(); var token: String? = null; var pages = 0
                do {
                    val page = repository.listSources(100, token); all += page.sources; token = page.nextPageToken; pages++
                } while (token != null && pages < 20)
                _state.update { it.copy(sources = all.distinctBy { source -> source.name }, isLoadingSources = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.update { it.copy(isLoadingSources = false, sourceError = friendlyError(e)) }
            }
        }
    }
    suspend fun refreshDetailNow(id: String) {
        detailMutex.lock()
        try {
            _state.update { it.copy(isRefreshingDetail = true, error = null) }
            coroutineScope {
                val session = async { repository.getSession(id) }
                val activityPage = async { repository.listActivities(id, _state.value.pageSize.coerceAtMost(100), null) }
                val updatedSession = session.await(); val updatedActivities = activityPage.await()
                _state.update { current ->
                    if ((current.screen as? AppScreen.Detail)?.sessionId == id) current.copy(
                        session = updatedSession, activities = updatedActivities.activities.sortedBy { it.createTime },
                        nextActivitiesToken = updatedActivities.nextPageToken, isRefreshingDetail = false
                    ) else current.copy(isRefreshingDetail = false)
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.update { it.copy(isRefreshingDetail = false, error = friendlyError(e)) }
        } finally { detailMutex.unlock() }
    }
    fun refreshDetail(id: String) { viewModelScope.launch { refreshDetailNow(id) } }
    fun loadOlderActivities(id: String) {
        val token = _state.value.nextActivitiesToken ?: return
        if (_state.value.isLoadingMoreActivities) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMoreActivities = true, error = null) }
            try {
                val page = repository.listActivities(id, _state.value.pageSize.coerceAtMost(100), token)
                _state.update { old -> old.copy(activities = (old.activities + page.activities).distinctBy { it.name.ifBlank { it.id } }.sortedBy { it.createTime }, nextActivitiesToken = page.nextPageToken, isLoadingMoreActivities = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(isLoadingMoreActivities = false, error = friendlyError(e)) } }
        }
    }
    fun createSession(prompt: String, title: String, source: Source?, branch: String?, requireApproval: Boolean, autoPr: Boolean) {
        if (_state.value.isCreating || prompt.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            try {
                val context = source?.let { SourceContext(it.name, branch?.takeIf(String::isNotBlank)?.let(::GitHubRepoContext)) }
                val session = repository.createSession(CreateSessionRequest(
                    prompt = prompt.trim(), title = title.trim().takeIf(String::isNotBlank), sourceContext = context,
                    requirePlanApproval = requireApproval, automationMode = if (autoPr) "AUTO_CREATE_PR" else null
                ))
                val id = session.name.ifBlank { session.id }.substringAfterLast('/')
                _state.update { it.copy(isCreating = false, screen = AppScreen.Detail(id), session = session, activities = emptyList(), nextActivitiesToken = null) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(isCreating = false, error = friendlyError(e)) } }
        }
    }
    fun sendMessage(id: String, message: String) {
        if (_state.value.isSending || message.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, error = null) }
            try { repository.sendMessage(id, message.trim()); refreshDetailNow(id) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = friendlyError(e)) } }
            finally { _state.update { it.copy(isSending = false) } }
        }
    }
    fun approvePlan(id: String) {
        if (_state.value.isApproving || _state.value.session?.state != "AWAITING_PLAN_APPROVAL") return
        viewModelScope.launch {
            _state.update { it.copy(isApproving = true, error = null) }
            try { repository.approvePlan(id); refreshDetailNow(id) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = friendlyError(e)) } }
            finally { _state.update { it.copy(isApproving = false) } }
        }
    }
    fun deleteSession(id: String) {
        if (_state.value.isDeleting) return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, error = null) }
            try {
                repository.deleteSession(id)
                _state.update { it.copy(isDeleting = false, sessions = it.sessions.filterNot { s -> (s.name.ifBlank { s.id }).substringAfterLast('/') == id }, screen = AppScreen.Home, session = null, activities = emptyList()) }
                refreshSessions()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(isDeleting = false, error = friendlyError(e)) } }
        }
    }
    fun validateAndSaveApiKey(key: String) {
        if (_state.value.isValidatingKey || key.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isValidatingKey = true, error = null) }
            try {
                val sources = repository.validateAndSetKey(key.trim())
                securePrefs.saveApiKey(key.trim())
                _state.update { it.copy(apiKeyConfigured = true, sources = sources, isValidatingKey = false, screen = AppScreen.Home) }
                refreshSessions(); loadSources()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(isValidatingKey = false, error = friendlyError(e)) } }
        }
    }
    fun removeApiKey() {
        repository.removeKey(); securePrefs.clearApiKey()
        _state.update { it.copy(apiKeyConfigured = false, sessions = emptyList(), sources = emptyList(), session = null, activities = emptyList(), screen = AppScreen.Settings, error = null) }
    }
    fun setTheme(theme: ThemePreference) { preferences.setTheme(theme.name); _state.update { it.copy(theme = theme) } }
    fun setPageSize(size: Int) { preferences.setPageSize(size); _state.update { it.copy(pageSize = size.coerceIn(10, 100)) } }
    fun refreshPrompts() { _state.update { it.copy(prompts = preferences.prompts()) } }
    fun savePrompt(title: String, prompt: String) {
        val current = preferences.prompts()
        preferences.setPrompts(current + SavedPrompt(UUID.randomUUID().toString(), title.trim(), prompt.trim()))
        refreshPrompts()
    }
    fun deletePrompt(id: String) { preferences.setPrompts(preferences.prompts().filterNot { it.id == id }); refreshPrompts() }
    fun usePrompt(prompt: String) { _state.update { it.copy(promptSeed = prompt, screen = AppScreen.NewTask) } }
    fun clearPromptSeed() { _state.update { it.copy(promptSeed = "") } }
    fun clearError() { _state.update { it.copy(error = null) } }

    private fun friendlyError(error: Exception): String = when (error) {
        is JulesApiException -> when (error.statusCode) {
            400 -> "Jules rejected the request. Check the prompt and selected source."
            401 -> "The API key is invalid. Update it in Settings."
            403 -> "This API key does not have permission for that Jules resource."
            404 -> "That Jules resource could not be found. Refresh and try again."
            429 -> "Jules API rate limit reached. Wait a little, then retry."
            in 500..599 -> "Jules is temporarily unavailable. Try again shortly."
            else -> "Jules API request failed. Please retry."
        }
        is UnknownHostException -> "No network connection. Check your connection and retry."
        is SocketTimeoutException -> "The request timed out. Please retry."
        else -> "Something went wrong. Please try again."
    }
}

class JulesViewModelFactory(private val repo: JulesRepository, private val secure: SecurePrefs, private val prefs: AppPreferences) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = JulesViewModel(repo, secure, prefs) as T
}
