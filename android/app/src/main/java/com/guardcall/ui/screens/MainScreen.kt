package com.guardcall.ui.screens

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardcall.data.BlocklistPayload
import com.guardcall.data.BlocklistStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(store: BlocklistStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var payload by remember { mutableStateOf(BlocklistPayload.DEFAULT) }
    var isScreeningRoleHeld by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newNumber by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            payload = store.load()
            // Vérif rôle screening (Android 10+)
            isScreeningRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false
            } else {
                // Avant Q, le service déclaré suffit si activé dans les paramètres
                true
            }
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GuardCall", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("GuardCall", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Protection anti-spam pour vos appels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Statut
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Statut de la protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (text, color) = if (isScreeningRoleHeld) "Protection active ✓" to MaterialTheme.colorScheme.primary else "Autorisation requise" to MaterialTheme.colorScheme.error
                            Icon(Icons.Default.Security, contentDescription = null, tint = color)
                            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!isScreeningRoleHeld) {
                            Text(
                                "Android exige que GuardCall soit autorisée comme service de filtrage. Touchez « Activer » et sélectionnez GuardCall.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Actualiser")
                            }
                            if (!isScreeningRoleHeld && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                OutlinedButton(onClick = {
                                    val rm = context.getSystemService(RoleManager::class.java)
                                    val intent = rm?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                    if (intent != null) roleLauncher.launch(intent)
                                }) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Activer")
                                }
                            } else if (!isScreeningRoleHeld) {
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                                }) { Text("Ouvrir paramètres") }
                            }
                        }
                    }
                }
            }

            // Liste bloqués
            item {
                Text("Numéros bloqués (${payload.blocked.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (payload.blocked.isEmpty()) {
                    Text("Aucun numéro bloqué. Ajoutez-en un pour tester.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(payload.blocked, key = { it.phoneNumber }) { entry ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(entry.phoneNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            entry.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        IconButton(onClick = { scope.launch { store.removeBlocked(entry.phoneNumber); payload = store.load() } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Identification
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Numéros identifiés comme spam (${payload.identified.size})", style = MaterialTheme.typography.titleSmall)
                        payload.identified.forEach { e ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.phoneNumber, style = MaterialTheme.typography.bodySmall)
                                Text(e.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            "Astuce : sur Android, les numéros « identifiés » font vibrer en silencieux au lieu d’être bloqués, pour que vous gardiez la trace.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Ajouter un numéro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saisissez un numéro complet (chiffres uniquement).", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        label = { Text("Ex. 014085551234") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newNumber.filter { it.isDigit() }.length >= 4,
                    onClick = {
                        scope.launch {
                            store.addBlocked(newNumber)
                            payload = store.load()
                            newNumber = ""
                            showAddDialog = false
                        }
                    }
                ) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Annuler") } }
        )
    }
}
