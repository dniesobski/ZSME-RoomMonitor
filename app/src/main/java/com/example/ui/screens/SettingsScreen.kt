package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.FloorLevelConfig
import com.example.model.FloorLevelMatcher
import com.example.ui.components.GitHubUpdateCard
import com.example.update.GitHubReleaseInfo
import com.example.util.TimeHelper
import com.example.viewmodel.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onSaveSettings: (timetableUrl: String, substitutionsUrl: String, autoRefreshMinutes: Int) -> Unit,
    onResetDefaultUrls: () -> Unit,
    onClearCleanedRooms: () -> Unit,
    onClearCache: () -> Unit,
    onAddFloorLevel: (name: String, initialRules: String) -> Unit,
    onRenameFloorLevel: (levelId: String, newName: String) -> Unit,
    onDeleteFloorLevel: (levelId: String) -> Unit,
    onAddRoomRuleToLevel: (levelId: String, rule: String) -> Unit,
    onRemoveRoomRuleFromLevel: (levelId: String, rule: String) -> Unit,
    onResetDefaultFloorLevels: () -> Unit,
    onSaveGithubSettings: (repo: String, autoCheck: Boolean) -> Unit,
    onCheckForUpdates: (repoOverride: String) -> Unit,
    onDownloadUpdate: (GitHubReleaseInfo) -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onUpdateInstallError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var timetableUrlInput by remember { mutableStateOf(state.timetableUrl) }
    var substitutionsUrlInput by remember { mutableStateOf(state.substitutionsUrl) }
    var selectedRefreshMinutes by remember { mutableIntStateOf(state.autoRefreshMinutes) }

    var showAddLevelDialog by remember { mutableStateOf(false) }
    var levelToRename by remember { mutableStateOf<FloorLevelConfig?>(null) }
    var levelIdForRooms by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.timetableUrl, state.substitutionsUrl, state.autoRefreshMinutes) {
        timetableUrlInput = state.timetableUrl
        substitutionsUrlInput = state.substitutionsUrl
        selectedRefreshMinutes = state.autoRefreshMinutes
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = "Ustawienia",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "Konfiguracja źródeł danych, poziomów oraz aktualizacji",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "github_update_card") {
            GitHubUpdateCard(
                currentVersion = state.currentAppVersion,
                githubRepo = state.githubRepo,
                autoCheckUpdates = state.autoCheckUpdates,
                updateStatus = state.updateStatus,
                onSaveGithubSettings = onSaveGithubSettings,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onCancelDownload = onCancelUpdateDownload,
                onInstallError = onUpdateInstallError
            )
        }

        item(key = "url_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Adresy źródeł danych",
                        style = MaterialTheme.typography.titleLarge
                    )

                    OutlinedTextField(
                        value = timetableUrlInput,
                        onValueChange = { timetableUrlInput = it },
                        label = { Text("Link do planu lekcji (Optivum)") },
                        placeholder = { Text("https://plan.zsme.pl") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timetable_url_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        value = substitutionsUrlInput,
                        onValueChange = { substitutionsUrlInput = it },
                        label = { Text("Link do zastępstw") },
                        placeholder = { Text("https://sekretariat.zsme.pl/tv.php") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("substitutions_url_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Text(
                        text = "Automatyczne odświeżanie w tle",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "Wył.",
                            10 to "10 min",
                            60 to "1 godz",
                            300 to "5 godz"
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = selectedRefreshMinutes == mins,
                                onClick = { selectedRefreshMinutes = mins },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveSettings(
                                    timetableUrlInput,
                                    substitutionsUrlInput,
                                    selectedRefreshMinutes
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_settings_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Zapisz i odśwież")
                        }

                        OutlinedButton(
                            onClick = onResetDefaultUrls,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reset_defaults_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Domyślne")
                        }
                    }

                    state.settingsFeedback?.let { feedback ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floor levels and room assignment card
        item(key = "levels_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Poziomy i sale",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { showAddLevelDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_level_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dodaj poziom")
                        }
                    }

                    Text(
                        text = "Przypisz numery sal (np. 14), zakresy (np. 10-29) lub prefiksy (np. W*) do wybranych poziomów.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.floorLevels.isEmpty()) {
                        Text(
                            text = "Brak skonfigurowanych poziomów. Dodaj nowy poziom lub przywróć domyślne.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.floorLevels.forEach { level ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = level.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row {
                                            IconButton(
                                                onClick = { levelToRename = level },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("rename_level_${level.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Zmień nazwę poziomu ${level.name}",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteFloorLevel(level.id) },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("delete_level_${level.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Usuń poziom ${level.name}",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (level.rules.isEmpty()) {
                                        Text(
                                            text = "Brak przypisanych sal",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            level.rules.forEach { rule ->
                                                InputChip(
                                                    selected = true,
                                                    onClick = { onRemoveRoomRuleFromLevel(level.id, rule) },
                                                    label = { Text(rule) },
                                                    trailingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Usuń $rule z poziomu ${level.name}",
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    TextButton(
                                        onClick = { levelIdForRooms = level.id },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("assign_rooms_${level.id}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Dodaj / przypisz sale")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = onResetDefaultFloorLevels,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("reset_levels_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Przywróć domyślne poziomy")
                    }
                }
            }
        }

        item(key = "cleaning_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Narzędzia sprzątania",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Oznaczenia posprzątanych sal resetują się automatycznie każdego nowego dnia. Możesz też wyczyścić je ręcznie.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onClearCleanedRooms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("clear_cleaned_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wyczyść dzisiejsze oznaczenia posprzątanych sal")
                    }
                }
            }
        }

        item(key = "diagnostics_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Pamięć podręczna i status",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Wykrytych sal w planie: ${state.totalRoomsCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Zastępstwa na dziś: ${state.substitutions.size} (dopasowanych do sal: ${state.appliedSubstitutionsCount})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.futureSubstitutionsDays.isNotEmpty()) {
                        val futureSummary = state.futureSubstitutionsDays.joinToString(", ") { day ->
                            "${TimeHelper.describeCachedDateLabel(day.isoDate, state.currentDate)} (${day.entries.size})"
                        }
                        Text(
                            text = "Zapamiętane na kolejne dni: $futureSummary",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Zapamiętane na kolejne dni: brak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Ostatnia aktualizacja: ${state.lastUpdatedText ?: "brak"} • Starsze dni czyszczą się automatycznie po północy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("clear_cache_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wyczyść pamięć podręczną i pobierz ponownie")
                    }
                }
            }
        }
    }

    if (showAddLevelDialog) {
        AddLevelDialog(
            onDismiss = { showAddLevelDialog = false },
            onConfirm = { name, rules ->
                onAddFloorLevel(name, rules)
                showAddLevelDialog = false
            }
        )
    }

    levelToRename?.let { level ->
        RenameLevelDialog(
            initialName = level.name,
            onDismiss = { levelToRename = null },
            onConfirm = { newName ->
                onRenameFloorLevel(level.id, newName)
                levelToRename = null
            }
        )
    }

    val activeLevelForRooms = levelIdForRooms?.let { id ->
        state.floorLevels.firstOrNull { it.id == id }
    }
    if (activeLevelForRooms != null) {
        AssignRoomsDialog(
            level = activeLevelForRooms,
            knownRooms = state.allKnownRoomNames,
            onAddRule = { rule -> onAddRoomRuleToLevel(activeLevelForRooms.id, rule) },
            onRemoveRule = { rule -> onRemoveRoomRuleFromLevel(activeLevelForRooms.id, rule) },
            onDismiss = { levelIdForRooms = null }
        )
    }
}

@Composable
private fun AddLevelDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, rules: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy poziom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa poziomu (np. 4. piętro)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    label = { Text("Sale lub zakresy (opcjonalnie)") },
                    placeholder = { Text("np. 70-79, Aula") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, rules) },
                enabled = name.isNotBlank()
            ) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

@Composable
private fun RenameLevelDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zmień nazwę poziomu") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa poziomu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignRoomsDialog(
    level: FloorLevelConfig,
    knownRooms: List<String>,
    onAddRule: (String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przypisz sale: ${level.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        label = { Text("Nr sali, zakres lub W*") },
                        placeholder = { Text("np. 14 lub 10-29") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (customInput.isNotBlank()) {
                                onAddRule(customInput)
                                customInput = ""
                            }
                        },
                        enabled = customInput.isNotBlank(),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Dodaj")
                    }
                }

                if (knownRooms.isNotEmpty()) {
                    Text(
                        text = "Lub wybierz bezpośrednio z pobranych sal:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        knownRooms.forEach { roomName ->
                            val isDirectRule = level.rules.any { it.equals(roomName, ignoreCase = true) }
                            val isMatchedByRule = FloorLevelMatcher.matchesAnyRule(roomName, level.rules)
                            FilterChip(
                                selected = isMatchedByRule,
                                onClick = {
                                    if (isDirectRule) {
                                        onRemoveRule(roomName)
                                    } else if (!isMatchedByRule) {
                                        onAddRule(roomName)
                                    }
                                },
                                label = { Text(roomName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Gotowe")
            }
        }
    )
}
