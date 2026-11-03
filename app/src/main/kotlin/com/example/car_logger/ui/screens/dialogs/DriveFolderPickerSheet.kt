package com.example.car_logger.ui.screens.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.services.DriveFileItem
import com.example.car_logger.services.GoogleDriveService

data class DriveFolderBreadcrumb(
    val id: String,
    val name: String
)

class DriveFolderPickerNavigationState(
    initialBreadcrumbs: List<DriveFolderBreadcrumb> = listOf(DriveFolderBreadcrumb(id = "root", name = "My Drive"))
) {
    var backStack: List<DriveFolderBreadcrumb> by mutableStateOf(initialBreadcrumbs)
        private set

    val currentFolder: DriveFolderBreadcrumb
        get() = backStack.lastOrNull() ?: DriveFolderBreadcrumb(id = "root", name = "My Drive")

    val isAtRoot: Boolean
        get() = currentFolder.id == "root" || backStack.size <= 1

    val canNavigateBack: Boolean
        get() = backStack.size > 1

    val breadcrumbPath: String
        get() = backStack.joinToString(" > ") { it.name }

    fun openFolder(id: String, name: String) {
        backStack = backStack + DriveFolderBreadcrumb(id = id, name = name)
    }

    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
            return true
        }
        return false
    }

    fun navigateToBreadcrumb(index: Int): Boolean {
        if (index in 0 until backStack.size - 1) {
            backStack = backStack.take(index + 1)
            return true
        }
        return false
    }

    fun resetToRoot() {
        if (backStack.size > 1) {
            backStack = listOf(backStack.first())
        }
    }
}

@Composable
fun rememberDriveFolderPickerNavigationState(): DriveFolderPickerNavigationState {
    return remember { DriveFolderPickerNavigationState() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveFolderPickerSheet(
    sheetState: SheetState,
    googleDriveService: GoogleDriveService,
    onFolderSelected: (folderId: String, folderName: String) -> Unit,
    onDismissRequest: () -> Unit,
    navState: DriveFolderPickerNavigationState = rememberDriveFolderPickerNavigationState()
) {
    var searchQuery by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<DriveFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Intercept back presses when deep in folders or searching
    BackHandler(enabled = searchQuery.isNotBlank() || navState.canNavigateBack) {
        if (searchQuery.isNotBlank()) {
            searchQuery = ""
        } else {
            navState.navigateBack()
        }
    }

    LaunchedEffect(navState.currentFolder.id, searchQuery) {
        isLoading = true
        folders = if (searchQuery.isNotBlank()) {
            googleDriveService.listFolders(searchName = searchQuery)
        } else {
            googleDriveService.listFolders(parentId = navState.currentFolder.id)
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with navigation support
            if (navState.isAtRoot) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = "Select Sync Folder",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose or browse a Google Drive folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    IconButton(
                        onClick = { navState.navigateBack() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate up"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = navState.currentFolder.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Current folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Clickable Breadcrumbs row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navState.backStack.forEachIndexed { index, crumb ->
                        if (index > 0) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        val isCurrent = index == navState.backStack.lastIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = if (!isCurrent) {
                                Modifier.clickable { navState.navigateToBreadcrumb(index) }
                            } else Modifier
                        ) {
                            Text(
                                text = crumb.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search folders in Drive...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                } else null,
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "No folders found matching \"$searchQuery\""
                            navState.isAtRoot -> "No folders found in Google Drive"
                            else -> "No subfolders found in \"${navState.currentFolder.name}\""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    searchQuery = ""
                                    navState.openFolder(folder.id, folder.name)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { onFolderSelected(folder.id, folder.name) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Select", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    navState.openFolder(folder.id, folder.name)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open ${folder.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom action: Select the folder currently navigated into
            if (!navState.isAtRoot) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onFolderSelected(navState.currentFolder.id, navState.currentFolder.name)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select \"${navState.currentFolder.name}\"",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
