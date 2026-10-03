package com.mi.explorer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.mi.explorer.data.model.*
import com.mi.explorer.data.repository.*
import com.mi.explorer.ui.components.MiTab
import com.mi.explorer.utils.FileOpener
import com.mi.explorer.utils.ArchiveHelper
import com.mi.explorer.utils.XapkInstaller
import com.mi.explorer.utils.XapkInfo
import com.mi.explorer.utils.BiometricHelper
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

enum class Screen {
    MAIN,
    CLEANER,
    FTP_SERVER,
    CATEGORY_VIEW,
    TEXT_EDITOR,
    IMAGE_VIEWER,
    APP_MANAGER,
    VAULT,
    DUPLICATES,
    STORAGE_ANALYZER,
    ZIP_VIEWER,
    TRASH,
    PDF_VIEWER,
    VIDEO_PLAYER,
    NETWORK_DRIVES,
    FAST_SHARE
}

data class PdfViewerState(
    val file: File? = null,
    val title: String = ""
)

data class VideoPlayerState(
    val file: File? = null,
    val title: String = "",
    val playlist: List<FileItem> = emptyList(),
    val currentIndex: Int = 0
)

data class AudioPlayerState(
    val currentFile: File? = null,
    val title: String = "",
    val artist: String = "Unknown Artist",
    val durationMs: Int = 0,
    val currentPositionMs: Int = 0,
    val isPlaying: Boolean = false,
    val isVisible: Boolean = false,
    val isExpanded: Boolean = false,
    val playlist: List<FileItem> = emptyList(),
    val currentIndex: Int = 0,
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false
)

