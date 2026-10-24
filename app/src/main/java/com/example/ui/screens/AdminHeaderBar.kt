package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AdminNotification
import com.example.data.model.AdminProfile
import com.example.data.model.ClubSettings
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoGoldBright
import com.example.ui.theme.JudoInk
import com.example.ui.theme.JudoInkSoft
import com.example.ui.theme.JudoRed

/**
 * Premium dark header for the administrator space: the club emblem on the left and a
 * row of clear, well-defined action buttons (Tatami, Cloud sync, admin profile menu).
 */
@Composable
fun AdminHeaderBar(
    clubSettings: ClubSettings,
    adminProfile: AdminProfile,
    notifications: List<AdminNotification> = emptyList(),
    isDarkTheme: Boolean = false,
    pendingSyncCount: Int = 0,
    absenceAlertCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenCloudSync: () -> Unit = {},
    onOpenProfileDialog: () -> Unit,
    onSwitchToCoach: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(JudoInk, JudoInkSoft)))
            .testTag("admin_header_bar")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Show button labels when the header is wide enough (tablet / landscape).
            val showLabels = maxWidth >= 520.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ---- Left: club emblem + name ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_asset),
                        contentDescription = "Emblème Judo Club Seddouk",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.5.dp, JudoGoldBright.copy(alpha = 0.85f), CircleShape),
                        contentScale = ContentScale.Fit
                    )

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = clubSettings.clubName.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Espace Administrateur",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = JudoGoldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ---- Right: action buttons ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeaderActionButton(
                        icon = Icons.Default.SportsMartialArts,
                        label = if (showLabels) "Tatami" else null,
                        contentDescription = "Accès Tatami (Entraîneur)",
                        tint = Color(0xFFFF6B6B),
                        onClick = onSwitchToCoach,
                        modifier = Modifier.testTag("admin_switch_coach_btn")
                    )

                    HeaderActionButton(
                        icon = Icons.Default.Notifications,
                        label = if (showLabels) "Alertes" else null,
                        contentDescription = "Notifications d'absences (3 absences et plus ce mois-ci)",
                        tint = if (absenceAlertCount > 0) JudoGoldBright else Color.White,
                        showBadge = absenceAlertCount > 0,
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("admin_header_notifications_btn")
                    )

                    // Admin profile avatar + menu
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.5.dp, JudoGoldBright.copy(alpha = 0.85f), CircleShape)
                                .clickable(role = Role.Button) { profileMenuExpanded = true }
                                .testTag("admin_profile_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!adminProfile.photoUri.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = adminProfile.photoUri,
                                    contentDescription = "Photo profil administrateur",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Profil Administrateur",
                                    tint = JudoGoldBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = profileMenuExpanded,
                            onDismissRequest = { profileMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = adminProfile.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = adminProfile.role,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onOpenProfileDialog()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Modifier mes informations") },
                                leadingIcon = {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onOpenProfileDialog()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Paramètres du club") },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onOpenSettings()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Supabase Cloud & Sauvegardes") },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = JudoGold, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onOpenCloudSync()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Espace Coach (Tatami)") },
                                leadingIcon = {
                                    Icon(Icons.Default.SportsMartialArts, contentDescription = null, tint = JudoRed, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onSwitchToCoach()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Quitter l'application",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clearly defined action button used in the admin header: rounded pill with a
 * translucent surface, an optional label and an optional notification dot.
 */
@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    tint: Color = Color.White,
    showBadge: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (label != null) 12.dp else 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(JudoRed)
                        .border(1.5.dp, JudoInk, CircleShape)
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
