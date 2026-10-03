package com.mi.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.ui.theme.*

data class MiCategory(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val category: FileCategory?,
    val isTools: Boolean = false,
    val isSocial: Boolean = false
)

@Composable
fun CategoryGrid(
    onCategoryClick: (FileCategory, String) -> Unit,
    onToolsClick: () -> Unit,
    onSocialClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        MiCategory("Images", Icons.Default.Image, Color.White, MiBlue, FileCategory.IMAGE),
        MiCategory("Videos", Icons.Default.Movie, Color.White, MiPurple, FileCategory.VIDEO),
        MiCategory("Docs", Icons.Default.Description, Color.White, MiYellow, FileCategory.DOCUMENT),
        MiCategory("Music", Icons.Default.Audiotrack, Color.White, MiRed, FileCategory.AUDIO),
        MiCategory("APKs", Icons.Default.Android, Color.White, MiGreen, FileCategory.APK),
        MiCategory("Downloads", Icons.Default.Download, Color.White, MiCyan, null),
        MiCategory("Social", Icons.Default.Chat, Color.White, Color(0xFF25D366), null, isSocial = true),
        MiCategory("Archives", Icons.Default.Archive, Color.White, MiAmber, FileCategory.ARCHIVE),
        MiCategory("Tools", Icons.Default.Widgets, Color.White, Color(0xFF6366F1), null, isTools = true)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mi_category_grid")
    ) {
        // Row 1 (Images, Videos, Docs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.slice(0..2).forEach { cat ->
                CategoryTile(
                    category = cat,
                    onClick = {
                        handleCategoryClick(cat, onToolsClick, onSocialClick, onCategoryClick)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 2 (Music, APKs, Downloads)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.slice(3..5).forEach { cat ->
                CategoryTile(
                    category = cat,
                    onClick = {
                        handleCategoryClick(cat, onToolsClick, onSocialClick, onCategoryClick)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 3 (Social, Archives, Tools)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.slice(6..8).forEach { cat ->
                CategoryTile(
                    category = cat,
                    onClick = {
                        handleCategoryClick(cat, onToolsClick, onSocialClick, onCategoryClick)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun handleCategoryClick(
    cat: MiCategory,
    onToolsClick: () -> Unit,
    onSocialClick: () -> Unit,
    onCategoryClick: (FileCategory, String) -> Unit
) {
    if (cat.isTools) {
        onToolsClick()
    } else if (cat.isSocial) {
        onSocialClick()
    } else if (cat.category != null) {
        onCategoryClick(cat.category, cat.title)
    } else if (cat.title == "Downloads") {
        onCategoryClick(FileCategory.UNKNOWN, "Downloads")
    }
}

@Composable
private fun CategoryTile(
    category: MiCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("category_tile_${category.title}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Signature MIUI Squircle Icon Container
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(category.bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.title,
                tint = category.iconColor,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = category.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
