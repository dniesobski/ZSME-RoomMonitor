package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.SubstitutionEntry
import com.example.model.SubstitutionKind
import com.example.util.TimeHelper
import com.example.viewmodel.MainUiState

@Composable
fun SubstitutionsScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedKind by remember { mutableStateOf<SubstitutionKind?>(null) }
    var selectedFutureIso by remember { mutableStateOf<String?>(null) }

    val selectedFutureDay = remember(state.futureSubstitutionsDays, selectedFutureIso) {
        selectedFutureIso?.let { iso ->
            state.futureSubstitutionsDays.firstOrNull { it.isoDate == iso }
        }
    }

    val activeList = selectedFutureDay?.entries ?: state.substitutions

    val filtered = remember(activeList, selectedKind) {
        if (selectedKind == null) activeList
        else activeList.filter { it.kind == selectedKind }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Zastępstwa",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = buildString {
                            append("Źródło: sekretariat.zsme.pl/tv.php")
                            val dateLabel = selectedFutureDay?.displayDateText
                                ?: state.substitutionsDateHeader
                            dateLabel?.let { append(" • $it") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("subs_refresh_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Odśwież zastępstwa")
                }
            }
        }

        if (state.futureSubstitutionsDays.isNotEmpty() || state.isUsingCachedTodaySubstitutions) {
            item(key = "day_selector") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.futureSubstitutionsDays.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFutureDay == null,
                                onClick = { selectedFutureIso = null },
                                label = {
                                    Text(
                                        "Dziś (${TimeHelper.formatIsoToDotDate(state.currentDate.isoKey)}) • ${state.substitutions.size}"
                                    )
                                },
                                modifier = Modifier.testTag("subs_day_today")
                            )
                            state.futureSubstitutionsDays.forEach { day ->
                                FilterChip(
                                    selected = selectedFutureDay?.isoDate == day.isoDate,
                                    onClick = { selectedFutureIso = day.isoDate },
                                    label = {
                                        Text(
                                            "${TimeHelper.describeCachedDateLabel(day.isoDate, state.currentDate)} • ${day.entries.size}"
                                        )
                                    },
                                    modifier = Modifier.testTag("subs_day_${day.isoDate}")
                                )
                            }
                        }
                    }

                    if (selectedFutureDay != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Podgląd zapamiętanych zastępstw na kolejny dzień — zostaną automatycznie użyte jutro.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (state.isUsingCachedTodaySubstitutions) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Wyświetlono zapamiętane zastępstwa na dziś (strona w internecie pokazuje już kolejny dzień lub jest offline).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "filter_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedKind == null,
                    onClick = { selectedKind = null },
                    label = { Text("Wszystkie (${activeList.size})") }
                )
                FilterChip(
                    selected = selectedKind == SubstitutionKind.CANCELLED_DISMISSED,
                    onClick = {
                        selectedKind = if (selectedKind == SubstitutionKind.CANCELLED_DISMISSED) null
                        else SubstitutionKind.CANCELLED_DISMISSED
                    },
                    label = { Text("Zwolnione / Odwołane") }
                )
                FilterChip(
                    selected = selectedKind == SubstitutionKind.MOVED_PERIOD,
                    onClick = {
                        selectedKind = if (selectedKind == SubstitutionKind.MOVED_PERIOD) null
                        else SubstitutionKind.MOVED_PERIOD
                    },
                    label = { Text("Przeniesione") }
                )
                FilterChip(
                    selected = selectedKind == SubstitutionKind.ROOM_CHANGED,
                    onClick = {
                        selectedKind = if (selectedKind == SubstitutionKind.ROOM_CHANGED) null
                        else SubstitutionKind.ROOM_CHANGED
                    },
                    label = { Text("Zmiana sali") }
                )
            }
        }

        if (filtered.isEmpty()) {
            item(key = "empty") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Brak wpisów zastępstw",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Na wybranej stronie zastępstw nie znaleziono zmian pasujących do filtra lub wszystkie lekcje odbywają się zgodnie z planem.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(items = filtered, key = { it.id }) { sub ->
                SubstitutionCard(sub = sub)
            }
        }
    }
}

@Composable
private fun SubstitutionCard(sub: SubstitutionEntry) {
    val badgeBg = when (sub.kind) {
        SubstitutionKind.CANCELLED_DISMISSED -> MaterialTheme.colorScheme.secondaryContainer
        SubstitutionKind.MOVED_PERIOD -> MaterialTheme.colorScheme.tertiaryContainer
        SubstitutionKind.ROOM_CHANGED -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when (sub.kind) {
        SubstitutionKind.CANCELLED_DISMISSED -> MaterialTheme.colorScheme.onSecondaryContainer
        SubstitutionKind.MOVED_PERIOD -> MaterialTheme.colorScheme.onTertiaryContainer
        SubstitutionKind.ROOM_CHANGED -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = sub.kind.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeFg
                        )
                    }
                }

                sub.periodNumber?.let { p ->
                    Text(
                        text = if (sub.targetPeriodNumber != null) {
                            "$p. → ${sub.targetPeriodNumber}. lekcja"
                        } else {
                            "$p. lekcja"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val headlineParts = listOfNotNull(
                sub.className?.let { "Klasa $it" },
                sub.originalRoom?.let { "Sala $it" },
                sub.newRoom?.takeIf { it != sub.originalRoom }?.let { "Nowa sala $it" },
                sub.teacherAbsent
            )
            if (headlineParts.isNotEmpty()) {
                Text(
                    text = headlineParts.joinToString(" • "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = sub.rawText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            sub.impactSummary?.let { impact ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = impact,
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeFg
                )
            }
        }
    }
}
