package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.update.ApkInstaller
import com.example.update.AppUpdateStatus
import com.example.update.GitHubReleaseInfo
import java.io.File

@Composable
fun rememberApkInstallAction(
    onError: (String) -> Unit
): (File) -> Unit {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var showPermissionDialog by remember { mutableStateOf<File?>(null) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingFile
        pendingFile = null
        if (file != null && ApkInstaller.canInstallPackages(context)) {
            ApkInstaller.installApk(context, file).onFailure { err ->
                onError(err.localizedMessage ?: "Nie udało się uruchomić instalatora APK.")
            }
        } else if (file != null) {
            onError("Aby zainstalować aktualizację z poziomu aplikacji, zezwól na instalację aplikacji z tego źródła w ustawieniach Androida.")
        }
    }

    showPermissionDialog?.let { apkFile ->
        AlertDialog(
            onDismissRequest = { showPermissionDialog = null },
            title = { Text("Wymagane uprawnienie instalacji") },
            text = {
                Text(
                    "Android wymaga jednorazowego zezwolenia tej aplikacji na instalowanie pakietów APK. " +
                        "Po włączeniu przełącznika „Zezwalaj z tego źródła” wróć do aplikacji — instalacja rozpocznie się automatycznie."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = null
                        pendingFile = apkFile
                        settingsLauncher.launch(ApkInstaller.createManageUnknownAppSourcesIntent(context))
                    }
                ) {
                    Text("Otwórz ustawienia")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = null }) {
                    Text("Anuluj")
                }
            }
        )
    }

    return remember(context) {
        { apkFile: File ->
            if (ApkInstaller.canInstallPackages(context)) {
                ApkInstaller.installApk(context, apkFile).onFailure { err ->
                    onError(err.localizedMessage ?: "Nie udało się uruchomić instalatora APK.")
                }
            } else {
                showPermissionDialog = apkFile
            }
        }
    }
}

@Composable
fun HomeUpdateBanner(
    status: AppUpdateStatus,
    currentVersion: String,
    dismissed: Boolean,
    onDownload: (GitHubReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallError: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (dismissed) return

    val triggerInstall = rememberApkInstallAction(onError = onInstallError)
    var autoInstallAfterDownload by remember { mutableStateOf(false) }

    LaunchedEffect(status, autoInstallAfterDownload) {
        if (autoInstallAfterDownload && status is AppUpdateStatus.ReadyToInstall) {
            autoInstallAfterDownload = false
            triggerInstall(status.apkFile)
        } else if (status is AppUpdateStatus.Error || status is AppUpdateStatus.Idle) {
            autoInstallAfterDownload = false
        }
    }

    when (status) {
        is AppUpdateStatus.UpdateAvailable -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("home_update_banner"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Dostępna aktualizacja ${status.release.tagName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Ukryj powiadomienie o aktualizacji",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Text(
                        text = "Zainstalowana wersja: $currentVersion" +
                            (status.release.apkSizeBytes?.let { " • Rozmiar APK: ${ApkInstaller.formatBytes(it)}" }.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                autoInstallAfterDownload = true
                                onDownload(status.release)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_update_download_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pobierz i zainstaluj")
                        }
                    }
                }
            }
        }

        is AppUpdateStatus.Downloading -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
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
                        Text(
                            text = "Pobieranie aktualizacji ${status.release.tagName}…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                autoInstallAfterDownload = false
                                onCancelDownload()
                            }
                        ) {
                            Text("Anuluj")
                        }
                    }
                    val progress = status.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(progress * 100).toInt()}% (${ApkInstaller.formatBytes(status.downloadedBytes)} / ${ApkInstaller.formatBytes(status.totalBytes)})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Pobrano ${ApkInstaller.formatBytes(status.downloadedBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        is AppUpdateStatus.ReadyToInstall -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gotowe do instalacji (${status.release.tagName})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Kliknij „Zainstaluj”, aby zaktualizować aplikację.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { triggerInstall(status.apkFile) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zainstaluj")
                    }
                }
            }
        }

        else -> Unit
    }
}

