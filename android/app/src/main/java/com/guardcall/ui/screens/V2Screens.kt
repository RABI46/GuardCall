package com.guardcall.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardcall.data.*
import com.guardcall.domain.CallAction
import com.guardcall.domain.HistoryEntry
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun InfoCard(title: String, text: String, content: @Composable ColumnScope.() -> Unit = {}) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
fun HomeScreen(payload: BlocklistPayload, roleHeld: Boolean, editable: Boolean,
    activate: () -> Unit, navigate: (Screen) -> Unit) {
    Page {
        item {
            InfoCard("Moins de démarchage. Plus de tranquillité.", "Vos règles restent sur votre téléphone.")
        }
        item {
            val status = when {
                !payload.settings.enabled -> "Protection en pause"
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "Filtrage disponible dès Android 10"
                roleHeld -> "Protection autorisée par Android"
                else -> "Autorisation requise"
            }
            InfoCard(status, "${payload.blocked.size} numéros bloqués · ${payload.effectiveRules.size} plages · ${payload.allowed.size} exceptions") {
                Button(onClick = activate) { Text("Configurer le filtrage") }
                Text("La liste blanche passe toujours avant les règles de blocage.")
                if (payload.isEmpty) Text("Aucun numéro n’est bloqué par défaut. Ajoutez une règle ou importez Begone-FR.")
            }
        }
        item { Button(enabled = editable, onClick = { navigate(Screen.RANGES) }) { Text("Gérer les plages anti-démarchage") } }
        item { OutlinedButton(onClick = { navigate(Screen.HISTORY) }) { Text("Voir l’historique local") } }
    }
}

@Composable
fun BlockedScreen(payload: BlocklistPayload, editable: Boolean, edit: (EntryDraft) -> Unit, remove: (String) -> Unit) {
    NumberList("Numéros bloqués", "Blocage exact du numéro. Une exception en liste blanche reste prioritaire.",
        EntryKind.BLOCKED, payload.blocked.map { it.phoneNumber to it.label.orEmpty() }, editable, edit, remove)
}

@Composable
fun AllowedScreen(payload: BlocklistPayload, editable: Boolean, edit: (EntryDraft) -> Unit, remove: (String) -> Unit) {
    NumberList("Liste blanche", "Ces numéros sonnent normalement, même s’ils sont couverts par une plage ou une identification.",
        EntryKind.ALLOWED, payload.allowed.map { it.phoneNumber to it.label.orEmpty() }, editable, edit, remove)
}

@Composable
fun IdentifiedScreen(payload: BlocklistPayload, editable: Boolean, edit: (EntryDraft) -> Unit, remove: (String) -> Unit) {
    NumberList("Numéros identifiés", "Ces appels ne sont pas rejetés, mais sont silencieux dès Android 10. Les règles de blocage restent prioritaires.",
        EntryKind.IDENTIFIED, payload.identified.map { it.phoneNumber to it.label }, editable, edit, remove)
}

@Composable
private fun NumberList(title: String, description: String, kind: EntryKind, entries: List<Pair<String, String>>,
    editable: Boolean, edit: (EntryDraft) -> Unit, remove: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val filtered = entries.filter { it.first.contains(query.filter { c -> c.isDigit() }.ifEmpty { query }) || it.second.contains(query, true) }
    Page {
        item { InfoCard(title, description) { Button(enabled = editable, onClick = { edit(EntryDraft(kind)) }) { Text("Ajouter") } } }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Rechercher") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (filtered.isEmpty()) item { Text("Aucun numéro${if (query.isNotBlank()) " correspondant" else " enregistré"}.") }
        items(filtered, key = { it.first }) { (number, label) ->
            InfoCard("+$number", label.ifBlank { "Sans étiquette" }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = editable, onClick = { edit(EntryDraft(kind, "+$number", label, true)) }) { Text("Modifier") }
                    TextButton(enabled = editable, onClick = { deleting = number }) { Text("Supprimer") }
                }
            }
        }
    }
    deleting?.let { number ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Supprimer ce numéro ?") },
            text = { Text("+$number sera retiré de cette liste.") },
            confirmButton = { TextButton(enabled = editable, onClick = { deleting = null; remove(number) }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } })
    }
}

