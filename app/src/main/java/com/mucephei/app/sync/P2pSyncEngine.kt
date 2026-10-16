package com.mucephei.app.sync

import android.util.Base64
import androidx.compose.ui.geometry.Offset
import com.google.gson.Gson
import com.mucephei.app.data.db.NoteDao
import com.mucephei.app.data.db.NoteEntity
import com.mucephei.app.data.model.Note
import com.mucephei.app.data.model.NoteDelta
import com.mucephei.app.data.model.RemoteCursorPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

enum class P2pConnectionStatus { DISCONNECTED, PAIRING, CONNECTED_COOP_ROOM, SYNCING, ERROR }

data class P2pSyncState(
    val status: P2pConnectionStatus = P2pConnectionStatus.DISCONNECTED,
    val roomCode: String = "",
    val hostIp: String = "127.0.0.1",
    val connectedPeerId: String? = null,
    val remoteCursor: RemoteCursorPosition? = null,
    val isServerHost: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val errorMessage: String? = null
)

data class DrawingStrokePayload(
    val noteId: String,
    val peerId: String,
    val points: List<Map<String, Float>>,
    val color: Int
)

data class MediaBlobPayload(
    val fileName: String,
    val base64Data: String
)

/**
 * Coroutine-based Local-First P2P Sync Engine:
 * - Direct TCP Socket / Mesh co-op room management for 2 peers
 * - Automatic Wi-Fi / Hotspot IPv4 detection
 * - Base64 line framing for socket stream safety with multi-line note content
 * - QR / 6-digit room code validation handshake (supports custom room codes)
 * - Real-time state delta patch broadcasting & Media file blob transfer
 * - Real-time drawing strokes & clear canvas synchronization
 * - LWW-Element-Set CRDT reconciliation on Room DB (Private notes excluded)
 */