data class ZipViewerState(
    val archiveInfo: ZipArchiveInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class StorageTabState(
    val currentDir: File,
    val backStack: List<File> = emptyList(),
    val forwardStack: List<File> = emptyList(),
    val items: List<FileItem> = emptyList(),
    val selectedItems: Set<FileItem> = emptySet(),
    val viewMode: ViewMode = ViewMode.LIST,
    val sortType: SortType = SortType.NAME_ASC,
    val searchQuery: String = "",
    val showHidden: Boolean = false,
    val filterOnlyBigFiles: Boolean = false,
    val isLoading: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedItems.isNotEmpty()

    val displayItems: List<FileItem>
        get() = if (filterOnlyBigFiles) {
            items.filter { it.effectiveSize >= 10L * 1024 * 1024 }
        } else {
            items
        }

    val bigItemsCount: Int
        get() = items.count { it.effectiveSize >= 10L * 1024 * 1024 }
}

data class TextEditorState(
    val file: File? = null,
    val sourceUri: android.net.Uri? = null,
    val title: String = "",
    val content: String = "",
    val originalContent: String = "",
    val wordWrap: Boolean = false,
    val isSaving: Boolean = false,
    val isHtmlMode: Boolean = false,
    val showHtmlPreview: Boolean = false
) {
    val isModified: Boolean get() = content != originalContent
    val lineCount: Int get() = if (content.isEmpty()) 1 else content.lines().size
    val charCount: Int get() = content.length
}

data class ImageViewerState(
    val currentFile: File? = null,
    val imageList: List<FileItem> = emptyList(),
    val currentIndex: Int = 0
)

data class CategoryViewState(
    val category: FileCategory = FileCategory.IMAGE,
    val title: String = "",
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false
)

data class FtpServerState(
    val isRunning: Boolean = false,
    val port: Int = 2121,
    val ipAddress: String = "192.168.1.108"
) {
    val url: String get() = "ftp://$ipAddress:$port"
}

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    val fileRepository = FileRepository(application.applicationContext)
    val appsRepository = AppsRepository(application.applicationContext)

    // Current Screen
    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val screenBackStack = mutableListOf<Screen>()

    // Current Main Tab (Recent vs Storage)
    private val _selectedTab = MutableStateFlow(MiTab.STORAGE)
    val selectedTab: StateFlow<MiTab> = _selectedTab.asStateFlow()

    // Recent Files State
    private val _recentFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val recentFiles: StateFlow<List<FileItem>> = _recentFiles.asStateFlow()
    val isRecentLoading = MutableStateFlow(false)

    // Storage Tab State
    private val initialDir = fileRepository.rootStorageDirectory
    private val _storageState = MutableStateFlow(StorageTabState(currentDir = initialDir))
    val storageState: StateFlow<StorageTabState> = _storageState.asStateFlow()

    // Storage capacity overview
    private val _storageSpace = MutableStateFlow(
        StorageSpace(totalBytes = 64L * 1024 * 1024 * 1024, freeBytes = 38L * 1024 * 1024 * 1024, usedBytes = 26L * 1024 * 1024 * 1024)
    )
    val storageSpace: StateFlow<StorageSpace> = _storageSpace.asStateFlow()

    // Clipboard
    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    // Cleaner State
    private val _cleanScan = MutableStateFlow<CleanScanResult?>(null)
    val cleanScan: StateFlow<CleanScanResult?> = _cleanScan.asStateFlow()
    val isCleaning = MutableStateFlow(false)
    val isCleanScanning = MutableStateFlow(false)
    val cleanedBytes = MutableStateFlow<Long?>(null)

    // FTP Server State
    private val _ftpServerState = MutableStateFlow(FtpServerState())
    val ftpServerState: StateFlow<FtpServerState> = _ftpServerState.asStateFlow()

    // Text Editor State
    private val _textEditorState = MutableStateFlow(TextEditorState())
    val textEditorState: StateFlow<TextEditorState> = _textEditorState.asStateFlow()

    // Image Viewer State
    private val _imageViewerState = MutableStateFlow(ImageViewerState())
    val imageViewerState: StateFlow<ImageViewerState> = _imageViewerState.asStateFlow()

    // Category View State
    private val _categoryViewState = MutableStateFlow(CategoryViewState())
    val categoryViewState: StateFlow<CategoryViewState> = _categoryViewState.asStateFlow()

    // App Manager State
    private val _installedApps = MutableStateFlow<List<AppInfoItem>>(emptyList())
    val installedApps: StateFlow<List<AppInfoItem>> = _installedApps.asStateFlow()
    val isAppsLoading = MutableStateFlow(false)
    val includeSystemApps = MutableStateFlow(false)
    val appsSearchQuery = MutableStateFlow("")

    private val _storageApks = MutableStateFlow<List<ApkFileItem>>(emptyList())
    val storageApks: StateFlow<List<ApkFileItem>> = _storageApks.asStateFlow()
    val isStorageApksLoading = MutableStateFlow(false)
    val apkScreenTab = MutableStateFlow(ApkTab.APK_FILES)
    private val _apkInstallTarget = MutableStateFlow<ApkFileItem?>(null)
    val apkInstallTarget: StateFlow<ApkFileItem?> = _apkInstallTarget.asStateFlow()

    // Vault Repository & State
    val vaultRepository = VaultRepository(application)
    val isVaultPinSet = MutableStateFlow(vaultRepository.isPinSet())
    val isVaultUnlocked = MutableStateFlow(false)
    private val _vaultFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val vaultFiles: StateFlow<List<FileItem>> = _vaultFiles.asStateFlow()
    val isVaultLoading = MutableStateFlow(false)

    // Duplicate Repository & State
    val duplicateRepository = DuplicateRepository(application)
    private val _duplicateScanResult = MutableStateFlow<DuplicateScanResult?>(null)
    val duplicateScanResult: StateFlow<DuplicateScanResult?> = _duplicateScanResult.asStateFlow()
    val isDuplicateScanning = MutableStateFlow(false)
    val selectedDuplicateFiles = MutableStateFlow<Set<FileItem>>(emptySet())

    // Storage Analyzer Repository & State
    val storageAnalyzerRepository = StorageAnalyzerRepository(application)
    private val _storageAnalysisResult = MutableStateFlow<StorageAnalysisResult?>(null)
    val storageAnalysisResult: StateFlow<StorageAnalysisResult?> = _storageAnalysisResult.asStateFlow()
    val isStorageAnalyzing = MutableStateFlow(false)

    // Zip Viewer & Compressor State
    val zipRepository = ZipRepository(application)
    private val _zipViewerState = MutableStateFlow(ZipViewerState())
    val zipViewerState: StateFlow<ZipViewerState> = _zipViewerState.asStateFlow()
    val isZipExtracting = MutableStateFlow(false)

    // AMOLED Pitch Black Mode State
    val isAmoledMode = MutableStateFlow(false)

    // Recycle Bin (Trash)
    val trashRepository = TrashRepository(application)
    private val _trashItems = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItems: StateFlow<List<TrashItem>> = _trashItems.asStateFlow()
    val isTrashLoading = MutableStateFlow(false)

    // Favorites / Pinned Folders
    val favoritesRepository = FavoritesRepository(application)
    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites.asStateFlow()

    // PDF Viewer State
    private val _pdfViewerState = MutableStateFlow(PdfViewerState())
    val pdfViewerState: StateFlow<PdfViewerState> = _pdfViewerState.asStateFlow()

    // Built-in Audio Player
    private var mediaPlayer: MediaPlayer? = null
    private var audioProgressJob: Job? = null
    private val _audioPlayerState = MutableStateFlow(AudioPlayerState())
    val audioPlayerState: StateFlow<AudioPlayerState> = _audioPlayerState.asStateFlow()

    // Built-in Video Player
    private val _videoPlayerState = MutableStateFlow(VideoPlayerState())
    val videoPlayerState: StateFlow<VideoPlayerState> = _videoPlayerState.asStateFlow()

    // Snackbar message
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // 1. Tags Repository & State
    val tagsRepository = TagsRepository(application)
    private val _fileTagsMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val fileTagsMap: StateFlow<Map<String, List<String>>> = _fileTagsMap.asStateFlow()
    val selectedTagFilter = MutableStateFlow<String?>(null)

    // 2. Network Drives (Cloud / SMB / WebDAV)
    val networkStorageRepository = NetworkStorageRepository(application)
    private val _networkDrives = MutableStateFlow<List<NetworkDrive>>(emptyList())
    val networkDrives: StateFlow<List<NetworkDrive>> = _networkDrives.asStateFlow()
    private val _activeNetworkDrive = MutableStateFlow<NetworkDrive?>(null)
    val activeNetworkDrive: StateFlow<NetworkDrive?> = _activeNetworkDrive.asStateFlow()
    private val _remoteFiles = MutableStateFlow<List<RemoteFileItem>>(emptyList())
    val remoteFiles: StateFlow<List<RemoteFileItem>> = _remoteFiles.asStateFlow()
    val isTestingNetworkDrive = MutableStateFlow(false)

    // 3. Fast Share (Direct P2P Offline Wi-Fi Transfer)
    val fastShareRepository = FastShareRepository(application)
    private val _fastShareState = MutableStateFlow(FastShareState())
    val fastShareState: StateFlow<FastShareState> = _fastShareState.asStateFlow()

    // 4. Multi-Storage Volumes (Internal, SD Card, USB OTG)
    private val _storageVolumes = MutableStateFlow<List<StorageVolumeItem>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeItem>> = _storageVolumes.asStateFlow()
    val selectedVolume = MutableStateFlow<StorageVolumeItem?>(null)

    // 5. Dual-Pane Split Screen Mode
    val isDualPaneActive = MutableStateFlow(false)
    private val _paneBState = MutableStateFlow(StorageTabState(currentDir = initialDir))
    val paneBState: StateFlow<StorageTabState> = _paneBState.asStateFlow()
    val activePaneIndex = MutableStateFlow(0) // 0: Pane A, 1: Pane B

    // 6. Split APKs / XAPK Installer
    val xapkInstallTarget = MutableStateFlow<XapkInfo?>(null)
    val isInstallingXapk = MutableStateFlow(false)
    val xapkInstallProgress = MutableStateFlow("")

    // 7. Biometric Vault Unlock
    val isBiometricVaultEnabled = MutableStateFlow(vaultRepository.isBiometricEnabled())

    init {
        refreshStorage()
        loadDirectory(initialDir)
        loadRecentFiles()
        loadFavorites()
        loadTrashItems()
        loadStorageApks()
        loadTags()
        loadStorageVolumes()
    }

    fun selectTab(tab: MiTab) {
        _selectedTab.value = tab
        if (tab == MiTab.RECENT && _recentFiles.value.isEmpty()) {
            loadRecentFiles()
        }
    }

    fun navigateToScreen(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenBackStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun handleBackPress(): Boolean {
        if (_currentScreen.value == Screen.MAIN) {
            val state = _storageState.value
            if (state.isSelectionMode) {
                clearSelection()
                return true
            }
            if (_selectedTab.value == MiTab.STORAGE && state.backStack.isNotEmpty()) {
                goBackInDirectory()
                return true
            }
            if (_selectedTab.value == MiTab.RECENT) {
                _selectedTab.value = MiTab.STORAGE
                return true
            }
            return false
        }

        if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeAt(screenBackStack.lastIndex)
            return true
        }

        _currentScreen.value = Screen.MAIN
        return true
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storageSpace.value = fileRepository.getStorageSpace()
        }
    }

    fun onStoragePermissionGranted() {
        refreshStorage()
        val currentDir = _storageState.value.currentDir
        val targetDir = if (currentDir.exists() && currentDir.canRead()) {
            currentDir
        } else {
            fileRepository.rootStorageDirectory
        }
        loadDirectory(targetDir)
        loadRecentFiles()
    }

    fun loadRecentFiles() {
        viewModelScope.launch {
            isRecentLoading.value = true
            val files = fileRepository.getRecentFiles()
            _recentFiles.value = files
            isRecentLoading.value = false
        }
    }

    fun loadDirectory(dir: File, addToHistory: Boolean = false) {
        viewModelScope.launch {
            val current = _storageState.value
            val newBackStack = if (addToHistory && current.currentDir != dir) {
                current.backStack + current.currentDir
            } else {
                current.backStack
            }

            _storageState.update {
                it.copy(
                    currentDir = dir,
                    backStack = newBackStack,
                    forwardStack = emptyList(),
                    selectedItems = emptySet(),
                    isLoading = true
                )
            }

            val items = fileRepository.listFiles(
                directory = dir,
                showHidden = current.showHidden,
                sortType = current.sortType,
                searchQuery = current.searchQuery
            )

            _storageState.update {
                it.copy(items = items, isLoading = false)
            }
        }
    }

    fun goBackInDirectory() {
        val current = _storageState.value
        if (current.backStack.isNotEmpty()) {
            val prev = current.backStack.last()
            val newBackStack = current.backStack.dropLast(1)
            val newForwardStack = current.forwardStack + current.currentDir

            viewModelScope.launch {
                val items = fileRepository.listFiles(
                    directory = prev,
                    showHidden = current.showHidden,
                    sortType = current.sortType,
                    searchQuery = current.searchQuery
                )
                _storageState.update {
                    it.copy(
                        currentDir = prev,
                        backStack = newBackStack,
                        forwardStack = newForwardStack,
                        items = items,
                        selectedItems = emptySet()
                    )
                }
            }
        }
    }

    fun refreshCurrentDirectory() {
        loadDirectory(_storageState.value.currentDir, addToHistory = false)
        refreshStorage()
        loadRecentFiles()
    }

    fun toggleViewMode() {
        _storageState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    fun setSortType(sortType: SortType) {
        _storageState.update { it.copy(sortType = sortType) }
        loadDirectory(_storageState.value.currentDir)
    }

    fun toggleShowHidden() {
        val newShow = !_storageState.value.showHidden
        _storageState.update { it.copy(showHidden = newShow) }
        loadDirectory(_storageState.value.currentDir)
    }

    fun toggleBigFilesFilter() {
        _storageState.update { it.copy(filterOnlyBigFiles = !it.filterOnlyBigFiles) }
    }

    fun setSearchQuery(query: String) {
        _storageState.update { it.copy(searchQuery = query) }
        loadDirectory(_storageState.value.currentDir)
    }

    fun toggleSelectItem(item: FileItem) {
        _storageState.update { state ->
            val set = state.selectedItems.toMutableSet()
            if (set.contains(item)) set.remove(item) else set.add(item)
            state.copy(selectedItems = set)
        }
    }

    fun selectAll() {
        _storageState.update { state ->
            state.copy(selectedItems = state.items.toSet())
        }
    }

    fun clearSelection() {
        _storageState.update { state ->
            state.copy(selectedItems = emptySet())
        }
    }

    fun copySelected() {
        val items = _storageState.value.selectedItems.toList()
        if (items.isNotEmpty()) {
            _clipboard.value = ClipboardState(items = items, isCut = false)
            clearSelection()
            showMessage("${items.size} item(s) copied")
        }
    }

    fun cutSelected() {
        val items = _storageState.value.selectedItems.toList()
        if (items.isNotEmpty()) {
            _clipboard.value = ClipboardState(items = items, isCut = true)
            clearSelection()
            showMessage("${items.size} item(s) cut to clipboard")
        }
    }

    fun copySingle(item: FileItem) {
        _clipboard.value = ClipboardState(items = listOf(item), isCut = false)
        showMessage("\"${item.name}\" copied")
    }

    fun cutSingle(item: FileItem) {
        _clipboard.value = ClipboardState(items = listOf(item), isCut = true)
        showMessage("\"${item.name}\" ready to move")
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun pasteToCurrentDirectory() {
        val clip = _clipboard.value ?: return
        val destDir = _storageState.value.currentDir

        viewModelScope.launch {
            if (clip.isCut) {
                val res = fileRepository.move(clip.items, destDir)
                if (res.isSuccess) {
                    showMessage("Moved ${res.getOrNull()} items successfully")
                    _clipboard.value = null
                } else {
                    showMessage("Move failed: ${res.exceptionOrNull()?.message}")
                }
            } else {
                val res = fileRepository.copy(clip.items, destDir)
                if (res.isSuccess) {
                    showMessage("Copied ${res.getOrNull()} items successfully")
                } else {
                    showMessage("Copy failed: ${res.exceptionOrNull()?.message}")
                }
            }
            refreshCurrentDirectory()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val res = fileRepository.createFolder(_storageState.value.currentDir, name)
            if (res.isSuccess) {
                showMessage("Folder \"$name\" created")
                refreshCurrentDirectory()
            } else {
                showMessage("Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun createTextFile(name: String) {
        viewModelScope.launch {
            val res = fileRepository.createTextFile(_storageState.value.currentDir, name)
            if (res.isSuccess) {
                showMessage("File \"$name\" created")
                refreshCurrentDirectory()
                openTextEditor(res.getOrThrow())
            } else {
                showMessage("Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteItems(items: List<FileItem>) {
        viewModelScope.launch {
            var count = 0
            for (item in items) {
                if (fileRepository.delete(item).getOrDefault(false)) count++
            }
            showMessage("Deleted $count item(s)")
            clearSelection()
            refreshCurrentDirectory()
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        viewModelScope.launch {
            val res = fileRepository.rename(item, newName)
            if (res.isSuccess) {
                showMessage("Renamed to \"$newName\"")
                refreshCurrentDirectory()
            } else {
                showMessage("Rename failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun zipItems(items: List<FileItem>, zipName: String) {
        val sanitized = if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
        val zipFile = File(_storageState.value.currentDir, sanitized)

        viewModelScope.launch {
            val res = fileRepository.zip(items, zipFile)
            if (res.isSuccess) {
                showMessage("Archive \"$sanitized\" created")
                refreshCurrentDirectory()
            } else {
                showMessage("Failed to compress: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun unzipItem(item: FileItem) {
        val outputName = item.name.removeSuffix(".zip").removeSuffix(".ZIP")
        val outputDir = File(_storageState.value.currentDir, outputName)

        viewModelScope.launch {
            val res = fileRepository.unzip(item.file, outputDir)
            if (res.isSuccess) {
                showMessage("Extracted to \"$outputName\"")
                refreshCurrentDirectory()
            } else {
                showMessage("Extraction failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    // Cleaner Tools
    fun openCleaner() {
        navigateToScreen(Screen.CLEANER)
        startCleanScan()
    }

    fun startCleanScan() {
        viewModelScope.launch {
            isCleanScanning.value = true
            cleanedBytes.value = null
            val result = fileRepository.scanForClean()
            _cleanScan.value = result
            isCleanScanning.value = false
        }
    }

    fun performClean() {
        val scan = _cleanScan.value ?: return
        viewModelScope.launch {
            isCleaning.value = true
            var bytesCleaned = 0L
            for (item in scan.junkFiles) {
                val size = item.size
                if (fileRepository.delete(item).getOrDefault(false)) {
                    bytesCleaned += size
                }
            }
            cleanedBytes.value = bytesCleaned
            _cleanScan.value = scan.copy(junkFiles = emptyList())
            isCleaning.value = false
            refreshStorage()
            showMessage("Cleaned ${FileItem.formatBytes(bytesCleaned)} of junk files")
        }
    }

    // FTP Server Tool
    fun openFtpServer() {
        navigateToScreen(Screen.FTP_SERVER)
    }

    fun toggleFtpServer() {
        _ftpServerState.update { it.copy(isRunning = !it.isRunning) }
        val running = _ftpServerState.value.isRunning
        showMessage(if (running) "FTP Service started" else "FTP Service stopped")
    }

    // Category Screen
    fun openCategory(category: FileCategory, title: String) {
        if (title == "Downloads") {
            _selectedTab.value = MiTab.STORAGE
            loadDirectory(fileRepository.downloadsDirectory, addToHistory = true)
            _currentScreen.value = Screen.MAIN
            return
        }

        _categoryViewState.value = CategoryViewState(
            category = category,
            title = title,
            isLoading = true
        )
        navigateToScreen(Screen.CATEGORY_VIEW)

        viewModelScope.launch {
            val items = fileRepository.getCategoryFiles(category)
            _categoryViewState.update { it.copy(items = items, isLoading = false) }
        }
    }

    // Text & HTML Editor/Viewer
    fun openTextEditor(file: File) {
        viewModelScope.launch {
            val content = fileRepository.readText(file).getOrDefault("")
            val isHtml = file.extension.lowercase() in listOf("html", "htm")
            _textEditorState.value = TextEditorState(
                file = file,
                title = file.name,
                content = content,
                originalContent = content,
                isHtmlMode = isHtml,
                showHtmlPreview = isHtml
            )
            navigateToScreen(Screen.TEXT_EDITOR)
        }
    }

    fun openTextFromUri(uri: android.net.Uri, displayName: String) {
        viewModelScope.launch {
            val content = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: ""
            } catch (e: Exception) {
                ""
            }

            val isHtml = displayName.endsWith(".html", ignoreCase = true) || displayName.endsWith(".htm", ignoreCase = true)

            _textEditorState.value = TextEditorState(
                file = null,
                sourceUri = uri,
                title = displayName,
                content = content,
                originalContent = content,
                isHtmlMode = isHtml,
                showHtmlPreview = isHtml
            )
            navigateToScreen(Screen.TEXT_EDITOR)
        }
    }

    fun toggleHtmlPreview() {
        _textEditorState.update { it.copy(showHtmlPreview = !it.showHtmlPreview) }
    }

    fun updateEditorContent(newContent: String) {
        _textEditorState.update { it.copy(content = newContent) }
    }

    fun toggleEditorWordWrap() {
        _textEditorState.update { it.copy(wordWrap = !it.wordWrap) }
    }

    fun saveEditorFile() {
        val state = _textEditorState.value
        viewModelScope.launch {
            _textEditorState.update { it.copy(isSaving = true) }
            val success = if (state.file != null) {
                fileRepository.writeText(state.file, state.content).isSuccess
            } else if (state.sourceUri != null) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(state.sourceUri, "wt")?.use { out ->
                        out.bufferedWriter(Charsets.UTF_8).use { it.write(state.content) }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            } else false

            if (success) {
                _textEditorState.update { it.copy(originalContent = it.content, isSaving = false) }
                showMessage("Saved successfully")
            } else {
                _textEditorState.update { it.copy(isSaving = false) }
                showMessage("Failed to save file")
            }
        }
    }

    // Image Viewer
    fun openImageViewer(file: File, siblingItems: List<FileItem>) {
        val imageFiles = siblingItems.filter { it.category == FileCategory.IMAGE }
        val index = imageFiles.indexOfFirst { it.file.absolutePath == file.absolutePath }.coerceAtLeast(0)
        _imageViewerState.value = ImageViewerState(
            currentFile = file,
            imageList = imageFiles,
            currentIndex = index
        )
        navigateToScreen(Screen.IMAGE_VIEWER)
    }

    fun nextImage() {
        val state = _imageViewerState.value
        if (state.imageList.isNotEmpty() && state.currentIndex < state.imageList.size - 1) {
            val nextIdx = state.currentIndex + 1
            _imageViewerState.value = state.copy(
                currentIndex = nextIdx,
                currentFile = state.imageList[nextIdx].file
            )
        }
    }

    fun prevImage() {
        val state = _imageViewerState.value
        if (state.imageList.isNotEmpty() && state.currentIndex > 0) {
            val prevIdx = state.currentIndex - 1
            _imageViewerState.value = state.copy(
                currentIndex = prevIdx,
                currentFile = state.imageList[prevIdx].file
            )
        }
    }

    // App & APK Manager
    fun openAppManager(tab: ApkTab = ApkTab.APK_FILES) {
        apkScreenTab.value = tab
        navigateToScreen(Screen.APP_MANAGER)
        loadStorageApks()
        loadApps()
    }

    fun setApkTab(tab: ApkTab) {
        apkScreenTab.value = tab
        if (tab == ApkTab.APK_FILES && _storageApks.value.isEmpty()) {
            loadStorageApks()
        } else if (tab == ApkTab.INSTALLED_APPS && _installedApps.value.isEmpty()) {
            loadApps()
        }
    }

    fun loadStorageApks() {
        viewModelScope.launch {
            isStorageApksLoading.value = true
            val apks = appsRepository.getStorageApkFiles()
            _storageApks.value = apks
            isStorageApksLoading.value = false
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            isAppsLoading.value = true
            val apps = appsRepository.getInstalledApps(includeSystemApps = includeSystemApps.value)
            _installedApps.value = apps
            isAppsLoading.value = false
        }
    }

    fun toggleSystemApps() {
        includeSystemApps.update { !it }
        loadApps()
    }

    fun setAppsSearchQuery(q: String) {
        appsSearchQuery.value = q
    }

    fun backupInstalledApp(app: AppInfoItem) {
        viewModelScope.launch {
            showMessage("Backing up ${app.appName}...")
            val res = appsRepository.backupAppApk(app)
            res.fold(
                onSuccess = { dest ->
                    showMessage("Saved APK to Downloads/MiExplorer/Backup")
                    loadStorageApks()
                },
                onFailure = { err ->
                    showMessage("Backup failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun deleteStorageApk(item: ApkFileItem) {
        viewModelScope.launch {
            if (item.file.delete()) {
                showMessage("Deleted \"${item.name}\"")
                loadStorageApks()
            } else {
                showMessage("Failed to delete file")
            }
        }
    }

    fun openApkInstallDialog(file: File) {
        viewModelScope.launch {
            val item = appsRepository.parseApkFile(file)
            _apkInstallTarget.value = item
        }
    }

    fun closeApkInstallDialog() {
        _apkInstallTarget.value = null
    }

    // AMOLED Mode
    fun toggleAmoledMode() {
        val next = !isAmoledMode.value
        isAmoledMode.value = next
        showMessage(if (next) "AMOLED Pure Black ON" else "AMOLED Pure Black OFF")
    }

    // Vault Functions
    fun openVault() {
        isVaultPinSet.value = vaultRepository.isPinSet()
        navigateToScreen(Screen.VAULT)
    }

    fun setupVaultPin(pin: String, answer: String) {
        vaultRepository.setPin(pin, answer)
        isVaultPinSet.value = true
        isVaultUnlocked.value = true
        loadVaultFiles()
        showMessage("Vault PIN set successfully")
    }

    fun unlockVault(pin: String): Boolean {
        val valid = vaultRepository.verifyPin(pin)
        if (valid) {
            isVaultUnlocked.value = true
            loadVaultFiles()
        }
        return valid
    }

    fun lockVault() {
        isVaultUnlocked.value = false
        _vaultFiles.value = emptyList()
        showMessage("Vault locked")
    }

    fun loadVaultFiles() {
        viewModelScope.launch {
            isVaultLoading.value = true
            _vaultFiles.value = vaultRepository.getVaultFiles()
            isVaultLoading.value = false
        }
    }

    fun addFileToVault(item: FileItem) {
        viewModelScope.launch {
            val success = vaultRepository.addToVault(item.file)
            if (success) {
                showMessage("Moved \"${item.name}\" to Private Vault")
                loadDirectory(_storageState.value.currentDir)
                if (isVaultUnlocked.value) loadVaultFiles()
            } else {
                showMessage("Failed to move file to Vault")
            }
        }
    }

    fun restoreFileFromVault(item: FileItem) {
        viewModelScope.launch {
            val target = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).let {
                File(it, "Restored")
            }
            val success = vaultRepository.restoreFromVault(item.file, target)
            if (success) {
                showMessage("Restored to Downloads/Restored")
                loadVaultFiles()
                loadDirectory(_storageState.value.currentDir)
            } else {
                showMessage("Failed to restore file")
            }
        }
    }

    fun deleteFileFromVault(item: FileItem) {
        viewModelScope.launch {
            val success = vaultRepository.deleteFromVault(item.file)
            if (success) {
                showMessage("Deleted from Vault")
                loadVaultFiles()
            }
        }
    }

    // Duplicate Finder Functions
    fun openDuplicateFinder() {
        navigateToScreen(Screen.DUPLICATES)
    }

    fun scanForDuplicates() {
        viewModelScope.launch {
            isDuplicateScanning.value = true
            val result = duplicateRepository.findDuplicates()
            _duplicateScanResult.value = result
            selectedDuplicateFiles.value = result.groups.flatMap { it.duplicates }.toSet()
            isDuplicateScanning.value = false
        }
    }

    fun toggleSelectDuplicate(file: FileItem) {
        val current = selectedDuplicateFiles.value.toMutableSet()
        if (current.contains(file)) current.remove(file) else current.add(file)
        selectedDuplicateFiles.value = current
    }

    fun selectAllDuplicateCopies() {
        val allCopies = _duplicateScanResult.value?.groups?.flatMap { it.duplicates }?.toSet() ?: emptySet()
        selectedDuplicateFiles.value = allCopies
    }

    fun clearSelectedDuplicates() {
        selectedDuplicateFiles.value = emptySet()
    }

    fun deleteSelectedDuplicates() {
        val toDelete = selectedDuplicateFiles.value.toList()
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            val deletedCount = duplicateRepository.deleteFiles(toDelete)
            val freedBytes = toDelete.sumOf { it.size }
            showMessage("Deleted $deletedCount duplicates (Freed ${FileItem.formatBytes(freedBytes)})")
            selectedDuplicateFiles.value = emptySet()
            scanForDuplicates()
            refreshStorage()
            loadDirectory(_storageState.value.currentDir)
        }
    }

    // Storage Analyzer Functions
    fun openStorageAnalyzer() {
        navigateToScreen(Screen.STORAGE_ANALYZER)
    }

    fun analyzeStorage() {
        viewModelScope.launch {
            isStorageAnalyzing.value = true
            _storageAnalysisResult.value = storageAnalyzerRepository.analyzeStorage()
            isStorageAnalyzing.value = false
        }
    }

    fun openDirectoryFromAnalyzer(dir: File) {
        loadDirectory(dir, addToHistory = true)
        _selectedTab.value = MiTab.STORAGE
        navigateToScreen(Screen.MAIN)
    }

    // Batch Rename Function
    fun batchRename(pairs: List<Pair<FileItem, String>>) {
        viewModelScope.launch {
            var successCount = 0
            for ((item, newName) in pairs) {
                if (item.name != newName) {
                    val target = File(item.file.parentFile, newName)
                    if (item.file.renameTo(target)) {
                        successCount++
                    }
                }
            }
            showMessage("Renamed $successCount files successfully")
            clearSelection()
            loadDirectory(_storageState.value.currentDir)
        }
    }

    // Zip / Tar / Gz / 7z / Rar Archive Functions
    fun openZipViewer(file: File) {
        viewModelScope.launch {
            _zipViewerState.value = ZipViewerState(isLoading = true)
            navigateToScreen(Screen.ZIP_VIEWER)
            val res = ArchiveHelper.inspectArchive(file)
            res.fold(
                onSuccess = { info ->
                    _zipViewerState.value = ZipViewerState(archiveInfo = info, isLoading = false)
                },
                onFailure = { err ->
                    _zipViewerState.value = ZipViewerState(isLoading = false, errorMessage = err.localizedMessage)
                }
            )
        }
    }

    fun openZipFromUri(uri: android.net.Uri, name: String) {
        viewModelScope.launch {
            _zipViewerState.value = ZipViewerState(isLoading = true)
            navigateToScreen(Screen.ZIP_VIEWER)
            val res = zipRepository.inspectZipUri(uri, name)
            res.fold(
                onSuccess = { info ->
                    _zipViewerState.value = ZipViewerState(archiveInfo = info, isLoading = false)
                },
                onFailure = { err ->
                    _zipViewerState.value = ZipViewerState(isLoading = false, errorMessage = err.localizedMessage)
                }
            )
        }
    }

    fun extractZipArchive(zipFile: File, targetDir: File, selectedEntries: Set<String>? = null) {
        viewModelScope.launch {
            isZipExtracting.value = true
            val res = ArchiveHelper.extractArchive(zipFile, targetDir, onProgress = { _, _ -> })
            isZipExtracting.value = false
            res.fold(
                onSuccess = {
                    showMessage("Extracted archive to ${targetDir.name}")
                    loadDirectory(_storageState.value.currentDir)
                    refreshStorage()
                },
                onFailure = { err ->
                    showMessage("Extraction failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun compressFilesToZip(items: List<File>, destinationZip: File, compressionLevel: Int) {
        viewModelScope.launch {
            showMessage("Compressing ${items.size} items...")
            val res = zipRepository.compressFiles(items, destinationZip, compressionLevel)
            res.fold(
                onSuccess = { createdFile ->
                    showMessage("Created ${createdFile.name} (${FileItem.formatBytes(createdFile.length())})")
                    clearSelection()
                    loadDirectory(_storageState.value.currentDir)
                    refreshStorage()
                },
                onFailure = { err ->
                    showMessage("Compression failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // ==========================================
    // RECYCLE BIN (TRASH) METHODS
    // ==========================================

    fun openTrash() {
        navigateToScreen(Screen.TRASH)
        loadTrashItems()
    }

    fun loadTrashItems() {
        viewModelScope.launch {
            isTrashLoading.value = true
            _trashItems.value = trashRepository.getTrashItems()
            isTrashLoading.value = false
        }
    }

    fun moveToTrash(items: List<FileItem>) {
        viewModelScope.launch {
            var successCount = 0
            for (item in items) {
                val res = trashRepository.moveToTrash(item)
                if (res.isSuccess) successCount++
            }
            showMessage("Moved $successCount item(s) to Recycle Bin")
            clearSelection()
            loadTrashItems()
            refreshCurrentDirectory()
            refreshStorage()
        }
    }

    fun restoreTrashItems(items: List<TrashItem>) {
        viewModelScope.launch {
            var restoredCount = 0
            for (item in items) {
                val res = trashRepository.restoreItem(item)
                if (res.isSuccess) restoredCount++
            }
            showMessage("Restored $restoredCount item(s)")
            loadTrashItems()
            refreshCurrentDirectory()
            refreshStorage()
        }
    }

    fun deletePermanentlyMultiple(items: List<TrashItem>) {
        viewModelScope.launch {
            var deletedCount = 0
            for (item in items) {
                if (trashRepository.deletePermanently(item)) deletedCount++
            }
            showMessage("Permanently deleted $deletedCount item(s)")
            loadTrashItems()
            refreshStorage()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            if (trashRepository.emptyTrash()) {
                showMessage("Recycle Bin emptied")
                loadTrashItems()
                refreshStorage()
            } else {
                showMessage("Failed to empty Recycle Bin")
            }
        }
    }

    // ==========================================
    // FAVORITES / PINNED FOLDERS METHODS
    // ==========================================

    fun loadFavorites() {
        viewModelScope.launch {
            _favorites.value = favoritesRepository.getFavorites()
        }
    }

    fun toggleFavorite(file: File, customName: String? = null) {
        viewModelScope.launch {
            val isNowFav = favoritesRepository.toggleFavorite(file, customName)
            loadFavorites()
            showMessage(if (isNowFav) "Added to Favorites" else "Removed from Favorites")
        }
    }

    suspend fun isFavorite(path: String): Boolean {
        return favoritesRepository.isFavorite(path)
    }

    // ==========================================
    // PDF VIEWER METHODS
    // ==========================================

    fun openPdfFile(file: File) {
        _pdfViewerState.value = PdfViewerState(
            file = file,
            title = file.name
        )
        navigateToScreen(Screen.PDF_VIEWER)
    }

    // ==========================================
    // BUILT-IN AUDIO PLAYER METHODS
    // ==========================================

    fun playAudio(item: FileItem, playlist: List<FileItem> = emptyList()) {
        viewModelScope.launch {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                audioProgressJob?.cancel()

                val player = MediaPlayer()
                java.io.FileInputStream(item.file).use { fis ->
                    player.setDataSource(fis.fd)
                }
                player.prepare()

                var title = item.name
                var artist = "Unknown Artist"
                var duration = player.duration

                try {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(item.file.absolutePath)
                    title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: item.name
                    artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    mmr.release()
                } catch (e: Exception) {
                    // Fallback to filename
                }

                val fullList = if (playlist.isNotEmpty()) playlist else listOf(item)
                val idx = fullList.indexOfFirst { it.path == item.path }.coerceAtLeast(0)

                player.start()
                mediaPlayer = player

                _audioPlayerState.value = AudioPlayerState(
                    currentFile = item.file,
                    title = title,
                    artist = artist,
                    durationMs = duration,
                    currentPositionMs = 0,
                    isPlaying = true,
                    isVisible = true,
                    isExpanded = true,
                    playlist = fullList,
                    currentIndex = idx,
                    isShuffle = _audioPlayerState.value.isShuffle,
                    isRepeat = _audioPlayerState.value.isRepeat
                )

                player.setOnCompletionListener {
                    val state = _audioPlayerState.value
                    if (state.isRepeat) {
                        player.seekTo(0)
                        player.start()
                    } else {
                        playNextAudio()
                    }
                }

                startAudioProgressTicker()
            } catch (e: Exception) {
                showMessage("Cannot play audio: ${e.localizedMessage}")
            }
        }
    }

    // ==========================================
    // BUILT-IN VIDEO PLAYER METHODS
    // ==========================================

    fun playVideo(item: FileItem, playlist: List<FileItem> = emptyList()) {
        // Pause audio if currently running
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            _audioPlayerState.update { it.copy(isPlaying = false) }
        }

        val fullList = if (playlist.isNotEmpty()) playlist else listOf(item)
        val idx = fullList.indexOfFirst { it.path == item.path }.coerceAtLeast(0)

        _videoPlayerState.value = VideoPlayerState(
            file = item.file,
            title = item.name,
            playlist = fullList,
            currentIndex = idx
        )
        navigateToScreen(Screen.VIDEO_PLAYER)
    }

    fun playNextVideo() {
        val state = _videoPlayerState.value
        if (state.playlist.isEmpty()) return
        val nextIdx = (state.currentIndex + 1) % state.playlist.size
        val nextItem = state.playlist[nextIdx]
        playVideo(nextItem, state.playlist)
    }

    fun playPreviousVideo() {
        val state = _videoPlayerState.value
        if (state.playlist.isEmpty()) return
        val prevIdx = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.lastIndex
        val prevItem = state.playlist[prevIdx]
        playVideo(prevItem, state.playlist)
    }

    private fun startAudioProgressTicker() {
        audioProgressJob?.cancel()
        audioProgressJob = viewModelScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _audioPlayerState.update {
                        it.copy(currentPositionMs = player.currentPosition, isPlaying = true)
                    }
                }
                delay(500)
            }
        }
    }

    fun toggleAudioPlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _audioPlayerState.update { it.copy(isPlaying = false) }
        } else {
            player.start()
            _audioPlayerState.update { it.copy(isPlaying = true) }
        }
    }

    fun seekAudioTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _audioPlayerState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun playNextAudio() {
        val state = _audioPlayerState.value
        if (state.playlist.isEmpty()) return
        val nextIdx = if (state.isShuffle) {
            state.playlist.indices.random()
        } else {
            (state.currentIndex + 1) % state.playlist.size
        }
        val nextItem = state.playlist[nextIdx]
        playAudio(nextItem, state.playlist)
    }

    fun playPreviousAudio() {
        val state = _audioPlayerState.value
        if (state.playlist.isEmpty()) return
        val prevIdx = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.lastIndex
        val prevItem = state.playlist[prevIdx]
        playAudio(prevItem, state.playlist)
    }

    fun toggleAudioExpanded() {
        _audioPlayerState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun toggleAudioShuffle() {
        _audioPlayerState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun toggleAudioRepeat() {
        _audioPlayerState.update { it.copy(isRepeat = !it.isRepeat) }
    }

    fun closeAudioPlayer() {
        audioProgressJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _audioPlayerState.update { it.copy(isPlaying = false, isVisible = false, isExpanded = false) }
    }

    // ==========================================
    // SMART DIRECT FILE OPENER
    // (Bypasses "Open With" dialog for known extensions)
    // ==========================================

    fun openFileSmart(item: FileItem, siblingItems: List<FileItem> = emptyList()): Boolean {
        if (item.isDirectory) {
            loadDirectory(item.file, addToHistory = true)
            return true
        }

        val ext = item.extension.lowercase()

        // 1. PDF Documents
        if (ext == "pdf") {
            openPdfFile(item.file)
            return true
        }

        // 2. Audio Files
        if (item.category == FileCategory.AUDIO || ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "opus", "amr", "m4b", "mid", "midi")) {
            val audioSiblings = siblingItems.filter { it.category == FileCategory.AUDIO || it.extension.lowercase() in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "opus", "amr") }
            playAudio(item, if (audioSiblings.isNotEmpty()) audioSiblings else listOf(item))
            return true
        }

        // 3. Video Files
        if (item.category == FileCategory.VIDEO || ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "mpg", "mpeg")) {
            val videoSiblings = siblingItems.filter { it.category == FileCategory.VIDEO || it.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "mpg", "mpeg") }
            playVideo(item, if (videoSiblings.isNotEmpty()) videoSiblings else listOf(item))
            return true
        }

        // 4. Image Files
        if (item.category == FileCategory.IMAGE || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")) {
            val imageSiblings = siblingItems.filter { it.category == FileCategory.IMAGE }
            openImageViewer(item.file, if (imageSiblings.isNotEmpty()) imageSiblings else listOf(item))
            return true
        }

        // 5. Archive (ZIP, TAR, GZ, TGZ, 7Z, RAR)
        if (ext in listOf("zip", "tar", "gz", "tgz", "7z", "rar") || item.name.lowercase().endsWith(".tar.gz")) {
            openZipViewer(item.file)
            return true
        }

        // 6. Text / Code / HTML / Markdown / Logs / JSON / XML / CSV
        if (item.category == FileCategory.CODE || ext in listOf(
                "txt", "log", "json", "xml", "html", "htm", "md", "csv",
                "kt", "java", "py", "js", "ts", "css", "c", "cpp", "h",
                "sh", "properties", "yaml", "yml", "sql", "conf", "ini",
                "gradle", "kts", "env", "bat"
            )
        ) {
            openTextEditor(item.file)
            return true
        }

        // 7. Split APKs Bundle (XAPK / APKS)
        if (ext in listOf("xapk", "apks")) {
            openXapkFile(item.file)
            return true
        }

        // 8. Regular APK Installation files
        if (item.category == FileCategory.APK || ext == "apk") {
            openApkInstallDialog(item.file)
            return true
        }

        // Non-previewable (documents like docx, unknown binary) -> return false so popup/chooser can be used
        return false
    }

    // ==========================================
    // 1. Color Tags & Labels
    // ==========================================
    fun loadTags() {
        viewModelScope.launch {
            _fileTagsMap.value = tagsRepository.getFileTagsMap()
        }
    }

    fun toggleTagForFile(file: File, tagId: String) {
        viewModelScope.launch {
            tagsRepository.toggleTagForFile(file.absolutePath, tagId)
            loadTags()
        }
    }

    fun filterByTag(tagId: String?) {
        selectedTagFilter.value = tagId
        refreshCurrentDirectory()
    }

    // ==========================================
    // 2. Cloud & Network Drives (SMB / WebDAV / FTP)
    // ==========================================
    fun openNetworkDrives() {
        navigateToScreen(Screen.NETWORK_DRIVES)
        loadNetworkDrives()
    }

    fun loadNetworkDrives() {
        viewModelScope.launch {
            _networkDrives.value = networkStorageRepository.getSavedDrives()
        }
    }

    fun saveNetworkDrive(drive: NetworkDrive) {
        viewModelScope.launch {
            networkStorageRepository.saveDrive(drive)
            loadNetworkDrives()
            showMessage("Saved ${drive.name}")
        }
    }

    fun deleteNetworkDrive(id: String) {
        viewModelScope.launch {
            networkStorageRepository.deleteDrive(id)
            loadNetworkDrives()
            showMessage("Drive removed")
        }
    }

    fun connectNetworkDrive(drive: NetworkDrive) {
        viewModelScope.launch {
            _activeNetworkDrive.value = drive
            refreshRemoteFiles()
        }
    }

    fun disconnectNetworkDrive() {
        _activeNetworkDrive.value = null
        _remoteFiles.value = emptyList()
    }

    fun refreshRemoteFiles() {
        val drive = _activeNetworkDrive.value ?: return
        viewModelScope.launch {
            _remoteFiles.value = networkStorageRepository.listRemoteFiles(drive, drive.remotePath)
        }
    }

    fun testNetworkDriveConnection(drive: NetworkDrive) {
        viewModelScope.launch {
            isTestingNetworkDrive.value = true
            val res = networkStorageRepository.testConnection(drive)
            isTestingNetworkDrive.value = false
            res.fold(
                onSuccess = { msg -> showMessage("✓ $msg") },
                onFailure = { err -> showMessage("✗ Connection failed: ${err.localizedMessage}") }
            )
        }
    }

    // ==========================================
    // 3. Fast Share (Direct Offline Wi-Fi P2P)
    // ==========================================
    fun openFastShare(files: List<FileItem> = emptyList()) {
        _fastShareState.update { it.copy(sharingFiles = files) }
        navigateToScreen(Screen.FAST_SHARE)
        if (files.isNotEmpty()) {
            startFastShare(files)
        }
    }

    fun startFastShare(files: List<FileItem>) {
        viewModelScope.launch {
            val ip = fastShareRepository.getLocalIpAddress()
            val res = fastShareRepository.startShareServer(files, port = 7777) { downloadedFile ->
                showMessage("Device downloaded: $downloadedFile")
            }
            res.fold(
                onSuccess = { url ->
                    _fastShareState.value = FastShareState(
                        isServerRunning = true,
                        hostIp = ip,
                        port = 7777,
                        sharingFiles = files
                    )
                    showMessage("Sharing active on $url")
                },
                onFailure = { err ->
                    showMessage("Failed to start transfer server: ${err.localizedMessage}")
                }
            )
        }
    }

    fun stopFastShare() {
        fastShareRepository.stopShareServer()
        _fastShareState.update { it.copy(isServerRunning = false) }
        showMessage("Transfer server stopped")
    }

    // ==========================================
    // 4. Multi-Storage Volumes (Internal, SD Card, USB OTG)
    // ==========================================
    fun loadStorageVolumes() {
        val vols = fileRepository.getStorageVolumes()
        _storageVolumes.value = vols
        if (selectedVolume.value == null && vols.isNotEmpty()) {
            selectedVolume.value = vols.first()
        }
    }

    fun switchStorageVolume(volume: StorageVolumeItem) {
        selectedVolume.value = volume
        loadDirectory(volume.file, addToHistory = true)
        showMessage("Switched to ${volume.name}")
    }

    // ==========================================
    // 5. Dual-Pane Split Screen Mode
    // ==========================================
    fun toggleDualPane() {
        val active = !isDualPaneActive.value
        isDualPaneActive.value = active
        if (active) {
            // Initialize Pane B with current folder or root
            _paneBState.value = StorageTabState(currentDir = _storageState.value.currentDir)
            loadDirectoryPaneB(_storageState.value.currentDir)
        }
    }

    fun setActivePane(index: Int) {
        activePaneIndex.value = index
    }

    fun navigatePaneB(dir: File) {
        val current = _paneBState.value
        val newStack = current.backStack + current.currentDir
        _paneBState.update { it.copy(currentDir = dir, backStack = newStack) }
        loadDirectoryPaneB(dir)
    }

    fun backPaneB() {
        val current = _paneBState.value
        if (current.backStack.isNotEmpty()) {
            val prev = current.backStack.last()
            val newStack = current.backStack.dropLast(1)
            _paneBState.update { it.copy(currentDir = prev, backStack = newStack) }
            loadDirectoryPaneB(prev)
        }
    }

    private fun loadDirectoryPaneB(dir: File) {
        viewModelScope.launch {
            val items = fileRepository.listFiles(dir, showHidden = false, sortType = SortType.NAME_ASC)
            _paneBState.update { it.copy(items = items) }
        }
    }

    fun copyPaneAtoB() {
        val sourceItems = _storageState.value.selectedItems.toList()
        if (sourceItems.isEmpty()) return
        val targetDir = _paneBState.value.currentDir
        viewModelScope.launch {
            fileRepository.copy(sourceItems, targetDir)
            showMessage("Copied ${sourceItems.size} items to Pane B (${targetDir.name})")
            clearSelection()
            loadDirectoryPaneB(targetDir)
        }
    }

    fun copyPaneBtoA() {
        val sourceItems = _paneBState.value.selectedItems.toList()
        if (sourceItems.isEmpty()) return
        val targetDir = _storageState.value.currentDir
        viewModelScope.launch {
            fileRepository.copy(sourceItems, targetDir)
            showMessage("Copied ${sourceItems.size} items to Pane A (${targetDir.name})")
            _paneBState.update { it.copy(selectedItems = emptySet()) }
            refreshCurrentDirectory()
        }
    }

    // ==========================================
    // 6. Split APKs / XAPK Installer
    // ==========================================
    fun openXapkFile(file: File) {
        viewModelScope.launch {
            val res = XapkInstaller.parseXapk(file)
            res.fold(
                onSuccess = { info ->
                    xapkInstallTarget.value = info
                },
                onFailure = { err ->
                    showMessage("Cannot parse XAPK/APKS: ${err.localizedMessage}")
                }
            )
        }
    }

    fun closeXapkDialog() {
        xapkInstallTarget.value = null
        isInstallingXapk.value = false
        xapkInstallProgress.value = ""
    }

    fun installActiveXapk() {
        val info = xapkInstallTarget.value ?: return
        viewModelScope.launch {
            isInstallingXapk.value = true
            xapkInstallProgress.value = "Preparing split installation..."
            val res = XapkInstaller.installXapk(
                context = getApplication(),
                xapkInfo = info,
                onProgress = { p, step ->
                    xapkInstallProgress.value = step
                }
            )
            isInstallingXapk.value = false
            res.fold(
                onSuccess = {
                    showMessage("Split APK installation started! Follow system prompt.")
                    closeXapkDialog()
                },
                onFailure = { err ->
                    showMessage("Split APK installation failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // ==========================================
    // 7. Biometric Vault Unlock
    // ==========================================
    fun toggleBiometricVault(enabled: Boolean) {
        vaultRepository.setBiometricEnabled(enabled)
        isBiometricVaultEnabled.value = enabled
        showMessage(if (enabled) "Biometric fingerprint unlock enabled" else "Biometric unlock disabled")
    }

    fun unlockVaultWithBiometrics() {
        viewModelScope.launch {
            _vaultFiles.value = vaultRepository.getVaultFiles()
            isVaultUnlocked.value = true
            showMessage("Vault unlocked with fingerprint")
        }
    }

    // ==========================================
    // 8. Recycle Bin Retention Auto-Purge
    // ==========================================
    fun purgeTrashExpired(retentionDays: Int = 30) {
        viewModelScope.launch {
            val purgedCount = trashRepository.purgeExpiredItems(retentionDays)
            if (purgedCount > 0) {
                showMessage("Auto-purged $purgedCount items older than $retentionDays days")
                loadTrashItems()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioProgressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun showMessage(msg: String) {
        _message.value = msg
    }

    fun clearMessage() {
        _message.value = null
    }
}
