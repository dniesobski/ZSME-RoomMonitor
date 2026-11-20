package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.RoomDaySummary
import com.example.ui.components.ClockHeroBanner
import com.example.ui.components.FreeRoomCard
import com.example.ui.components.FreeSoonRoomCard
import com.example.ui.components.RoomDetailBottomSheet
import com.example.viewmodel.FLOOR_ID_OTHER
import com.example.viewmodel.MainUiState

@Composable
fun HomeScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFloorLevelSelect: (String?) -> Unit,
    onToggleOnlyWithSubstitutions: () -> Unit,
    onToggleHideCleanedRooms: () -> Unit,
    onToggleRoomCleaned: (String) -> Unit,
    onOpenRoomDetails: (RoomDaySummary?) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOccupiedSection by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "hero") {
                ClockHeroBanner(
                    currentTime = state.currentTime,
                    currentDate = state.currentDate,
                    bellStatus = state.bellStatus,
                    freeNowCount = state.freeNowRooms.size,
                    freeSoonCount = state.freeAfterNextRooms.size,
                    substitutionsCount = state.substitutions.size,
                    lastUpdatedText = state.lastUpdatedText,
                    isRefreshing = state.isRefreshing || state.isLoading,
                    onRefreshClick = onRefresh
                )
            }

            if (state.warningMessage != null || !state.substitutionsMatchesToday) {
                item(key = "warning") {
                    val text = state.warningMessage ?: run {
                        val headerDate = state.substitutionsDateHeader ?: "inny dzień"
                        "Uwaga: strona zastępstw pokazuje datę ($headerDate), która może nie odpowiadać dzisiejszemu dniu."
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            if (state.cacheNoticeMessage != null) {
                item(key = "cache_notice") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("cache_notice_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = state.cacheNoticeMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (state.isLoading && state.totalRoomsCount == 0) {
                item(key = "loading") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Pobieranie planu lekcji i zastępstw…",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = state.timetableUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (state.errorMessage != null && state.totalRoomsCount == 0) {
                item(key = "error") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Nie udało się pobrać planu",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onRefresh,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("retry_fetch_button")
                                ) {
                                    Text("Spróbuj ponownie")
                                }
                                OutlinedButton(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_settings_from_error")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ustawienia")
                                }
                            }
                        }
                    }
                }
            } else {
                // Search & floor filter controls
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("room_search_input"),
                            placeholder = { Text("Szukaj sali (np. 104, 212, Aula)…") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Wyczyść wyszukiwanie")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.selectedFloorLevelId == null,
                                onClick = { onFloorLevelSelect(null) },
                                label = { Text("Wszystkie") },
                                modifier = Modifier.testTag("filter_all")
                            )
                            state.floorLevels.forEach { level ->
                                FilterChip(
                                    selected = state.selectedFloorLevelId == level.id,
                                    onClick = { onFloorLevelSelect(level.id) },
                                    label = { Text(level.name) },
                                    modifier = Modifier.testTag("filter_level_${level.id}")
                                )
                            }
                            FilterChip(
                                selected = state.selectedFloorLevelId == FLOOR_ID_OTHER,
                                onClick = { onFloorLevelSelect(FLOOR_ID_OTHER) },
                                label = { Text("Inne") },
                                modifier = Modifier.testTag("filter_other")
                            )
                            FilterChip(
                                selected = state.onlyWithSubstitutions,
                                onClick = onToggleOnlyWithSubstitutions,
                                label = { Text("Ze zmianami") },
                                modifier = Modifier.testTag("filter_substitutions_only")
                            )
                            FilterChip(
                                selected = state.hideCleanedRooms,
                                onClick = onToggleHideCleanedRooms,
                                label = { Text("Ukryj posprzątane") },
                                modifier = Modifier.testTag("filter_hide_cleaned")
                            )
                        }
                    }
                }

                val pendingFree = state.freeNowRooms.filter { !it.isCleanedToday }
                val cleanedFree = state.freeNowRooms.filter { it.isCleanedToday }

                // SECTION 1: Free for the rest of today
                item(key = "header_free_now") {
                    SectionHeader(
                        icon = Icons.Default.CheckCircle,
                        title = "Sale wolne na dziś",
                        subtitle = "Dotknij salę, aby oznaczyć jako posprzątaną (przesunie się na dół)",
                        count = pendingFree.size,
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }

                if (state.freeNowRooms.isEmpty()) {
                    item(key = "empty_free_now") {
                        EmptySectionNotice(
                            text = "Brak sal spełniających wybrane filtry, które byłyby już wolne do końca dnia."
                        )
                    }
                } else {
                    if (pendingFree.isEmpty()) {
                        item(key = "all_cleaned_notice") {
                            EmptySectionNotice(
                                text = "Wszystkie widoczne wolne sale zostały już oznaczone jako posprzątane."
                            )
                        }
                    } else {
                        val chunkedPending = pendingFree.chunked(columns)
                        items(
                            items = chunkedPending,
                            key = { row -> "free_pending_" + row.joinToString("_") { it.roomName } }
                        ) { rowRooms ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowRooms.forEach { room ->
                                    FreeRoomCard(
                                        room = room,
                                        onToggleCleaned = { onToggleRoomCleaned(room.roomName) },
                                        onOpenDetails = { onOpenRoomDetails(room) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowRooms.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (cleanedFree.isNotEmpty()) {
                        item(key = "cleaned_divider") {
                            Text(
                                text = "Posprzątane dziś (${cleanedFree.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        val chunkedCleaned = cleanedFree.chunked(columns)
                        items(
                            items = chunkedCleaned,
                            key = { row -> "free_cleaned_" + row.joinToString("_") { it.roomName } }
                        ) { rowRooms ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowRooms.forEach { room ->
                                    FreeRoomCard(
                                        room = room,
                                        onToggleCleaned = { onToggleRoomCleaned(room.roomName) },
                                        onOpenDetails = { onOpenRoomDetails(room) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowRooms.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Free after next lesson
                item(key = "header_free_soon") {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionHeader(
                        icon = Icons.Default.Schedule,
                        title = "Wolne po następnej lekcji",
                        subtitle = "Ostatnia lekcja kończy się po bieżącym lub następnym bloku",
                        count = state.freeAfterNextRooms.size,
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (state.freeAfterNextRooms.isEmpty()) {
                    item(key = "empty_free_soon") {
                        EmptySectionNotice(
                            text = "Brak kolejnych sal zwalniających się bezpośrednio po następnej lekcji."
                        )
                    }
                } else {
                    items(
                        items = state.freeAfterNextRooms,
                        key = { "soon_${it.roomName}" }
                    ) { room ->
                        FreeSoonRoomCard(
                            room = room,
                            onClick = { onOpenRoomDetails(room) }
                        )
                    }
                }

                // SECTION 3: Collapsible remaining occupied rooms
                if (state.occupiedLaterRooms.isNotEmpty()) {
                    item(key = "header_occupied") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showOccupiedSection = !showOccupiedSection }
                                .testTag("toggle_occupied_section"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MeetingRoom,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Pozostałe sale zajęte później (${state.occupiedLaterRooms.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Icon(
                                    imageVector = if (showOccupiedSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showOccupiedSection) "Zwiń" else "Rozwiń"
                                )
                            }
                        }
                    }

                    item(key = "occupied_list") {
                        AnimatedVisibility(visible = showOccupiedSection) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.occupiedLaterRooms.forEach { room ->
                                    FreeSoonRoomCard(
                                        room = room,
                                        onClick = { onOpenRoomDetails(room) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedRoom?.let { selected ->
        RoomDetailBottomSheet(
            room = selected,
            onDismiss = { onOpenRoomDetails(null) },
            onToggleCleaned = { onToggleRoomCleaned(selected.roomName) }
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    count: Int,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accentColor.copy(alpha = 0.14f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptySectionNotice(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}
