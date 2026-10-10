package com.guardcall.ui.screens

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.guardcall.data.*
import com.guardcall.domain.HistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class Screen(val title: String) {
    HOME("Accueil"), BLOCKED("Bloqués"), ALLOWED("Liste blanche"), RANGES("Plages"),
    IDENTIFIED("Identifiés"), HISTORY("Historique"), SETTINGS("Réglages")
}

enum class EntryKind(val title: String) {
    BLOCKED("Numéro bloqué"), ALLOWED("Numéro autorisé"), IDENTIFIED("Numéro identifié"), RANGE("Plage bloquée")
}

data class EntryDraft(val kind: EntryKind, val number: String = "", val label: String = "", val editing: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(store: BlocklistStore, historyStore: HistoryStore) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = Screen.valueOf(screenName)
    var payload by remember { mutableStateOf(BlocklistPayload.EMPTY) }
    var history by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var roleHeld by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<EntryDraft?>(null) }
    var clearHistory by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else false // A declared service alone does not prove selection on Android 8/9.
        try {
            payload = store.load()
            loadError = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { loadError = e.message ?: "Liste indisponible." }
        try {
            history = historyStore.load()
            historyError = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { historyError = "Historique illisible. Vous pouvez l’effacer depuis cet écran." }
        loading = false
    }

    fun change(operation: suspend () -> MutationResult) {
        if (busy || loading || loadError != null) return
        busy = true
        scope.launch {
            try {
                val result = operation()
                result.payloadOrNull?.let { payload = it; draft = null }
                busy = false
                snackbar.showSnackbar(result.errorMessageOrNull ?: "Enregistré")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { busy = false; snackbar.showSnackbar(e.message ?: "Opération impossible.") }
            finally { busy = false }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch { refresh() }
    }
    fun activate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = context.getSystemService(RoleManager::class.java)
                if (manager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true) {
                    launcher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                } else scope.launch { snackbar.showSnackbar("Filtrage indisponible sur cet appareil.") }
            } else {
                scope.launch { snackbar.showSnackbar("Le filtrage par Onraccroche nécessite Android 10 ou plus.") }
            }
        } catch (e: Exception) { scope.launch { snackbar.showSnackbar("Ouvrez les réglages Téléphone de votre appareil.") } }
    }

    LaunchedEffect(screen) { refresh() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refresh() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = screen != Screen.HOME && draft == null) { screenName = Screen.HOME.name }
    val editable = !busy && !loading && loadError == null

    Scaffold(
        topBar = { TopAppBar(title = { Text("Onraccroche") }, actions = {
            TextButton(enabled = !busy && !loading, onClick = { scope.launch { refresh() } }) { Text("Actualiser") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Screen.entries.forEach { destination ->
                    FilterChip(selected = screen == destination, onClick = { screenName = destination.name },
                        label = { Text(destination.title) })
                }
            }
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(payload, roleHeld, editable, { activate() }, { screenName = it.name })
                    Screen.BLOCKED -> BlockedScreen(payload, editable, { draft = it }, { n -> change { store.removeBlocked(n) } })
                    Screen.ALLOWED -> AllowedScreen(payload, editable, { draft = it }, { n -> change { store.removeAllowed(n) } })
                    Screen.IDENTIFIED -> IdentifiedScreen(payload, editable, { draft = it }, { n -> change { store.removeIdentification(n) } })
                    Screen.RANGES -> RangesScreen(payload, editable, { draft = it },
                        { key -> change { store.removeRange(key) } }, { change { store.importBegoneList() } },
                        { change { store.clearBegoneList() } })
                    Screen.HISTORY -> HistoryScreen(history, historyError, editable, { clearHistory = true }, { draft = it })
                    Screen.SETTINGS -> SettingsScreen(payload, editable, roleHeld, { activate() },
                        { enabled -> change { store.setProtection(enabled) } }, { enabled -> change { store.setHistory(enabled) } })
                }
            }
        }
    }

    draft?.let { entry ->
        EntryDialog(entry, busy, { draft = null }) { number, label ->
            change {
                when (entry.kind) {
                    EntryKind.BLOCKED -> store.addBlocked(number, label.takeIf { it.isNotBlank() })
                    EntryKind.ALLOWED -> store.addAllowed(number, label.takeIf { it.isNotBlank() })
                    EntryKind.IDENTIFIED -> if (label.isBlank()) MutationResult.InvalidNumber("Saisissez une étiquette.")
                        else store.addIdentification(number, label)
                    EntryKind.RANGE -> store.addRange(number, label.takeIf { it.isNotBlank() })
                }
            }
        }
    }
    if (clearHistory) AlertDialog(
        onDismissRequest = { if (!busy) clearHistory = false },
        title = { Text("Effacer l’historique ?") }, text = { Text("Cette action est définitive. Les règles sont conservées.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { historyStore.clear(); history = emptyList(); historyError = null; clearHistory = false }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { snackbar.showSnackbar("Effacement impossible.") }
                finally { busy = false }
            }
        }) { Text("Effacer") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { clearHistory = false }) { Text("Annuler") } }
    )
}

@Composable
private fun EntryDialog(draft: EntryDraft, busy: Boolean, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var number by rememberSaveable(draft) { mutableStateOf(draft.number) }
    var label by rememberSaveable(draft) { mutableStateOf(draft.label) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(draft.kind.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = number, onValueChange = { if (it.length <= 64) number = it },
                label = { Text(if (draft.kind == EntryKind.RANGE) "Motif +33162######" else "Numéro de téléphone") },
                singleLine = true, readOnly = draft.editing, enabled = !busy)
            OutlinedTextField(value = label, onValueChange = { if (it.length <= 100) label = it },
                label = { Text("Étiquette") }, singleLine = true, enabled = !busy)
            if (draft.kind == EntryKind.ALLOWED) Text("Prioritaire sur tout blocage et toute identification silencieuse.")
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { save(number, label.trim()) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Annuler") } }
    )
}
