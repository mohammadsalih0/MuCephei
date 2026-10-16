package com.mucephei.app.ui.screens.oasis

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mucephei.app.theme.HandwritingTypography
import com.mucephei.app.ui.components.ThemedActionButton
import kotlinx.coroutines.launch

@Composable
fun OasisBottomBar(
    addressInputState: TextFieldValue,
    onAddressValueChange: (TextFieldValue) -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabsCount: Int,
    pagerState: PagerState,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onTabsClick: () -> Unit,
    onSpeedDialHomeClick: () -> Unit,
    onAddSpeedDialClick: () -> Unit = {},
    onBookmarkPageClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadManagerClick: () -> Unit,
    onBrowserSettingsClick: () -> Unit,
    onToolbarSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (!pagerState.isScrollInProgress) {
                                change.consume()
                                scope.launch {
                                    val targetPage = if (dragAmount < -15f) {
                                        0
                                    } else if (dragAmount > 15f) {
                                        1
                                    } else {
                                        pagerState.currentPage
                                    }
                                    if (targetPage != pagerState.currentPage) {
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Back Button
            ThemedActionButton(
                onClick = onBackClick,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )

            // Forward Button
            ThemedActionButton(
                onClick = onForwardClick,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Forward",
                tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )

            // Address Input Field
            val isUrl = isUrlInput(addressInputState.text)
            OutlinedTextField(
                value = addressInputState,
                onValueChange = onAddressValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && addressInputState.text.isNotEmpty()) {
                            onAddressValueChange(
                                addressInputState.copy(
                                    selection = TextRange(0, addressInputState.text.length)
                                )
                            )
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                textStyle = HandwritingTypography.bodySmall,
                maxLines = 1,
                placeholder = { Text("Search or URL") },
                keyboardOptions = KeyboardOptions(
                    imeAction = if (isUrl) ImeAction.Go else ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onGo = { onNavigate(addressInputState.text) },
                    onSearch = { onNavigate(addressInputState.text) },
                    onDone = { onNavigate(addressInputState.text) }
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (addressInputState.text.isNotEmpty()) {
                            IconButton(onClick = {
                                onAddressValueChange(TextFieldValue(""))
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Address", modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = {
                            onNavigate(addressInputState.text)
                        }) {
                            Icon(
                                imageVector = if (isUrl) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Search,
                                contentDescription = if (isUrl) "Go" else "Search"
                            )
                        }
                    }
                }
            )

            // Tabs Switcher Button
            ThemedActionButton(
                onClick = onTabsClick,
                icon = Icons.Default.Tab,
                contentDescription = "Tabs ($tabsCount)",
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )

            // Menu / More Options Dropdown
            Box {
                ThemedActionButton(
                    onClick = { showMenu = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Speed Dial Home") },
                        onClick = {
                            showMenu = false
                            onSpeedDialHomeClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Speed Dial") },
                        onClick = {
                            showMenu = false
                            onAddSpeedDialClick()
                        },
                        leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmark Page") },
                        onClick = {
                            showMenu = false
                            onBookmarkPageClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        onClick = { showMenu = false; onBookmarksClick() },
                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        onClick = { showMenu = false; onHistoryClick() },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Download Manager") },
                        onClick = { showMenu = false; onDownloadManagerClick() },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Browser & Download Settings") },
                        onClick = { showMenu = false; onBrowserSettingsClick() },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Toolbar Settings & Behavior") },
                        onClick = { showMenu = false; onToolbarSettingsClick() },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                    )
                }
            }
        }
    }
}
