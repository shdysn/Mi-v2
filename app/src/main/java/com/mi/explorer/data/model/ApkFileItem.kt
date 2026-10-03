package com.mi.explorer.data.model

import android.graphics.drawable.Drawable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApkFileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val size: Long = file.length(),
    val appName: String,
    val packageName: String,
    val versionName: String,
    val isInstalled: Boolean,
    val icon: Drawable? = null,
    val lastModified: Long = file.lastModified()
) {
    val formattedSize: String get() = FileItem.formatBytes(size)
    val formattedDate: String get() {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(lastModified))
    }
}

enum class ApkTab {
    APK_FILES,
    INSTALLED_APPS
}
