package com.mucephei.app.ui.screens.oasis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mucephei.app.theme.HandwritingTypography
import kotlinx.coroutines.launch

@Composable
fun OasisBottomBar(
    addressInputState: TextFieldValue,
    onAddressValueChange: (TextFieldValue) -> Unit,
    historyList: List<HistoryItem> = emptyList(),
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
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val query = addressInputState.text.trim()
    val autocompleteSuggestions = remember(query, historyList) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            historyList
                .distinctBy { it.url }
                .filter { item ->
                    item.url.contains(query, ignoreCase = true) ||
                    item.title.contains(query, ignoreCase = true)
                }
                .take(5)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isFocused && autocompleteSuggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        autocompleteSuggestions.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isFocused = false
                                        onAddressValueChange(TextFieldValue(text = item.url, selection = TextRange(item.url.length)))
                                        onNavigate(item.url)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (item.title.isNotBlank()) {
                                        Text(
                                            text = item.title,
                                            style = HandwritingTypography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = item.url,
                                        style = HandwritingTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onAddressValueChange(TextFieldValue(text = item.url, selection = TextRange(item.url.length)))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NorthWest,
                                        contentDescription = "Fill Address",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
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
                /* Back Button
                OasisActionButton(
                    onClick = onBackClick,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                ) */

                /* Forward Button
                OasisActionButton(
                    onClick = onForwardClick,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                )*/

                // Address Input Field
                val isUrl = isUrlInput(addressInputState.text)
                OutlinedTextField(
                    value = addressInputState,
                    onValueChange = onAddressValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
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
                        onGo = {
                            isFocused = false
                            onNavigate(addressInputState.text)
                        },
                        onSearch = {
                            isFocused = false
                            onNavigate(addressInputState.text)
                        },
                        onDone = {
                            isFocused = false
                            onNavigate(addressInputState.text)
                        }
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
                                isFocused = false
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

                // New tab button
                OasisActionButton(
                    onClick = onNewTab,
                    icon = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                )

                // Tabs Switcher Button
                OasisActionButton(
                    onClick = onTabsClick,
                    icon = Icons.Default.Tab,
                    contentDescription = "Tabs ($tabsCount)",
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                )

                // Menu / More Options Dropdown
                Box(modifier = Modifier.fillMaxHeight()) {
                    OasisActionButton(
                        onClick = { showMenu = true },
                        icon = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
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
}

@Composable
private fun OasisActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
