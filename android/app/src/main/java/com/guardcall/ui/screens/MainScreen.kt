package com.guardcall.ui.screens

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guardcall.data.BlockedEntry
import com.guardcall.data.BlocklistPayload
import com.guardcall.data.BlocklistStore
import com.guardcall.data.MutationResult
import com.guardcall.ui.theme.GuardCallAccent
import com.guardcall.ui.theme.GuardCallCoral
import com.guardcall.ui.theme.GuardCallPrimary
import com.guardcall.util.PhoneNumberNormalizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(store: BlocklistStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var payload by remember { mutableStateOf(BlocklistPayload.EMPTY) }
    var isScreeningRoleHeld by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var newNumber by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    fun roleIsHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false
    }

    fun refresh() {
        scope.launch {
            isLoading = true
            try {
                payload = store.load()
                isScreeningRoleHeld = roleIsHeld()
            } finally {
                isLoading = false
            }
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
    }

    fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            if (intent != null) {
                roleLauncher.launch(intent)
            }
        } else {
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GuardCall",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Votre téléphone, plus serein",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Actualiser"
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    formError = null
                    newNumber = ""
                    showAddSheet = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Bloquer un numéro", fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(18.dp),
                containerColor = GuardCallPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Une protection simple et locale",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Filtrez les appels indésirables sans partager vos données.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                ProtectionStatusCard(
                    isEnabled = isScreeningRoleHeld,
                    isLoading = isLoading,
                    blockedCount = payload.blocked.size,
                    onActivate = { requestScreeningRole() }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = payload.blocked.size.toString(),
                        label = "Numéros bloqués",
                        icon = Icons.Rounded.Phone,
                        accent = GuardCallPrimary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = payload.identified.size.toString(),
                        label = "Spams identifiés",
                        icon = Icons.Rounded.Warning,
                        accent = GuardCallCoral
                    )
                }
            }

            item { SectionTitle(title = "Protection") }

            item {
                ProtectionActionCard(
                    isEnabled = isScreeningRoleHeld,
                    onActivate = { requestScreeningRole() },
                    onRefresh = { refresh() }
                )
            }

            item {
                SectionTitle(
                    title = "Vos numéros bloqués",
                    count = payload.blocked.size
                )
            }

            if (payload.blocked.isEmpty()) {
                item {
                    EmptyBlockedCard(onAddNumber = {
                        formError = null
                        newNumber = ""
                        showAddSheet = true
                    })
                }
            } else {
                items(payload.blocked, key = { it.phoneNumber }) { entry ->
                    BlockedNumberCard(
                        entry = entry,
                        onDelete = {
                            scope.launch {
                                when (val result = store.removeBlocked(entry.phoneNumber)) {
                                    is MutationResult.StorageFailure -> {
                                        snackbarHostState.showSnackbar(result.message)
                                    }

                                    else -> {
                                        payload = result.payloadOrNull ?: store.load()
                                        snackbarHostState.showSnackbar("Numéro retiré de la liste")
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (payload.identified.isNotEmpty()) {
                item {
                    IdentifiedNumbersCard(payload = payload)
                }
            }

            item {
                PrivacyCard()
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!isSaving) {
                    showAddSheet = false
                    newNumber = ""
                    formError = null
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddNumberSheet(
                value = newNumber,
                error = formError,
                isSaving = isSaving,
                onValueChange = {
                    newNumber = it
                    formError = null
                },
                onDismiss = {
                    showAddSheet = false
                    newNumber = ""
                    formError = null
                },
                onAdd = {
                    scope.launch {
                        isSaving = true
                        when (val result = store.addBlocked(newNumber, label = "Ajout manuel")) {
                            is MutationResult.Success -> {
                                payload = result.payload
                                showAddSheet = false
                                newNumber = ""
                                formError = null
                                snackbarHostState.showSnackbar("Numéro bloqué")
                            }

                            is MutationResult.Duplicate -> {
                                formError = "Ce numéro est déjà présent dans vos numéros bloqués."
                            }

                            is MutationResult.InvalidNumber -> {
                                formError = result.message
                            }

                            is MutationResult.StorageFailure -> {
                                formError = result.message
                            }
                        }
                        isSaving = false
                    }
                }
            )
        }
    }
}

@Composable
private fun ProtectionStatusCard(
    isEnabled: Boolean,
    isLoading: Boolean,
    blockedCount: Int,
    onActivate: () -> Unit
) {
    val containerColor = if (isEnabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isEnabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val accentColor = if (isEnabled) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }
    val protectionSummary = if (blockedCount == 0) {
        "Prête à bloquer les numéros que vous ajoutez"
    } else {
        val suffix = if (blockedCount > 1) "s" else ""
        "$blockedCount numéro${suffix} protégé${suffix}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isEnabled) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = accentColor
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnabled) "Protection active" else "Protection à activer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor
                    )
                    Text(
                        text = if (isEnabled) {
                            "Les appels indésirables sont filtrés automatiquement."
                        } else {
                            "Autorisez GuardCall pour filtrer les appels entrants."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f)
                    )
                }
            }

            if (isEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = accentColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = protectionSummary,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
            } else {
                Button(
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Activer la protection", fontWeight = FontWeight.Bold)
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.End),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (count != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ProtectionActionCard(
    isEnabled: Boolean,
    onActivate: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Filtrage des appels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) {
                        "Service système connecté"
                    } else {
                        "Une autorisation Android est nécessaire"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isEnabled) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualiser le statut",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onActivate) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Ouvrir les réglages",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBlockedCard(onAddNumber: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "Votre liste est vide",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ajoutez un numéro pour empêcher ses appels d'arriver jusqu'à vous.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onAddNumber,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter un numéro")
            }
        }
    }
}

@Composable
private fun BlockedNumberCard(
    entry: BlockedEntry,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PhoneNumberNormalizer.formatForDisplay(entry.phoneNumber),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.label ?: "Numéro bloqué",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Supprimer le numéro",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun IdentifiedNumbersCard(payload: BlocklistPayload) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = GuardCallCoral.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = GuardCallCoral
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Spams identifiés",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Ces appels sont reconnus comme suspects et peuvent être silencés sans être bloqués.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            payload.identified.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = PhoneNumberNormalizer.formatForDisplay(entry.phoneNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = GuardCallCoral
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Vos données restent sur l'appareil",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "GuardCall ne transmet pas votre liste de numéros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddNumberSheet(
    value: String,
    error: String?,
    isSaving: Boolean,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    val validation = PhoneNumberNormalizer.validate(value)
    val normalized = validation.normalizedOrNull
    val showValidationError = value.isNotBlank() && normalized == null
    val displayedError = error ?: if (showValidationError) {
        (validation as? PhoneNumberNormalizer.Result.Invalid)?.message
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Bloquer un numéro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Le numéro sera filtré automatiquement à chaque appel entrant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Numéro de téléphone") },
            placeholder = { Text("06 12 34 56 78") },
            supportingText = {
                if (displayedError != null) {
                    Text(displayedError, color = MaterialTheme.colorScheme.error)
                } else if (normalized != null) {
                    Text(
                        "Enregistré au format ${PhoneNumberNormalizer.formatForDisplay(normalized)}",
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("France par défaut · indicatif international accepté")
                }
            },
            isError = displayedError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Annuler")
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.weight(1f),
                enabled = normalized != null && !isSaving,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Rounded.Phone, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bloquer")
                }
            }
        }
    }
}
