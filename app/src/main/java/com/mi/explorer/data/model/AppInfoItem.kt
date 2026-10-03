package com.mi.explorer.data.model

import android.graphics.drawable.Drawable

data class AppInfoItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val apkSize: Long,
    val icon: Drawable? = null
) {
    val formattedSize: String
        get() = FileItem.formatBytes(apkSize)
}
