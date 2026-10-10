package com.guardcall.ui.screens

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardcall.R
import com.guardcall.data.BlocklistPayload
import com.guardcall.data.BlocklistStore
import com.guardcall.util.PhoneNumberNormalizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(store: BlocklistStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var payload by remember { mutableStateOf(BlocklistPayload.EMPTY) }
    var isScreeningRoleHeld by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newNumber by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            payload = store.load()
            isScreeningRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false
            } else {
                // Avant Android 10, la déclaration du service suffit s'il est activé.
                true
            }
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("GuardCall", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                formError = null
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "GuardCall",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Protection anti-spam pour vos appels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Statut de la protection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val (text, color) = if (isScreeningRoleHeld) {
                                "Protection active ✓" to MaterialTheme.colorScheme.primary
                            } else {
                                "Autorisation requise" to MaterialTheme.colorScheme.error
                            }
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = color)
                            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!isScreeningRoleHeld) {
                            Text(
                                "Android exige que GuardCall soit autorisée comme service de filtrage. " +
                                    "Touchez « Activer » et sélectionnez GuardCall.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { refresh() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Actualiser")
                            }
                            if (!isScreeningRoleHeld && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                OutlinedButton(onClick = {
                                    val roleManager = context.getSystemService(RoleManager::class.java)
                                    val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                    if (intent != null) roleLauncher.launch(intent)
                                }) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Activer")
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

            item {
                Text(
                    "Numéros bloqués (${payload.blocked.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (payload.blocked.isEmpty()) {
                    Text(
                        "Aucun numéro bloqué. Ajoutez-en un pour commencer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(payload.blocked, key = { it.phoneNumber }) { entry ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                PhoneNumberNormalizer.formatForDisplay(entry.phoneNumber),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            entry.label?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val result = store.removeBlocked(entry.phoneNumber)
                                result.errorMessageOrNull?.let {
                                    snackbarHostState.showSnackbar("Suppression impossible : $it")
                                }
                                payload = store.load()
                            }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Numéros identifiés comme spam (${payload.identified.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        payload.identified.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    PhoneNumberNormalizer.formatForDisplay(entry.phoneNumber),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    entry.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            "Les numéros « identifiés » sonnent en silencieux au lieu d'être bloqués, " +
                                "pour que vous gardiez la trace de l'appel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        val validation = PhoneNumberNormalizer.validate(newNumber)

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Ajouter un numéro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = {
                            newNumber = it
                            formError = null
                        },
                        label = { Text("Ex. 01 40 85 55 12") },
                        singleLine = true
                    )
                    validation.normalizedOrNull?.let {
                        Text(
                            "Sera enregistré : ${PhoneNumberNormalizer.formatForDisplay(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    formError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Indicatif pays obligatoire : un numéro national (01…, 06…) est converti " +
                            "avec l'indicatif +${PhoneNumberNormalizer.DEFAULT_COUNTRY_CALLING_CODE}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validation.normalizedOrNull != null,
                    onClick = {
                        scope.launch {
                            val result = store.addBlocked(newNumber, label = "Manuel")
                            result.errorMessageOrNull?.let {
                                formError = it
                                snackbarHostState.showSnackbar(it)
                            }
                            payload = store.load()
                            if (result.errorMessageOrNull == null) {
                                newNumber = ""
                                showAddDialog = false
                            }
                        }
                    }
                ) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Annuler") } }
        )
    }
}
