package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.QuitSyncDialog
import com.example.ui.screens.AdminCloudSyncScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ActiveTrainingSession
import com.example.data.model.AdminNotification
import com.example.data.model.AdminProfile
import com.example.data.model.AdminSubScreen
import com.example.data.model.ClubSettings
import com.example.data.model.CoachProfile
import com.example.data.model.CoachTab
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.Member
import com.example.data.model.PastTrainingSession
import com.example.data.model.UserRole
import com.example.data.repository.CheckInResult
import com.example.ui.MainViewModel
import com.example.ui.ThemePreference
import com.example.ui.components.AddEditMemberDialog
import com.example.ui.components.AdminPinDialog
import com.example.ui.components.AlreadyCheckedInDialog
import com.example.ui.components.AssociateCardDialog
import com.example.ui.components.CheckInConfirmationDialog
import com.example.ui.components.CoachProfileDialog
import com.example.ui.components.ConfirmAddMemberDialog
import com.example.ui.components.RegistrationOnlyDialog
import com.example.ui.screens.AdminAbsenceNotificationsScreen
import com.example.ui.screens.AdminAttendanceOverviewScreen
import com.example.ui.screens.AdminCategoriesScreen
import com.example.ui.screens.AdminCoachesScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminExportsScreen
import com.example.ui.screens.AdminProfileEditDialog
import com.example.ui.screens.AdminSessionsScreen
import com.example.ui.screens.AdminSettingsScreen
import com.example.ui.screens.CategorySelectionScreen
import com.example.ui.screens.CoachAthletesScreen
import com.example.ui.screens.CoachHistoryScreen
import com.example.ui.screens.CoachMinimalHomeScreen
import com.example.ui.screens.CoachDedicatedScannerScreen
import com.example.ui.screens.CoachFacePointageScreen
import com.example.ui.screens.CoachProfileScreen
import com.example.ui.screens.CoachSessionPointageScreen
import com.example.ui.screens.CoachSessionPreparationScreen
import com.example.ui.screens.CoachSessionSummaryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MemberDetailScreen
import com.example.ui.screens.MembersScreen
import com.example.ui.screens.NotificationsDialog
import com.example.ui.screens.ScannerTargetMode
import com.example.ui.screens.StatFilterMode
import com.example.ui.screens.StatFilteredMembersScreen
import com.example.ui.screens.StatPeriod
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.SportsMartialArts
import com.example.ui.theme.JudoGoldBright
import com.example.ui.theme.JudoRed
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AttendancePolicy
import com.example.util.DouchetteReceiver
import com.example.util.MemberDuplicates
import com.example.util.SessionScope

// Ordre navbar admin : Accueil, Athlètes, Coachs, Catégories puis Séances.
enum class ScreenTab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Default.Dashboard),
    MEMBERS("Athlètes", Icons.Default.People),
    COACHES("Coachs", Icons.Default.SportsMartialArts),
    CATEGORIES("Catégories", Icons.Default.Category),
    SESSIONS("Séances", Icons.Default.EventNote)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Single receiver for the USB / Bluetooth HID barcode scanner ("douchette").
    // It accumulates the whole code and fires exactly once per physical read — either on
    // the scanner's Enter suffix, or after an inactivity timeout when no suffix is sent.
    // This replaces the previous two competing receivers (the Activity buffer and a hidden
    // text field) that used to process a long QR code several times while it was still
    // being transmitted.
    private val douchette = DouchetteReceiver(
        onCode = { code -> viewModel.onQrScanned(code) },
        onIncomplete = { viewModel.onIncompleteScan() }
    )

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Only capture hardware keys while a training session is running. Soft-keyboard
        // input goes through the InputConnection (not dispatchKeyEvent), so normal typing
        // in dialogs and text fields is never affected.
        // La douchette n'est active que si la séance en cours appartient à la catégorie affichée :
        // une séance laissée en arrière-plan dans une autre catégorie ne doit pas capturer le clavier.
        if (!SessionScope.isActive(viewModel.activeSession.value, viewModel.selectedCategory.value)) {
            douchette.reset()
            return super.dispatchKeyEvent(event)
        }

        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_TAB -> {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    douchette.feedTerminator()
                }
                return true
            }

            android.view.KeyEvent.KEYCODE_BACK,
            android.view.KeyEvent.KEYCODE_ESCAPE -> {
                douchette.reset()
                return super.dispatchKeyEvent(event)
            }

            else -> {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    val unicode = event.getUnicodeChar(event.metaState)
                    if (unicode != 0 && !Character.isISOControl(unicode)) {
                        douchette.feedChar(unicode.toChar())
                        return true
                    }
                } else if (event.action == android.view.KeyEvent.ACTION_MULTIPLE) {
                    @Suppress("DEPRECATION")
                    val chars = event.characters
                    if (!chars.isNullOrEmpty()) {
                        douchette.feedString(chars)
                        return true
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePref by viewModel.themePreference.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themePref) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> systemDark
            }

            // Keep the status/navigation bar icons in sync with the in-app theme
            // (the app may be forced light/dark independently of the device setting).
            val view = LocalView.current
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JudoApp(viewModel = viewModel, isDarkTheme = isDark)
                }
            }
        }
    }
}

