package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.JudoCategory
import com.example.data.model.JudoGroup
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoGoldBright
import com.example.ui.theme.JudoInk
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoWarmBackground

/**
 * Welcome / landing screen shown to coaches before a category has been picked.
 *
 * Premium judo identity: the club banner as a dark hero header (with the emblem and
 * the dark-mode toggle), a warm off-white canvas with rounded category cards
 * (pale-red icon chip + gold group badge) and the original Admin access gate / Quit
 * actions pinned at the bottom.
 */
@Composable
fun CategorySelectionScreen(
    categories: List<JudoCategory>,
    onSelectCategory: (JudoCategory) -> Unit,
    onAdminAccess: () -> Unit,
    onQuitApp: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The cards follow the athletes' age: youngest first (Éveil → Mini-Poussins → Poussins →
    // Benjamins → Minimes → Cadets → Juniors/Séniors). The stored list can come in any order
    // from the admin configuration, so the age rank is derived from the JUDO group enum here.
    val activeCategories = categories
        .filter { it.isActive }
        .sortedWith(
            compareBy(
                { JudoGroup.fromString(it.groupCode).ordinal },
                { it.name.lowercase() }
            )
        )
    val canvasColor = if (isDarkTheme) MaterialTheme.colorScheme.background else JudoWarmBackground

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("category_selection_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeroHeader(isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)

            // ---- Category list ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "Sélectionnez votre catégorie",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Espace entraîneur • pointage des présences",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (activeCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune catégorie active n'est configurée.\nAccédez à l'espace administrateur pour en créer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        // 3 columns on tablets, 2 on phones (matches the reference grid).
                        val columns = if (maxWidth >= 520.dp) 3 else 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeCategories, key = { it.id }) { category ->
                                CategoryCard(
                                    category = category,
                                    onClick = { onSelectCategory(category) }
                                )
                            }
                        }
                    }
                }
            }

            BottomActions(onAdminAccess = onAdminAccess, onQuitApp = onQuitApp)
        }
    }
}

/**
 * Dark hero header built on the club banner artwork. The poster already contains the
 * emblem, the club name and the venue, so nothing is overlaid on it except the theme
 * toggle, kept at the top-right corner.
 */
@Composable
private fun HeroHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Match the banner artwork's own ratio so the whole poster is shown and the
            // hero is noticeably taller than a content-height header.
            .aspectRatio(1671f / 941f)
            .background(JudoInk)
    ) {
        // Provided club banner, pasted as the hero background (sized to the header).
        // The artwork already carries the emblem, the club name and the venue, so it is
        // shown unmasked; the only overlay is the theme toggle.
        Image(
            painter = painterResource(id = R.drawable.ic_home_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Top
        ) {
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Changer le thème",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: JudoCategory,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("category_card_${category.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Pale-red chip with the red judo icon.
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(JudoRed.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsMartialArts,
                    contentDescription = null,
                    tint = JudoRed,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                // Small gold group badge.
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = JudoGoldBright
                ) {
                    Text(
                        text = category.groupCode,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF3A2A00),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = category.coach1Name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BottomActions(
    onAdminAccess: () -> Unit,
    onQuitApp: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAdminAccess,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JudoRed,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("category_admin_access_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Administrateur",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            TextButton(
                onClick = onQuitApp,
                modifier = Modifier.testTag("category_quit_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "Quitter",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
