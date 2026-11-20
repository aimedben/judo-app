package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminProfile
import com.example.data.model.ClubSettings
import com.example.data.local.face.FaceDatasetTransfer
import com.example.ui.ThemePreference
import com.example.ui.components.HeroStat
import com.example.ui.components.JudoHeroCard
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import kotlinx.coroutines.launch

@Composable
fun AdminSettingsScreen(
    clubSettings: ClubSettings,
    adminProfile: AdminProfile,
    currentThemePreference: ThemePreference,
    onSetThemePreference: (ThemePreference) -> Unit,
    onUpdateClubSettings: (ClubSettings) -> Unit,
    onUpdateAdminProfile: (AdminProfile) -> Unit,
    onLogout: () -> Unit,
    onNavigateToCloudSync: () -> Unit = {},
    onClearDatabase: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearDatabaseConfirmDialog by remember { mutableStateOf(false) }

    // Club state
    var clubName by remember { mutableStateOf(clubSettings.clubName) }
    var dojoAddress by remember { mutableStateOf(clubSettings.dojoAddress) }
    var phone by remember { mutableStateOf(clubSettings.phone) }
    var season by remember { mutableStateOf(clubSettings.season) }
    var openingHours by remember { mutableStateOf(clubSettings.openingHours) }
    var officialStartDate by remember { mutableStateOf(clubSettings.officialStartDate) }
    var smsSenderNumber by remember { mutableStateOf(clubSettings.smsSenderNumber) }
    var isClubSaved by remember { mutableStateOf(false) }

    // Admin account state
    var adminName by remember { mutableStateOf(adminProfile.name) }
    var adminRole by remember { mutableStateOf(adminProfile.role) }
    var adminPin by remember { mutableStateOf(adminProfile.pinCode) }
    var isAccountSaved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("admin_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Banniere heros (style accueil du dashboard)
        item {
            JudoHeroCard(
                title = "Paramètres",
                subtitle = "Configuration du dojo, apparence et sécurité",
                icon = Icons.Default.Settings,
                onBack = onBack,
                stats = listOf(
                    HeroStat(value = clubSettings.clubName.take(14), label = "Club"),
                    HeroStat(
                        value = when (currentThemePreference) {
                            ThemePreference.DARK -> "Sombre"
                            ThemePreference.LIGHT -> "Clair"
                            else -> "Auto"
                        },
                        label = "Thème"
                    ),
                    HeroStat(value = clubSettings.season, label = "Saison")
                )
            )
        }

        // Section A: Apparence (Thème clair, sombre, automatique)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(JudoRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = JudoRed, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = "A. Apparence & Thème",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Choisissez le mode d'affichage adapté aux conditions de votre dojo :",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeOptionTile(
                            title = "Clair",
                            icon = Icons.Default.LightMode,
                            isSelected = currentThemePreference == ThemePreference.LIGHT,
                            onClick = { onSetThemePreference(ThemePreference.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionTile(
                            title = "Sombre",
                            icon = Icons.Default.DarkMode,
                            isSelected = currentThemePreference == ThemePreference.DARK,
                            onClick = { onSetThemePreference(ThemePreference.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionTile(
                            title = "Système",
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = currentThemePreference == ThemePreference.SYSTEM,
                            onClick = { onSetThemePreference(ThemePreference.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section B: Gestion du Club
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(JudoGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = JudoGold, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = "B. Gestion du Club",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedTextField(
                        value = clubName,
                        onValueChange = { clubName = it; isClubSaved = false },
                        label = { Text("Nom officiel du club") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dojoAddress,
                        onValueChange = { dojoAddress = it; isClubSaved = false },
                        label = { Text("Adresse du dojo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; isClubSaved = false },
                        label = { Text("Numéro de téléphone officiel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = season,
                            onValueChange = { season = it; isClubSaved = false },
                            label = { Text("Saison sportive") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = openingHours,
                            onValueChange = { openingHours = it; isClubSaved = false },
                            label = { Text("Horaires d'ouverture") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Debut du pointage officiel : les seances anterieures a cette date sont
                    // ignorees dans les statistiques (aucune donnee n'est supprimee).
                    OutlinedTextField(
                        value = officialStartDate,
                        onValueChange = { officialStartDate = it; isClubSaved = false },
                        label = { Text("Début du pointage officiel (AAAA-MM-JJ)") },
                        placeholder = { Text("2026-10-20") },
                        singleLine = true,
                        supportingText = {
                            Text("Les séances antérieures à cette date ne comptent plus dans les présences, absences et taux. Laisser vide = aucune restriction.")
                        },
                        trailingIcon = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    officialStartDate = com.example.util.OfficialPeriod.todayIsoDate()
                                    isClubSaved = false
                                }
                            ) {
                                Text("Aujourd'hui")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Numero expediteur des SMS : ajoute comme contact dans le message envoye
                    // aux parents des absents. Les numeros des parents, eux, sont repris
                    // automatiquement depuis les fiches athletes.
                    OutlinedTextField(
                        value = smsSenderNumber,
                        onValueChange = { smsSenderNumber = it; isClubSaved = false },
                        label = { Text("Numéro expéditeur des SMS") },
                        placeholder = { Text("0782487120") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        supportingText = {
                            Text("Utilisé comme contact du club dans les SMS aux parents d'absents.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isClubSaved) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = JudoSuccess, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Modifications enregistrées",
                                    style = MaterialTheme.typography.labelSmall.copy(color = JudoSuccess, fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                val normalizedStart = officialStartDate.trim()
                                if (normalizedStart.isNotEmpty() &&
                                    !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(normalizedStart)
                                ) {
                                    Toast.makeText(
                                        context,
                                        "Date invalide : utilisez le format AAAA-MM-JJ",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                val normalizedSms = smsSenderNumber.trim()
                                if (normalizedSms.isNotEmpty() &&
                                    normalizedSms.count { it.isDigit() } < 6
                                ) {
                                    Toast.makeText(
                                        context,
                                        "Numéro SMS invalide",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                onUpdateClubSettings(
                                    clubSettings.copy(
                                        clubName = clubName.trim(),
                                        dojoAddress = dojoAddress.trim(),
                                        phone = phone.trim(),
                                        season = season.trim(),
                                        openingHours = openingHours.trim(),
                                        officialStartDate = normalizedStart,
                                        smsSenderNumber = normalizedSms
                                    )
                                )
                                isClubSaved = true
                                Toast.makeText(context, "Informations du club enregistrées", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JudoRed)
                        ) {
                            Text("Enregistrer le club")
                        }
                    }
                }
            }
        }

        // Section C: Sécurité et Compte Administrateur
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2196F3).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = "C. Sécurité & Compte Administrateur",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedTextField(
                        value = adminName,
                        onValueChange = { adminName = it; isAccountSaved = false },
                        label = { Text("Nom de l'administrateur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminRole,
                        onValueChange = { adminRole = it; isAccountSaved = false },
                        label = { Text("Rôle / Droits") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { if (it.length <= 6) { adminPin = it; isAccountSaved = false } },
                        label = { Text("Code PIN d'accès administratif (défaut : 2026)") },
                        supportingText = { Text("Ce code PIN protège l'accès à l'espace administrateur depuis l'accueil") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAccountSaved) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = JudoSuccess, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Compte mis à jour",
                                    style = MaterialTheme.typography.labelSmall.copy(color = JudoSuccess, fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                onUpdateAdminProfile(
                                    adminProfile.copy(
                                        name = adminName.trim(),
                                        role = adminRole.trim(),
                                        pinCode = adminPin.trim()
                                    )
                                )
                                isAccountSaved = true
                                Toast.makeText(context, "Profil administrateur et code PIN mis à jour", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JudoRed)
                        ) {
                            Text("Enregistrer le compte")
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Logout Button
                    OutlinedButton(
                        onClick = onLogout,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Se déconnecter de l'Espace Administrateur")
                    }
                }
            }
        }

        // Section C.2: Synchronisation Supabase Cloud & Sauvegardes
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, JudoGold.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(JudoGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = JudoGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "D. Supabase Cloud & Sauvegardes",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = JudoGold
                                )
                            )
                            Text(
                                text = "Base distante, offline-first & exports JSON",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Text(
                        text = "Synchronisez vos pointages et adhérents avec Supabase PostgreSQL (offre gratuite) ou exportez une sauvegarde JSON complète de votre dojo.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Button(
                        onClick = onNavigateToCloudSync,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JudoGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_open_cloud_sync_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gérer la synchronisation Cloud & Sauvegardes",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section E: Maintenance & Réinitialisation de la Base de Données
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "D. Base de Données (Zone Critique)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                            Text(
                                text = "Maintenance et suppression définitive",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Text(
                        text = "Vider la base de données supprimera irréversiblement tous les membres athlètes enregistrés ainsi que l'ensemble des historiques de présence et de pointage.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Button(
                        onClick = { showClearDatabaseConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_database_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vider toute la base de données",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            AboutLicensesCard()
        }

        // Tout en bas : transfert de la dataset de reconnaissance faciale
        item {
            FaceDatasetCard()
        }
    }

    if (showClearDatabaseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearDatabaseConfirmDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Vider toute la base de données ?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Attention : cette action supprimera définitivement tous les judokas et tous les pointages de présence enregistrés.\n\nÊtes-vous sûr de vouloir tout supprimer ?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDatabaseConfirmDialog = false
                        onClearDatabase()
                        Toast.makeText(context, "Base de données vidée avec succès", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_clear_database_btn")
                ) {
                    Text("Oui, tout supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearDatabaseConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionTile(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) JudoRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) JudoRed else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) JudoRed else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/** Attribution legale du modele de reconnaissance faciale et des donnees biometriques. */
@Composable
private fun AboutLicensesCard() {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_licenses_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = JudoRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "À propos / Licences",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Text(
                text = "Reconnaissance faciale : EdgeFace-S (Idiap Research Institute), licence CC BY-NC-SA 4.0 — attribution, usage non commercial, partage à l'identique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Poids embarqué : edgeface_s.onnx. Exécution 100 % locale via ONNX Runtime Mobile (licence MIT).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Aucune photo de visage n'est conservée : seules des empreintes chiffrées restent sur la tablette, hors sauvegarde et hors synchronisation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    uriHandler.openUri(
                        "https://gitlab.idiap.ch/bob/bob.paper.tbiom2023_edgeface/-/blob/master/LICENSE"
                    )
                },
                modifier = Modifier.testTag("edgeface_license_link")
            ) {
                Text("Voir la licence EdgeFace (CC BY-NC-SA 4.0)")
            }
        }
    }
}
/**
 * Transfert de la dataset de reconnaissance faciale entre appareils.
 *
 * Les empreintes (vecteurs) dorment chiffrees avec une cle du Keystore propre a CET appareil :
 * l'export les dechiffre dans un fichier JSON choisi par l'admin, l'import les re-chiffre avec
 * la cle du nouvel appareil. Aucune photo de visage ne circule.
 */
@Composable
private fun FaceDatasetCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    suspend fun refreshStats() {
        stats = runCatching { FaceDatasetTransfer.localStats(context) }.getOrNull()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { refreshStats() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { FaceDatasetTransfer.export(context, uri) }.fold(
                onSuccess = { r ->
                    isError = false
                    message = "${r.templates} empreintes (${r.members} athlètes) exportées."
                },
                onFailure = { e ->
                    isError = true
                    message = "Échec de l'export : ${e.message ?: "erreur inconnue"}"
                }
            )
            busy = false
            refreshStats()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { FaceDatasetTransfer.import(context, uri) }.fold(
                onSuccess = { r ->
                    isError = r.imported == 0 && (r.unknownMembers > 0 || r.incompatible > 0)
                    message = "${r.imported} empreintes importées • ${r.skippedExisting} déjà présentes • " +
                        "${r.unknownMembers} athlètes introuvables • ${r.incompatible} incompatibles."
                },
                onFailure = { e ->
                    isError = true
                    message = "Échec de l'import : ${e.message ?: "fichier illisible"}"
                }
            )
            busy = false
            refreshStats()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("face_dataset_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = JudoRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dataset reconnaissance faciale",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Text(
                text = "Sauvegardez les empreintes (vecteurs) pour les réinstaller sur un nouvel " +
                    "appareil, ou après un changement de tablette. Les visages eux-mêmes ne sont " +
                    "jamais enregistrés ni exportés.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stats?.let { (templates, members) -> "$templates empreintes • $members athlètes enregistrés" }
                    ?: "Calcul en cours…",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        exportLauncher.launch("dataset-visages-jcs.json")
                    },
                    enabled = !busy && (stats?.first ?: 0) > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("face_dataset_export_btn")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Télécharger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("face_dataset_import_btn")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uploader", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (busy) {
                Text(
                    text = "Traitement en cours…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            message?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isError) MaterialTheme.colorScheme.error else JudoSuccess
                )
            }
        }
    }
}
