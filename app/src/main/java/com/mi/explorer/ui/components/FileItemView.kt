package com.mi.explorer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.explorer.data.model.ColorTag
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.theme.*

fun getMiCategoryColors(category: FileCategory): Pair<ImageVector, Color> {
    return when (category) {
        FileCategory.FOLDER -> Pair(Icons.Default.Folder, Color(0xFFFFB300))
        FileCategory.IMAGE -> Pair(Icons.Default.Image, MiBlue)
        FileCategory.AUDIO -> Pair(Icons.Default.Audiotrack, MiRed)
        FileCategory.VIDEO -> Pair(Icons.Default.Movie, MiPurple)
        FileCategory.DOCUMENT -> Pair(Icons.Default.Description, MiYellow)
        FileCategory.ARCHIVE -> Pair(Icons.Default.Archive, MiAmber)
        FileCategory.APK -> Pair(Icons.Default.Android, MiGreen)
        FileCategory.CODE -> Pair(Icons.Default.Code, MiMint)
        FileCategory.UNKNOWN -> Pair(Icons.Default.InsertDriveFile, Gray400)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiFileRow(
    item: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onMenuAction: (String) -> Unit,
    tags: List<ColorTag> = emptyList(),
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val (icon, color) = getMiCategoryColors(item.category)

    val itemBg = if (isSelected) MiOrange.copy(alpha = 0.08f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(itemBg)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("mi_file_row_${item.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = MiOrange),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // MIUI Squircle icon badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = item.category.name,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        tags.take(3).forEach { t ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(t.composeColor)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.formattedSize,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (item.isLarge) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = when {
                        item.isHuge -> Color(0xFFDC2626)
                        item.isVeryLarge -> Color(0xFFEA580C)
                        item.isLarge -> MiOrange
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                item.sizeBadgeText?.let { badge ->
                    Surface(
                        color = when {
                            item.isHuge -> Color(0xFFFEE2E2)
                            item.isVeryLarge -> Color(0xFFFFEDD5)
                            item.isLarge -> MiOrange.copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = when {
                                item.isHuge -> Color(0xFFDC2626)
                                item.isVeryLarge -> Color(0xFFC2410C)
                                item.isLarge -> MiOrange
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
                Text(
                    text = item.formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open") },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Favorite") },
                    leadingIcon = { Icon(Icons.Default.StarBorder, contentDescription = null, tint = Color(0xFFF59E0B)) },
                    onClick = {
                        showMenu = false
                        onMenuAction("toggle_favorite")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Color Tags & Labels") },
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, tint = MiOrange) },
                    onClick = {
                        showMenu = false
                        onMenuAction("tags")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Fast Share (Wi-Fi P2P)") },
                    leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF10B981)) },
                    onClick = {
                        showMenu = false
                        onMenuAction("fast_share")
                    }
                )
                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Open with...") },
                        leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMenuAction("open_with")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Calculate Checksum") },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MiOrange) },
                        onClick = {
                            showMenu = false
                            onMenuAction("checksum")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hide in Vault") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MiOrange) },
                        onClick = {
                            showMenu = false
                            onMenuAction("vault")
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onMenuAction("copy")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Cut") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onMenuAction("cut")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onMenuAction("rename")
                    }
                )
                if (item.category == FileCategory.ARCHIVE) {
                    DropdownMenuItem(
                        text = { Text("Extract") },
                        leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMenuAction("unzip")
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Compress") },
                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMenuAction("zip")
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Details") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onMenuAction("details")
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEF4444)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                    onClick = {
                        showMenu = false
                        onMenuAction("delete")
                    }
                )
            }
        }
    }
}
