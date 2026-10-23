package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ActiveTrainingSession
import com.example.data.model.AdminNotification
import com.example.data.model.AdminProfile
import com.example.data.model.AttendanceWithMember
import com.example.data.model.ClubSettings
import com.example.data.model.CoachProfile
import com.example.data.model.CoachScanFeedback
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.Member
import com.example.data.model.NotificationType
import com.example.data.model.PastTrainingSession
import com.example.data.model.ScheduledTrainingSession
import com.example.data.model.SessionStatus
import com.example.data.model.RecurrenceScope
import com.example.data.model.SessionConflict
import com.example.data.model.ConflictType
import com.example.data.model.UserRole
import com.example.data.repository.CheckInResult
import com.example.data.repository.JudoRepository
import com.example.data.repository.MemberAttendanceStat
import com.example.ui.screens.StatPeriod
import com.example.util.CardDataParser
import com.example.util.ExtractedMemberInfo
import com.example.util.GroupCodes
import com.example.data.local.CategoryEntity
import com.example.data.local.CoachEntity
import com.example.data.local.PastSessionEntity
import com.example.data.local.ScheduledSessionEntity
import com.example.data.local.sync.SyncAuditLogEntity
import com.example.data.sync.SupabaseConfig
import com.example.data.sync.SupabaseSyncService
import com.example.data.sync.SyncReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemePreference {
    DARK, LIGHT, SYSTEM
}

/**
 * Scan d'un code inconnu portant l'identite d'un athlete : l'ajout doit etre accepte
 * explicitement (fenetre d'information) avant toute creation de fiche.
 */