@Composable
fun JudoApp(viewModel: MainViewModel, isDarkTheme: Boolean) {
    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
    var selectedMemberId by remember { mutableStateOf<Long?>(null) }
    var membersSearchQuery by remember { mutableStateOf("") }
    var membersGroupFilter by remember { mutableStateOf("ALL") }
    var activeStatFilter by remember { mutableStateOf<StatFilterMode?>(null) }
    var activeStatPeriod by remember { mutableStateOf(StatPeriod.TODAY) }
    var dashboardPeriod by remember { mutableStateOf(StatPeriod.TODAY) }
    var memberToEdit by remember { mutableStateOf<Member?>(null) }
    var scannerMode by remember { mutableStateOf(ScannerTargetMode.POINTAGE) }
    var isCoachProfileDialogOpen by remember { mutableStateOf(false) }

    // Admin State & SubScreens
    var activeAdminSubScreen by remember { mutableStateOf(AdminSubScreen.NONE) }
    var isNotificationsDialogOpen by remember { mutableStateOf(false) }
    var isAdminProfileDialogOpen by remember { mutableStateOf(false) }
    var isAdminPinDialogOpen by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsState()
    val coaches by viewModel.coaches.collectAsState()
    val clubSettings by viewModel.clubSettings.collectAsState()
    val adminProfile by viewModel.adminProfile.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    // Une séance en cours n'appartient qu'à la catégorie pour laquelle elle a été démarrée.
    // Toute séance d'une autre catégorie est obsolète et ne doit jamais s'afficher, sinon toutes
    // les cartes de catégorie semblaient ouvrir le pointage de la même séance.
    val sessionForSelectedCategory = SessionScope.forCategory(activeSession, selectedCategory)
    val coachScanFeedback by viewModel.coachScanFeedback.collectAsState()
    val pastSessions by viewModel.pastSessions.collectAsState()

    var isPreparingSession by remember { mutableStateOf(false) }
    var isSessionSummaryShown by remember { mutableStateOf(false) }
    var currentCoachTab by remember { mutableStateOf(CoachTab.SESSION) }
    var isCoachCameraScannerOpen by remember { mutableStateOf(false) }
    // Pointage facial plein ecran (meme pattern que le scanner QR plein ecran).
    var isCoachFacePointageOpen by remember { mutableStateOf(false) }

    val coachProfile by viewModel.coachProfile.collectAsState()
    val allMembers by viewModel.allMembers.collectAsState()
    val todayAttendance by viewModel.todayAttendance.collectAsState()
    val recentAttendance by viewModel.recentAttendance.collectAsState()
    val allAttendance by viewModel.allAttendance.collectAsState()
    val lastScanned by viewModel.lastScanned.collectAsState()
    val activeCheckInResult by viewModel.activeCheckInResult.collectAsState()
    val unassignedQrCode by viewModel.unassignedQrCode.collectAsState()
    val scannedMemberForCreation by viewModel.scannedMemberForCreation.collectAsState()
    val pendingMemberRegistration by viewModel.pendingMemberRegistration.collectAsState()
    val scheduledSessions by viewModel.scheduledSessions.collectAsStateWithLifecycle()

    val supabaseConfig by viewModel.supabaseConfig.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val lastSyncReport by viewModel.lastSyncReport.collectAsStateWithLifecycle()
    val recentAuditLogs by viewModel.recentAuditLogs.collectAsStateWithLifecycle()
    var showQuitSyncDialog by remember { mutableStateOf(false) }

    val handleLogoutWithSync = {
        if (supabaseConfig.isConfigured && supabaseConfig.isAutoSyncEnabled && (pendingSyncCount > 0 || viewModel.isOnline())) {
            showQuitSyncDialog = true
        } else {
            viewModel.selectCategory(null)
            viewModel.setUserRole(UserRole.COACH)
        }
    }

    val topStats = remember(allMembers, allAttendance, dashboardPeriod, clubSettings.officialStartDate) {
        viewModel.getTopAttendanceList(
            allMembers,
            allAttendance,
            dashboardPeriod,
            clubSettings.officialStartDate
        )
    }

    // Filter members for active coach session category.
    // Une categorie peut couvrir plusieurs groupes ("Cadets, Juniors & Seniors" = U18+SENIOR).
    val categoryMembers = remember(allMembers, selectedCategory) {
        if (selectedCategory == null) allMembers
        else allMembers.filter { selectedCategory!!.covers(it.groupCode) }
    }

    // Galerie faciale du groupe : dechiffree en memoire uniquement pour la seance en cours.
    val faceGallery by viewModel.faceGallery.collectAsStateWithLifecycle()
    LaunchedEffect(sessionForSelectedCategory?.categoryId, sessionForSelectedCategory?.startTimestamp, categoryMembers.size) {
        if (sessionForSelectedCategory != null) {
            viewModel.prepareFaceGallery(categoryMembers.map { it.id })
        } else {
            viewModel.clearFaceGallery()
        }
    }

    // Pointages du jour pour la catégorie sélectionnée. Portée « journée » : sert
    // uniquement aux badges « présent aujourd'hui », indépendants d'une séance.
    val categoryTodayAttendances = remember(todayAttendance, selectedCategory) {
        if (selectedCategory == null) todayAttendance
        else todayAttendance.filter { selectedCategory!!.covers(it.groupCode) }
    }

    // Pointage de la séance EN COURS uniquement.
    // - Aucune séance active ⇒ liste vide : la présence d'une séance précédente n'est
    //   jamais réutilisée et une nouvelle séance démarre toujours à 0 présent.
    // - Un athlète n'est présent que s'il a été scanné pendant CETTE séance.
    // - Dédoublonnage par athlète : les compteurs ne bougent qu'une fois par pointage.
    val sessionAttendances = remember(categoryTodayAttendances, sessionForSelectedCategory) {
        val startTs = sessionForSelectedCategory?.startTimestamp ?: return@remember emptyList()
        val presentIds = AttendancePolicy.presentMemberIds(
            startTs,
            categoryTodayAttendances.map { it.memberId to it.timestamp }
        )
        categoryTodayAttendances
            .filter { it.timestamp >= startTs && it.memberId in presentIds }
            .distinctBy { it.memberId }
    }

    val context = LocalContext.current

    // Back handling priority
    if (showQuitSyncDialog) {
        BackHandler {
            if (!isSyncing) {
                showQuitSyncDialog = false
            }
        }
    } else if (userRole == UserRole.COACH && selectedCategory != null) {
        if (isSessionSummaryShown) {
            BackHandler {
                isSessionSummaryShown = false
                viewModel.endSession()
            }
        } else if (isCoachCameraScannerOpen) {
            BackHandler {
                isCoachCameraScannerOpen = false
            }
        } else if (isPreparingSession) {
            BackHandler {
                isPreparingSession = false
            }
        } else if (currentCoachTab != CoachTab.SESSION) {
            BackHandler {
                currentCoachTab = CoachTab.SESSION
            }
        } else if (sessionForSelectedCategory == null) {
            BackHandler {
                viewModel.selectCategory(null)
            }
        } else {
            BackHandler {
                showQuitSyncDialog = true
            }
        }
    } else if (activeAdminSubScreen != AdminSubScreen.NONE) {
        BackHandler {
            activeAdminSubScreen = AdminSubScreen.NONE
        }
    } else if (selectedMemberId != null) {
        BackHandler {
            selectedMemberId = null
        }
    } else if (activeStatFilter != null) {
        BackHandler {
            activeStatFilter = null
        }
    } else if (currentTab != ScreenTab.HOME) {
        BackHandler {
            currentTab = ScreenTab.HOME
        }
    } else {
        // Intercept back key at root screen (Welcome / Category selection or Admin Home)
        BackHandler {
            showQuitSyncDialog = true
        }
    }

    // Fenêtre globale de synchronisation et de confirmation avant de quitter
    if (showQuitSyncDialog) {
        QuitSyncDialog(
            pendingCount = pendingSyncCount,
            supabaseConfig = supabaseConfig,
            isOnline = viewModel.isOnline(),
            isSyncing = isSyncing,
            lastSyncReport = lastSyncReport,
            onPerformSync = { onFinished ->
                viewModel.performFullSync(onFinished)
            },
            onQuitWithoutSync = {
                showQuitSyncDialog = false
                (context as? Activity)?.finish()
            },
            onDismiss = {
                showQuitSyncDialog = false
            }
        )
    }

    // 1. Welcome Screen: Category Selection (when in COACH mode and no category selected yet)
    if (userRole == UserRole.COACH && selectedCategory == null) {
        CategorySelectionScreen(
            categories = categories,
            onSelectCategory = { category ->
                // Une séance en cours n'appartient qu'à la catégorie pour laquelle elle a été
                // démarrée. On la conserve (le coach peut la reprendre en revenant sur sa
                // catégorie) mais elle ne s'affiche jamais dans une autre catégorie.
                viewModel.selectCategory(category)
                currentCoachTab = CoachTab.SESSION
            },
            onAdminAccess = {
                isAdminPinDialogOpen = true
            },
            onQuitApp = {
                showQuitSyncDialog = true
            },
            isDarkTheme = isDarkTheme,
            onToggleTheme = { viewModel.toggleTheme() },
            coaches = coaches
        )

        if (isAdminPinDialogOpen) {
            AdminPinDialog(
                currentPin = adminProfile.pinCode,
                onPinValidated = {
                    isAdminPinDialogOpen = false
                    viewModel.setUserRole(UserRole.ADMIN)
                },
                onDismiss = {
                    isAdminPinDialogOpen = false
                }
            )
        }
        return
    }

    // 2. Coach Flow with selected category & dedicated Coach Navbar
    if (userRole == UserRole.COACH && selectedCategory != null) {
        // Ecran plein ecran de reconnaissance faciale (comme le scanner QR entier).
        if (sessionForSelectedCategory != null && isCoachFacePointageOpen) {
            CoachFacePointageScreen(
                session = sessionForSelectedCategory!!,
                categoryMembers = categoryMembers,
                faceGallery = faceGallery,
                onManualCheckInMember = { member -> viewModel.manualCheckIn(member.id) },
                scanFeedback = coachScanFeedback,
                onDismissFeedback = { viewModel.clearCoachScanFeedback() },
                onBack = { isCoachFacePointageOpen = false }
            )
            return
        }

        if (sessionForSelectedCategory != null && isCoachCameraScannerOpen) {
            CoachDedicatedScannerScreen(
                session = sessionForSelectedCategory!!,
                sessionAttendances = sessionAttendances,
                totalCategoryCount = categoryMembers.size,
                scanFeedback = coachScanFeedback,
                onQrScanned = { code -> viewModel.onQrScanned(code) },
                onBackToSession = { isCoachCameraScannerOpen = false },
                onDismissFeedback = { viewModel.clearCoachScanFeedback() }
            )
            return
        }

        @Composable
        fun CoachScreenBody() {
            when (currentCoachTab) {
                CoachTab.SESSION -> {
                    if (isSessionSummaryShown && sessionForSelectedCategory != null) {
                        // Screen 4: Fin de séance - Récapitulatif
                        CoachSessionSummaryScreen(
                            session = sessionForSelectedCategory!!,
                            categoryMembers = categoryMembers,
                            sessionAttendances = sessionAttendances,
                            clubName = clubSettings.clubName,
                            onValidateAndSave = { endTime: String ->
                                viewModel.saveFinishedSession(
                                    session = sessionForSelectedCategory!!,
                                    endTime = endTime,
                                    categoryMembers = categoryMembers,
                                    presentAttendances = sessionAttendances
                                )
                                isSessionSummaryShown = false
                            },
                            onCancelSession = {
                                isSessionSummaryShown = false
                                viewModel.selectCategory(null)
                            }
                        )
                    } else if (sessionForSelectedCategory != null) {
                        // Screen 3: Pointage pendant entraînement (Statistiques de séance)
                        CoachSessionPointageScreen(
                            session = sessionForSelectedCategory!!,
                            categoryMembers = categoryMembers,
                            sessionAttendances = sessionAttendances,
                            scanFeedback = coachScanFeedback,
                            onOpenScanner = { isCoachCameraScannerOpen = true },
                            onOpenFacePointage = { isCoachFacePointageOpen = true },
                            onManualCheckInMember = { member -> viewModel.manualCheckIn(member.id) },
                            onNumeroScanned = { code -> code?.let { viewModel.onQrScanned(it) } },
                            onFinishSession = { isSessionSummaryShown = true },
                            faceGallery = faceGallery,
                            onDismissFeedback = { viewModel.clearCoachScanFeedback() }
                        )
                    } else if (isPreparingSession) {
                        // Screen 2: Préparation de séance
                        CoachSessionPreparationScreen(
                            category = selectedCategory!!,
                            onBack = { isPreparingSession = false },
                            coaches = coaches,
                            onStartTraining = { session ->
                                viewModel.startSession(session)
                                isPreparingSession = false
                                // Douchette HID (Bluetooth/USB) is active by default on the
                                // pointage screen; the camera scanner stays an optional action.
                                isCoachCameraScannerOpen = false
                            }
                        )
                    } else {
                        // Screen 1: Minimalist Coach Home with prominent "Nouvelle séance" button
                        CoachMinimalHomeScreen(
                            category = selectedCategory!!,
                            onChangeCategory = { viewModel.selectCategory(null) },
                            onStartNewSession = { isPreparingSession = true },
                            coaches = coaches
                        )
                    }
                }
                CoachTab.ATHLETES -> {
                    CoachAthletesScreen(
                        category = selectedCategory!!,
                        members = categoryMembers,
                        todayAttendances = categoryTodayAttendances,
                        onManualCheckIn = { member -> viewModel.manualCheckIn(member.id) }
                    )
                }
                CoachTab.HISTORY -> {
                    CoachHistoryScreen(
                        category = selectedCategory!!,
                        pastSessions = pastSessions,
                        members = allMembers,
                        clubName = clubSettings.clubName
                    )
                }
                CoachTab.PROFILE -> {
                    CoachProfileScreen(
                        category = selectedCategory!!,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { viewModel.toggleTheme() },
                        onChangeCategory = { viewModel.selectCategory(null) },
                        coaches = coaches
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 600.dp
            if (isTablet) {
                // Tablet layout: Coach NavigationRail on left
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("coach_tablet_navigation_rail"),
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 36.sp)) {
                append("🥋\n") // Retour à la ligne après l'emoji
            }
            append("COACH")
        },
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Black,
            color = JudoRed
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 16.dp)
    )
                        }
                    ) {
                        CoachTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentCoachTab == tab,
                                onClick = {
                                    isCoachCameraScannerOpen = false
                                    currentCoachTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            CoachTab.SESSION -> Icons.Default.SportsMartialArts
                                            CoachTab.ATHLETES -> Icons.Default.People
                                            CoachTab.HISTORY -> Icons.Default.History
                                            CoachTab.PROFILE -> Icons.Default.AccountCircle
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = JudoRed,
                                    indicatorColor = JudoRed
                                ),
                                modifier = Modifier.testTag("coach_rail_${tab.name.lowercase()}")
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Theme toggle on coach rail
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Changer thème",
                                tint = if (isDarkTheme) Color(0xFFFFD54F) else JudoRed
                            )
                        }

                        // Quit coach space and go back to role / category selection
                        IconButton(
                            onClick = {
                                isCoachCameraScannerOpen = false
                                isPreparingSession = false
                                isSessionSummaryShown = false
                                currentCoachTab = CoachTab.SESSION
                                // La séance en cours est conservée (elle ne peut plus s'afficher
                                // que dans sa propre catégorie) : revenir sur la même catégorie
                                // permet de la reprendre sans perdre le pointage déjà effectué.
                                viewModel.selectCategory(null)
                            },
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .testTag("coach_rail_quit")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Quitter l'espace coach",
                                tint = JudoRed
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .navigationBarsPadding()
                    ) {
                        CoachScreenBody()
                    }
                }
            } else {
                // Phone layout: Coach Bottom Navigation Bar (4 tabs only: Séance, Athlètes, Historique, Profil)
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.testTag("coach_bottom_navigation_bar")
                        ) {
                            CoachTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentCoachTab == tab,
                                    onClick = {
                                        isCoachCameraScannerOpen = false
                                        currentCoachTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = when (tab) {
                                                CoachTab.SESSION -> Icons.Default.SportsMartialArts
                                                CoachTab.ATHLETES -> Icons.Default.People
                                                CoachTab.HISTORY -> Icons.Default.History
                                                CoachTab.PROFILE -> Icons.Default.AccountCircle
                                            },
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (currentCoachTab == tab) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = JudoRed,
                                        indicatorColor = JudoRed,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("coach_nav_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        CoachScreenBody()
                    }
                }
            }
        }

        // Global CheckIn Confirmation Dialogs
        when (val result = activeCheckInResult) {
            is CheckInResult.Success -> {
                CheckInConfirmationDialog(
                    member = result.member,
                    timeString = result.attendance.timeString,
                    onDismiss = { viewModel.dismissCheckInDialog() },
                    onViewProfile = { _ ->
                        viewModel.dismissCheckInDialog()
                    }
                )
            }
            is CheckInResult.AlreadyCheckedIn -> {
                AlreadyCheckedInDialog(
                    member = result.member,
                    existingTime = result.existingAttendance.timeString,
                    onDismiss = { viewModel.dismissCheckInDialog() }
                )
            }
            is CheckInResult.Registered -> {
                RegistrationOnlyDialog(
                    member = result.member,
                    onDismiss = { viewModel.dismissCheckInDialog() }
                )
            }
            else -> Unit
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // Tablet layout: NavigationRail on left
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .testTag("tablet_navigation_rail"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_asset),
                            contentDescription = "Judo Club Seddouk",
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, JudoGoldBright, CircleShape)
                        )
                    }
                ) {
                    ScreenTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = currentTab == tab && selectedMemberId == null && activeStatFilter == null && activeAdminSubScreen == AdminSubScreen.NONE,
                            onClick = {
                                selectedMemberId = null
                                activeStatFilter = null
                                activeAdminSubScreen = AdminSubScreen.NONE
                                currentTab = tab
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = JudoRed,
                                indicatorColor = JudoRed
                            ),
                            modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Theme toggle on tablet rail
                    IconButton(
                        onClick = { viewModel.toggleTheme() }
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Changer thème",
                            tint = if (isDarkTheme) Color(0xFFFFD54F) else JudoRed
                        )
                    }

                    // Quitter l'espace administrateur : retour à la sélection du rôle / catégorie
                    IconButton(
                        onClick = {
                            selectedMemberId = null
                            activeStatFilter = null
                            activeAdminSubScreen = AdminSubScreen.NONE
                            currentTab = ScreenTab.HOME
                            viewModel.selectCategory(null)
                            viewModel.setUserRole(UserRole.COACH)
                        },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .testTag("admin_rail_quit")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Quitter l'espace administrateur",
                            tint = JudoRed
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .navigationBarsPadding()
                ) {
                    AppScreenContent(
                        currentTab = currentTab,
                        selectedMemberId = selectedMemberId,
                        activeStatFilter = activeStatFilter,
                        activeStatPeriod = activeStatPeriod,
                        dashboardPeriod = dashboardPeriod,
                        activeAdminSubScreen = activeAdminSubScreen,
                        coaches = coaches,
                        categories = categories,
                        clubSettings = clubSettings,
                        adminProfile = adminProfile,
                        pastSessions = pastSessions,
                        notifications = notifications,
                        themePreference = themePreference,
                        allMembers = allMembers,
                        todayAttendance = todayAttendance,
                        recentAttendance = recentAttendance,
                        allAttendance = allAttendance,
                        lastScanned = lastScanned,
                        topStats = topStats,
                        isDarkTheme = isDarkTheme,
                        coachProfile = coachProfile,
                        activeSession = activeSession,
                        onAdminSubScreenChange = { activeAdminSubScreen = it },
                        onDashboardPeriodChange = { dashboardPeriod = it },
                        onActiveStatPeriodChange = { activeStatPeriod = it },
                        onFilterModeChange = { activeStatFilter = it },
                        onEditCoachProfile = { isCoachProfileDialogOpen = true },
                        onToggleTheme = { viewModel.toggleTheme() },
                        onNavigateToTab = { tab ->
                            activeAdminSubScreen = AdminSubScreen.NONE
                            activeStatFilter = null
                            selectedMemberId = null
                            currentTab = tab
                        },
                        onSelectMember = { selectedMemberId = it },
                        onBackFromMemberDetail = { selectedMemberId = null },
                        onStatClick = { filterMode, period ->
                            selectedMemberId = null
                            activeStatFilter = filterMode
                            activeStatPeriod = period
                        },
                        onBackFromStatFilter = { activeStatFilter = null },
                        onEditMember = { memberToEdit = it },
                        onDeleteMember = { viewModel.deleteMember(it) },
                        onSaveMember = { viewModel.saveMember(it) },
                        onDeleteAttendance = { viewModel.deleteAttendance(it) },
                        onManualCheckIn = { viewModel.manualCheckIn(it) },
                        onRecordAttendanceForDate = { id, date -> viewModel.recordAttendanceForDate(id, date) },
                        onDeleteAttendanceForDate = { id, date -> viewModel.deleteAttendanceForDate(id, date) },
                        onSwitchRole = {
                            viewModel.selectCategory(null)
                            viewModel.setUserRole(UserRole.COACH)
                        },
                        onOpenNotifications = {
                            activeAdminSubScreen = AdminSubScreen.ABSENCE_NOTIFICATIONS
                        },
                        onOpenProfileDialog = { isAdminProfileDialogOpen = true },
                        onAddCoach = { viewModel.addCoach(it) },
                        onUpdateCoach = { viewModel.updateCoach(it) },
                        onToggleCoachActive = { viewModel.toggleCoachActive(it) },
                        onDeleteCoach = { viewModel.deleteCoach(it) },
                        onAddCategory = { viewModel.addCategory(it) },
                        onUpdateCategory = { viewModel.updateCategory(it) },
                        onToggleCategoryActive = { viewModel.toggleCategoryActive(it) },
                        onDeleteCategory = { viewModel.deleteCategory(it) },
                        onCorrectAttendance = { sId, mName, pres -> viewModel.correctSessionAttendance(sId, mName, pres) },
                        onSetThemePreference = { viewModel.setThemePreference(it) },
                        onUpdateClubSettings = { viewModel.updateClubSettings(it) },
                        onUpdateAdminProfile = { viewModel.updateAdminProfile(it) },
                        onLogout = {
                            showQuitSyncDialog = true
                        },
                        scheduledSessions = scheduledSessions,
                        onAddScheduledSession = { s, repeat -> viewModel.addScheduledSession(s, repeat) },
                        onUpdateScheduledSession = { s -> viewModel.updateScheduledSession(s) },
                        onDuplicateScheduledSession = { s -> viewModel.duplicateScheduledSession(s) },
                        onDeleteScheduledSession = { id, scope -> viewModel.deleteScheduledSession(id, scope) },
                        onDeletePastSessions = { ids -> viewModel.deletePastSessions(ids) },
                        onCheckSessionConflicts = { s, excludeId -> viewModel.checkSessionConflicts(s, excludeId) },
                        membersSearchQuery = membersSearchQuery,
                        onMembersSearchQueryChange = { membersSearchQuery = it },
                        membersGroupFilter = membersGroupFilter,
                        onMembersGroupFilterChange = { membersGroupFilter = it }
                    )
                }
            }
        } else {
            // Phone layout: Bottom Navigation Bar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        ScreenTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab && selectedMemberId == null && activeStatFilter == null && activeAdminSubScreen == AdminSubScreen.NONE,
                                onClick = {
                                    selectedMemberId = null
                                    activeStatFilter = null
                                    activeAdminSubScreen = AdminSubScreen.NONE
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (currentTab == tab && activeStatFilter == null && activeAdminSubScreen == AdminSubScreen.NONE) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = JudoRed,
                                    indicatorColor = JudoRed,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_bar_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AppScreenContent(
                        currentTab = currentTab,
                        selectedMemberId = selectedMemberId,
                        activeStatFilter = activeStatFilter,
                        activeStatPeriod = activeStatPeriod,
                        dashboardPeriod = dashboardPeriod,
                        activeAdminSubScreen = activeAdminSubScreen,
                        coaches = coaches,
                        categories = categories,
                        clubSettings = clubSettings,
                        adminProfile = adminProfile,
                        pastSessions = pastSessions,
                        notifications = notifications,
                        themePreference = themePreference,
                        allMembers = allMembers,
                        todayAttendance = todayAttendance,
                        recentAttendance = recentAttendance,
                        allAttendance = allAttendance,
                        lastScanned = lastScanned,
                        topStats = topStats,
                        isDarkTheme = isDarkTheme,
                        coachProfile = coachProfile,
                        activeSession = activeSession,
                        onAdminSubScreenChange = { activeAdminSubScreen = it },
                        onDashboardPeriodChange = { dashboardPeriod = it },
                        onActiveStatPeriodChange = { activeStatPeriod = it },
                        onFilterModeChange = { activeStatFilter = it },
                        onEditCoachProfile = { isCoachProfileDialogOpen = true },
                        onToggleTheme = { viewModel.toggleTheme() },
                        onNavigateToTab = { tab ->
                            activeAdminSubScreen = AdminSubScreen.NONE
                            activeStatFilter = null
                            selectedMemberId = null
                            currentTab = tab
                        },
                        onSelectMember = { selectedMemberId = it },
                        onBackFromMemberDetail = { selectedMemberId = null },
                        onStatClick = { filterMode, period ->
                            selectedMemberId = null
                            activeStatFilter = filterMode
                            activeStatPeriod = period
                        },
                        onBackFromStatFilter = { activeStatFilter = null },
                        onEditMember = { memberToEdit = it },
                        onDeleteMember = { viewModel.deleteMember(it) },
                        onSaveMember = { viewModel.saveMember(it) },
                        onDeleteAttendance = { viewModel.deleteAttendance(it) },
                        onManualCheckIn = { viewModel.manualCheckIn(it) },
                        onRecordAttendanceForDate = { id, date -> viewModel.recordAttendanceForDate(id, date) },
                        onDeleteAttendanceForDate = { id, date -> viewModel.deleteAttendanceForDate(id, date) },
                        onSwitchRole = {
                            viewModel.selectCategory(null)
                            viewModel.setUserRole(UserRole.COACH)
                        },
                        onOpenNotifications = {
                            activeAdminSubScreen = AdminSubScreen.ABSENCE_NOTIFICATIONS
                        },
                        onOpenProfileDialog = { isAdminProfileDialogOpen = true },
                        onAddCoach = { viewModel.addCoach(it) },
                        onUpdateCoach = { viewModel.updateCoach(it) },
                        onToggleCoachActive = { viewModel.toggleCoachActive(it) },
                        onDeleteCoach = { viewModel.deleteCoach(it) },
                        onAddCategory = { viewModel.addCategory(it) },
                        onUpdateCategory = { viewModel.updateCategory(it) },
                        onToggleCategoryActive = { viewModel.toggleCategoryActive(it) },
                        onDeleteCategory = { viewModel.deleteCategory(it) },
                        onCorrectAttendance = { sId, mName, pres -> viewModel.correctSessionAttendance(sId, mName, pres) },
                        onSetThemePreference = { viewModel.setThemePreference(it) },
                        onUpdateClubSettings = { viewModel.updateClubSettings(it) },
                        onUpdateAdminProfile = { viewModel.updateAdminProfile(it) },
                        onLogout = {
                            showQuitSyncDialog = true
                        },
                        onClearDatabase = { viewModel.clearDatabase() },
                        scheduledSessions = scheduledSessions,
                        onAddScheduledSession = { s, repeat -> viewModel.addScheduledSession(s, repeat) },
                        onUpdateScheduledSession = { s -> viewModel.updateScheduledSession(s) },
                        onDuplicateScheduledSession = { s -> viewModel.duplicateScheduledSession(s) },
                        onDeleteScheduledSession = { id, scope -> viewModel.deleteScheduledSession(id, scope) },
                        onDeletePastSessions = { ids -> viewModel.deletePastSessions(ids) },
                        onCheckSessionConflicts = { s, excludeId -> viewModel.checkSessionConflicts(s, excludeId) },
                        membersSearchQuery = membersSearchQuery,
                        onMembersSearchQueryChange = { membersSearchQuery = it },
                        membersGroupFilter = membersGroupFilter,
                        onMembersGroupFilterChange = { membersGroupFilter = it }
                    )
                }
            }
        }
    }

    // Admin Dialogs
    if (isNotificationsDialogOpen) {
        NotificationsDialog(
            notifications = notifications,
            onMarkAsRead = { viewModel.markNotificationRead(it) },
            onMarkAllAsRead = { viewModel.markAllNotificationsRead() },
            onDismiss = { isNotificationsDialogOpen = false }
        )
    }

    if (isAdminProfileDialogOpen) {
        AdminProfileEditDialog(
            profile = adminProfile,
            onSave = { viewModel.updateAdminProfile(it) },
            onDismiss = { isAdminProfileDialogOpen = false }
        )
    }

    // Modal dialogs triggered by scan or actions
    when (val result = activeCheckInResult) {
        is CheckInResult.Success -> {
            CheckInConfirmationDialog(
                member = result.member,
                timeString = result.attendance.timeString,
                onDismiss = { viewModel.dismissCheckInDialog() },
                onViewProfile = { memberId ->
                    viewModel.dismissCheckInDialog()
                    selectedMemberId = memberId
                }
            )
        }
        is CheckInResult.AlreadyCheckedIn -> {
            AlreadyCheckedInDialog(
                member = result.member,
                existingTime = result.existingAttendance.timeString,
                onDismiss = { viewModel.dismissCheckInDialog() }
            )
        }
        is CheckInResult.Registered -> {
            RegistrationOnlyDialog(
                member = result.member,
                onDismiss = { viewModel.dismissCheckInDialog() }
            )
        }
        else -> Unit
    }

    // Associate unassigned QR Card Dialog
    if (unassignedQrCode != null && scannedMemberForCreation == null) {
        val pendingCode = unassignedQrCode!!
        AssociateCardDialog(
            qrCode = pendingCode,
            members = allMembers,
            onAssociate = { memberId, code ->
                viewModel.associateQrToMember(memberId, code)
            },
            onCreateNewMemberWithCard = { code ->
                viewModel.cancelUnassignedQr()
                viewModel.extractCardDataForMemberCreation(code)
            },
            onDismiss = { viewModel.cancelUnassignedQr() }
        )
    }

    // Athlete inconnu : information + acceptation AVANT tout ajout (jamais automatique)
    if (pendingMemberRegistration != null) {
        ConfirmAddMemberDialog(
            extracted = pendingMemberRegistration!!.extracted,
            onConfirm = { viewModel.confirmPendingMemberRegistration() },
            onDismiss = { viewModel.cancelPendingMemberRegistration() }
        )
    }

    // Scanned Card Auto-Extraction Dialog for Member Registration!
    if (scannedMemberForCreation != null) {
        AddEditMemberDialog(
            initialMember = null,
            initialExtractedInfo = scannedMemberForCreation,
            coachProfile = coachProfile,
            coaches = coaches,
            categories = categories,
            onEditCoachProfile = { isCoachProfileDialogOpen = true },
            onRequestScanCard = {
                activeStatFilter = null
                selectedMemberId = null
                scannerMode = ScannerTargetMode.EXTRACTION_MEMBRE
                currentTab = ScreenTab.HOME
            },
            findDuplicate = { candidate -> MemberDuplicates.find(allMembers, candidate) },
            onSave = { newMember ->
                viewModel.saveMember(newMember)
                viewModel.clearScannedMemberForCreation()
            },
            onDismiss = { viewModel.clearScannedMemberForCreation() }
        )
    }

    // Edit Member Dialog
    if (memberToEdit != null) {
        AddEditMemberDialog(
            initialMember = memberToEdit,
            coachProfile = coachProfile,
            coaches = coaches,
            categories = categories,
            onEditCoachProfile = { isCoachProfileDialogOpen = true },
            findDuplicate = { candidate -> MemberDuplicates.find(allMembers, candidate) },
            getFaceEnrollmentCount = { memberId ->
                viewModel.faceEnrollmentCount(memberId)
            },
            saveFaceSamples = { memberId, samples, onDone ->
                viewModel.saveFaceSamples(memberId, samples, onDone)
            },
            onSave = { updated ->
                viewModel.saveMember(updated)
                memberToEdit = null
            },
            onDismiss = { memberToEdit = null }
        )
    }

    // Coach / Sensei Profile Dialog
    if (isCoachProfileDialogOpen) {
        CoachProfileDialog(
            coachProfile = coachProfile,
            onSave = { updated ->
                viewModel.updateCoachProfile(updated)
                isCoachProfileDialogOpen = false
            },
            onDismiss = { isCoachProfileDialogOpen = false }
        )
    }
}

