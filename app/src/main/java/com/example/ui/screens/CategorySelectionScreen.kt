package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.JudoGroup
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoGoldBright
import com.example.ui.theme.JudoInk
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoWarmBackground
import com.example.util.CoachNameKey
import java.util.Locale

/**
 * Welcome / landing screen shown to coaches before a category has been picked.
 *
 * Premium judo identity: the club banner as a dark hero header (with the emblem and
 * the dark-mode toggle) and a warm off-white canvas with "duo" category cards: the
 * two referent coaches side by side as a TEAM (the category is framed by both, no
 * duel), with the real coach portraits coming from the admin > Coachs tab
 * (local `photoPath`).
 */
@Composable
fun CategorySelectionScreen(
    categories: List<JudoCategory>,
    onSelectCategory: (JudoCategory) -> Unit,
    onAdminAccess: () -> Unit,
    onQuitApp: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    coaches: List<CoachUser> = emptyList(),
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
                    .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 10.dp)
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

                Spacer(modifier = Modifier.height(12.dp))

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
                        // Progressive columns: 1 on very narrow phones, 2 on phones, 3-4 on
                        // tablets / landscape. The duo cards need width to stay readable.
                        val columns = when {
                            maxWidth >= 1000.dp -> 4
                            maxWidth >= 620.dp -> 3
                            maxWidth >= 350.dp -> 2
                            else -> 1
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeCategories, key = { it.id }) { category ->
                                CategoryCard(
                                    category = category,
                                    coachLeft = coaches.firstOrNull {
                                        CoachNameKey.same(it.name, category.coach1Name)
                                    },
                                    coachRight = coaches.firstOrNull {
                                        CoachNameKey.same(it.name, category.coach2Name)
                                    },
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

/** Fond commun aux deux portraits : nuit neutre, accent doré, aucun duel. */
@Composable
private fun DuoBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Night base shared by both portraits.
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF15151E), Color(0xFF0A0A0F))))

        // Soft golden glow behind the "+" badge : reads as "équipe", pas opposition.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(JudoGoldBright.copy(alpha = 0.20f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f),
                radius = w * 0.36f
            ),
            radius = w * 0.36f,
            center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
        )

        // Thin golden seam (séparateur) between the two portraits.
        drawLine(
            color = JudoGoldBright.copy(alpha = 0.35f),
            start = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.16f),
            end = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.74f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private enum class DuoSide { LEFT, RIGHT }

/**
 * Un des deux coachs du duo : portrait (ou initials fallback si pas encore de photo),
 * meallure dorée commune (pas de couleur d'opposition), role et nom complet.
 */
@Composable
private fun DuoPortrait(
    name: String,
    coach: CoachUser?,
    side: DuoSide,
    modifier: Modifier = Modifier
) {
    val accent = JudoGoldBright
    val photoModel = coach?.photoPath.orEmpty()
    var photoFailed by remember(photoModel) { mutableStateOf(false) }
    val showPhoto = photoModel.isNotBlank() && !photoFailed

    Box(modifier = modifier.fillMaxSize()) {
        if (showPhoto) {
            AsyncImage(
                model = photoModel,
                contentDescription = "Photo de ${name.ifBlank { "coach" }}",
                contentScale = ContentScale.Crop,
                onError = { photoFailed = true },
                modifier = Modifier.fillMaxSize()
            )
            // Low-key scolorisation identique pour les deux cotes.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.70f)
                            )
                        )
                    )
            )
        } else {
            // Fallback: duotone panel + initials, so a missing photo still looks intentional.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.20f),
                                Color(0xFF0A0A0F)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialsOf(name),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White.copy(alpha = 0.22f)
                )
            }
        }

        // Readability scrim at the bottom for the name block.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        startY = 0.42f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = if (side == DuoSide.LEFT) 6.dp else 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = accent.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.60f))
            ) {
                Text(
                    text = "COACH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 8.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Text(
                text = name.ifBlank { "À définir" },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 13.sp,
                    fontSize = 11.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Medaillon "+" or : les deux coachs encadrent la catégorie ENSEMBLE. */
@Composable
private fun DuoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF0A0A0F), CircleShape)
                .border(2.dp, JudoGoldBright.copy(alpha = 0.85f), CircleShape)
                .clip(CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                JudoGoldBright.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Text(
            text = "+",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                fontSize = 21.sp
            ),
            color = JudoGoldBright
        )
    }
}

@Composable
private fun CategoryCard(
    category: JudoCategory,
    coachLeft: CoachUser?,
    coachRight: CoachUser?,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "duel_card_scale"
    )
    // Light entrance: cards fade/settle in, no looping animation.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val appear by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "duel_card_appear"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0F)),
        border = BorderStroke(
            1.dp,
            if (pressed) JudoGold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (pressed) 2.dp else 8.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = appear
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = buildString {
                    append(category.name)
                    append(". Encadrée par ")
                    append(category.coach1Name.ifBlank { "coach à définir" })
                    append(" et ")
                    append(category.coach2Name.ifBlank { "coach à définir" })
                }
            }
            .testTag("category_card_${category.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DuoBackdrop()

            // Les deux coachs du duo, séparés par une fine ligne dorée.
            Row(modifier = Modifier.fillMaxSize()) {
                DuoPortrait(
                    name = category.coach1Name,
                    coach = coachLeft,
                    side = DuoSide.LEFT,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(34.dp))
                DuoPortrait(
                    name = category.coach2Name,
                    coach = coachRight,
                    side = DuoSide.RIGHT,
                    modifier = Modifier.weight(1f)
                )
            }

            DuoBadge(modifier = Modifier.align(Alignment.Center))

            // Header: club icon + category name + group badge, over a top scrim.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.92f), Color.Transparent)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(JudoRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsMartialArts,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = JudoGoldBright
                ) {
                    Text(
                        text = category.description.ifBlank { category.groupLabel },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.4.sp
                        ),
                        color = Color(0xFF3A2A00),
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/** "BOURENANE LYES (Coach & DTS)" -> "BL". */
private fun initialsOf(name: String): String {
    val words = name
        .replace(Regex("\\(.*?\\)"), " ")
        .split(' ')
        .filter { it.isNotBlank() && it.length > 1 }
    return words.take(2)
        .map { it.first().uppercase(Locale.ROOT) }
        .joinToString("")
        .ifBlank { "JC" }
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
