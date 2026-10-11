package com.mucephei.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mucephei.app.sync.OpenVpnEngine
import com.mucephei.app.sync.P2pConnectionStatus
import com.mucephei.app.sync.P2pSyncEngine
import com.mucephei.app.sync.VpnProfile
import com.mucephei.app.sync.VpnStatus
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.theme.ThemePreferences
import com.mucephei.app.theme.sketchyBorder

/**
 * Re-organized, clean, and intuitive P2P & OpenVPN Sync Dialog:
 * - Section 1: Local P2P Co-Op Mesh (Host / Join with 1-tap quick connect & remembered data)
 * - Section 2: OpenVPN Tunnel & Saved Profiles (.ovpn)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2pSyncDialog(
    p2pEngine: P2pSyncEngine,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val themePrefs = remember { ThemePreferences(context) }
    val openVpnEngine = remember { OpenVpnEngine(context) }
    val vpnState by openVpnEngine.vpnState.collectAsState()

    val syncState by p2pEngine.syncState.collectAsState()
    val detectedLocalIp = remember { p2pEngine.getLocalIpAddress() }

    var hostIpInput by remember { mutableStateOf(themePrefs.lastHostIp.ifBlank { detectedLocalIp }) }
    var roomCodeInput by remember { mutableStateOf(themePrefs.lastRoomCode) }
    var customHostRoomCode by remember { mutableStateOf(themePrefs.customRoomCode) }

    val savedProfiles = remember { openVpnEngine.getSavedProfiles() }
    var selectedVpnProfile by remember { mutableStateOf<VpnProfile?>(savedProfiles.firstOrNull()) }
    var showVpnDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(vpnState.assignedTunnelIp) {
        vpnState.assignedTunnelIp?.let { tunnelIp ->
            if (tunnelIp.isNotBlank()) {
                hostIpInput = tunnelIp
                themePrefs.lastHostIp = tunnelIp
            }
        }
    }

    // Launcher for importing .ovpn files
    val ovpnPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    val fileName = it.lastPathSegment ?: "custom.ovpn"
                    val imported = openVpnEngine.importOvpnConfig(fileName, content)
                    selectedVpnProfile = imported
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val primaryButtonContentColor = if (MaterialTheme.colorScheme.primary.luminance() > 0.5f) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .sketchyBorder(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    strokeWidth = 1.dp,
                    cornerRadius = 18.dp
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "P2P Mesh & Remote Sync",
                        style = HandwritingTypography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Connection Status Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Local Mesh Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                when (syncState.status) {
                                    P2pConnectionStatus.CONNECTED_COOP_ROOM -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    P2pConnectionStatus.SYNCING -> Color(0xFFD97706).copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = "Mesh", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mesh: ${syncState.status.name}",
                            style = HandwritingTypography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // OpenVPN Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                when (vpnState.status) {
                                    VpnStatus.CONNECTED -> Color(0xFF1D4ED8).copy(alpha = 0.2f)
                                    VpnStatus.CONNECTING -> Color(0xFFD97706).copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VpnKey, contentDescription = "VPN", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "VPN: ${vpnState.status.name}",
                            style = HandwritingTypography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (syncState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = syncState.errorMessage!!,
                        style = HandwritingTypography.labelMedium,
                        color = Color.Red
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 1: Local P2P Co-Op Mesh Pairing (Clean & Easy 1-Tap)
                if (syncState.status == P2pConnectionStatus.DISCONNECTED || syncState.status == P2pConnectionStatus.ERROR) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LOCAL MESH CO-OP:",
                            style = HandwritingTypography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (themePrefs.lastHostIp.isNotBlank() && themePrefs.lastRoomCode.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .clickable {
                                        hostIpInput = themePrefs.lastHostIp
                                        roomCodeInput = themePrefs.lastRoomCode
                                        p2pEngine.joinRoom(hostIp = hostIpInput, roomCode = roomCodeInput)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.FlashOn, contentDescription = "Quick", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("1-Tap Connect", style = HandwritingTypography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = hostIpInput,
                        onValueChange = {
                            hostIpInput = it
                            themePrefs.lastHostIp = it
                        },
                        label = { Text("Peer Host IP Address", style = HandwritingTypography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = {
                            roomCodeInput = it
                            themePrefs.lastRoomCode = it
                        },
                        label = { Text("6-Digit Peer Room Code", style = HandwritingTypography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            themePrefs.lastHostIp = hostIpInput
                            themePrefs.lastRoomCode = roomCodeInput
                            p2pEngine.joinRoom(hostIp = hostIpInput, roomCode = roomCodeInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Connect & Pair Peer Journal", style = HandwritingTypography.titleMedium, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customHostRoomCode,
                        onValueChange = {
                            customHostRoomCode = it
                            themePrefs.customRoomCode = it
                        },
                        label = { Text("Custom Host Room Code (Optional)", style = HandwritingTypography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            p2pEngine.startHostingRoom(customRoomCode = customHostRoomCode.ifBlank { null })
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = "Host", tint = primaryButtonContentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Create Co-Op Room", style = HandwritingTypography.titleMedium, color = primaryButtonContentColor)
                    }

                } else if (syncState.roomCode.isNotBlank()) {
                    Text(
                        text = "Host IP: ${syncState.hostIp}",
                        style = HandwritingTypography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your Room Code:",
                        style = HandwritingTypography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = syncState.roomCode,
                        style = HandwritingTypography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { p2pEngine.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disconnect Room", style = HandwritingTypography.titleMedium, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: OpenVPN Remote Sync (Clean & Organized)
                Text(
                    text = "OPENVPN REMOTE TUNNEL:",
                    style = HandwritingTypography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (savedProfiles.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = showVpnDropdown,
                            onExpandedChange = { showVpnDropdown = !showVpnDropdown },
                            modifier = Modifier.weight(1.3f)
                        ) {
                            OutlinedTextField(
                                value = selectedVpnProfile?.profileName ?: "Select Profile",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVpnDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                textStyle = HandwritingTypography.labelMedium,
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = showVpnDropdown,
                                onDismissRequest = { showVpnDropdown = false }
                            ) {
                                savedProfiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.profileName, style = HandwritingTypography.bodyMedium) },
                                        onClick = {
                                            selectedVpnProfile = profile
                                            openVpnEngine.importOvpnConfig(profile.profileName, profile.configContent)
                                            showVpnDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { ovpnPickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = "Import", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Import .ovpn", style = HandwritingTypography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        if (vpnState.status == VpnStatus.CONNECTED) {
                            openVpnEngine.disconnectVpn()
                        } else {
                            openVpnEngine.connectVpn(selectedVpnProfile)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vpnState.status == VpnStatus.CONNECTED) Color(0xFFDC2626) else Color(0xFF2563EB)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (vpnState.status == VpnStatus.CONNECTED) "Disconnect VPN Tunnel" else "Connect VPN Tunnel",
                        style = HandwritingTypography.titleMedium,
                        color = Color.White
                    )
                }

                if (vpnState.assignedTunnelIp != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tunnel Active: ${vpnState.assignedTunnelIp}",
                        style = HandwritingTypography.labelMedium,
                        color = Color(0xFF2563EB)
                    )
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
