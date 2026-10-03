package com.mi.explorer.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.ApkFileItem
import com.mi.explorer.data.model.ApkTab
import com.mi.explorer.data.model.AppInfoItem
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.components.ChecksumDialog
import com.mi.explorer.ui.theme.MiGreen
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.utils.FileOpener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.apkScreenTab.collectAsStateWithLifecycle()
    val storageApks by viewModel.storageApks.collectAsStateWithLifecycle()
    val isStorageApksLoading by viewModel.isStorageApksLoading.collectAsStateWithLifecycle()

    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isAppsLoading by viewModel.isAppsLoading.collectAsStateWithLifecycle()
    val includeSystem by viewModel.includeSystemApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.appsSearchQuery.collectAsStateWithLifecycle()

    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }
    var checksumTarget by remember { mutableStateOf<FileItem?>(null) }
    var apkToDelete by remember { mutableStateOf<ApkFileItem?>(null) }
    var apkFilter by remember { mutableStateOf("ALL") }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredApks = remember(storageApks, searchQuery, apkFilter) {
        val base = if (searchQuery.isBlank()) storageApks else storageApks.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        when (apkFilter) {
            "NOT_INSTALLED" -> base.filter { !it.isInstalled }
            "INSTALLED" -> base.filter { it.isInstalled }
            else -> base
        }
    }

    Scaffold(
        modifier = modifier.testTag("app_manager_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("APKs & Applications", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (currentTab == ApkTab.APK_FILES) {
                                "${storageApks.size} APK file(s) found"
                            } else {
                                "${filteredApps.size} installed apps"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("apps_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentTab == ApkTab.APK_FILES) {
                        IconButton(onClick = { viewModel.loadStorageApks() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh APKs")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "System",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = includeSystem,
                                onCheckedChange = { viewModel.toggleSystemApps() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MiOrange,
                                    checkedTrackColor = MiOrange.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // MIUI 2-Tab Switcher (APK Files vs Installed Apps)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabPill(
                    text = "APK Files (${storageApks.size})",
                    isSelected = currentTab == ApkTab.APK_FILES,
                    onClick = { viewModel.setApkTab(ApkTab.APK_FILES) },
                    modifier = Modifier.weight(1f)
                )
                TabPill(
                    text = "Installed Apps (${installedApps.size})",
                    isSelected = currentTab == ApkTab.INSTALLED_APPS,
                    onClick = { viewModel.setApkTab(ApkTab.INSTALLED_APPS) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setAppsSearchQuery(it) },
                placeholder = {
                    Text(if (currentTab == ApkTab.APK_FILES) "Search APK installation files..." else "Search installed apps...")
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MiOrange) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setAppsSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Filter Chips (Only for APK Files Tab)
            if (currentTab == ApkTab.APK_FILES) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = apkFilter == "ALL",
                        onClick = { apkFilter = "ALL" },
                        label = { Text("All (${storageApks.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MiGreen.copy(alpha = 0.18f),
                            selectedLabelColor = MiGreen
                        )
                    )
                    FilterChip(
                        selected = apkFilter == "NOT_INSTALLED",
                        onClick = { apkFilter = "NOT_INSTALLED" },
                        label = { Text("Not Installed (${storageApks.count { !it.isInstalled }})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MiGreen.copy(alpha = 0.18f),
                            selectedLabelColor = MiGreen
                        )
                    )
                    FilterChip(
                        selected = apkFilter == "INSTALLED",
                        onClick = { apkFilter = "INSTALLED" },
                        label = { Text("Installed (${storageApks.count { it.isInstalled }})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MiGreen.copy(alpha = 0.18f),
                            selectedLabelColor = MiGreen
                        )
                    )
                }
            }

            // Content Area
            if (currentTab == ApkTab.APK_FILES) {
                // TAB 1: APK FILES ON STORAGE
                if (isStorageApksLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MiOrange)
                    }
                } else if (filteredApks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MiGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = MiGreen,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No APK files found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "APK installation files downloaded from the browser, WhatsApp, or Telegram will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadStorageApks() },
                                colors = ButtonDefaults.buttonColors(containerColor = MiGreen)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Storage")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApks, key = { it.path }) { apk ->
                            StorageApkRow(
                                apk = apk,
                                onInstall = {
                                    viewModel.openApkInstallDialog(apk.file)
                                },
                                onMenuAction = { action ->
                                    when (action) {
                                        "install" -> viewModel.openApkInstallDialog(apk.file)
                                        "share" -> FileOpener.shareFile(context, FileItem(apk.file))
                                        "checksum" -> checksumTarget = FileItem(apk.file)
                                        "delete" -> apkToDelete = apk
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // TAB 2: INSTALLED APPS
                if (isAppsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MiOrange)
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No applications found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            InstalledAppRow(
                                app = app,
                                onClick = { selectedApp = app },
                                onBackup = { viewModel.backupInstalledApp(app) },
                                onLaunch = {
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    } else {
                                        viewModel.showMessage("Cannot launch this app")
                                    }
                                },
                                onSettings = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Checksum Dialog
    checksumTarget?.let { item ->
        ChecksumDialog(
            item = item,
            onDismiss = { checksumTarget = null }
        )
    }

    // Delete APK confirmation
    apkToDelete?.let { apk ->
        AlertDialog(
            onDismissRequest = { apkToDelete = null },
            title = { Text("Delete APK File") },
            text = { Text("Delete \"${apk.name}\"? This APK file will be removed from storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStorageApk(apk)
                        apkToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { apkToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // App Details Bottom Sheet
    selectedApp?.let { app ->
        AppDetailSheet(
            app = app,
            onDismiss = { selectedApp = null },
            onBackup = {
                selectedApp = null
                viewModel.backupInstalledApp(app)
            },
            onLaunch = {
                selectedApp = null
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) context.startActivity(intent)
            },
            onUninstall = {
                selectedApp = null
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
                context.startActivity(intent)
            },
            onSettings = {
                selectedApp = null
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun StorageApkRow(
    apk: ApkFileItem,
    onInstall: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onInstall),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (apk.icon != null) {
                val bitmap = remember(apk.icon) {
                    try { apk.icon.toBitmap(width = 96, height = 96).asImageBitmap() } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = apk.appName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    DefaultApkIcon()
                }
            } else {
                DefaultApkIcon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = apk.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (apk.isInstalled) MiGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (apk.isInstalled) "Installed" else "Not installed",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (apk.isInstalled) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "${apk.name} • ${apk.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${apk.versionName} • ${apk.formattedDate}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 1-Tap Install Button
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MiGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(if (apk.isInstalled) "Update" else "Install", style = MaterialTheme.typography.labelMedium)
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Install / Open") },
                        leadingIcon = { Icon(Icons.Default.Android, contentDescription = null, tint = MiGreen) },
                        onClick = {
                            showMenu = false
                            onMenuAction("install")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share APK") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMenuAction("share")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Calculate Hash") },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MiOrange) },
                        onClick = {
                            showMenu = false
                            onMenuAction("checksum")
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete APK", color = Color(0xFFEF4444)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                        onClick = {
                            showMenu = false
                            onMenuAction("delete")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultApkIcon() {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiGreen.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = MiGreen,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun InstalledAppRow(
    app: AppInfoItem,
    onClick: () -> Unit,
    onBackup: () -> Unit,
    onLaunch: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                val bitmap = remember(app.icon) {
                    try { app.icon.toBitmap(width = 96, height = 96).asImageBitmap() } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                } else {
                    DefaultApkIcon()
                }
            } else {
                DefaultApkIcon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${app.formattedSize} • v${app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Backup APK button
            FilledTonalButton(
                onClick = onBackup,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Backup", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(
    app: AppInfoItem,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
    onSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.icon != null) {
                    val bitmap = remember(app.icon) {
                        try { app.icon.toBitmap(width = 120, height = 120).asImageBitmap() } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Version: ${app.versionName} • Size: ${app.formattedSize}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBackup,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MiGreen)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Backup APK (Save to Storage)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onLaunch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch")
                }

                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("App Info")
                }
            }

            if (!app.isSystemApp) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uninstall App")
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "tabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = textColor
        )
    }
}