@Composable
fun GitHubUpdateCard(
    currentVersion: String,
    githubRepo: String,
    autoCheckUpdates: Boolean,
    updateStatus: AppUpdateStatus,
    onSaveGithubSettings: (repo: String, autoCheck: Boolean) -> Unit,
    onCheckForUpdates: (repoOverride: String) -> Unit,
    onDownloadUpdate: (GitHubReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var repoInput by remember { mutableStateOf(githubRepo) }
    var autoCheckInput by remember { mutableStateOf(autoCheckUpdates) }
    var autoInstallAfterDownload by remember { mutableStateOf(false) }

    LaunchedEffect(githubRepo, autoCheckUpdates) {
        repoInput = githubRepo
        autoCheckInput = autoCheckUpdates
    }

    val triggerInstall = rememberApkInstallAction(onError = onInstallError)

    LaunchedEffect(updateStatus, autoInstallAfterDownload) {
        if (autoInstallAfterDownload && updateStatus is AppUpdateStatus.ReadyToInstall) {
            autoInstallAfterDownload = false
            triggerInstall(updateStatus.apkFile)
        } else if (updateStatus is AppUpdateStatus.Error || updateStatus is AppUpdateStatus.Idle) {
            autoInstallAfterDownload = false
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
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
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Aktualizacja z GitHub",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = currentVersion,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Sprawdzaj nowe wydania (GitHub Releases), pobieraj plik APK i instaluj aktualizację bezpośrednio z aplikacji.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = repoInput,
                onValueChange = { repoInput = it },
                label = { Text("Repozytorium GitHub") },
                placeholder = { Text("dniesobski/ZSME-RoomMonitor") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_repo_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sprawdzaj automatycznie przy starcie",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoCheckInput,
                    onCheckedChange = { checked ->
                        autoCheckInput = checked
                        onSaveGithubSettings(repoInput, checked)
                    },
                    modifier = Modifier.testTag("auto_check_updates_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSaveGithubSettings(repoInput, autoCheckInput) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("save_github_repo_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Zapisz")
                }

                Button(
                    onClick = { onCheckForUpdates(repoInput) },
                    enabled = updateStatus !is AppUpdateStatus.Checking && updateStatus !is AppUpdateStatus.Downloading,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("check_updates_button")
                ) {
                    if (updateStatus is AppUpdateStatus.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sprawdzanie…")
                    } else {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sprawdź aktualizacje")
                    }
                }
            }

            when (updateStatus) {
                is AppUpdateStatus.UpToDate -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Aplikacja jest aktualna (${updateStatus.currentVersion}" +
                                    (updateStatus.latestTag?.let { ", najnowsze wydanie: $it" }.orEmpty()) + ").",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                is AppUpdateStatus.UpdateAvailable -> {
                    val rel = updateStatus.release
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Nowa wersja: ${rel.releaseName} (${rel.tagName})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            rel.apkAssetName?.let { asset ->
                                val sizeStr = ApkInstaller.formatBytes(rel.apkSizeBytes)
                                Text(
                                    text = if (sizeStr.isNotBlank()) "$asset • $sizeStr" else asset,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            if (rel.body.isNotBlank()) {
                                Text(
                                    text = rel.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (rel.apkDownloadUrl != null) {
                                    Button(
                                        onClick = {
                                            autoInstallAfterDownload = true
                                            onDownloadUpdate(rel)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("download_install_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pobierz i zainstaluj")
                                    }
                                }
                                if (rel.htmlUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { ApkInstaller.openUrlInBrowser(context, rel.htmlUrl) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Otwórz na GitHubie", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                is AppUpdateStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Pobieranie ${updateStatus.release.apkAssetName ?: updateStatus.release.tagName}…",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val progress = updateStatus.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% • ${ApkInstaller.formatBytes(updateStatus.downloadedBytes)} / ${ApkInstaller.formatBytes(updateStatus.totalBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Pobrano: ${ApkInstaller.formatBytes(updateStatus.downloadedBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedButton(
                            onClick = {
                                autoInstallAfterDownload = false
                                onCancelDownload()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Anuluj pobieranie")
                        }
                    }
                }

                is AppUpdateStatus.ReadyToInstall -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Pobrano aktualizację ${updateStatus.release.tagName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Button(
                                onClick = { triggerInstall(updateStatus.apkFile) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("install_apk_button")
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Zainstaluj aktualizację teraz")
                            }
                        }
                    }
                }

                is AppUpdateStatus.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = updateStatus.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                AppUpdateStatus.Idle, AppUpdateStatus.Checking -> Unit
            }
        }
    }
}