class P2pSyncEngine(
    private val noteDao: NoteDao,
    private val mediaDirectory: File,
    private val deviceId: String = UUID.randomUUID().toString()
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _syncState = MutableStateFlow(P2pSyncState())
    val syncState: StateFlow<P2pSyncState> = _syncState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var socketWriter: PrintWriter? = null
    private var socketReader: BufferedReader? = null

    // Callbacks for UI updates (e.g., incoming drawing strokes or canvas clear)
    var onDrawingStrokeReceived: ((String, List<Offset>, Int) -> Unit)? = null
    var onCanvasCleared: ((String) -> Unit)? = null

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    /**
     * Host a 2-person co-op room with a custom or generated 6-digit pairing key.
     */
    fun startHostingRoom(port: Int = 8888, customRoomCode: String? = null): String {
        val roomCode = if (!customRoomCode.isNullOrBlank()) customRoomCode.trim() else (100000..999999).random().toString()
        val localIp = getLocalIpAddress()

        _syncState.value = P2pSyncState(
            status = P2pConnectionStatus.PAIRING,
            roomCode = roomCode,
            hostIp = localIp,
            isServerHost = true
        )

        scope.launch {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(port)
                val socket = serverSocket!!.accept()
                clientSocket = socket
                setupSocketStreams(socket)

                listenIncomingP2pMessages(socket)

            } catch (e: Exception) {
                e.printStackTrace()
                _syncState.value = _syncState.value.copy(
                    status = P2pConnectionStatus.ERROR,
                    errorMessage = "Host error: ${e.localizedMessage}"
                )
            }
        }
        return roomCode
    }

    /**
     * Join an active 2-person co-op room via host IP & room code.
     */
    fun joinRoom(hostIp: String, port: Int = 8888, roomCode: String) {
        _syncState.value = P2pSyncState(
            status = P2pConnectionStatus.PAIRING,
            roomCode = roomCode,
            hostIp = hostIp,
            isServerHost = false
        )

        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(hostIp, port), 6000)
                clientSocket = socket
                setupSocketStreams(socket)

                val pairReqMap = mapOf(
                    "roomCode" to roomCode.trim(),
                    "deviceId" to deviceId
                )
                sendPayload("PAIR_REQUEST", gson.toJson(pairReqMap))

                listenIncomingP2pMessages(socket)

            } catch (e: Exception) {
                e.printStackTrace()
                _syncState.value = _syncState.value.copy(
                    status = P2pConnectionStatus.ERROR,
                    errorMessage = "Connection failed: ${e.localizedMessage ?: "Unreachable host IP"}"
                )
            }
        }
    }

    /**
     * Broadcast local note mutation delta to connected peer (Private notes are ignored)
     */
    fun broadcastNoteDelta(updatedNote: Note) {
        if (_syncState.value.status != P2pConnectionStatus.CONNECTED_COOP_ROOM) return
        if (updatedNote.isPrivate) return // Private notes are never synced over P2P

        scope.launch {
            // First send media file blobs if any attached files exist
            updatedNote.media.forEach { mediaItem ->
                try {
                    val file = File(mediaItem.uri)
                    if (file.exists() && file.isFile) {
                        val bytes = file.readBytes()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
                        val blob = MediaBlobPayload(file.name, b64)
                        sendPayload("MEDIA_BLOB", gson.toJson(blob))
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }

            val delta = NoteDelta(
                noteId = updatedNote.id,
                updatedNote = updatedNote,
                senderDeviceId = deviceId
            )
            sendPayload("DELTA_PATCH", gson.toJson(delta))
        }
    }

    fun broadcastCursorPosition(noteId: String, x: Float, y: Float, isDrawing: Boolean) {
        if (_syncState.value.status != P2pConnectionStatus.CONNECTED_COOP_ROOM) return
        scope.launch {
            val cursor = RemoteCursorPosition(
                peerId = deviceId,
                noteId = noteId,
                x = x,
                y = y,
                isDrawing = isDrawing
            )
            sendPayload("CURSOR_MOVE", gson.toJson(cursor))
        }
    }

    fun broadcastDrawingStroke(noteId: String, points: List<Offset>, colorArgb: Int) {
        if (_syncState.value.status != P2pConnectionStatus.CONNECTED_COOP_ROOM) return
        scope.launch {
            val stroke = DrawingStrokePayload(
                noteId = noteId,
                peerId = deviceId,
                points = points.map { mapOf("x" to it.x, "y" to it.y) },
                color = colorArgb
            )
            sendPayload("DRAWING_STROKE", gson.toJson(stroke))
        }
    }

    fun broadcastClearCanvas(noteId: String) {
        if (_syncState.value.status != P2pConnectionStatus.CONNECTED_COOP_ROOM) return
        scope.launch {
            val clearMap = mapOf("noteId" to noteId, "peerId" to deviceId)
            sendPayload("CLEAR_CANVAS", gson.toJson(clearMap))
        }
    }

    private fun sendPayload(type: String, payloadJson: String) {
        try {
            val msgMap = mapOf(
                "type" to type,
                "payload" to payloadJson
            )
            val rawJson = gson.toJson(msgMap)
            val base64Line = Base64.encodeToString(
                rawJson.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE
            )
            socketWriter?.println(base64Line)
            socketWriter?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun performTwoWaySyncHandshake() {
        _syncState.value = _syncState.value.copy(status = P2pConnectionStatus.SYNCING)

        val localEntities = noteDao.getNotesModifiedSince(sinceTimestamp = 0L)
        // Filter out private notes
        val localNotes = localEntities.map { it.toNote() }.filter { !it.isPrivate }

        // Send media blobs for all local notes first
        localNotes.forEach { note ->
            note.media.forEach { mediaItem ->
                try {
                    val file = File(mediaItem.uri)
                    if (file.exists() && file.isFile) {
                        val bytes = file.readBytes()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
                        val blob = MediaBlobPayload(file.name, b64)
                        sendPayload("MEDIA_BLOB", gson.toJson(blob))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        sendPayload("SYNC_HANDSHAKE", gson.toJson(localNotes))
    }

    private fun listenIncomingP2pMessages(socket: Socket) {
        try {
            var line: String?
            while (socketReader?.readLine().also { line = it } != null) {
                line?.let { rawLine ->
                    if (rawLine.isNotBlank()) {
                        try {
                            val decodedBytes = Base64.decode(
                                rawLine,
                                Base64.NO_WRAP or Base64.URL_SAFE
                            )
                            val jsonMessage = String(decodedBytes, Charsets.UTF_8)
                            processReceivedMessage(jsonMessage)
                        } catch (e: Exception) {
                            processReceivedMessage(rawLine)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            disconnect()
        }
    }

    private fun processReceivedMessage(jsonMessage: String) {
        try {
            val msgMap = gson.fromJson(jsonMessage, Map::class.java) as? Map<*, *> ?: return
            val type = msgMap["type"] as? String ?: return
            val payloadStr = msgMap["payload"] as? String ?: return

            when (type) {
                "PAIR_REQUEST" -> {
                    val reqData = gson.fromJson(payloadStr, Map::class.java) as? Map<*, *>
                    val reqRoomCode = reqData?.get("roomCode") as? String ?: ""
                    val peerDeviceId = reqData?.get("deviceId") as? String

                    if (reqRoomCode == _syncState.value.roomCode) {
                        sendPayload("PAIR_ACCEPT", gson.toJson(mapOf("deviceId" to deviceId)))
                        _syncState.value = _syncState.value.copy(
                            status = P2pConnectionStatus.CONNECTED_COOP_ROOM,
                            connectedPeerId = peerDeviceId
                        )
                        scope.launch {
                            performTwoWaySyncHandshake()
                        }
                    } else {
                        sendPayload("PAIR_REJECT", gson.toJson(mapOf("reason" to "Invalid room code")))
                        disconnect()
                    }
                }
                "PAIR_ACCEPT" -> {
                    val accData = gson.fromJson(payloadStr, Map::class.java) as? Map<*, *>
                    val peerDeviceId = accData?.get("deviceId") as? String
                    _syncState.value = _syncState.value.copy(
                        status = P2pConnectionStatus.CONNECTED_COOP_ROOM,
                        connectedPeerId = peerDeviceId
                    )
                    scope.launch {
                        performTwoWaySyncHandshake()
                    }
                }
                "PAIR_REJECT" -> {
                    _syncState.value = _syncState.value.copy(
                        status = P2pConnectionStatus.ERROR,
                        errorMessage = "Invalid 6-digit room code entered."
                    )
                    disconnect()
                }
                "MEDIA_BLOB" -> {
                    val blob = gson.fromJson(payloadStr, MediaBlobPayload::class.java)
                    if (blob != null && blob.fileName.isNotBlank()) {
                        try {
                            if (!mediaDirectory.exists()) mediaDirectory.mkdirs()
                            val targetFile = File(mediaDirectory, blob.fileName)
                            val bytes = Base64.decode(blob.base64Data, Base64.NO_WRAP or Base64.URL_SAFE)
                            FileOutputStream(targetFile).use { fos ->
                                fos.write(bytes)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "DELTA_PATCH" -> {
                    val delta = gson.fromJson(payloadStr, NoteDelta::class.java)
                    if (delta.senderDeviceId != deviceId && !delta.updatedNote.isPrivate) {
                        scope.launch {
                            // Remap media URIs to local receiver mediaDirectory paths if files exist locally
                            val fixedMedia = delta.updatedNote.media.map { mediaItem ->
                                val fileName = File(mediaItem.uri).name
                                val localFile = File(mediaDirectory, fileName)
                                if (localFile.exists()) {
                                    mediaItem.copy(uri = localFile.absolutePath)
                                } else {
                                    mediaItem
                                }
                            }
                            val updatedNote = delta.updatedNote.copy(media = fixedMedia)
                            val entity = NoteEntity.fromNote(updatedNote)
                            noteDao.reconcileLwwDelta(entity)
                        }
                    }
                }
                "CURSOR_MOVE" -> {
                    val cursor = gson.fromJson(payloadStr, RemoteCursorPosition::class.java)
                    if (cursor.peerId != deviceId) {
                        _syncState.value = _syncState.value.copy(remoteCursor = cursor)
                    }
                }
                "DRAWING_STROKE" -> {
                    val stroke = gson.fromJson(payloadStr, DrawingStrokePayload::class.java)
                    if (stroke != null && stroke.peerId != deviceId) {
                        val pts = stroke.points.map { Offset(it["x"] ?: 0f, it["y"] ?: 0f) }
                        onDrawingStrokeReceived?.invoke(stroke.noteId, pts, stroke.color)
                    }
                }
                "CLEAR_CANVAS" -> {
                    val clearMap = gson.fromJson(payloadStr, Map::class.java) as? Map<*, *>
                    val peerId = clearMap?.get("peerId") as? String
                    val noteId = clearMap?.get("noteId") as? String
                    if (peerId != deviceId && noteId != null) {
                        onCanvasCleared?.invoke(noteId)
                    }
                }
                "SYNC_HANDSHAKE" -> {
                    val remoteNotes = gson.fromJson(payloadStr, Array<Note>::class.java).toList()
                    scope.launch {
                        remoteNotes.filter { !it.isPrivate }.forEach { note ->
                            // Remap media URIs to local receiver mediaDirectory paths
                            val fixedMedia = note.media.map { mediaItem ->
                                val fileName = File(mediaItem.uri).name
                                val localFile = File(mediaDirectory, fileName)
                                if (localFile.exists()) {
                                    mediaItem.copy(uri = localFile.absolutePath)
                                } else {
                                    mediaItem
                                }
                            }
                            val fixedNote = note.copy(media = fixedMedia)
                            noteDao.reconcileLwwDelta(NoteEntity.fromNote(fixedNote))
                        }
                        _syncState.value = _syncState.value.copy(
                            status = P2pConnectionStatus.CONNECTED_COOP_ROOM,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupSocketStreams(socket: Socket) {
        socketWriter = PrintWriter(socket.getOutputStream(), true)
        socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
    }

    fun disconnect() {
        try {
            socketWriter?.close()
            socketReader?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _syncState.value = P2pSyncState(status = P2pConnectionStatus.DISCONNECTED)
    }
}