@Composable
fun RangesScreen(payload: BlocklistPayload, editable: Boolean, edit: (EntryDraft) -> Unit,
    remove: (String) -> Unit, importBegone: () -> Unit, clearBegone: () -> Unit) {
    var deleting by remember { mutableStateOf<BlockRule?>(null) }
    var clear by remember { mutableStateOf(false) }
    Page {
        item { InfoCard("Plages de numéros", "Un # représente un chiffre. Le préfixe et la longueur doivent correspondre exactement.") {
            Button(enabled = editable, onClick = { edit(EntryDraft(EntryKind.RANGE)) }) { Text("Ajouter une plage") }
        } }
        item { InfoCard("Begone-FR", "Liste communautaire embarquée, importée sans accès réseau. L’import est additif et ne remplace pas vos règles.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = editable, onClick = importBegone) { Text("Importer") }
                OutlinedButton(enabled = editable && payload.effectiveRules.any { it.source == BegoneListParser.SOURCE_BEGONE_FR }, onClick = { clear = true }) { Text("Retirer") }
            }
        } }
        if (payload.effectiveRules.isEmpty()) item { Text("Aucune plage enregistrée.") }
        items(payload.effectiveRules, key = { it.key }) { rule ->
            val pattern = "+${rule.prefix}${"#".repeat((rule.length - rule.prefix.length).coerceAtLeast(0))}"
            InfoCard(pattern, "${rule.label ?: "Plage bloquée"} · ${rule.source ?: "Ajout manuel"}") {
                Row {
                    TextButton(enabled = editable, onClick = { edit(EntryDraft(EntryKind.RANGE, pattern, rule.label.orEmpty(), true)) }) { Text("Modifier") }
                    TextButton(enabled = editable, onClick = { deleting = rule }) { Text("Supprimer") }
                }
            }
        }
    }
    if (clear || deleting != null) AlertDialog(onDismissRequest = { clear = false; deleting = null },
        title = { Text(if (clear) "Retirer les plages Begone ?" else "Supprimer cette plage ?") },
        text = { Text("Vos autres règles et la liste blanche seront conservées.") },
        confirmButton = { TextButton(enabled = editable, onClick = {
            if (clear) clearBegone() else deleting?.let { remove(it.key) }
            clear = false; deleting = null
        }) { Text("Supprimer") } }, dismissButton = { TextButton(onClick = { clear = false; deleting = null }) { Text("Annuler") } })
}

@Composable
fun HistoryScreen(entries: List<HistoryEntry>, error: String?, editable: Boolean, clear: () -> Unit, add: (EntryDraft) -> Unit) {
    var actionName by rememberSaveable { mutableStateOf("ALL") }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.FRANCE) }
    Page {
        item { InfoCard("Historique local", "Les ${com.guardcall.domain.CallHistory.MAX_ENTRIES} derniers appels traités par Onraccroche. Ce n’est pas le journal complet du téléphone.") {
            OutlinedButton(enabled = entries.isNotEmpty() || error != null, onClick = clear) { Text("Effacer l’historique") }
        } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = actionName == "ALL", onClick = { actionName = "ALL" }, label = { Text("Tous") })
                FilterChip(selected = actionName == CallAction.BLOCK.name, onClick = { actionName = CallAction.BLOCK.name }, label = { Text("Bloqués") })
                FilterChip(selected = actionName == CallAction.ALLOW.name, onClick = { actionName = CallAction.ALLOW.name }, label = { Text("Autorisés") })
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        val filtered = entries.filter { actionName == "ALL" || it.action.name == actionName }
        if (filtered.isEmpty() && error == null) item { Text("Aucun appel dans cet historique.") }
        items(filtered, key = { it.id }) { entry ->
            val action = when (entry.action) { CallAction.ALLOW -> "Autorisé"; CallAction.BLOCK -> "Bloqué"; CallAction.SILENCE -> "Silencieux" }
            InfoCard(entry.number?.let { "+$it" } ?: "Numéro inconnu", "$action · ${dateFormat.format(Date(entry.timestamp))}\n${entry.reason}") {
                entry.number?.let { number ->
                    Row {
                        TextButton(enabled = editable, onClick = { add(EntryDraft(EntryKind.ALLOWED, "+$number")) }) { Text("Autoriser") }
                        TextButton(enabled = editable, onClick = { add(EntryDraft(EntryKind.BLOCKED, "+$number")) }) { Text("Bloquer") }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(payload: BlocklistPayload, editable: Boolean, roleHeld: Boolean, activate: () -> Unit,
    protection: (Boolean) -> Unit, history: (Boolean) -> Unit) {
    Page {
        item { InfoCard("Protection", "Mettre en pause laisse passer tous les appels sans supprimer vos règles.") {
            SettingSwitch("Activer mes règles", payload.settings.enabled, editable, protection)
            Text(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "Android 8/9 : gestion des listes disponible, filtrage non pris en charge."
                else if (roleHeld) "Onraccroche détient le rôle de filtrage." else "Le rôle de filtrage n’est pas accordé.")
            OutlinedButton(onClick = activate) { Text("Configurer dans Android") }
        } }
        item { InfoCard("Vie privée", "Aucun compte, aucune permission de contacts, aucun envoi réseau. L’historique est exclu des sauvegardes Android.") {
            SettingSwitch("Enregistrer les prochains appels", payload.settings.historyEnabled, editable, history)
            Text("Désactiver l’enregistrement conserve les entrées existantes. Pour les supprimer, ouvrez Historique → Effacer.")
        } }
        item { InfoCard("Onraccroche 2.0", "Liste blanche → numéro bloqué → plage → identification → autorisation. Le silencieux nécessite Android 10. Les numéros masqués ou invalides passent sans blocage.") }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}
