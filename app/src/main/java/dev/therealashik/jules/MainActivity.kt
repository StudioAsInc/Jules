package dev.therealashik.jules

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val secure = SecurePrefs(applicationContext)
        val prefs = AppPreferences(applicationContext)
        val repository = JulesRepository(secure.readApiKey())
        val factory = JulesViewModelFactory(repository, secure, prefs)
        setContent { JulesApplication(factory) }
    }
}

private val JulesBlue = Color(0xFF3569D4)
private val GlowBlue = Color(0xFF71A7FF)

@Composable
private fun JulesApplication(factory: JulesViewModelFactory) {
    val vm: JulesViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val dark = when (state.theme) { ThemePreference.LIGHT -> false; ThemePreference.DARK -> true; ThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme() }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF9BB9FF), background = Color(0xFF171717), surface = Color(0xFF202020), surfaceVariant = Color(0xFF2A2A2A), onSurface = Color(0xFFECECEC), onSurfaceVariant = Color(0xFFB4B4B4))
        else lightColorScheme(primary = JulesBlue, background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF4F4F5), onSurface = Color(0xFF111111), onSurfaceVariant = Color(0xFF686868))
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { JulesRoot(vm, state) }
    }
}

@Composable
private fun JulesRoot(vm: JulesViewModel, state: AppUiState) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val savedScreens = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val goHome: () -> Unit = { vm.navigate(AppScreen.Home) }
    BackHandler(enabled = state.screen != AppScreen.Home) { goHome() }

    if (state.screen == AppScreen.Home) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                HistoryDrawer(state, onNew = { scope.launch { drawerState.close() }; vm.navigate(AppScreen.NewTask) },
                    onSelect = { session -> scope.launch { drawerState.close() }; vm.openSession(session) },
                    onSettings = { scope.launch { drawerState.close() }; vm.navigate(AppScreen.Settings) })
            }
        ) {
            HomeScreen(state, onMenu = openDrawer, onSettings = { vm.navigate(AppScreen.Settings) }, onRefresh = vm::refreshSessions,
                onNew = { vm.navigate(AppScreen.NewTask) }, onSelect = vm::openSession, onLoadMore = vm::loadMoreSessions, onClearError = vm::clearError)
        }
    } else {
        val saveKey = when (val current = state.screen) {
            is AppScreen.Detail -> "detail:${current.sessionId}"
            AppScreen.NewTask -> "new-task"
            AppScreen.Settings -> "settings"
            AppScreen.Gallery -> "gallery"
            AppScreen.Home -> "home"
        }
        savedScreens.SaveableStateProvider(saveKey) {
        when (val screen = state.screen) {
        AppScreen.Settings -> SettingsScreen(state, onBack = goHome, onSaveKey = vm::validateAndSaveApiKey,
            onRemoveKey = vm::removeApiKey, onTheme = vm::setTheme, onPageSize = vm::setPageSize,
            onGallery = { vm.openGallery(false) }, onClearError = vm::clearError)
        AppScreen.NewTask -> NewTaskScreen(state, onBack = goHome, onCreate = vm::createSession,
            onGallery = { vm.openGallery(true) }, onSourcesRetry = vm::retrySources, onClearSeed = vm::clearPromptSeed,
            onClearError = vm::clearError)
        AppScreen.Gallery -> PromptGalleryScreen(state, onBack = vm::returnFromGallery,
            onSave = vm::savePrompt, onDelete = vm::deletePrompt, onUse = vm::usePrompt)
        is AppScreen.Detail -> SessionDetailScreen(state, onBack = goHome, onRefresh = { vm.refreshDetail(screen.sessionId) },
            onRefreshNow = { vm.refreshDetailNow(screen.sessionId) }, onSend = { vm.sendMessage(screen.sessionId, it) },
            onApprove = { vm.approvePlan(screen.sessionId) }, onDelete = { vm.deleteSession(screen.sessionId) },
            onLoadOlder = { vm.loadOlderActivities(screen.sessionId) }, onClearError = vm::clearError)
        AppScreen.Home -> Unit
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDrawer(state: AppUiState, onNew: () -> Unit, onSelect: (Session) -> Unit, onSettings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalDrawerSheet(modifier = Modifier.widthIn(max = 360.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Jules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search sessions") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(18.dp))
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("New task")
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val matches = state.sessions.filter { query.isBlank() || it.title.contains(query, true) || it.prompt.contains(query, true) }
                items(matches, key = { it.name.ifBlank { it.id } }) { session ->
                    TextButton(onClick = { onSelect(session) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(session.title.ifBlank { "Untitled task" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Text(statusLabel(session.state), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (matches.isEmpty() && !state.isLoadingSessions) item { Text("No matching Jules sessions", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider()
            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null); Spacer(Modifier.width(10.dp)); Text("Settings", color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(state: AppUiState, onMenu: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit, onNew: () -> Unit, onSelect: (Session) -> Unit, onLoadMore: () -> Unit, onClearError: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Jules", fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "Open session history") } }, actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh sessions") }; IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("New task") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search your sessions") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(20.dp))
            state.error?.let { ErrorBanner(it, onRetry = onRefresh, onDismiss = onClearError) }
            if (state.isLoadingSessions && state.sessions.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Loading your Jules sessions…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val filtered = state.sessions.filter { search.isBlank() || it.title.contains(search, true) || it.prompt.contains(search, true) }
                if (filtered.isEmpty()) {
                    EmptyState(title = if (search.isBlank()) "Your Jules history starts here" else "No sessions found", subtitle = if (search.isBlank()) "Create a task to see its real Jules progress and results here." else "Try another title or prompt.", action = if (search.isBlank()) "Start a task" else null, onAction = onNew)
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groupSessions(filtered).forEach { (group, sessions) ->
                            item(key = "group_$group") { Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) }
                            items(sessions, key = { it.name.ifBlank { it.id } }) { session -> SessionRow(session, onClick = { onSelect(session) }) }
                        }
                        if (state.nextSessionsToken != null) item {
                            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoadingMoreSessions) {
                                if (state.isLoadingMoreSessions) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Load more sessions")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(20.dp)); Button(onClick = onAction) { Text(action) } }
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(session.state); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.title.ifBlank { session.prompt.ifBlank { "Untitled task" } }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp)); Text(session.updateTime.ifBlank { session.createTime }.take(19).replace('T', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp)); Text(statusLabel(session.state), style = MaterialTheme.typography.labelSmall, color = statusColor(session.state))
        }
    }
}

@Composable
private fun StatusDot(state: String) {
    val color = statusColor(state)
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}
@Composable
private fun statusColor(state: String): Color = when (state) {
    "COMPLETED" -> Color(0xFF2E8B57)
    "FAILED" -> MaterialTheme.colorScheme.error
    "IN_PROGRESS", "PLANNING", "QUEUED" -> MaterialTheme.colorScheme.primary
    "AWAITING_PLAN_APPROVAL", "AWAITING_USER_FEEDBACK" -> Color(0xFFD28B24)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
private fun statusLabel(state: String) = state.removePrefix("STATE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "Unknown" }

private fun groupSessions(sessions: List<Session>): List<Pair<String, List<Session>>> {
    val today = LocalDate.now(ZoneId.systemDefault()); val yesterday = today.minusDays(1); val week = today.minusDays(7)
    return sessions.groupBy { session ->
        val raw = session.updateTime.ifBlank { session.createTime }
        val date = runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        when { date == today -> "Today"; date == yesterday -> "Yesterday"; date != null && !date.isBefore(week) -> "Previous 7 Days"; else -> "Earlier" }
    }.toList().sortedBy { (label, _) -> listOf("Today", "Yesterday", "Previous 7 Days", "Earlier").indexOf(label) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskScreen(state: AppUiState, onBack: () -> Unit, onCreate: (String, String, Source?, String?, Boolean, Boolean) -> Unit, onGallery: () -> Unit, onSourcesRetry: () -> Unit, onClearSeed: () -> Unit, onClearError: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf(state.promptSeed) }
    var selectedSourceName by rememberSaveable { mutableStateOf("") }
    var selectedBranch by rememberSaveable { mutableStateOf("") }
    var requireApproval by rememberSaveable { mutableStateOf(true) }
    var autoPr by rememberSaveable { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    LaunchedEffect(state.promptSeed) { if (state.promptSeed.isNotBlank()) { prompt = state.promptSeed; onClearSeed() } }
    val selectedSource = state.sources.firstOrNull { it.name == selectedSourceName }
    val branches = selectedSource?.githubRepo?.branches.orEmpty()
    val sourceLabel = selectedSource?.let { it.githubRepo?.let { repo -> "${repo.owner}/${repo.repo}" } ?: it.name } ?: "Choose a connected repository (optional)"

    Scaffold(topBar = { TopAppBar(title = { Text("New task") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("What should Jules work on?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Task title (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(10.dp))
            Box {
                OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(sourceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text("⌄") }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    DropdownMenuItem(text = { Text("No repository") }, onClick = { selectedSourceName = ""; selectedBranch = ""; sourceMenu = false })
                    state.sources.forEach { source -> DropdownMenuItem(text = { Text(source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name) }, onClick = { selectedSourceName = source.name; selectedBranch = source.githubRepo?.defaultBranch?.displayName.orEmpty(); sourceMenu = false }) }
                }
            }
            if (state.isLoadingSources) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            if (state.sources.isEmpty() && !state.isLoadingSources) {
                Text(state.sourceError ?: "No connected Jules sources were returned. You can create a repository-free task or retry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (state.sourceError != null) TextButton(onClick = onSourcesRetry) { Text("Retry sources") }
            }
            if (branches.isNotEmpty()) {
                Spacer(Modifier.height(8.dp)); Box {
                    OutlinedButton(onClick = { branchMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Branch: ${selectedBranch.ifBlank { "Select branch" }}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("⌄") }
                    DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) { branches.forEach { branch -> DropdownMenuItem(text = { Text(branch.displayName) }, onClick = { selectedBranch = branch.displayName; branchMenu = false }) } }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Task prompt", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onGallery) { Icon(Icons.Default.Bookmark, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Prompt gallery") }
            }
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f).fillMaxWidth(), placeholder = { Text("Describe the coding task for Jules…") }, shape = RoundedCornerShape(20.dp), minLines = 5, maxLines = 10, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Require plan approval", style = MaterialTheme.typography.bodyMedium); Text("Wait for you to approve Jules’ plan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(requireApproval, { requireApproval = it }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Auto-create pull request", style = MaterialTheme.typography.bodyMedium); Text("Use Jules’ AUTO_CREATE_PR mode.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(autoPr, { autoPr = it }) }
            state.error?.let { ErrorBanner(it, onRetry = { onCreate(prompt, title, selectedSource, selectedBranch, requireApproval, autoPr) }, onDismiss = onClearError) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onCreate(prompt, title, selectedSource, selectedBranch, requireApproval, autoPr) }, enabled = prompt.isNotBlank() && !state.isCreating, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
                if (state.isCreating) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)); Text("Starting task…") } else { Icon(Icons.Default.Send, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Start task") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailScreen(state: AppUiState, onBack: () -> Unit, onRefresh: () -> Unit, onRefreshNow: suspend () -> Unit, onSend: (String) -> Unit, onApprove: () -> Unit, onDelete: () -> Unit, onLoadOlder: () -> Unit, onClearError: () -> Unit) {
    val screen = state.screen as? AppScreen.Detail ?: return
    var message by rememberSaveable(screen.sessionId) { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(screen.sessionId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            onRefreshNow()
            while (isActive) { delay(25_000); onRefreshNow() }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(state.session?.title?.ifBlank { "Jules task" } ?: "Jules task", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, actions = {
        Box { IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Session actions") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("Refresh") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menuExpanded = false; onRefresh() })
                DropdownMenuItem(text = { Text("Delete session", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; confirmDelete = true })
            }
        }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            state.error?.let { ErrorBanner(it, onRetry = onRefresh, onDismiss = onClearError) }
            val session = state.session
            if (state.isRefreshingDetail && state.activities.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (session == null && state.activities.isEmpty() && !state.isRefreshingDetail) {
                EmptyState("Session details unavailable", state.error ?: "Refresh this Jules session to load its current state.", "Retry", onRefresh)
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    session?.let { current ->
                        item(key = "session_state") { Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(current.state); Spacer(Modifier.width(8.dp)); Text(statusLabel(current.state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        val hasInitialMessage = state.activities.any { it.userMessaged?.userMessage?.trim() == current.prompt.trim() }
                        if (current.prompt.isNotBlank() && !hasInitialMessage) item(key = "initial_prompt") { UserBubble(current.prompt) }
                    }
                    if (state.activities.isEmpty() && !state.isRefreshingDetail) item { Text("No activity has been reported yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp)) }
                    items(state.activities, key = { it.name.ifBlank { it.id.ifBlank { it.createTime + it.description } } }) { activity -> ActivityItem(activity, canApprove = session?.state == "AWAITING_PLAN_APPROVAL", isApproving = state.isApproving, onApprove = onApprove) }
                    session?.outputs.orEmpty().mapNotNull { it.pullRequest }.forEachIndexed { index, pr -> item(key = "pr_$index") { PullRequestCard(pr, onOpen = { runCatching { uriHandler.openUri(pr.url) } }) } }
                    if (state.nextActivitiesToken != null) item(key = "load_older") { TextButton(onClick = onLoadOlder, enabled = !state.isLoadingMoreActivities, modifier = Modifier.fillMaxWidth()) { if (state.isLoadingMoreActivities) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Load earlier activity") } }
                }
            }
            Composer(value = message, onValueChange = { message = it }, sending = state.isSending, enabled = state.session?.state != "COMPLETED" && state.session?.state != "FAILED", onSend = { val sent = message.trim(); if (sent.isNotEmpty()) { onSend(sent); message = "" } })
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this session?") }, text = { Text("This removes the session from Jules. This action cannot be undone.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !state.isDeleting) { Text(if (state.isDeleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, sending: Boolean, enabled: Boolean, onSend: () -> Unit) {
    val borderBrush = Brush.linearGradient(listOf(Color(0xFF8CAEFF), Color(0xFF78C5E8), Color(0xFFB6A1FF)))
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).shadow(4.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).border(BorderStroke(1.dp, borderBrush), RoundedCornerShape(26.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        BasicTextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f).heightIn(min = 42.dp, max = 120.dp).padding(vertical = 10.dp), enabled = enabled && !sending,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), maxLines = 5, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), decorationBox = { inner -> Box { if (value.isBlank()) Text(if (enabled) "Message Jules…" else "This session is no longer accepting messages", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onSend, enabled = enabled && value.isNotBlank() && !sending, modifier = Modifier.size(44.dp), shape = CircleShape) {
            if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, contentDescription = "Send message")
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp), modifier = Modifier.widthIn(max = 340.dp)) {
            SelectionContainer { Text(text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun ActivityItem(activity: Activity, canApprove: Boolean, isApproving: Boolean, onApprove: () -> Unit) {
    when {
        activity.userMessaged != null -> UserBubble(activity.userMessaged.userMessage)
        activity.agentMessaged != null -> AgentCard(activity.agentMessaged.agentMessage.ifBlank { activity.description }, activity)
        activity.planGenerated?.plan != null -> PlanCard(activity.planGenerated.plan, canApprove, isApproving, onApprove)
        activity.planApproved != null -> SystemCard("Plan approved", "Jules has started working through the approved plan.", Icons.Default.CheckCircle)
        activity.progressUpdated != null -> SystemCard(activity.progressUpdated.title.ifBlank { "Jules is working" }, activity.progressUpdated.description.ifBlank { activity.description }, Icons.Default.Code)
        activity.sessionCompleted != null -> SystemCard("Task completed", activity.description.ifBlank { "Jules finished this task." }, Icons.Default.CheckCircle)
        activity.sessionFailed != null -> SystemCard("Task failed", activity.sessionFailed.reason.ifBlank { activity.description }, Icons.Default.ErrorOutline, isError = true)
        activity.description.isNotBlank() -> AgentCard(activity.description, activity)
        else -> Unit
    }
    activity.artifacts.forEach { artifact ->
        artifact.changeSet?.gitPatch?.let { patch ->
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
                Text("Code changes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (patch.suggestedCommitMessage.isNotBlank()) Text(patch.suggestedCommitMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                if (patch.unidiffPatch.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(patch.unidiffPatch.take(5000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        artifact.bashOutput?.let { output ->
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
                Text(output.command.ifBlank { "Command output" }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(output.output.take(5000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Text(if (output.exitCode == 0) "Exit ${output.exitCode}" else "Exit ${output.exitCode} · failed", style = MaterialTheme.typography.labelSmall, color = if (output.exitCode == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        }
        artifact.media?.let { Text("Jules produced a ${it.mimeType.ifBlank { "media" }} artifact.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun AgentCard(text: String, activity: Activity) {
    Column(Modifier.fillMaxWidth().padding(end = 18.dp)) {
        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
        if (activity.createTime.isNotBlank()) Text(activity.createTime.take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun PlanCard(plan: Plan, canApprove: Boolean, isApproving: Boolean, onApprove: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Jules’ plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
        plan.steps.sortedBy { it.index }.forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text("${index + 1}.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                Column { Text(step.title.ifBlank { "Step ${index + 1}" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium); if (step.description.isNotBlank()) Text(step.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (canApprove) { Spacer(Modifier.height(12.dp)); Button(onClick = onApprove, enabled = !isApproving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) { if (isApproving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Approve plan") } }
        else { Spacer(Modifier.height(8.dp)); Text("Approval is not currently requested for this session.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SystemCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isError: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant).padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall); if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun PullRequestCard(pr: PullRequest, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
        Text("Pull request", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(pr.title.ifBlank { "View pull request" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        if (pr.description.isNotBlank()) Text(pr.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp), maxLines = 5, overflow = TextOverflow.Ellipsis)
        if (pr.url.startsWith("https://")) TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) { Text("Open on GitHub"); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: AppUiState, onBack: () -> Unit, onSaveKey: (String) -> Unit, onRemoveKey: () -> Unit, onTheme: (ThemePreference) -> Unit, onPageSize: (Int) -> Unit, onGallery: () -> Unit, onClearError: () -> Unit) {
    var keyDraft by rememberSaveable { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { if (state.apiKeyConfigured) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).imePadding()) {
            Spacer(Modifier.height(12.dp)); Text("Jules API", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Connect your own Jules API key to load real sources and sessions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            OutlinedTextField(value = keyDraft, onValueChange = { keyDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Jules API key") }, placeholder = { Text(if (state.apiKeyConfigured) "Key stored securely · enter a replacement" else "Paste your key") }, singleLine = true, visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (reveal) "Hide key" else "Reveal key") } }, shape = RoundedCornerShape(16.dp))
            Text("The key is validated against the Jules Sources API before it is saved. It is encrypted with Android Keystore on this device. A distributed client cannot hide a shared credential; use a trusted backend for multi-user releases.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            state.error?.let { ErrorBanner(it, onRetry = { if (keyDraft.isNotBlank()) onSaveKey(keyDraft) }, onDismiss = onClearError) }
            Button(onClick = { onSaveKey(keyDraft) }, enabled = keyDraft.isNotBlank() && !state.isValidatingKey, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) {
                if (state.isValidatingKey) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)); Text("Validating…") } else Text(if (state.apiKeyConfigured) "Validate and update key" else "Validate and save key")
            }
            if (state.apiKeyConfigured) TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Remove API key", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(22.dp)); HorizontalDivider(); Spacer(Modifier.height(18.dp))
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Theme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemePreference.entries.forEach { pref -> FilterChip(selected = state.theme == pref, onClick = { onTheme(pref) }, label = { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            Text("Sessions per page · ${state.pageSize}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp))
            Slider(value = state.pageSize.toFloat(), onValueChange = { onPageSize(it.toInt()) }, valueRange = 10f..100f, steps = 8)
            Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(18.dp))
            Text("Prompts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Browse and manage prompts you save on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Bookmark, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Open prompt gallery") }
            Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(14.dp))
            Text("Jules for Android · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Jules API client · https://jules.googleapis.com/v1alpha", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(30.dp))
        }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("Remove Jules API key?") }, text = { Text("You will need to validate a key again before using Jules API features.") }, confirmButton = { TextButton(onClick = { confirmRemove = false; onRemoveKey() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptGalleryScreen(state: AppUiState, onBack: () -> Unit, onSave: (String, String) -> Unit, onDelete: (String) -> Unit, onUse: (String) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Prompt gallery") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Save a prompt") } }) }) { padding ->
        if (state.prompts.isEmpty()) Box(Modifier.fillMaxSize().padding(padding)) { EmptyState("No saved prompts yet", "Save reusable prompts here. They stay on this device and can be inserted into a new task.", "Save a prompt", { showAdd = true }) }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.prompts, key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${item.title}", tint = MaterialTheme.colorScheme.error) } }
                    Text(item.prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { onUse(item.prompt) }, modifier = Modifier.align(Alignment.End)) { Text("Use in a task") }
                }
            }
        }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Save a prompt") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true); OutlinedTextField(prompt, { prompt = it }, label = { Text("Prompt") }, minLines = 3, maxLines = 6) } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank() && prompt.isNotBlank()) { onSave(title, prompt); title = ""; prompt = ""; showAdd = false } }, enabled = title.isNotBlank() && prompt.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onRetry) { Text("Retry") }; TextButton(onClick = onDismiss) { Text("Dismiss") } }
    }
}
