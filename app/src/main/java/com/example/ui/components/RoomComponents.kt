package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BellStatus
import com.example.model.ClockTime
import com.example.model.RoomAvailabilityStatus
import com.example.model.RoomDaySummary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.SchoolDateInfo
import java.util.Locale

@Composable
fun ClockHeroBanner(
    currentTime: ClockTime,
    currentDate: SchoolDateInfo,
    bellStatus: BellStatus,
    freeNowCount: Int,
    freeSoonCount: Int,
    substitutionsCount: Int,
    lastUpdatedText: String?,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0C3E4E), Color(0xFF125A70), Color(0xFF0E6873))
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .drawBehind {
                    val step = 28.dp.toPx()
                    val lineColor = Color.White.copy(alpha = 0.05f)
                    var x = step
                    while (x < size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }
                }
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDate.polishFullDate,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFAEE5F2)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = currentTime.formattedHm(),
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                                modifier = Modifier.testTag("hero_clock_time")
                            )
                            Text(
                                text = ":${String.format(Locale.US, "%02d", currentTime.second)}",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color(0xFF9ED5E3),
                                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Odśwież plan i zastępstwa",
                            tint = Color.White,
                            modifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier
                        )
                    }
                }

                // Current bell / lesson period status pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.13f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bellStatus.headline,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = bellStatus.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD5EFF5)
                            )
                        }
                    }
                }

                // Quick summary metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroStatChip(
                        label = "Wolne teraz",
                        value = freeNowCount.toString(),
                        accent = Color(0xFF34D399),
                        modifier = Modifier.weight(1f)
                    )
                    HeroStatChip(
                        label = "Po następnej",
                        value = freeSoonCount.toString(),
                        accent = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1f)
                    )
                    HeroStatChip(
                        label = "Zastępstwa",
                        value = substitutionsCount.toString(),
                        accent = Color(0xFF93C5FD),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (lastUpdatedText != null) {
                    Text(
                        text = "Ostatnia aktualizacja danych: $lastUpdatedText",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9ED5E3)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.22f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD5EFF5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

@Composable
fun FreeRoomCard(
    room: RoomDaySummary,
    onToggleCleaned: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCleaned = room.isCleanedToday
    val cardColor = if (isCleaned) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isCleaned -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        room.becameFreeEarlier -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCleaned) 0.72f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggleCleaned)
            .testTag("free_room_card_${room.roomName}"),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(if (room.becameFreeEarlier && !isCleaned) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isCleaned) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCleaned) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = room.roomName,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isCleaned) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (isCleaned) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (isCleaned) {
                        "Posprzątana — kliknij, aby cofnąć"
                    } else {
                        "Kliknij, aby oznaczyć jako posprzątaną"
                    },
                    tint = if (isCleaned) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = when {
                            isCleaned -> "Posprzątana"
                            room.freeFromTime != null -> "Wolna od ${room.freeFromTime.formattedHm()}"
                            else -> "Brak lekcji dziś"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCleaned) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (room.becameFreeEarlier && !isCleaned) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Szybciej wolna",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenDetails,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("room_info_${room.roomName}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Plan sali ${room.roomName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FreeSoonRoomCard(
    room: RoomDaySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("soon_room_card_${room.roomName}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.widthIn(min = 48.dp, max = 88.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = room.roomName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val lesson = room.nextLessonEnding
                val infoParts = if (lesson != null) {
                    listOfNotNull(
                        lesson.className.takeIf { it.isNotBlank() },
                        lesson.subject.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                } else ""

                Text(
                    text = if (room.freeAfterPeriod != null) {
                        "Po ${room.freeAfterPeriod}. lekcji" +
                            if (infoParts.isNotBlank()) " • $infoParts" else ""
                    } else {
                        infoParts.ifBlank { "Zajęcia w toku" }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (room.becameFreeLater) {
                    Text(
                        text = "Przeniesiona na późniejszą",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (room.becameFreeEarlier) {
                    Text(
                        text = "Zastępstwo: kończy szybciej",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "od ${room.freeFromTime?.formattedHm() ?: "--:--"}",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailBottomSheet(
    room: RoomDaySummary,
    onDismiss: () -> Unit,
    onToggleCleaned: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sala ${room.roomName}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (room.roomTitle != room.roomName && room.roomTitle != "Sala ${room.roomName}") {
                        Text(
                            text = room.roomTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val badgeColor = when (room.status) {
                    RoomAvailabilityStatus.FREE_FOR_TODAY -> MaterialTheme.colorScheme.secondaryContainer
                    RoomAvailabilityStatus.FREE_AFTER_NEXT -> MaterialTheme.colorScheme.tertiaryContainer
                    RoomAvailabilityStatus.OCCUPIED_LATER -> MaterialTheme.colorScheme.surfaceVariant
                }
                val badgeTextColor = when (room.status) {
                    RoomAvailabilityStatus.FREE_FOR_TODAY -> MaterialTheme.colorScheme.onSecondaryContainer
                    RoomAvailabilityStatus.FREE_AFTER_NEXT -> MaterialTheme.colorScheme.onTertiaryContainer
                    RoomAvailabilityStatus.OCCUPIED_LATER -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val badgeLabel = when (room.status) {
                    RoomAvailabilityStatus.FREE_FOR_TODAY -> "Wolna teraz"
                    RoomAvailabilityStatus.FREE_AFTER_NEXT -> "Wolna od ${room.freeFromTime?.formattedHm() ?: ""}"
                    RoomAvailabilityStatus.OCCUPIED_LATER -> "Zajęta do ${room.freeFromTime?.formattedHm() ?: ""}"
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (room.substitutionNotes.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Wpływ zastępstw na dziś:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        room.substitutionNotes.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (room.status == RoomAvailabilityStatus.FREE_FOR_TODAY) {
                Button(
                    onClick = onToggleCleaned,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("sheet_clean_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (room.isCleanedToday) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        contentColor = if (room.isCleanedToday) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSecondary
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (room.isCleanedToday) {
                            "Cofnij oznaczenie (Posprzątana)"
                        } else {
                            "Oznacz salę jako posprzątaną"
                        }
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Dzisiejszy plan w tej sali",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (room.activeLessons.isEmpty() && room.cancelledLessons.isEmpty()) {
                Text(
                    text = "Brak zaplanowanych lekcji na dzisiejszy dzień.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    room.activeLessons.forEach { lesson ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (lesson.isSubstitutionAdded) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = "${lesson.periodNumber}.",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${lesson.timeRange.formattedRange} • ${lesson.className.ifBlank { "Zajęcia" }}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = listOfNotNull(
                                        lesson.subject.takeIf { it.isNotBlank() },
                                        lesson.teacher.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                lesson.substitutionNote?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    room.cancelledLessons.forEach { cancelled ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "${cancelled.periodNumber}.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${cancelled.timeRange?.formattedRange ?: ""} • ${cancelled.className} (${cancelled.subject})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = cancelled.reason,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