data class PendingMemberRegistration(
    val extracted: ExtractedMemberInfo
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("judo_seddouk_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getInstance(application)
    val supabaseSyncService = SupabaseSyncService(application, database)
    private val repository = JudoRepository(
        memberDao = database.memberDao(),
        attendanceDao = database.attendanceDao(),
        clubDao = database.clubDao(),
        syncService = supabaseSyncService
    )

    val supabaseConfig: StateFlow<SupabaseConfig> = supabaseSyncService.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, SupabaseConfig())

    val isSyncing: StateFlow<Boolean> = supabaseSyncService.isSyncing
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pendingSyncCount: StateFlow<Int> = supabaseSyncService.pendingCountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val recentAuditLogs: StateFlow<List<SyncAuditLogEntity>> = supabaseSyncService.recentAuditLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _lastSyncReport = MutableStateFlow<SyncReport?>(null)
    val lastSyncReport: StateFlow<SyncReport?> = _lastSyncReport.asStateFlow()

    fun updateSupabaseConfig(url: String, anonKey: String, autoSync: Boolean, dailySync: Boolean) {
        supabaseSyncService.updateConfig(url, anonKey, autoSync, dailySync)
    }

    suspend fun testSupabaseConnection(): Result<String> {
        return supabaseSyncService.testConnection()
    }

    fun performFullSync(onFinished: ((SyncReport) -> Unit)? = null) {
        viewModelScope.launch {
            val report = supabaseSyncService.performFullSync(progressiveRefresh())
            _lastSyncReport.value = report
            if (report.isSuccess && report.itemsReceived > 0) {
                refreshStatesFromRoom()
            }
            onFinished?.invoke(report)
        }
    }

    suspend fun generateBackupJson(): String {
        return supabaseSyncService.generateBackupJson()
    }

    suspend fun restoreBackupJson(jsonString: String): Result<String> {
        return supabaseSyncService.restoreBackupJson(jsonString)
    }

    fun isOnline(): Boolean {
        return supabaseSyncService.isOnline()
    }

    private fun startDaily22hSyncChecker() {
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                try {
                    val cal = java.util.Calendar.getInstance()
                    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val minute = cal.get(java.util.Calendar.MINUTE)
                    if (hour == 22 && minute == 0 && supabaseConfig.value.isDaily22hSyncEnabled && isOnline()) {
                        val report = supabaseSyncService.performFullSync(progressiveRefresh())
                        _lastSyncReport.value = report
                        if (report.isSuccess && report.itemsReceived > 0) {
                            refreshStatesFromRoom()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private val _themePreference = MutableStateFlow(
        try {
            ThemePreference.valueOf(prefs.getString("theme_pref", ThemePreference.LIGHT.name) ?: ThemePreference.LIGHT.name)
        } catch (_: Exception) {
            ThemePreference.LIGHT
        }
    )
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _coachProfile = MutableStateFlow(loadCoachProfile())
    val coachProfile: StateFlow<CoachProfile> = _coachProfile.asStateFlow()

    private fun loadCoachProfile(): CoachProfile {
        return CoachProfile(
            name = prefs.getString("coach_name", "") ?: "",
            title = prefs.getString("coach_title", "EntraÃ®neur Principal") ?: "EntraÃ®neur Principal",
            beltName = prefs.getString("coach_belt", "NOIRE") ?: "NOIRE",
            danGrade = prefs.getString("coach_dan", "Ceinture Noire 4Ã¨me Dan") ?: "Ceinture Noire 4Ã¨me Dan",
            phone = prefs.getString("coach_phone", "0782487120") ?: "0782487120",
            photoUrl = prefs.getString("coach_photo_url", "") ?: "",
            clubName = prefs.getString("coach_club", "JUDO CLUB SEDDOUK") ?: "JUDO CLUB SEDDOUK",
            bio = prefs.getString("coach_bio", "Professeur diplÃ´mÃ© d'Ã‰tat â€¢ Dojo Municipal de Seddouk") ?: "Professeur diplÃ´mÃ© d'Ã‰tat â€¢ Dojo Municipal de Seddouk"
        )
    }

    fun updateCoachProfile(profile: CoachProfile) {
        _coachProfile.value = profile
        prefs.edit()
            .putString("coach_name", profile.name)
            .putString("coach_title", profile.title)
            .putString("coach_belt", profile.beltName)
            .putString("coach_dan", profile.danGrade)
            .putString("coach_phone", profile.phone)
            .putString("coach_photo_url", profile.photoUrl)
            .putString("coach_club", profile.clubName)
            .putString("coach_bio", profile.bio)
            .apply()
        viewModelScope.launch { supabaseSyncService.enqueueClubSettings() }
    }

    fun updateCoachPhoto(photoUrl: String) {
        val updated = _coachProfile.value.copy(photoUrl = photoUrl)
        updateCoachProfile(updated)
    }

    // Categories Management
    // No bundled demo categories: the app starts empty and gets its real content
    // from Room (populated by the Supabase pull). An empty state must never be
    // seeded back into the queue.
    private val _categories = MutableStateFlow<List<JudoCategory>>(emptyList())
    val categories: StateFlow<List<JudoCategory>> = _categories.asStateFlow()

    private val _userRole = MutableStateFlow(UserRole.COACH)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    private val _selectedCategory = MutableStateFlow<JudoCategory?>(null)
    val selectedCategory: StateFlow<JudoCategory?> = _selectedCategory.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveTrainingSession?>(null)
    val activeSession: StateFlow<ActiveTrainingSession?> = _activeSession.asStateFlow()

    private val _coachScanFeedback = MutableStateFlow<CoachScanFeedback?>(null)
    val coachScanFeedback: StateFlow<CoachScanFeedback?> = _coachScanFeedback.asStateFlow()

    fun clearCoachScanFeedback() {
        _coachScanFeedback.value = null
    }

    // No bundled demo history: the history is fed by Room (pull from Supabase).
    private val _pastSessions = MutableStateFlow<List<PastTrainingSession>>(emptyList())
    val pastSessions: StateFlow<List<PastTrainingSession>> = _pastSessions.asStateFlow()

    // Coach Users Management
    // No bundled demo coaches: the coach list comes from Room after the pull.
    private val _coaches = MutableStateFlow<List<CoachUser>>(emptyList())
    val coaches: StateFlow<List<CoachUser>> = _coaches.asStateFlow()

    private fun loadCoachesFromDatabase() {
        viewModelScope.launch {
            try {
                val stored = database.clubDao().getAllCoaches()
                if (stored.isNotEmpty()) {
                    _coaches.value = stored.map { it.toDomain() }
                } else if (_coaches.value.isNotEmpty()) {
                    // First run: seed the local database so coaches (and their photos) sync to the cloud.
                    database.clubDao().insertCoaches(_coaches.value.map { CoachEntity.fromDomain(it) })
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun persistCoach(coach: CoachUser, enqueue: Boolean = true) {
        viewModelScope.launch {
            database.clubDao().insertCoach(CoachEntity.fromDomain(coach))
            if (enqueue) supabaseSyncService.enqueueCoach(CoachEntity.fromDomain(coach))
        }
    }

    fun addCoach(coach: CoachUser) {
        _coaches.value = _coaches.value + coach
        persistCoach(coach)
    }

    fun updateCoach(coach: CoachUser) {
        _coaches.value = _coaches.value.map { if (it.id == coach.id) coach else it }
        persistCoach(coach)
    }

    fun toggleCoachActive(coachId: String) {
        _coaches.value = _coaches.value.map {
            if (it.id == coachId) it.copy(isActive = !it.isActive) else it
        }
        _coaches.value.firstOrNull { it.id == coachId }?.let { persistCoach(it) }
    }

    fun deleteCoach(coachId: String) {
        val removed = _coaches.value.firstOrNull { it.id == coachId }
        _coaches.value = _coaches.value.filterNot { it.id == coachId }
        viewModelScope.launch {
            removed?.let {
                database.clubDao().deleteCoach(CoachEntity.fromDomain(it))
                com.example.util.PhotoCompressor.deleteLocal(it.photoPath)
            }
            supabaseSyncService.enqueueCoachDelete(coachId)
        }
    }

    // Categories Management
    /**
     * Local-first persistence for a single category: written to Room (source of truth) and then
     * queued for the Supabase backup. Mirrors the [persistCoach] pattern.
     */
    private fun persistCategory(category: JudoCategory, enqueue: Boolean = true) {
        viewModelScope.launch {
            try {
                database.clubDao().insertCategory(CategoryEntity.fromDomain(category))
                if (enqueue) supabaseSyncService.enqueueCategory(CategoryEntity.fromDomain(category))
            } catch (_: Exception) {
            }
        }
    }

    private fun loadCategoriesFromDatabase() {
        viewModelScope.launch {
            try {
                val stored = database.clubDao().getAllCategories()
                if (stored.isNotEmpty()) {
                    // Source de verite unique : la base (categories creees par l'admin et
                    // synchronisees depuis Supabase). Aucune categorie inventee par le code,
                    // aucune fusion/renommage automatique.
                    _categories.value = stored.map { it.toDomain() }
                } else {
                    // Base locale vide : on ne fabrique AUCUNE categorie. Le contenu reel
                    // vient du cloud (pull -> Room), jamais du code.
                    _categories.value = emptyList()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun addCategory(category: JudoCategory) {
        _categories.value = _categories.value + category
        persistCategory(category)
    }

    fun updateCategory(category: JudoCategory) {
        _categories.value = _categories.value.map { if (it.id == category.id) category else it }
        persistCategory(category)
    }

    /**
     * Removes a category everywhere it lives: UI state (plus the selection that pointed
     * at it), Room, and the cloud table on the next sync.
     */
    fun deleteCategory(categoryId: String) {
        val removed = _categories.value.firstOrNull { it.id == categoryId }
        _categories.value = _categories.value.filterNot { it.id == categoryId }
        if (_selectedCategory.value?.id == categoryId) {
            _selectedCategory.value = null
        }
        viewModelScope.launch {
            try {
                database.clubDao().deleteCategoryById(categoryId)
            } catch (_: Exception) {
            }
            supabaseSyncService.enqueueDelete("CATEGORIES", categoryId)
        }
    }

    fun toggleCategoryActive(categoryId: String) {
        _categories.value = _categories.value.map {
            if (it.id == categoryId) it.copy(isActive = !it.isActive) else it
        }
        _categories.value.firstOrNull { it.id == categoryId }?.let { persistCategory(it) }
    }

    // Coach assignment to categories
    fun assignCoachToCategory(categoryId: String, coachName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.id == categoryId) {
                if (cat.coach1Name.isBlank() || cat.coach1Name == "Non assignÃ©") {
                    cat.copy(coach1Name = coachName)
                } else {
                    cat.copy(coach2Name = coachName)
                }
            } else cat
        }
        _coaches.value = _coaches.value.map { coach ->
            if (com.example.util.CoachNameKey.same(coach.name, coachName)) {
                val catName = _categories.value.find { it.id == categoryId }?.name ?: categoryId
                if (!coach.assignedCategories.contains(catName)) {
                    coach.copy(assignedCategories = coach.assignedCategories + catName)
                } else coach
            } else coach
        }
        _coaches.value.filter { com.example.util.CoachNameKey.same(it.name, coachName) }.forEach { persistCoach(it) }
        _categories.value.firstOrNull { it.id == categoryId }?.let { persistCategory(it) }
    }

    fun removeCoachFromCategory(categoryId: String, coachName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.id == categoryId) {
                when {
                    com.example.util.CoachNameKey.same(cat.coach1Name, coachName) -> cat.copy(coach1Name = cat.coach2Name, coach2Name = "")
                    com.example.util.CoachNameKey.same(cat.coach2Name, coachName) -> cat.copy(coach2Name = "")
                    else -> cat
                }
            } else cat
        }
        _coaches.value = _coaches.value.map { coach ->
            if (com.example.util.CoachNameKey.same(coach.name, coachName)) {
                val catName = _categories.value.find { it.id == categoryId }?.name ?: categoryId
                coach.copy(assignedCategories = coach.assignedCategories.filterNot { it.equals(catName, ignoreCase = true) })
            } else coach
        }
        _coaches.value.filter { com.example.util.CoachNameKey.same(it.name, coachName) }.forEach { persistCoach(it) }
        _categories.value.firstOrNull { it.id == categoryId }?.let { persistCategory(it) }
    }

    // Scheduled Training Sessions (Weekly Planning & Calendar)
    // No bundled demo planning: the planning comes from Room after the pull.
    private val _scheduledSessions = MutableStateFlow<List<ScheduledTrainingSession>>(emptyList())
    private fun saveScheduledSessionsToPrefs() {
        try {
            val json = serializeScheduledSessions(_scheduledSessions.value)
            prefs.edit().putString("scheduled_sessions_json", json).apply()
        } catch (_: Exception) {}
        mirrorScheduledSessionsToDb()
    }

    /**
     * Single choke point for the scheduled sessions: they stay in SharedPreferences (the format
     * the app reads at start-up) but are also mirrored into Room and queued for the Supabase
     * backup, so the cloud tables are never left empty.
     */
    private fun mirrorScheduledSessionsToDb() {
        val snapshot = _scheduledSessions.value
        viewModelScope.launch {
            try {
                database.clubDao().deleteAllScheduledSessions()
                database.clubDao().insertScheduledSessions(snapshot.map { ScheduledSessionEntity.fromDomain(it) })
                for (session in snapshot) {
                    supabaseSyncService.enqueueScheduledSession(ScheduledSessionEntity.fromDomain(session))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun serializeScheduledSessions(sessions: List<ScheduledTrainingSession>): String {
        val arr = org.json.JSONArray()
        for (s in sessions) {
            val obj = org.json.JSONObject()
            obj.put("id", s.id)
            obj.put("categoryId", s.categoryId)
            obj.put("categoryName", s.categoryName)
            obj.put("groupCode", s.groupCode)
            obj.put("dayOfWeekName", s.dayOfWeekName)
            obj.put("dateString", s.dateString)
            obj.put("formattedDisplayDate", s.formattedDisplayDate)
            obj.put("startTime", s.startTime)
            obj.put("endTime", s.endTime)
            obj.put("coachIds", org.json.JSONArray(s.coachIds))
            obj.put("coachNames", org.json.JSONArray(s.coachNames))
            obj.put("isRecurringWeekly", s.isRecurringWeekly)
            if (s.recurrenceEndDate != null) obj.put("recurrenceEndDate", s.recurrenceEndDate)
            if (s.seriesId != null) obj.put("seriesId", s.seriesId)
            obj.put("notes", s.notes)
            obj.put("status", s.status.name)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeScheduledSessions(jsonStr: String): List<ScheduledTrainingSession> {
        val result = mutableListOf<ScheduledTrainingSession>()
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val coachIdsArr = obj.optJSONArray("coachIds")
                val coachIds = mutableListOf<String>()
                if (coachIdsArr != null) {
                    for (j in 0 until coachIdsArr.length()) coachIds.add(coachIdsArr.getString(j))
                }
                val coachNamesArr = obj.optJSONArray("coachNames")
                val coachNames = mutableListOf<String>()
                if (coachNamesArr != null) {
                    for (j in 0 until coachNamesArr.length()) coachNames.add(coachNamesArr.getString(j))
                }
                result.add(
                    ScheduledTrainingSession(
                        id = obj.getString("id"),
                        categoryId = obj.getString("categoryId"),
                        categoryName = obj.getString("categoryName"),
                        groupCode = obj.optString("groupCode", "U15"),
                        dayOfWeekName = obj.getString("dayOfWeekName"),
                        dateString = obj.optString("dateString", "2026-10-14"),
                        formattedDisplayDate = obj.optString("formattedDisplayDate", ""),
                        startTime = obj.getString("startTime"),
                        endTime = obj.getString("endTime"),
                        coachIds = coachIds,
                        coachNames = coachNames,
                        isRecurringWeekly = obj.optBoolean("isRecurringWeekly", true),
                        recurrenceEndDate = if (obj.has("recurrenceEndDate")) obj.getString("recurrenceEndDate") else null,
                        seriesId = if (obj.has("seriesId")) obj.getString("seriesId") else null,
                        notes = obj.optString("notes", ""),
                        status = try {
                            SessionStatus.valueOf(obj.optString("status", SessionStatus.SCHEDULED.name))
                        } catch (_: Exception) {
                            SessionStatus.SCHEDULED
                        }
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    val scheduledSessions: StateFlow<List<ScheduledTrainingSession>> = _scheduledSessions.asStateFlow()

    init {
        val savedSessionsJson = prefs.getString("scheduled_sessions_json", null)
        if (!savedSessionsJson.isNullOrBlank()) {
            val parsed = deserializeScheduledSessions(savedSessionsJson)
            if (parsed.isNotEmpty()) {
                _scheduledSessions.value = parsed
            }
        }
        viewModelScope.launch {
            // Repare une fois les fiches dont le code QR contient encore la charge utile
            // brute d'une carte (anciennes versions) : "N: 355 ..." -> "JCS-355".
            try {
                repository.repairMalformedQrCodes()
            } catch (_: Exception) {
            }
            if (supabaseConfig.value.isConfigured && isOnline()) {
                val report = supabaseSyncService.performFullSync(progressiveRefresh())
                _lastSyncReport.value = report
                if (report.isSuccess && report.itemsReceived > 0) {
                    refreshStatesFromRoom()
                }
            }
        }
        loadCoachesFromDatabase()
        loadCategoriesFromDatabase()
        loadPastSessionsFromDatabase()
        loadScheduledSessionsFromDatabase()
        startDaily22hSyncChecker()
        startLocalSnapshotWriter()
    }

    /**
     * Sauvegarde locale instantanee : a chaque changement (athlete, presence, categorie,
     * planning, historique) la base complete est ecrite dans le fichier `jcsdb`
     * (stockage de l'app). Debounce court pour ne pas ecrire pendant une rafale de scans.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startLocalSnapshotWriter() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.merge(
                _categories.map { },
                _coaches.map { },
                _scheduledSessions.map { },
                _pastSessions.map { },
                repository.allMembers.map { },
                repository.allAttendance.map { }
            )
                .debounce(1200)
                .collect {
                    try {
                        val json = supabaseSyncService.generateBackupJson()
                        com.example.data.local.LocalSnapshotStore.write(getApplication(), json)
                    } catch (_: Exception) {
                    }
                }
        }
    }

    /**
     * Re-reads every UI-facing collection from Room after a successful pull, so the
     * screen content never lags one app restart behind the cloud content.
     */
    private fun refreshStatesFromRoom() {
        loadCoachesFromDatabase()
        loadCategoriesFromDatabase()
        loadPastSessionsFromDatabase()
        loadScheduledSessionsFromDatabase()
        // Le profil administrateur / coach a pu etre complete par le pull club_settings.
        _adminProfile.value = loadAdminProfile()
        _coachProfile.value = loadCoachProfile()
    }

    /** Rafraichit l'ecran des qu'une section du pull est terminee (planning avant athletes). */
    private fun progressiveRefresh(): () -> Unit = {
        refreshStatesFromRoom()
    }

    /**
     * Loads the scheduled sessions from Room into the UI state. New content can only
     * arrive through the pull (Room) or user mutations (which re-persist on their own),
     * so this stays a plain Room-first reader.
     */
    private fun loadScheduledSessionsFromDatabase() {
        viewModelScope.launch {
            try {
                val stored = database.clubDao().getAllScheduledSessions()
                if (stored.isNotEmpty()) {
                    // Anti-doublon : un meme creneau (categorie + jour + horaires) ne doit
                    // apparaitre qu'une seule fois. Les lignes en trop (anciennes versions qui
                    // declinaient la recurrence semaine par semaine) sont ecartees puis la
                    // version nettoyee est re-miroitee en base.
                    val seen = mutableSetOf<String>()
                    val deduped = mutableListOf<ScheduledTrainingSession>()
                    for (entity in stored) {
                        val session = entity.toDomain()
                        val key = listOf(
                            session.categoryId,
                            session.dayOfWeekName.lowercase(),
                            session.startTime,
                            session.endTime
                        ).joinToString("|")
                        if (seen.add(key)) deduped.add(session)
                    }
                    _scheduledSessions.value = deduped
                    if (deduped.size != stored.size) saveScheduledSessionsToPrefs()
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Ajoute UN seul creneau au planning (une seule ligne en base).
     *
     * Le caractere "toute la saison" est porte par [ScheduledTrainingSession.isRecurringWeekly] :
     * le creneau se repete chaque semaine tout seul. On ne materialise donc plus une ligne par
     * semaine (c'etait la cause des doublons dans le planning).
     */
    @Suppress("UNUSED_PARAMETER")
    fun addScheduledSession(session: ScheduledTrainingSession, repeatWeeks: Int = 1) {
        val seriesId = if (session.isRecurringWeekly) java.util.UUID.randomUUID().toString() else null
        _scheduledSessions.value = _scheduledSessions.value + session.copy(seriesId = seriesId)
        saveScheduledSessionsToPrefs()
    }

    fun duplicateScheduledSession(session: ScheduledTrainingSession, targetDay: String? = null, targetStartTime: String? = null): ScheduledTrainingSession {
        val duplicated = session.copy(
            id = java.util.UUID.randomUUID().toString(),
            dayOfWeekName = targetDay ?: session.dayOfWeekName,
            startTime = targetStartTime ?: session.startTime,
            seriesId = if (session.isRecurringWeekly) java.util.UUID.randomUUID().toString() else null
        )
        _scheduledSessions.value = _scheduledSessions.value + duplicated
        saveScheduledSessionsToPrefs()
        return duplicated
    }

    fun updateScheduledSession(session: ScheduledTrainingSession, scope: RecurrenceScope = RecurrenceScope.THIS_ONLY) {
        _scheduledSessions.value = _scheduledSessions.value.map { s ->
            when {
                s.id == session.id -> session
                scope == RecurrenceScope.ALL_SERIES && session.seriesId != null && s.seriesId == session.seriesId -> {
                    s.copy(
                        startTime = session.startTime,
                        endTime = session.endTime,
                        coachIds = session.coachIds,
                        coachNames = session.coachNames,
                        categoryId = session.categoryId,
                        categoryName = session.categoryName,
                        groupCode = session.groupCode,
                        notes = session.notes
                    )
                }
                else -> s
            }
        }
        saveScheduledSessionsToPrefs()
    }

    fun deleteScheduledSession(sessionId: String, scope: RecurrenceScope = RecurrenceScope.THIS_ONLY) {
        val target = _scheduledSessions.value.find { it.id == sessionId }
        val removed = if (scope == RecurrenceScope.ALL_SERIES && target?.seriesId != null) {
            _scheduledSessions.value.filter { it.seriesId == target.seriesId }
        } else {
            _scheduledSessions.value.filter { it.id == sessionId }
        }
        _scheduledSessions.value = _scheduledSessions.value.filterNot { removedSession ->
            removed.any { it.id == removedSession.id }
        }
        saveScheduledSessionsToPrefs()
        viewModelScope.launch {
            for (session in removed) {
                try {
                    database.clubDao().deleteScheduledSessionById(session.id)
                } catch (_: Exception) {
                }
                supabaseSyncService.enqueueDelete("SCHEDULED_SESSIONS", session.id)
            }
        }
    }

    /**
     * Deletes history entries (multi-selection): the UI state drops them at once, then
     * [deletePastSessionsCascade] removes the local Room rows, the session attendances
     * and queues the cloud DELETEs.
     */
    fun deletePastSessions(sessionIds: List<String>) {
        if (sessionIds.isEmpty()) return
        val idSet = sessionIds.toSet()
        val removed = _pastSessions.value.filter { it.id in idSet }
        _pastSessions.value = _pastSessions.value.filterNot { it.id in idSet }
        viewModelScope.launch {
            try {
                repository.deletePastSessionsCascade(removed.map { PastSessionEntity.fromDomain(it) })
            } catch (_: Exception) {
            }
        }
    }

    fun getNextSessionForCategory(categoryId: String): ScheduledTrainingSession? {
        return _scheduledSessions.value
            .filter { it.categoryId == categoryId && it.status == SessionStatus.SCHEDULED }
            .minByOrNull { it.startTime }
    }

    fun getTodayScheduledSessionForCategory(categoryId: String): ScheduledTrainingSession? {
        val calendar = java.util.Calendar.getInstance()
        val frenchDays = mapOf(
            java.util.Calendar.SATURDAY to "Samedi",
            java.util.Calendar.SUNDAY to "Dimanche",
            java.util.Calendar.MONDAY to "Lundi",
            java.util.Calendar.TUESDAY to "Mardi",
            java.util.Calendar.WEDNESDAY to "Mercredi",
            java.util.Calendar.THURSDAY to "Jeudi",
            java.util.Calendar.FRIDAY to "Vendredi"
        )
        val currentDay = frenchDays[calendar.get(java.util.Calendar.DAY_OF_WEEK)] ?: "Mercredi"
        return _scheduledSessions.value
            .filter { it.categoryId.equals(categoryId, ignoreCase = true) && it.dayOfWeekName.equals(currentDay, ignoreCase = true) && it.status != SessionStatus.CANCELLED }
            .minByOrNull { it.startTime }
    }

    fun checkSessionConflicts(
        newSession: ScheduledTrainingSession,
        excludeSessionId: String? = null
    ): List<SessionConflict> {
        val conflicts = mutableListOf<SessionConflict>()

        val startMin = timeToMinutes(newSession.startTime)
        val endMin = timeToMinutes(newSession.endTime)

        if (startMin < 0 || endMin < 0) {
            conflicts.add(
                SessionConflict(
                    type = ConflictType.INVALID_TIME,
                    message = "Format d'heure invalide. Utilisez le format 24h HH:mm (ex: 17:30).",
                    isBlocking = true
                )
            )
        } else if (endMin <= startMin) {
            conflicts.add(
                SessionConflict(
                    type = ConflictType.INVALID_TIME,
                    message = "L'heure de fin (${newSession.endTime}) doit Ãªtre postÃ©rieure Ã  l'heure de dÃ©but (${newSession.startTime}).",
                    isBlocking = true
                )
            )
        } else {
            val duration = endMin - startMin
            if (duration < 30) {
                conflicts.add(
                    SessionConflict(
                        type = ConflictType.INVALID_DURATION,
                        message = "La durÃ©e minimale d'une sÃ©ance est de 30 minutes ($duration min actuellement).",
                        isBlocking = true
                    )
                )
            } else if (duration > 240) {
                conflicts.add(
                    SessionConflict(
                        type = ConflictType.INVALID_DURATION,
                        message = "La durÃ©e maximale d'une sÃ©ance est de 4 heures ($duration min actuellement).",
                        isBlocking = true
                    )
                )
            }
        }

        val sameDaySessions = _scheduledSessions.value.filter { s ->
            s.id != excludeSessionId &&
            s.status != SessionStatus.CANCELLED &&
            s.dayOfWeekName.equals(newSession.dayOfWeekName, ignoreCase = true)
        }

        for (existing in sameDaySessions) {
            val eStart = timeToMinutes(existing.startTime)
            val eEnd = timeToMinutes(existing.endTime)
            if (eStart < 0 || eEnd < 0) continue

            val overlaps = (startMin < eEnd && endMin > eStart)
            if (overlaps) {
                if (existing.categoryId == newSession.categoryId) {
                    val isExact = (existing.startTime == newSession.startTime && existing.endTime == newSession.endTime)
                    conflicts.add(
                        SessionConflict(
                            type = ConflictType.CATEGORY_OVERLAP,
                            message = if (isExact) {
                                "Doublon interdit : la catÃ©gorie ${newSession.categoryName} a dÃ©jÃ  ce crÃ©neau le ${newSession.dayOfWeekName} (${existing.startTime} - ${existing.endTime})."
                            } else {
                                "Chevauchement horaire : la catÃ©gorie ${newSession.categoryName} a dÃ©jÃ  une sÃ©ance prÃ©vue sur ce crÃ©neau (${existing.startTime} - ${existing.endTime})."
                            },
                            conflictingSession = existing,
                            isBlocking = true
                        )
                    )
                }

                val commonCoaches = newSession.coachNames.filter { cn ->
                    existing.coachNames.any { it.equals(cn, ignoreCase = true) }
                }
                if (commonCoaches.isNotEmpty()) {
                    conflicts.add(
                        SessionConflict(
                            type = ConflictType.COACH_OVERLAP,
                            message = "${commonCoaches.joinToString(", ")} est dÃ©jÃ  affectÃ©(e) Ã  ${existing.categoryName} (${existing.startTime} - ${existing.endTime}).",
                            conflictingSession = existing,
                            isBlocking = false
                        )
                    )
                }
            }
        }

        return conflicts
    }

    private fun timeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size != 2) return -1
            val h = parts[0].trim().toInt()
            val m = parts[1].trim().toInt()
            if (h !in 0..23 || m !in 0..59) return -1
            h * 60 + m
        } catch (_: Exception) {
            -1
        }
    }

    // Club Settings Management
    private fun readClubSettings(): ClubSettings = ClubSettings(
        clubName = prefs.getString("club_name", "Judo Club Seddouk") ?: "Judo Club Seddouk",
        dojoAddress = prefs.getString("club_address", "Dojo Municipal de Seddouk, 06000 Seddouk") ?: "Dojo Municipal de Seddouk, 06000 Seddouk",
        phone = prefs.getString("club_phone", "07 82 48 71 20") ?: "07 82 48 71 20",
        season = prefs.getString("club_season", "2024 - 2025") ?: "2024 - 2025",
        openingHours = prefs.getString("club_hours", "Samedi - Jeudi : 09h00 - 21h00") ?: "Samedi - Jeudi : 09h00 - 21h00",
        officialStartDate = prefs.getString("official_start_date", "") ?: "",
        smsSenderNumber = prefs.getString("sms_sender_number", "") ?: ""
    )

    private val _clubSettings = MutableStateFlow(readClubSettings())
    val clubSettings: StateFlow<ClubSettings> = _clubSettings.asStateFlow()

    fun updateClubSettings(settings: ClubSettings) {
        _clubSettings.value = settings
        prefs.edit()
            .putString("club_name", settings.clubName)
            .putString("club_address", settings.dojoAddress)
            .putString("club_phone", settings.phone)
            .putString("club_season", settings.season)
            .putString("club_hours", settings.openingHours)
            .putString("official_start_date", settings.officialStartDate)
            .putString("sms_sender_number", settings.smsSenderNumber)
            .apply()
        // La date de debut filtre les statistiques : on recharge l'etat affiche
        // immediatement (lecture locale, aucune requete reseau).
        loadPastSessionsFromDatabase()
        viewModelScope.launch { supabaseSyncService.enqueueClubSettings() }
    }

    // Admin Profile Management
    private fun loadAdminProfile(): AdminProfile {
        val savedPin = prefs.getString("admin_pin", "2026") ?: "2026"
        val effectivePin = if (savedPin == "1234") "2026" else savedPin
        return AdminProfile(
            name = prefs.getString("admin_name", "") ?: "",
            phone = prefs.getString("admin_phone", "07 82 48 71 20") ?: "07 82 48 71 20",
            email = prefs.getString("admin_email", "contact@judoclubseddouk.dz") ?: "contact@judoclubseddouk.dz",
            role = prefs.getString("admin_role", "Directeur Technique & Administrateur") ?: "Directeur Technique & Administrateur",
            pinCode = effectivePin,
            photoUri = prefs.getString("admin_photo_uri", null)
        )
    }

    private val _adminProfile = MutableStateFlow(loadAdminProfile())
    val adminProfile: StateFlow<AdminProfile> = _adminProfile.asStateFlow()

    fun updateAdminProfile(profile: AdminProfile) {
        _adminProfile.value = profile
        prefs.edit()
            .putString("admin_name", profile.name)
            .putString("admin_phone", profile.phone)
            .putString("admin_email", profile.email)
            .putString("admin_role", profile.role)
            .putString("admin_pin", profile.pinCode)
            .putString("admin_photo_uri", profile.photoUri)
            .apply()
        viewModelScope.launch { supabaseSyncService.enqueueClubSettings() }
    }

    // Admin Notifications
    private val _notifications = MutableStateFlow<List<AdminNotification>>(
        listOf(
            AdminNotification(
                id = "n1",
                title = "Cotisation en attente",
                message = "3 judokas ont une cotisation mensuelle en retard.",
                timeAgo = "Il y a 10 min",
                type = NotificationType.COTISATION
            ),
            AdminNotification(
                id = "n2",
                title = "SÃ©ance Benjamins terminÃ©e",
                message = "EntraÃ®nement terminÃ© avec 18 prÃ©sents sur 21 (86%).",
                timeAgo = "Hier Ã  19h00",
                type = NotificationType.SESSION
            ),
            AdminNotification(
                id = "n3",
                title = "Certificats mÃ©dicaux",
                message = "2 athlÃ¨tes doivent renouveler leur certificat mÃ©dical ce mois-ci.",
                timeAgo = "Il y a 2 jours",
                type = NotificationType.MEDICAL
            )
        )
    )
    val notifications: StateFlow<List<AdminNotification>> = _notifications.asStateFlow()

    fun markNotificationRead(id: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun markAllNotificationsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    // Past sessions (training history)
    /**
     * Local-first persistence for a single past session: written to Room (source of truth) and
     * queued for the Supabase backup.
     */
    private fun persistPastSession(session: PastTrainingSession) {
        viewModelScope.launch {
            try {
                database.clubDao().insertPastSession(PastSessionEntity.fromDomain(session))
                supabaseSyncService.enqueuePastSession(PastSessionEntity.fromDomain(session))
            } catch (_: Exception) {
            }
        }
    }

    private fun loadPastSessionsFromDatabase() {
        viewModelScope.launch {
            try {
                val stored = database.clubDao().getAllPastSessions()
                if (stored.isNotEmpty()) {
                    // Seules les seances officielles comptent (date de debut reglable).
                    // Rien n'est supprime : la base conserve tout l'historique.
                    val start = _clubSettings.value.officialStartDate
                    _pastSessions.value = stored.map { it.toDomain() }.filter {
                        com.example.util.OfficialPeriod.isOfficial(it.dateString, start)
                    }
                } else {
                    // Aucun historique en dur : base vide => liste vide (le pull fait foi).
                    _pastSessions.value = emptyList()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun correctSessionAttendance(sessionId: String, memberName: String, markAsPresent: Boolean) {
        // Comparaison insensible a la casse : les listes enregistrees (import Excel, fiches
        // renommees) peuvent differer du fullName actuel et la correction doit toujours mordre.
        fun sameName(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

        _pastSessions.value = _pastSessions.value.map { session ->
            if (session.id == sessionId) {
                if (markAsPresent) {
                    val newAbsents = session.absentMemberNames.filter { !sameName(it, memberName) }
                    val newPresents = if (session.presentMemberNames.any { sameName(it, memberName) }) {
                        session.presentMemberNames
                    } else {
                        session.presentMemberNames + memberName
                    }
                    session.copy(
                        presentCount = newPresents.size,
                        absentCount = newAbsents.size,
                        presentMemberNames = newPresents,
                        absentMemberNames = newAbsents
                    )
                } else {
                    val newPresents = session.presentMemberNames.filter { !sameName(it, memberName) }
                    val newAbsents = if (session.absentMemberNames.any { sameName(it, memberName) }) {
                        session.absentMemberNames
                    } else {
                        session.absentMemberNames + memberName
                    }
                    session.copy(
                        presentCount = newPresents.size,
                        absentCount = newAbsents.size,
                        presentMemberNames = newPresents,
                        absentMemberNames = newAbsents
                    )
                }
            } else session
        }
        _pastSessions.value.firstOrNull { it.id == sessionId }?.let { persistPastSession(it) }
    }

    fun selectCategory(category: JudoCategory?) {
        _selectedCategory.value = category
    }

    fun setUserRole(role: UserRole) {
        _userRole.value = role
    }

    fun startSession(session: ActiveTrainingSession) {
        _activeSession.value = session
    }

    fun endSession() {
        _activeSession.value = null
    }

    fun saveFinishedSession(
        session: ActiveTrainingSession,
        endTime: String,
        categoryMembers: List<Member>,
        presentAttendances: List<AttendanceWithMember>
    ) {
        val presentMemberIds = presentAttendances.map { it.memberId }.toSet()
        val presentNames = presentAttendances.map { it.fullName }
        val absentNames = categoryMembers
            .filter { it.id !in presentMemberIds }
            .map { it.fullName }

        val newPastSession = PastTrainingSession(
            categoryId = session.categoryId,
            categoryName = session.categoryName,
            groupCode = _selectedCategory.value?.groupCode ?: "U13",
            dateString = session.startDate,
            startTime = session.startTime,
            endTime = endTime,
            coach1Name = session.coach1Name,
            coach1Present = session.coach1Present,
            coach2Name = session.coach2Name,
            coach2Present = session.coach2Present,
            presentCount = presentAttendances.size,
            absentCount = absentNames.size,
            presentMemberNames = presentNames,
            absentMemberNames = absentNames
        )

        _pastSessions.value = listOf(newPastSession) + _pastSessions.value
        _activeSession.value = null
        persistPastSession(newPastSession)
    }

    val allMembers: StateFlow<List<Member>> = repository.allMembers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayAttendance: StateFlow<List<AttendanceWithMember>> = repository.getTodayAttendance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAttendance: StateFlow<List<AttendanceWithMember>> = repository.recentAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Pointages officiels uniquement : tout ce qui precede la date de debut du pointage
     * officiel (reglage club) est exclu des statistiques, sans rien supprimer de la base.
     */
    val allAttendance: StateFlow<List<AttendanceWithMember>> =
        kotlinx.coroutines.flow.combine(repository.allAttendance, _clubSettings) { rows, settings ->
            val start = settings.officialStartDate
            if (start.isBlank()) rows
            else rows.filter {
                com.example.util.OfficialPeriod.isOfficial(it.dateString, start)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastScanned = MutableStateFlow<AttendanceWithMember?>(null)
    val lastScanned: StateFlow<AttendanceWithMember?> = _lastScanned.asStateFlow()

    private val _activeCheckInResult = MutableStateFlow<CheckInResult?>(null)
    val activeCheckInResult: StateFlow<CheckInResult?> = _activeCheckInResult.asStateFlow()

    private val _unassignedQrCode = MutableStateFlow<String?>(null)
    val unassignedQrCode: StateFlow<String?> = _unassignedQrCode.asStateFlow()

    private val _scannedMemberForCreation = MutableStateFlow<ExtractedMemberInfo?>(null)
    val scannedMemberForCreation: StateFlow<ExtractedMemberInfo?> = _scannedMemberForCreation.asStateFlow()

    /** Athlete inconnu detecte : en attente d'acceptation avant ajout a la liste. */
    private val _pendingMemberRegistration = MutableStateFlow<PendingMemberRegistration?>(null)
    val pendingMemberRegistration: StateFlow<PendingMemberRegistration?> =
        _pendingMemberRegistration.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroupFilter = MutableStateFlow("ALL")
    val selectedGroupFilter: StateFlow<String> = _selectedGroupFilter.asStateFlow()

    private var lastScannedCode: String = ""
    private var lastScannedTimestamp: Long = 0L

    // Secondary guard only: the douchette receiver already prevents a single read from
    // being delivered twice. Never rely on this delay as the sole protection.
    private val SCAN_DUPLICATE_WINDOW_MS = 800L

    fun clearDatabase(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearEntireDatabase()
            prefs.edit().putBoolean("has_initialized_db", true).apply()
            _activeCheckInResult.value = null
            _lastScanned.value = null
            _scannedMemberForCreation.value = null
            _pendingMemberRegistration.value = null
            _unassignedQrCode.value = null
            onComplete()
        }
    }

    fun setThemePreference(pref: ThemePreference) {
        _themePreference.value = pref
        prefs.edit().putString("theme_pref", pref.name).apply()
    }

    fun toggleTheme() {
        val next = if (_themePreference.value == ThemePreference.DARK) ThemePreference.LIGHT else ThemePreference.DARK
        setThemePreference(next)
    }

    private var isProcessingScan: Boolean = false

    fun onQrScanned(code: String) {
        val clean = code.trim()
        if (clean.isBlank()) return

        // The scanner already guarantees a single delivery per physical read; this lock
        // and the identical-code guard are defence in depth against a retransmitted read.
        if (isProcessingScan) return
        val now = System.currentTimeMillis()
        if (clean == lastScannedCode && (now - lastScannedTimestamp) < SCAN_DUPLICATE_WINDOW_MS) {
            return
        }

        isProcessingScan = true
        lastScannedCode = clean
        lastScannedTimestamp = now

        viewModelScope.launch {
            try {
                val session = _activeSession.value
                // Categorie de la seance en cours : sert a refuser un athlete d'un autre groupe.
                val sessionCategory = session?.let { active ->
                    _categories.value.firstOrNull { it.id == active.categoryId }
                }
                val result = repository.processCheckIn(
                    rawCode = clean,
                    method = "QR_SCAN",
                    sessionStartTimestamp = session?.startTimestamp,
                    sessionCategoryName = sessionCategory?.name ?: session?.categoryName,
                    sessionGroupCode = sessionCategory?.groupCode
                )
                // IMPORTANT : aucun ajout automatique d'athlete. Un code inconnu declenche
                // une fenetre d'information a accepter (voir emitUnknownQr).
                // One read â‡’ exactly one feedback, one vibration, one sound.
                triggerHapticFeedback(result)
                when (result) {
                    is CheckInResult.Success -> emitCheckInSuccess(result)
                    is CheckInResult.AlreadyCheckedIn -> emitAlreadyChecked(result)
                    is CheckInResult.WrongCategory -> emitWrongCategory(result)
                    is CheckInResult.UnknownQr -> emitUnknownQr(clean, result)
                    is CheckInResult.Registered -> {
                        _activeCheckInResult.value = result
                        _coachScanFeedback.value = CoachScanFeedback.Invalid(
                            "Fiche crÃ©Ã©e : ${result.member.fullName}. Scannez Ã  nouveau pour pointer."
                        )
                    }
                    is CheckInResult.Error -> {
                        _activeCheckInResult.value = result
                        _coachScanFeedback.value = CoachScanFeedback.Invalid(result.message)
                        playScanSound(false)
                    }
                }
            } finally {
                isProcessingScan = false
            }
        }
    }

    /** Incomplete douchette read (too short / interrupted): a single, non-vibrating hint. */
    fun onIncompleteScan() {
        if (isProcessingScan) return
        _activeCheckInResult.value = null
        _coachScanFeedback.value = CoachScanFeedback.Invalid("Lecture incomplÃ¨te, veuillez rescanner")
    }

    /**
     * No member matches the complete code: never create anything silently.
     * A code carrying an identity opens an explicit confirmation dialog; a bare code
     * falls back to the "associate card" dialog.
     */
    private fun emitUnknownQr(rawCode: String, result: CheckInResult.UnknownQr) {
        val extracted = CardDataParser.parseCardData(rawCode)
        if (extracted.firstName.isNotBlank() || extracted.lastName.isNotBlank()) {
            _pendingMemberRegistration.value = PendingMemberRegistration(extracted)
            _coachScanFeedback.value = CoachScanFeedback.Invalid(
                "AthlÃ¨te non reconnu : validez l'ajout Ã  la liste des membres"
            )
        } else {
            _unassignedQrCode.value = rawCode
            _coachScanFeedback.value = CoachScanFeedback.Invalid("QR code non reconnu dans le club")
        }
        _activeCheckInResult.value = result
        playScanSound(false)
    }

    /** L'utilisateur accepte : ouvre le formulaire pre-rempli (creation explicite). */
    fun confirmPendingMemberRegistration() {
        val pending = _pendingMemberRegistration.value ?: return
        _pendingMemberRegistration.value = null
        _scannedMemberForCreation.value = pending.extracted
    }

    /** L'utilisateur refuse : rien n'est cree. */
    fun cancelPendingMemberRegistration() {
        _pendingMemberRegistration.value = null
        _coachScanFeedback.value = null
    }

    private fun emitCheckInSuccess(result: CheckInResult.Success) {
        _activeCheckInResult.value = result
        _coachScanFeedback.value = CoachScanFeedback.Success(
            member = result.member,
            isCotisationUpToDate = result.member.isCotisationUpToDate,
            timeString = result.attendance.timeString
        )
        playScanSound(true)
        _lastScanned.value = AttendanceWithMember(
            attendanceId = result.attendance.id,
            memberId = result.member.id,
            firstName = result.member.firstName,
            lastName = result.member.lastName,
            groupCode = result.member.groupCode,
            beltName = result.member.beltName,
            dateString = result.attendance.dateString,
            timeString = result.attendance.timeString,
            timestamp = result.attendance.timestamp,
            qrCode = result.member.qrCode,
            phone = result.member.phone
        )
    }

    /**
     * Athlete reconnu mais rattache a une AUTRE categorie : alerte explicite, aucun pointage.
     */
    private fun emitWrongCategory(result: CheckInResult.WrongCategory) {
        _activeCheckInResult.value = null
        _coachScanFeedback.value = CoachScanFeedback.WrongCategory(
            member = result.member,
            memberGroupLabel = GroupCodes.label(result.memberGroupCode)
                .ifBlank { result.memberGroupCode },
            sessionCategoryName = result.sessionCategoryName
        )
        playScanSound(false)
    }

    private fun emitAlreadyChecked(result: CheckInResult.AlreadyCheckedIn) {        _activeCheckInResult.value = result
        _coachScanFeedback.value = CoachScanFeedback.AlreadyChecked(
            member = result.member,
            timeString = result.existingAttendance.timeString
        )
        playScanSound(true)
    }

    fun extractCardDataForMemberCreation(rawCardData: String) {
        val extracted = CardDataParser.parseCardData(rawCardData)
        _scannedMemberForCreation.value = extracted
    }

    fun clearScannedMemberForCreation() {
        _scannedMemberForCreation.value = null
    }

    fun manualCheckIn(memberId: Long) {
        viewModelScope.launch {
            val session = _activeSession.value
            val sessionCategory = session?.let { active ->
                _categories.value.firstOrNull { it.id == active.categoryId }
            }
            val result = repository.manualCheckIn(
                memberId = memberId,
                sessionStartTimestamp = session?.startTimestamp,
                sessionCategoryName = sessionCategory?.name ?: session?.categoryName,
                sessionGroupCode = sessionCategory?.groupCode
            )
            triggerHapticFeedback(result)
            _activeCheckInResult.value = result
            when (result) {
                is CheckInResult.Success -> {
                    _coachScanFeedback.value = CoachScanFeedback.Success(
                        member = result.member,
                        isCotisationUpToDate = result.member.isCotisationUpToDate,
                        timeString = result.attendance.timeString
                    )
                    playScanSound(true)
                    _lastScanned.value = AttendanceWithMember(
                        attendanceId = result.attendance.id,
                        memberId = result.member.id,
                        firstName = result.member.firstName,
                        lastName = result.member.lastName,
                        groupCode = result.member.groupCode,
                        beltName = result.member.beltName,
                        dateString = result.attendance.dateString,
                        timeString = result.attendance.timeString,
                        timestamp = result.attendance.timestamp,
                        qrCode = result.member.qrCode,
                        phone = result.member.phone
                    )
                }
                is CheckInResult.AlreadyCheckedIn -> {
                    _coachScanFeedback.value = CoachScanFeedback.AlreadyChecked(
                        member = result.member,
                        timeString = result.existingAttendance.timeString
                    )
                    playScanSound(true)
                }
                is CheckInResult.WrongCategory -> emitWrongCategory(result)
                else -> {
                    val message = (result as? CheckInResult.Error)?.message
                        ?: "Erreur lors du pointage"
                    _coachScanFeedback.value = CoachScanFeedback.Invalid(message)
                    playScanSound(false)
                }
            }
        }
    }

    private fun playScanSound(isSuccess: Boolean) {
        try {
            val toneType = if (isSuccess) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_NACK
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGenerator.startTone(toneType, 180)
        } catch (_: Exception) {}
    }

    fun recordAttendanceForDate(memberId: Long, dateString: String) {
        viewModelScope.launch {
            repository.recordAttendanceForDate(memberId, dateString)
        }
    }

    fun deleteAttendanceForDate(memberId: Long, dateString: String) {
        viewModelScope.launch {
            repository.deleteAttendanceForMemberAndDate(memberId, dateString)
        }
    }

    fun associateQrToMember(memberId: Long, qrCode: String) {
        viewModelScope.launch {
            // RÃ¨gle : associer une carte Ã  une fiche n'est pas un pointage.
            // Aucune prÃ©sence n'est enregistrÃ©e ici.
            repository.associateQrCodeToMember(memberId, qrCode)
            _unassignedQrCode.value = null
        }
    }

    fun cancelUnassignedQr() {
        _unassignedQrCode.value = null
        if (_activeCheckInResult.value is CheckInResult.UnknownQr) {
            _activeCheckInResult.value = null
        }
    }

    fun dismissCheckInDialog() {
        _activeCheckInResult.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setGroupFilter(groupCode: String) {
        _selectedGroupFilter.value = groupCode
    }

    fun saveMember(member: Member) {
        viewModelScope.launch {
            if (member.id == 0L) {
                // RÃ¨gle : crÃ©er une fiche n'est jamais un pointage. L'athlÃ¨te dÃ©marre
                // Â« Absent / Non pointÃ© Â» dans toutes les sÃ©ances ; seul un scan valide
                // le rend prÃ©sent.
                repository.insertMember(member)
            } else {
                repository.updateMember(member)
            }
        }
    }

    fun deleteMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
        }
    }

    fun deleteAttendance(attendanceId: Long) {
        viewModelScope.launch {
            repository.deleteAttendance(attendanceId)
        }
    }

    fun filterAttendancesByPeriod(
        attendances: List<AttendanceWithMember>,
        period: StatPeriod
    ): List<AttendanceWithMember> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 3600 * 1000L
        val todayStr = repository.getTodayDateString()

        return when (period) {
            StatPeriod.TODAY -> attendances.filter { it.dateString == todayStr }
            StatPeriod.WEEK -> {
                val cutoff = now - (7L * dayMs)
                attendances.filter { it.timestamp >= cutoff }
            }
            StatPeriod.MONTH -> {
                val cutoff = now - (30L * dayMs)
                attendances.filter { it.timestamp >= cutoff }
            }
            StatPeriod.TWO_MONTHS -> {
                val cutoff = now - (60L * dayMs)
                attendances.filter { it.timestamp >= cutoff }
            }
            StatPeriod.SEASON -> attendances
        }
    }

    fun getTopAttendanceList(
        allMembersList: List<Member>,
        allAttendances: List<AttendanceWithMember>,
        period: StatPeriod = StatPeriod.SEASON,
        officialStartDate: String = ""
    ): List<MemberAttendanceStat> {
        if (allMembersList.isEmpty()) return emptyList()
        val periodAttendances = filterAttendancesByPeriod(allAttendances, period)
            .filter { com.example.util.OfficialPeriod.isOfficial(it.dateString, officialStartDate) }
        val attendanceCountByMember = periodAttendances.groupBy { it.memberId }
            .mapValues { it.value.size }

        // Taux reel : nombre de jours de seance effectivement tenus sur la periode
        // (plus de denominateur arbitraire 10 / 18 / 30).
        val sessionsHeld = periodAttendances.map { it.dateString }.distinct().size
        val baselineTotal = sessionsHeld.coerceAtLeast(1)

        return allMembersList.map { member ->
            val count = attendanceCountByMember[member.id] ?: 0
            val pct = if (count == 0) 0 else ((count.toDouble() / baselineTotal.toDouble()) * 100).toInt().coerceIn(1, 100)
            MemberAttendanceStat(
                member = member,
                totalAttendances = count,
                attendancePercentage = pct
            )
        }.sortedWith(
            compareByDescending<MemberAttendanceStat> { it.totalAttendances }
                .thenByDescending { it.attendancePercentage }
        )
    }

    private fun triggerHapticFeedback(result: CheckInResult) {
        try {
            val context = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                val pattern = when (result) {
                    is CheckInResult.Success -> longArrayOf(0, 70, 50, 90)
                    is CheckInResult.AlreadyCheckedIn -> longArrayOf(0, 150)
                    else -> longArrayOf(0, 100, 100, 100)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }
}
