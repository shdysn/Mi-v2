package com.mi.explorer.data.model

import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileCategory {
    FOLDER,
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    ARCHIVE,
    APK,
    CODE,
    UNKNOWN
}

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val isHidden: Boolean = file.isHidden || file.name.startsWith("."),
    val extension: String = if (file.isDirectory) "" else file.extension.lowercase(Locale.ROOT),
    val itemCount: Int = 0,
    val folderSize: Long? = null
) {
    val effectiveSize: Long
        get() = if (isDirectory) (folderSize ?: size) else size

    val isHuge: Boolean get() = effectiveSize >= 500L * 1024 * 1024
    val isVeryLarge: Boolean get() = effectiveSize >= 100L * 1024 * 1024
    val isLarge: Boolean get() = effectiveSize >= 25L * 1024 * 1024
    val isBig: Boolean get() = effectiveSize >= 10L * 1024 * 1024

    val sizeBadgeText: String?
        get() = when {
            isHuge -> "🔥 HUGE"
            isVeryLarge -> "⚡ BIG"
            isLarge -> "LARGE"
            isBig -> ">10MB"
            else -> null
        }
    val category: FileCategory
        get() = when {
            isDirectory -> FileCategory.FOLDER
            extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") -> FileCategory.IMAGE
            extension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "opus", "amr", "m4b", "mid", "midi") -> FileCategory.AUDIO
            extension in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "mpg", "mpeg") -> FileCategory.VIDEO
            extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub") -> FileCategory.DOCUMENT
            extension in listOf("zip", "rar", "7z", "tar", "gz") -> FileCategory.ARCHIVE
            extension in listOf("apk", "xapk", "apks") -> FileCategory.APK
            extension in listOf("kt", "java", "py", "js", "html", "css", "json", "xml", "c", "cpp", "sh", "md") -> FileCategory.CODE
            else -> FileCategory.UNKNOWN
        }

    val mimeType: String
        get() {
            if (isDirectory) return "resource/folder"
            val map = MimeTypeMap.getSingleton()
            val extMime = map.getMimeTypeFromExtension(extension)
            if (!extMime.isNullOrEmpty()) return extMime

            return when (extension) {
                "apk", "xapk", "apks" -> "application/vnd.android.package-archive"
                "json" -> "application/json"
                "md" -> "text/markdown"
                "html", "htm" -> "text/html"
                "xml" -> "text/xml"
                "csv" -> "text/csv"
                "kt", "java", "py", "js", "css", "c", "cpp", "sh", "log", "txt" -> "text/plain"
                "pdf" -> "application/pdf"
                "epub" -> "application/epub+zip"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "7z" -> "application/x-7z-compressed"
                "tar" -> "application/x-tar"
                "gz" -> "application/gzip"
                "mp3" -> "audio/mpeg"
                "m4a", "aac" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "*/*"
            }
        }

    val friendlyTypeLabel: String
        get() = when (category) {
            FileCategory.FOLDER -> "Folder"
            FileCategory.IMAGE -> "${extension.uppercase(Locale.ROOT)} Image"
            FileCategory.AUDIO -> "${extension.uppercase(Locale.ROOT)} Audio"
            FileCategory.VIDEO -> "${extension.uppercase(Locale.ROOT)} Video"
            FileCategory.DOCUMENT -> when (extension) {
                "pdf" -> "PDF Document"
                "doc", "docx" -> "Word Document"
                "xls", "xlsx" -> "Excel Spreadsheet"
                "ppt", "pptx" -> "PowerPoint Presentation"
                "txt" -> "Text File"
                else -> "${extension.uppercase(Locale.ROOT)} Document"
            }
            FileCategory.ARCHIVE -> "Compressed Archive (${extension.uppercase(Locale.ROOT)})"
            FileCategory.APK -> "Android Application (APK)"
            FileCategory.CODE -> "${extension.uppercase(Locale.ROOT)} Source Code"
            FileCategory.UNKNOWN -> if (extension.isNotEmpty()) "${extension.uppercase(Locale.ROOT)} File" else "File"
        }

    val formattedSize: String
        get() = if (isDirectory) {
            val itemStr = "$itemCount item${if (itemCount == 1) "" else "s"}"
            val s = effectiveSize
            if (s > 0L) {
                "${formatBytes(s)} • $itemStr"
            } else if (itemCount > 0) {
                itemStr
            } else {
                "0 B • 0 items"
            }
        } else {
            formatBytes(size)
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val timeGroup: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - lastModified
            val oneDay = 24 * 60 * 60 * 1000L
            return when {
                diff < oneDay -> "Today"
                diff < 2 * oneDay -> "Yesterday"
                diff < 7 * oneDay -> "Earlier this week"
                diff < 30 * oneDay -> "This month"
                else -> "Earlier"
            }
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }
}

data class StorageSpace(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedTotal: String get() = FileItem.formatBytes(totalBytes)
    val formattedUsed: String get() = FileItem.formatBytes(usedBytes)
    val formattedFree: String get() = FileItem.formatBytes(freeBytes)
}

enum class SortType {
    NAME_ASC,
    NAME_DESC,
    DATE_NEWEST,
    DATE_OLDEST,
    SIZE_LARGEST,
    SIZE_SMALLEST,
    TYPE
}

enum class ViewMode {
    LIST,
    GRID
}

data class ClipboardState(
    val items: List<FileItem>,
    val isCut: Boolean = false
)
