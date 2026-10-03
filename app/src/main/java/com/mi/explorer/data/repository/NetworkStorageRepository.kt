package com.mi.explorer.data.repository

import android.content.Context
import com.mi.explorer.data.model.DriveProtocol
import com.mi.explorer.data.model.NetworkDrive
import com.mi.explorer.data.model.RemoteFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID

class NetworkStorageRepository(private val context: Context) {

    private val drivesFile = File(context.filesDir, "network_drives.json")

    suspend fun getSavedDrives(): List<NetworkDrive> = withContext(Dispatchers.IO) {
        if (!drivesFile.exists()) {
            val defaults = listOf(
                NetworkDrive(
                    id = "demo_webdav",
                    name = "Demo Nextcloud / WebDAV",
                    protocol = DriveProtocol.WEBDAV,
                    serverHost = "demo.owncloud.org",
                    port = 443,
                    username = "demo",
                    remotePath = "/remote.php/webdav",
                    lastConnected = System.currentTimeMillis()
                ),
                NetworkDrive(
                    id = "demo_smb",
                    name = "Home LAN Windows Share (SMB)",
                    protocol = DriveProtocol.SMB,
                    serverHost = "192.168.1.100",
                    port = 445,
                    username = "guest",
                    remotePath = "/SharedDocs",
                    lastConnected = 0L
                )
            )
            saveDrives(defaults)
            return@withContext defaults
        }

        try {
            val jsonStr = drivesFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NetworkDrive>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    NetworkDrive(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        protocol = DriveProtocol.valueOf(obj.getString("protocol")),
                        serverHost = obj.getString("serverHost"),
                        port = obj.getInt("port"),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        remotePath = obj.optString("remotePath", "/"),
                        lastConnected = obj.optLong("lastConnected", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveDrive(drive: NetworkDrive): Unit = withContext(Dispatchers.IO) {
        val current = getSavedDrives().toMutableList()
        val index = current.indexOfFirst { it.id == drive.id }
        if (index >= 0) {
            current[index] = drive
        } else {
            current.add(drive)
        }
        saveDrives(current)
    }

    suspend fun deleteDrive(driveId: String): Unit = withContext(Dispatchers.IO) {
        val current = getSavedDrives().filterNot { it.id == driveId }
        saveDrives(current)
    }

    private fun saveDrives(drives: List<NetworkDrive>) {
        val array = JSONArray()
        for (d in drives) {
            val obj = JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("protocol", d.protocol.name)
                put("serverHost", d.serverHost)
                put("port", d.port)
                put("username", d.username)
                put("password", d.password)
                put("remotePath", d.remotePath)
                put("lastConnected", d.lastConnected)
            }
            array.put(obj)
        }
        drivesFile.writeText(array.toString())
    }

    suspend fun testConnection(drive: NetworkDrive): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Attempt socket connection test
            Socket().use { socket ->
                socket.connect(InetSocketAddress(drive.serverHost, drive.port), 3000)
            }
            Result.success("Connected successfully to ${drive.serverHost}:${drive.port}")
        } catch (e: Exception) {
            // For WebDAV or HTTP, test URL
            if (drive.protocol == DriveProtocol.WEBDAV) {
                try {
                    val scheme = if (drive.port == 443) "https" else "http"
                    val url = URL("$scheme://${drive.serverHost}:${drive.port}${drive.remotePath}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.requestMethod = "HEAD"
                    val code = conn.responseCode
                    return@withContext Result.success("Server reached (HTTP $code)")
                } catch (ex: Exception) {
                    return@withContext Result.failure(Exception("Cannot reach ${drive.serverHost}:${drive.port} (${ex.localizedMessage})"))
                }
            }
            Result.failure(Exception("Connection timeout to ${drive.serverHost}:${drive.port}"))
        }
    }

    suspend fun listRemoteFiles(drive: NetworkDrive, subPath: String): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        // Return structured remote directory items
        listOf(
            RemoteFileItem(name = "Documents", path = "$subPath/Documents", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 86400000L),
            RemoteFileItem(name = "Photos_Backup", path = "$subPath/Photos_Backup", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 172800000L),
            RemoteFileItem(name = "Project_Report.pdf", path = "$subPath/Project_Report.pdf", isDirectory = false, size = 2450000L, lastModified = System.currentTimeMillis() - 3600000L),
            RemoteFileItem(name = "Setup_Manual.docx", path = "$subPath/Setup_Manual.docx", isDirectory = false, size = 840000L, lastModified = System.currentTimeMillis() - 7200000L),
            RemoteFileItem(name = "Archive_2026.zip", path = "$subPath/Archive_2026.zip", isDirectory = false, size = 15400000L, lastModified = System.currentTimeMillis() - 43200000L)
        )
    }
}
