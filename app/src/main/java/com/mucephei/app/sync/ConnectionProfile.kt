package com.mucephei.app.sync

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.UUID

data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String,
    val hostIp: String,
    val roomCode: String,
    val ovpnProfileName: String? = null,
    val ovpnConfigContent: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class ConnectionManager(private val context: Context) {
    private val gson = Gson()
    val connectionsDirectory: File by lazy {
        File(context.filesDir, "mucephi_connections").apply { if (!exists()) mkdirs() }
    }

    fun getRecentConnections(): List<ConnectionProfile> {
        val list = mutableListOf<ConnectionProfile>()
        try {
            if (connectionsDirectory.exists() && connectionsDirectory.isDirectory) {
                connectionsDirectory.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".muconn")) {
                        val json = file.readText()
                        val profile = gson.fromJson(json, ConnectionProfile::class.java)
                        if (profile != null) {
                            list.add(profile)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun saveProfile(profile: ConnectionProfile): File {
        val safeName = profile.profileName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val file = File(connectionsDirectory, "${safeName}_${profile.roomCode}.muconn")
        file.writeText(gson.toJson(profile))
        return file
    }

    fun parseProfileFromFile(file: File): ConnectionProfile? {
        return try {
            val json = file.readText()
            gson.fromJson(json, ConnectionProfile::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