@Composable
private fun AppScreenContent(
    currentTab: ScreenTab,
    selectedMemberId: Long?,
    activeStatFilter: StatFilterMode?,
    activeStatPeriod: StatPeriod,
    dashboardPeriod: StatPeriod,
    activeAdminSubScreen: AdminSubScreen,
    coaches: List<CoachUser>,
    categories: List<JudoCategory>,
    clubSettings: ClubSettings,
    adminProfile: AdminProfile,
    pastSessions: List<PastTrainingSession>,
    notifications: List<AdminNotification>,
    themePreference: ThemePreference,
    allMembers: List<Member>,
    todayAttendance: List<com.example.data.model.AttendanceWithMember>,
    recentAttendance: List<com.example.data.model.AttendanceWithMember>,
    allAttendance: List<com.example.data.model.AttendanceWithMember>,
    lastScanned: com.example.data.model.AttendanceWithMember?,
    topStats: List<com.example.data.repository.MemberAttendanceStat>,
    isDarkTheme: Boolean,
    coachProfile: CoachProfile,
    activeSession: ActiveTrainingSession?,
    onAdminSubScreenChange: (AdminSubScreen) -> Unit,
    onDashboardPeriodChange: (StatPeriod) -> Unit,
    onActiveStatPeriodChange: (StatPeriod) -> Unit,
    onFilterModeChange: (StatFilterMode) -> Unit,
    onEditCoachProfile: () -> Unit,
    onToggleTheme: () -> Unit,
    onNavigateToTab: (ScreenTab) -> Unit,
    onSelectMember: (Long) -> Unit,
    onBackFromMemberDetail: () -> Unit,
    onStatClick: (StatFilterMode, StatPeriod) -> Unit,
    onBackFromStatFilter: () -> Unit,
    onEditMember: (Member) -> Unit,
    onDeleteMember: (Member) -> Unit,
    onSaveMember: (Member) -> Unit,
    onDeleteAttendance: (Long) -> Unit,
    onManualCheckIn: (Long) -> Unit,
    onRecordAttendanceForDate: (Long, String) -> Unit = { _, _ -> },
    onDeleteAttendanceForDate: (Long, String) -> Unit = { _, _ -> },
    onSwitchRole: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenProfileDialog: () -> Unit = {},
    onAddCoach: (CoachUser) -> Unit = {},
    onUpdateCoach: (CoachUser) -> Unit = {},
    onToggleCoachActive: (String) -> Unit = {},
    onDeleteCoach: (String) -> Unit = {},
    onAddCategory: (JudoCategory) -> Unit = {},
    onUpdateCategory: (JudoCategory) -> Unit = {},
    onToggleCategoryActive: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onCorrectAttendance: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onSetThemePreference: (ThemePreference) -> Unit = {},
    onUpdateClubSettings: (ClubSettings) -> Unit = {},
    onUpdateAdminProfile: (AdminProfile) -> Unit = {},
    onLogout: () -> Unit = {},
    onClearDatabase: () -> Unit = {},
    scheduledSessions: List<com.example.data.model.ScheduledTrainingSession> = emptyList(),
    onAddScheduledSession: (com.example.data.model.ScheduledTrainingSession, Int) -> Unit = { _, _ -> },
    onUpdateScheduledSession: (com.example.data.model.ScheduledTrainingSession) -> Unit = {},
    onDuplicateScheduledSession: (com.example.data.model.ScheduledTrainingSession) -> Unit = {},
    onDeleteScheduledSession: (String, com.example.data.model.RecurrenceScope) -> Unit = { _, _ -> },
    onDeletePastSessions: (List<String>) -> Unit = {},
    onCheckSessionConflicts: (com.example.data.model.ScheduledTrainingSession, String?) -> List<com.example.data.model.SessionConflict> = { _, _ -> emptyList() },
    supabaseConfig: com.example.data.sync.SupabaseConfig = com.example.data.sync.SupabaseConfig(),
    isSyncing: Boolean = false,
    pendingSyncCount: Int = 0,
    lastSyncReport: com.example.data.sync.SyncReport? = null,
    recentAuditLogs: List<com.example.data.local.sync.SyncAuditLogEntity> = emptyList(),
    isOnline: Boolean = true,
    onUpdateSupabaseConfig: (String, String, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onTestConnection: suspend () -> Result<String> = { Result.success("") },
    onPerformSync: () -> Unit = {},
    onGenerateBackup: suspend () -> String = { "" },
    onRestoreBackup: suspend (String) -> Result<String> = { Result.success("") },
    // Recherche + filtre du groupe hoistés depuis l'onglet Athlètes (conservés entre onglets).
    membersSearchQuery: String = "",
    onMembersSearchQueryChange: (String) -> Unit = {},
    membersGroupFilter: String = "ALL",
    onMembersGroupFilterChange: (String) -> Unit = {}
) {
    // 1. Member Profile Detail Screen
    if (selectedMemberId != null) {
        val member = allMembers.find { it.id == selectedMemberId }
        if (member != null) {
            val memberAttendances = allAttendance
                .filter { it.memberId == selectedMemberId }
                .map {
                    com.example.data.model.Attendance(
                        id = it.attendanceId,
                        memberId = it.memberId,
                        dateString = it.dateString,
                        timeString = it.timeString,
                        timestamp = it.timestamp
                    )
                }

            MemberDetailScreen(
                member = member,
                attendanceList = memberAttendances,
                allClubAttendances = allAttendance,
                categories = categories,
                scheduledSessions = scheduledSessions,
                officialStartDate = clubSettings.officialStartDate,
                onBack = onBackFromMemberDetail,
                onManualCheckIn = onManualCheckIn,
                onRecordAttendanceForDate = onRecordAttendanceForDate,
                onDeleteAttendanceForDate = onDeleteAttendanceForDate,
                onEditMember = onEditMember,
                onDeleteMember = onDeleteMember
            )
            return
        }
    }

    // 2. Stat Filtered Members Screen (Absents, Présents, Inscrits, Taux)
    if (activeStatFilter != null) {
        StatFilteredMembersScreen(
            filterMode = activeStatFilter,
            statPeriod = activeStatPeriod,
            onPeriodChange = onActiveStatPeriodChange,
            onFilterModeChange = onFilterModeChange,
            allMembers = allMembers,
            todayAttendance = todayAttendance,
            allAttendance = allAttendance,
            onSelectMember = onSelectMember,
            onManualCheckIn = onManualCheckIn,
            onBack = onBackFromStatFilter
        )
        return
    }

    // 3. Sub-Screens accessible from Quick Access or Header
    when (activeAdminSubScreen) {
        AdminSubScreen.STATS_DETAIL -> {
            DashboardScreen(
                members = allMembers,
                todayAttendance = todayAttendance,
                allAttendance = allAttendance,
                selectedPeriod = dashboardPeriod,
                onPeriodChange = onDashboardPeriodChange,
                onSelectMember = onSelectMember,
                onStatClick = onStatClick
            )
            return
        }
        AdminSubScreen.ATTENDANCE_OVERVIEW -> {
            AdminAttendanceOverviewScreen(
                allMembers = allMembers,
                todayAttendance = todayAttendance,
                allAttendance = allAttendance,
                categories = categories,
                pastSessions = pastSessions,
                onSelectMember = onSelectMember,
                onBack = { onAdminSubScreenChange(AdminSubScreen.NONE) }
            )
            return
        }
        AdminSubScreen.EXPORTS -> {
            AdminExportsScreen(
                allMembers = allMembers,
                allAttendance = allAttendance,
                todayAttendance = todayAttendance,
                categories = categories,
                onBack = { onAdminSubScreenChange(AdminSubScreen.NONE) }
            )
            return
        }
        AdminSubScreen.SETTINGS -> {
            AdminSettingsScreen(
                clubSettings = clubSettings,
                adminProfile = adminProfile,
                currentThemePreference = themePreference,
                onSetThemePreference = onSetThemePreference,
                onUpdateClubSettings = onUpdateClubSettings,
                onUpdateAdminProfile = onUpdateAdminProfile,
                onNavigateToCloudSync = { onAdminSubScreenChange(AdminSubScreen.CLOUD_SYNC) },
                onLogout = onLogout,
                onClearDatabase = onClearDatabase,
                onBack = { onAdminSubScreenChange(AdminSubScreen.NONE) }
            )
            return
        }
        AdminSubScreen.CLOUD_SYNC -> {
            AdminCloudSyncScreen(
                supabaseConfig = supabaseConfig,
                isOnline = isOnline,
                isSyncing = isSyncing,
                pendingCount = pendingSyncCount,
                lastSyncReport = lastSyncReport,
                recentAuditLogs = recentAuditLogs,
                onUpdateConfig = onUpdateSupabaseConfig,
                onTestConnection = onTestConnection,
                onPerformSync = onPerformSync,
                onGenerateBackup = onGenerateBackup,
                onRestoreBackup = onRestoreBackup,
                onBack = { onAdminSubScreenChange(AdminSubScreen.NONE) }
            )
            return
        }
        AdminSubScreen.ABSENCE_NOTIFICATIONS -> {
            AdminAbsenceNotificationsScreen(
                allMembers = allMembers,
                allAttendance = allAttendance,
                scheduledSessions = scheduledSessions,
                clubSettings = clubSettings,
                onSelectMember = { memberId ->
                    onAdminSubScreenChange(AdminSubScreen.NONE)
                    onSelectMember(memberId)
                },
                onBack = { onAdminSubScreenChange(AdminSubScreen.NONE) }
            )
            return
        }
        AdminSubScreen.NONE -> Unit
    }

    // 4. Primary Admin Tab Screens (Without QR scan or scanner tools)
    when (currentTab) {
        ScreenTab.HOME -> {
            AdminDashboardScreen(
                clubSettings = clubSettings,
                adminProfile = adminProfile,
                allMembers = allMembers,
                todayAttendance = todayAttendance,
                allAttendance = allAttendance,
                categories = categories,
                coaches = coaches,
                pastSessions = pastSessions,
                notifications = notifications,
                scheduledSessions = scheduledSessions,
                isDarkTheme = isDarkTheme,
                onNavigateToAthletes = { onNavigateToTab(ScreenTab.MEMBERS) },
                onNavigateToCoaches = { onNavigateToTab(ScreenTab.COACHES) },
                onNavigateToCategories = { onNavigateToTab(ScreenTab.CATEGORIES) },
                onNavigateToAssignments = { onNavigateToTab(ScreenTab.COACHES) },
                onNavigateToSessions = { onNavigateToTab(ScreenTab.SESSIONS) },
                onNavigateToAttendanceOverview = { onAdminSubScreenChange(AdminSubScreen.ATTENDANCE_OVERVIEW) },
                onNavigateToHistory = { onNavigateToTab(ScreenTab.SESSIONS) },
                onNavigateToStats = { onAdminSubScreenChange(AdminSubScreen.STATS_DETAIL) },
                onNavigateToExports = { onAdminSubScreenChange(AdminSubScreen.EXPORTS) },
                onNavigateToSettings = { onAdminSubScreenChange(AdminSubScreen.SETTINGS) },
                onOpenNotifications = onOpenNotifications,
                onOpenProfileDialog = onOpenProfileDialog,
                onSwitchToCoach = onSwitchRole,
                onLogout = onLogout,
                onSelectMember = onSelectMember
            )
        }
        ScreenTab.MEMBERS -> {
            MembersScreen(
                members = allMembers,
                todayAttendance = todayAttendance,
                coachProfile = coachProfile,
                onEditCoachProfile = onEditCoachProfile,
                onSelectMember = onSelectMember,
                onSaveMember = onSaveMember,
                categories = categories,
                coaches = coaches,
                searchQuery = membersSearchQuery,
                onSearchQueryChange = onMembersSearchQueryChange,
                selectedGroupFilter = membersGroupFilter,
                onSelectedGroupFilterChange = onMembersGroupFilterChange
            )
        }
        ScreenTab.COACHES -> {
            AdminCoachesScreen(
                coaches = coaches,
                categories = categories,
                pastSessions = pastSessions,
                members = allMembers,
                onAddCoach = onAddCoach,
                onUpdateCoach = onUpdateCoach,
                onToggleCoachActive = onToggleCoachActive,
                onDeleteCoach = onDeleteCoach
            )
        }
        ScreenTab.CATEGORIES -> {
            AdminCategoriesScreen(
                categories = categories,
                allMembers = allMembers,
                coaches = coaches,
                onAddCategory = onAddCategory,
                onUpdateCategory = onUpdateCategory,
                onToggleCategoryActive = onToggleCategoryActive,
                onDeleteCategory = onDeleteCategory,
                onViewCategoryMembers = { onNavigateToTab(ScreenTab.MEMBERS) }
            )
        }
        ScreenTab.SESSIONS -> {
            AdminSessionsScreen(
                scheduledSessions = scheduledSessions,
                pastSessions = pastSessions,
                activeSession = activeSession,
                categories = categories,
                coaches = coaches,
                members = allMembers,
                clubName = clubSettings.clubName,
                onAddScheduledSession = onAddScheduledSession,
                onUpdateScheduledSession = onUpdateScheduledSession,
                onDuplicateScheduledSession = onDuplicateScheduledSession,
                onDeleteScheduledSession = onDeleteScheduledSession,
                onDeletePastSessions = onDeletePastSessions,
                onCheckConflicts = onCheckSessionConflicts,
                onCorrectAttendance = onCorrectAttendance
            )
        }
    }
}
