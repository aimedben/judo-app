package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.Member
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCategoriesScreen(
    categories: List<JudoCategory>,
    allMembers: List<Member>,
    coaches: List<CoachUser>,
    onAddCategory: (JudoCategory) -> Unit,
    onUpdateCategory: (JudoCategory) -> Unit,
    onToggleCategoryActive: (String) -> Unit,
    onViewCategoryMembers: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var categoryToEdit by remember { mutableStateOf<JudoCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<JudoCategory?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("admin_categories_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Catégories & Groupes",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${categories.count { it.isActive }} catégories actives sur ${categories.size}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Button(
                onClick = { isAddDialogOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_category_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nouvelle Catégorie")
            }
        }

        // List of Categories
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(categories) { cat ->
                val membersInCat = allMembers.filter { cat.covers(it.groupCode) || it.group.name.equals(cat.name, ignoreCase = true) }
                val activeMembers = membersInCat.count { it.status == "ACTIF" }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cat.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, if (cat.isActive) JudoSuccess.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Photo de profil de la categorie : judoka (icon judo)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (cat.isActive) JudoRed.copy(alpha = 0.12f)
                                        else Color.Gray.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsMartialArts,
                                    contentDescription = "Catégorie de judo",
                                    tint = if (cat.isActive) JudoRed else Color.Gray,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Textes : largeur contrainte pour que les boutons restent visibles
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = JudoRed.copy(alpha = 0.12f),
                                        modifier = Modifier.widthIn(max = 150.dp)
                                    ) {
                                        Text(
                                            text = cat.groupLabel,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = JudoRed
                                            )
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (cat.isActive) JudoSuccess.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (cat.isActive) "ACTIVE" else "DÉSACTIVÉE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = if (cat.isActive) JudoSuccess else Color.Gray
                                            )
                                        )
                                    }
                                }

                                if (cat.description.isNotBlank()) {
                                    Text(
                                        text = cat.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Actions : taille fixe, jamais poussees hors de la carte
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { categoryToEdit = cat },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("edit_category_${cat.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Modifier",
                                        tint = JudoRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { categoryToDelete = cat },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("delete_category_${cat.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Supprimer",
                                        tint = if (membersInCat.any { it.status == "ACTIF" }) Color.Gray else JudoRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Encadrement + effectif (bloc lisible, jamais tronque brutalement)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(16.dp),
                                        tint = JudoGold
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Coachs référents",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Text(
                                            text = listOfNotNull(
                                                cat.coach1Name.takeIf { it.isNotBlank() },
                                                cat.coach2Name.takeIf { it.isNotBlank() }
                                            ).joinToString(" • ").ifBlank { "À définir" },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.People,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF2196F3)
                                    )
                                    Text(
                                        text = "$activeMembers athlètes actifs • ${membersInCat.size} inscrits",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Status Action Button / Card (Clear green when active)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (cat.isActive) JudoSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (cat.isActive) JudoSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCategoryActive(cat.id) }
                                .testTag("toggle_category_${cat.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (cat.isActive) JudoSuccess.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (cat.isActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (cat.isActive) JudoSuccess else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = if (cat.isActive) "Catégorie Active" else "Catégorie Désactivée",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (cat.isActive) JudoSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Text(
                                            text = if (cat.isActive) "Visible lors des séances" else "Masquée du pointage",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                Switch(
                                    checked = cat.isActive,
                                    onCheckedChange = { onToggleCategoryActive(cat.id) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = JudoSuccess,
                                        checkedBorderColor = JudoSuccess,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f),
                                        uncheckedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAddDialogOpen) {
        AddEditCategoryDialog(
            initialCategory = null,
            coachesList = coaches,
            onSave = onAddCategory,
            onDismiss = { isAddDialogOpen = false }
        )
    }

    if (categoryToEdit != null) {
        AddEditCategoryDialog(
            initialCategory = categoryToEdit,
            coachesList = coaches,
            onSave = onUpdateCategory,
            onDismiss = { categoryToEdit = null }
        )
    }

    categoryToDelete?.let { doomed ->
        val activeMembers = allMembers.count {
            it.status == "ACTIF" && doomed.covers(it.groupCode)
        }
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Supprimer la catégorie ?") },
            text = {
                Text(
                    if (activeMembers > 0) {
                        "« ${doomed.name} » compte encore $activeMembers athlète(s) actif(s). " +
                            "La catégorie sera supprimée fidèlement du club et du cloud ; " +
                            "rattachez d'abord ces athlètes à une autre catégorie."
                    } else {
                        "« ${doomed.name} » sera supprimée du club et du cloud. Cette action est définitive."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(doomed.id)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = JudoRed)
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}
