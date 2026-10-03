package com.mi.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.explorer.data.model.StorageSpace
import com.mi.explorer.data.model.StorageVolumeItem
import com.mi.explorer.data.model.VolumeType
import com.mi.explorer.ui.theme.MiOrange

@Composable
fun StorageCard(
    storageSpace: StorageSpace,
    onCleanClick: () -> Unit,
    modifier: Modifier = Modifier,
    storageVolumes: List<StorageVolumeItem> = emptyList(),
    selectedVolume: StorageVolumeItem? = null,
    onSwitchVolume: (StorageVolumeItem) -> Unit = {}
) {
    val usedFraction = storageSpace.usedPercentage
    val usedPercent = (usedFraction * 100).toInt().coerceIn(0, 100)
    var showVolumeMenu by remember { mutableStateOf(false) }

    val volumeName = selectedVolume?.name ?: "Internal Storage"
    val hasMultipleVolumes = storageVolumes.size > 1

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("mi_storage_card"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (hasMultipleVolumes) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showVolumeMenu = true }
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            } else Modifier
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MiOrange.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedVolume?.type) {
                                VolumeType.SD_CARD -> Icons.Default.SdCard
                                VolumeType.USB_OTG -> Icons.Default.Usb
                                else -> Icons.Default.Storage
                            },
                            contentDescription = "Storage",
                            tint = MiOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = volumeName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (hasMultipleVolumes) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Storage",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "${storageSpace.formattedFree} free of ${storageSpace.formattedTotal} ($usedPercent% used)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (hasMultipleVolumes) {
                        DropdownMenu(
                            expanded = showVolumeMenu,
                            onDismissRequest = { showVolumeMenu = false }
                        ) {
                            storageVolumes.forEach { vol ->
                                DropdownMenuItem(
                                    text = { Text("${vol.name} (${vol.formattedFree} free)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (vol.type) {
                                                VolumeType.INTERNAL -> Icons.Default.PhoneAndroid
                                                VolumeType.SD_CARD -> Icons.Default.SdCard
                                                VolumeType.USB_OTG -> Icons.Default.Usb
                                            },
                                            contentDescription = null,
                                            tint = if (vol.id == selectedVolume?.id) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showVolumeMenu = false
                                        onSwitchVolume(vol)
                                    }
                                )
                            }
                        }
                    }
                }

                // Signature MIUI "Clean" pill button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onCleanClick)
                        .testTag("storage_card_clean_button"),
                    color = MiOrange.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = MiOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clean",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MiOrange,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Smooth MIUI style progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = usedFraction.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (usedFraction > 0.9f) Color(0xFFEF4444) else MiOrange
                        )
                )
            }
        }
    }
}
