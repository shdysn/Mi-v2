package com.mi.explorer.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.data.model.SortType
import com.mi.explorer.data.model.StorageSpace
import com.mi.explorer.data.model.StorageVolumeItem
import com.mi.explorer.data.model.VolumeType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class CleanScanResult(
    val junkFiles: List<FileItem>,
    val largeFiles: List<FileItem>,
    val apkFiles: List<FileItem>,
    val emptyFolders: List<FileItem>
) {
    val totalJunkBytes: Long get() = junkFiles.sumOf { it.size }
    val totalLargeBytes: Long get() = largeFiles.sumOf { it.size }
    val totalApkBytes: Long get() = apkFiles.sumOf { it.size }
    val formattedJunkSize: String get() = FileItem.formatBytes(totalJunkBytes)
}

class FileRepository(private val context: Context) {

    val rootStorageDirectory: File
        get() = try {
            val ext = Environment.getExternalStorageDirectory()
            if (ext.exists() && ext.canRead()) ext else context.filesDir
        } catch (e: Exception) {
            context.filesDir
        }

    val downloadsDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).let {
            if (it.exists()) it else File(rootStorageDirectory, "Download")
        }

    val documentsDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).let {
            if (it.exists()) it else File(rootStorageDirectory, "Documents")
        }

    val picturesDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).let {
            if (it.exists()) it else File(rootStorageDirectory, "Pictures")
        }

    val dcimDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).let {
            if (it.exists()) it else File(rootStorageDirectory, "DCIM")
        }

    fun getStorageVolumes(): List<StorageVolumeItem> {
        val list = mutableListOf<StorageVolumeItem>()
        // 1. Primary Internal Storage
        val primaryDir = rootStorageDirectory
        val primaryStat = try { StatFs(primaryDir.path) } catch (e: Exception) { null }
        val primaryTotal = primaryStat?.let { it.blockCountLong * it.blockSizeLong } ?: (64L * 1024 * 1024 * 1024)
        val primaryFree = primaryStat?.let { it.availableBlocksLong * it.blockSizeLong } ?: (20L * 1024 * 1024 * 1024)

        list.add(
            StorageVolumeItem(
                id = "internal_storage",
                name = "Internal Storage",
                file = primaryDir,
                type = VolumeType.INTERNAL,
                totalBytes = primaryTotal,
                freeBytes = primaryFree,
                isPrimary = true
            )
        )

        // 2. Query removable / secondary directories via context.getExternalFilesDirs(null)
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    val root = getRootOfVolume(dir)
                    if (root != null && root.absolutePath != primaryDir.absolutePath && root.canRead()) {
                        val stat = try { StatFs(root.path) } catch (e: Exception) { null }
                        val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
                        val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
                        val isUsb = root.name.lowercase().contains("usb") || root.path.lowercase().contains("usb")
                        val volType = if (isUsb) VolumeType.USB_OTG else VolumeType.SD_CARD
                        val volName = if (isUsb) "USB OTG Drive" else "SD Card (${root.name})"

                        if (list.none { it.file.absolutePath == root.absolutePath }) {
                            list.add(
                                StorageVolumeItem(
                                    id = root.absolutePath,
                                    name = volName,
                                    file = root,
                                    type = volType,
                                    totalBytes = total,
                                    freeBytes = free,
                                    isPrimary = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        return list
    }

    private fun getRootOfVolume(file: File): File? {
        var current: File? = file
        while (current != null && current.parentFile != null) {
            if (current.parentFile?.name == "Android") {
                return current.parentFile?.parentFile
            }
            current = current.parentFile
        }
        return null
    }

    val musicDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).let {
            if (it.exists()) it else File(rootStorageDirectory, "Music")
        }

    val moviesDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).let {
            if (it.exists()) it else File(rootStorageDirectory, "Movies")
        }

    init {
        ensureMiExplorerSampleData()
    }

    private fun ensureMiExplorerSampleData() {
        try {
            val base = File(context.filesDir, "MiExplorer")
            if (!base.exists()) {
                base.mkdirs()
                File(base, "Welcome_Mi_Explorer.txt").writeText(
                    """
                    Welcome to Mi Explorer!
                    
                    Inspired by Xiaomi MIUI / HyperOS File Manager:
                    • Recent tab: Quick access to recently created, captured, or downloaded items.
                    • Storage tab: Clear overview of used space with 8 fast category shortcuts.
                    • Deep Clean: Scan for app cache, residual junk, obsolete APKs, and large files.
                    • Built-in Tools: Text Editor, Photo Previewer, APK Inspector, and ZIP manager.
                    • 100% Free: No ads, no subscriptions, fast and lightweight.
                    """.trimIndent()
                )
                File(base, "MIUI_Tips.md").writeText(
                    """
                    # MIUI File Manager Tips
                    - Switch between Recent and Storage using the top tabs.
                    - Tap the Cleaner button to free up gigabytes of space in seconds.
                    - Use FTP server mode to transfer files directly to your PC without cables!
                    """.trimIndent()
                )
                val docs = File(base, "Documents")
                docs.mkdirs()
                File(docs, "Project_Roadmap.txt").writeText("1. Modern Compose UI\n2. MIUI-inspired Squircles\n3. Zero Ads\n4. Lightning fast\n")

                val musicDir = File(base, "Music").apply { mkdirs() }
                val sampleAudio = File(musicDir, "Mi_Melody_Sample.wav")
                if (!sampleAudio.exists()) {
                    createSampleWavFile(sampleAudio)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createSampleWavFile(file: File) {
        try {
            val sampleRate = 8000
            val numSeconds = 4
            val numSamples = sampleRate * numSeconds
            val buffer = ByteArray(numSamples)
            val freqs = listOf(523.25, 659.25, 783.99, 1046.50)
            for (i in 0 until numSamples) {
                val sec = i / sampleRate
                val freq = freqs[(sec % freqs.size)]
                val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                val sample = (Math.sin(angle) * 127 + 128).toInt().toByte()
                buffer[i] = sample
            }

            FileOutputStream(file).use { out ->
                val totalDataLen = buffer.size + 36
                val byteRate = sampleRate

                out.write("RIFF".toByteArray(Charsets.US_ASCII))
                out.write(intToByteArray(totalDataLen))
                out.write("WAVE".toByteArray(Charsets.US_ASCII))
                out.write("fmt ".toByteArray(Charsets.US_ASCII))
                out.write(intToByteArray(16))
                out.write(shortToByteArray(1))
                out.write(shortToByteArray(1))
                out.write(intToByteArray(sampleRate))
                out.write(intToByteArray(byteRate))
                out.write(shortToByteArray(1))
                out.write(shortToByteArray(8))
                out.write("data".toByteArray(Charsets.US_ASCII))
                out.write(intToByteArray(buffer.size))
                out.write(buffer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }

    private val folderSizeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>() // path -> (lastModified, sizeBytes)

    fun invalidateFolderSize(dir: File?) {
        if (dir == null) return
        folderSizeCache.remove(dir.absolutePath)
        dir.parentFile?.let { folderSizeCache.remove(it.absolutePath) }
    }

    fun calculateDirectorySize(dir: File, maxDepth: Int = 3): Long {
        if (!dir.isDirectory || !dir.canRead()) return 0L
        val cached = folderSizeCache[dir.absolutePath]
        if (cached != null && cached.first == dir.lastModified()) {
            return cached.second
        }
        val size = computeDirSize(dir, maxDepth, currentDepth = 0)
        folderSizeCache[dir.absolutePath] = Pair(dir.lastModified(), size)
        return size
    }

    private fun computeDirSize(dir: File, maxDepth: Int, currentDepth: Int): Long {
        if (currentDepth > maxDepth) return 0L
        if (dir.name.equals("Android", ignoreCase = true) && currentDepth > 0) return 0L
        var total = 0L
        try {
            val list = dir.listFiles() ?: return 0L
            for (f in list) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    total += computeDirSize(f, maxDepth, currentDepth + 1)
                } else {
                    total += f.length()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return total
    }

    suspend fun listFiles(
        directory: File,
        showHidden: Boolean = false,
        sortType: SortType = SortType.NAME_ASC,
        searchQuery: String = ""
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val files = directory.listFiles() ?: return@withContext emptyList()
        var items = files.map { file ->
            if (file.isDirectory) {
                val count = file.list()?.size ?: 0
                val dirSize = calculateDirectorySize(file, maxDepth = 3)
                FileItem(
                    file = file,
                    size = dirSize,
                    folderSize = dirSize,
                    itemCount = count
                )
            } else {
                FileItem(
                    file = file,
                    size = file.length(),
                    itemCount = 0
                )
            }
        }

        if (!showHidden) {
            items = items.filter { !it.isHidden }
        }

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            items = items.filter { it.name.lowercase().contains(query) }
        }

        val folders = items.filter { it.isDirectory }
        val normalFiles = items.filter { !it.isDirectory }

        val sortedFolders = sortList(folders, sortType)
        val sortedFiles = sortList(normalFiles, sortType)

        sortedFolders + sortedFiles
    }

    private fun sortList(list: List<FileItem>, sortType: SortType): List<FileItem> {
        return when (sortType) {
            SortType.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortType.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortType.DATE_NEWEST -> list.sortedByDescending { it.lastModified }
            SortType.DATE_OLDEST -> list.sortedBy { it.lastModified }
            SortType.SIZE_LARGEST -> list.sortedByDescending { it.effectiveSize }
            SortType.SIZE_SMALLEST -> list.sortedBy { it.effectiveSize }
            SortType.TYPE -> list.sortedWith(compareBy({ it.extension }, { it.name.lowercase() }))
        }
    }

    suspend fun getRecentFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val seenPaths = HashSet<String>()

        // 1. Instant MediaStore query (20-40ms)
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val uri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 60"

            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                if (dataCol != -1) {
                    while (cursor.moveToNext() && list.size < 60) {
                        val path = cursor.getString(dataCol)
                        if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                            val f = File(path)
                            if (f.exists() && !f.isDirectory && !f.name.startsWith(".")) {
                                list.add(FileItem(f))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // MediaStore fallback
        }

        // 2. Fast shallow scan of primary user directories (Never scan Android/ or deep roots!)
        if (list.size < 30) {
            val keyDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(context.filesDir, "MiExplorer")
            ).filter { it.exists() && it.canRead() }

            for (dir in keyDirs) {
                dir.listFiles()?.forEach { f ->
                    if (!f.isDirectory && !f.name.startsWith(".") && seenPaths.add(f.absolutePath)) {
                        list.add(FileItem(f))
                    }
                }
            }
        }

        list.sortedByDescending { it.lastModified }.take(60)
    }

    suspend fun getCategoryFiles(category: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val seenPaths = HashSet<String>()

        // 1. Try fast MediaStore query
        try {
            val (uri, selection, sortOrder) = when (category) {
                FileCategory.IMAGE -> Triple(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT 300"
                )
                FileCategory.VIDEO -> Triple(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    null,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC LIMIT 300"
                )
                FileCategory.AUDIO -> Triple(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    null,
                    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC LIMIT 300"
                )
                FileCategory.APK -> Triple(
                    MediaStore.Files.getContentUri("external"),
                    "${MediaStore.MediaColumns.DATA} LIKE '%.apk' OR ${MediaStore.MediaColumns.DATA} LIKE '%.xapk' OR ${MediaStore.MediaColumns.DATA} LIKE '%.apks' OR ${MediaStore.MediaColumns.MIME_TYPE} = 'application/vnd.android.package-archive'",
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC LIMIT 300"
                )
                else -> Triple(null, null, null)
            }

            if (uri != null) {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataCol != -1) {
                        while (cursor.moveToNext() && list.size < 300) {
                            val path = cursor.getString(dataCol)
                            if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                                val f = File(path)
                                if (f.exists() && !f.isDirectory) {
                                    list.add(FileItem(f))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // MediaStore fallback
        }

        if (list.isNotEmpty()) {
            return@withContext list.sortedByDescending { it.lastModified }
        }

        // 2. Targeted shallow folder search (Never scan the entire root or Android/ folder!)
        val searchFolders = when (category) {
            FileCategory.IMAGE -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            )
            FileCategory.VIDEO -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(context.filesDir, "MiExplorer/Videos"),
                File(context.filesDir, "MiExplorer")
            )
            FileCategory.AUDIO -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(context.filesDir, "MiExplorer/Music"),
                File(context.filesDir, "MiExplorer")
            )
            FileCategory.DOCUMENT -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(context.filesDir, "MiExplorer")
            )
            FileCategory.APK -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Downloads"),
                File(Environment.getExternalStorageDirectory(), "Bluetooth"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Documents"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents"),
                File(Environment.getExternalStorageDirectory(), "ShareMe"),
                File(Environment.getExternalStorageDirectory(), "Apks"),
                File(Environment.getExternalStorageDirectory(), "Apps"),
                File(Environment.getExternalStorageDirectory(), "ADM"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MiExplorer/APKs"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MiExplorer/Backup"),
                File(context.filesDir, "MiExplorer/APKs"),
                File(context.filesDir, "MiExplorer/Backup"),
                Environment.getExternalStorageDirectory()
            )
            else -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(context.filesDir, "MiExplorer")
            )
        }.filter { it.exists() && it.canRead() }

        for (dir in searchFolders) {
            scanCategoryFast(dir, category, list, seenPaths, maxDepth = 2)
        }

        list.sortedByDescending { it.lastModified }
    }

    private fun scanCategoryFast(
        dir: File,
        category: FileCategory,
        results: MutableList<FileItem>,
        seenPaths: MutableSet<String>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth || results.size >= 250) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (f.name.startsWith(".") || f.name.equals("Android", ignoreCase = true)) continue
            if (f.isDirectory) {
                scanCategoryFast(f, category, results, seenPaths, maxDepth, currentDepth + 1)
            } else if (seenPaths.add(f.absolutePath)) {
                val item = FileItem(f)
                if (item.category == category) {
                    results.add(item)
                }
            }
        }
    }

    suspend fun scanForClean(): CleanScanResult = withContext(Dispatchers.IO) {
        val targetRoots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            context.filesDir,
            context.getExternalFilesDir(null)
        ).filter { it.exists() && it.canRead() }.distinct()

        val junk = mutableListOf<FileItem>()
        val large = mutableListOf<FileItem>()
        val apks = mutableListOf<FileItem>()
        val emptyFolders = mutableListOf<FileItem>()

        for (root in targetRoots) {
            scanCleanRecursively(root, junk, large, apks, emptyFolders, depth = 0, maxDepth = 3)
        }

        CleanScanResult(
            junkFiles = junk,
            largeFiles = large.sortedByDescending { it.size },
            apkFiles = apks.sortedByDescending { it.size },
            emptyFolders = emptyFolders
        )
    }

    private fun scanCleanRecursively(
        dir: File,
        junk: MutableList<FileItem>,
        large: MutableList<FileItem>,
        apks: MutableList<FileItem>,
        emptyFolders: MutableList<FileItem>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return
        if (dir.name.startsWith(".") || dir.name.equals("Android", ignoreCase = true)) return
        val files = dir.listFiles() ?: return
        if (files.isEmpty() && dir != rootStorageDirectory) {
            emptyFolders.add(FileItem(dir))
            return
        }
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanCleanRecursively(f, junk, large, apks, emptyFolders, depth + 1, maxDepth)
            } else {
                val item = FileItem(f)
                val ext = item.extension
                if (ext in listOf("tmp", "temp", "log", "thumb", "bak") || f.name.contains("cache", ignoreCase = true)) {
                    junk.add(item)
                }
                if (item.size > 15L * 1024 * 1024) { // > 15MB
                    large.add(item)
                }
                if (ext == "apk") {
                    apks.add(item)
                }
            }
        }
    }

    suspend fun getStorageSpace(): StorageSpace = withContext(Dispatchers.IO) {
        try {
            val path = rootStorageDirectory.path
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            StorageSpace(
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                usedBytes = usedBytes
            )
        } catch (e: Exception) {
            val total = 64L * 1024 * 1024 * 1024
            val used = 26L * 1024 * 1024 * 1024
            StorageSpace(totalBytes = total, freeBytes = total - used, usedBytes = used)
        }
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = name.trim().replace("/", "")
            if (sanitized.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            val target = File(parent, sanitized)
            if (target.exists()) return@withContext Result.failure(IllegalStateException("A folder with this name already exists"))
            if (target.mkdirs()) {
                invalidateFolderSize(parent)
                Result.success(target)
            } else Result.failure(Exception("Failed to create folder"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTextFile(parent: File, name: String, initialContent: String = ""): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = name.trim().replace("/", "")
            if (sanitized.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            val target = File(parent, sanitized)
            if (target.exists()) return@withContext Result.failure(IllegalStateException("A file with this name already exists"))
            target.writeText(initialContent)
            invalidateFolderSize(parent)
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(item: FileItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = if (item.isDirectory) item.file.deleteRecursively() else item.file.delete()
            invalidateFolderSize(item.file.parentFile)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(item: FileItem, newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = newName.trim().replace("/", "")
            if (sanitized.isEmpty()) return@withContext Result.failure(IllegalArgumentException("New name cannot be empty"))
            val newFile = File(item.file.parentFile, sanitized)
            if (newFile.exists()) return@withContext Result.failure(IllegalStateException("Target already exists"))
            val success = item.file.renameTo(newFile)
            invalidateFolderSize(item.file.parentFile)
            if (success) Result.success(newFile) else Result.failure(Exception("Rename failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copy(sources: List<FileItem>, destinationDir: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            for (source in sources) {
                val dest = File(destinationDir, source.name)
                if (source.isDirectory) {
                    source.file.copyRecursively(dest, overwrite = true)
                } else {
                    source.file.copyTo(dest, overwrite = true)
                }
                count++
            }
            invalidateFolderSize(destinationDir)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun move(sources: List<FileItem>, destinationDir: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            for (source in sources) {
                val dest = File(destinationDir, source.name)
                val moved = source.file.renameTo(dest)
                if (!moved) {
                    if (source.isDirectory) {
                        source.file.copyRecursively(dest, overwrite = true)
                        source.file.deleteRecursively()
                    } else {
                        source.file.copyTo(dest, overwrite = true)
                        source.file.delete()
                    }
                }
                invalidateFolderSize(source.file.parentFile)
                count++
            }
            invalidateFolderSize(destinationDir)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readText(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(file.readText())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeText(file: File, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            file.writeText(content)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun zip(items: List<FileItem>, zipOutputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            ZipOutputStream(FileOutputStream(zipOutputFile)).use { zos ->
                for (item in items) {
                    addToZip("", item.file, zos)
                }
            }
            Result.success(zipOutputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addToZip(basePath: String, file: File, zos: ZipOutputStream) {
        val entryName = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryName/"))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addToZip(entryName, child, zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    suspend fun unzip(zipFile: File, outputDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val destFile = File(outputDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Result.success(outputDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
