package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.face.FaceDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.CoachEntity
import com.example.data.local.PastSessionEntity
import com.example.data.local.ScheduledSessionEntity
import com.example.data.local.sync.SyncAuditLogEntity
import com.example.data.local.sync.SyncQueueEntity
import com.example.data.local.sync.SyncStatus
import com.example.data.model.Attendance
import com.example.data.model.Member
import com.example.util.PhotoCompressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseSyncService(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        const val DEFAULT_SUPABASE_URL = "https://vkcsqgxsylsuyyszlbko.supabase.co"
        const val DEFAULT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZrY3NxZ3hzeWxzdXl5c3psYmtvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE5OTU3MTcsImV4cCI6MjEwNzU3MTcxN30.xExE0X-0Kx76GxXpLohBHToqvgJC76S6_PT6f3nDpwE"
    }

    private val prefs = context.getSharedPreferences("judo_supabase_prefs", Context.MODE_PRIVATE)

    /** The app's own preferences: club identity, admin/coach profiles (local source of truth). */
    private val appPrefs = context.getSharedPreferences("judo_seddouk_prefs", Context.MODE_PRIVATE)
    private val syncQueueDao = database.syncQueueDao()
    private val auditLogDao = database.syncAuditLogDao()
    private val memberDao = database.memberDao()
    private val attendanceDao = database.attendanceDao()
    private val clubDao = database.clubDao()

    /** Profile photos travel through Supabase Storage, never through a database row. */
    private val storage = SupabaseStorageService()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _config = MutableStateFlow(loadConfig())
    val config: Flow<SupabaseConfig> = _config.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: Flow<Boolean> = _isSyncing.asStateFlow()

    val pendingCountFlow: Flow<Int> = syncQueueDao.countPendingOperationsFlow()
    val recentAuditLogs: Flow<List<SyncAuditLogEntity>> = auditLogDao.getRecentAuditLogs(50)

    private val syncMutex = Mutex()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoSyncJob: Job? = null

    /**
     * Déclenche une synchronisation automatique en arrière-plan lorsque la connexion est active,
     * évitant la perte de données en cas de fermeture inattendue.
     */
    fun triggerBackgroundAutoSync() {
        if (_config.value.isAutoSyncEnabled && isOnline() && !_isSyncing.value) {
            autoSyncJob?.cancel()
            autoSyncJob = syncScope.launch {
                delay(1200L)
                if (isOnline() && !_isSyncing.value) {
                    try {
                        performFullSync()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun loadConfig(): SupabaseConfig {
        val savedUrl = prefs.getString("supabase_url", null)
        val savedKey = prefs.getString("supabase_anon_key", null)
        val url = if (savedUrl.isNullOrBlank()) DEFAULT_SUPABASE_URL else savedUrl.trimEnd('/')
        val key = if (savedKey.isNullOrBlank()) DEFAULT_SUPABASE_ANON_KEY else savedKey.trim()

        return SupabaseConfig(
            url = url,
            anonKey = key,
            isAutoSyncEnabled = prefs.getBoolean("supabase_auto_sync", true),
            isDaily22hSyncEnabled = prefs.getBoolean("supabase_daily_22h_sync", true),
            lastSyncTimestamp = prefs.getLong("supabase_last_sync_timestamp", 0L),
            lastSyncStatus = prefs.getString("supabase_last_sync_status", "CONFIGURÉ") ?: "CONFIGURÉ"
        )
    }

    fun updateConfig(
        url: String,
        anonKey: String,
        isAutoSyncEnabled: Boolean = _config.value.isAutoSyncEnabled,
        isDaily22hSyncEnabled: Boolean = _config.value.isDaily22hSyncEnabled
    ) {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        prefs.edit()
            .putString("supabase_url", cleanUrl)
            .putString("supabase_anon_key", cleanKey)
            .putBoolean("supabase_auto_sync", isAutoSyncEnabled)
            .putBoolean("supabase_daily_22h_sync", isDaily22hSyncEnabled)
            .apply()
        _config.value = loadConfig()
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val cfg = _config.value
        if (!cfg.isConfigured) {
            return@withContext Result.failure(Exception("URL ou Clé API Supabase non renseignée"))
        }
        if (!isOnline()) {
            return@withContext Result.failure(Exception("Aucune connexion Internet disponible sur la tablette"))
        }

        try {
            val request = Request.Builder()
                .url("${cfg.url}/rest/v1/")
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 200..299 || response.code == 404) {
                    val cloudMembers = countRemoteRows(cfg, "members")
                    val cloudCoaches = countRemoteRows(cfg, "coaches")
                    val cloudSessions = countRemoteRows(cfg, "scheduled_sessions")
                    val localMembers = runCatching { memberDao.countMembers() }.getOrDefault(0)
                    val detail = buildString {
                        if (cloudMembers != null) {
                            append("Cloud : $cloudMembers athlètes")
                            if (cloudCoaches != null) append(", $cloudCoaches coachs")
                            if (cloudSessions != null) append(", $cloudSessions créneaux")
                            append(" • Tablette : $localMembers athlètes")
                        }
                    }
                    val base = "Connexion à Supabase réussie (HTTP ${response.code})"
                    Result.success(if (detail.isBlank()) base else "$base\n$detail")
                } else {
                    Result.failure(Exception("Erreur de connexion Supabase (Code HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Impossible de joindre le serveur Supabase : ${e.localizedMessage ?: e.message}"))
        }
    }

    /** Exact row count of a table (null when the count cannot be read). */
    private fun countRemoteRows(cfg: SupabaseConfig, table: String): Int? {
        return try {
            val request = Request.Builder()
                .url("${cfg.url}/rest/v1/$table?select=id")
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .addHeader("Prefer", "count=exact")
                .addHeader("Range", "0-0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val range = response.header("Content-Range") ?: return null
                range.substringAfter('/', "").trim().toIntOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ----------------------------------------------------
    // ENQUEUE HELPERS (LOCAL SQLITE IMMEDIATE WRITE)
    // ----------------------------------------------------
    suspend fun enqueueMember(member: Member, operation: String = "UPSERT") = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", member.id)
            put("qr_code", member.qrCode)
            put("first_name", member.firstName)
            put("last_name", member.lastName)
            put("birth_date", member.birthDate)
            put("phone", member.phone)
            put("group_code", member.groupCode)
            put("belt_name", member.beltName)
            put("status", member.status)
            put("registration_date", member.registrationDate)
            put("photo_url", member.photoUrl)
            // The photo bytes go to Supabase Storage; the row only carries the object key.
            // "photo_local_path" is a local-only helper read by the sync engine (and stripped
            // before the row is sent), so the tablet still keeps the original file.
            put(
                "photo_object",
                if (member.photoUrl.isNotBlank()) SupabaseStorageService.memberPhotoKey(member.id)
                else JSONObject.NULL
            )
            put("photo_local_path", member.photoUrl)
            put("photo_webp", JSONObject.NULL)
            put("notes", member.notes)
            put("updated_at", getIsoNow())
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "MEMBERS",
                entityId = member.id.toString(),
                operation = operation,
                payloadJson = json.toString()
            )
        )
        pushNow("MEMBERS", member.id.toString())
    }

    // NOTE : les pointages (table `attendances`) ne sont volontairement PLUS synchronises.
    // Les presences/absences sont deja sauvegardees par seance dans `past_sessions`
    // (present_members / absent_members) : la table cloud etait donc redondante et
    // gonflait la base (une ligne par scan). Le pointage reste 100 % local (Room).

    suspend fun enqueueCategory(category: CategoryEntity, operation: String = "UPSERT") = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", category.id)
            put("name", category.name)
            put("group_code", category.groupCode)
            put("coach1_name", category.coach1Name)
            put("coach2_name", category.coach2Name)
            put("description", category.description)
            put("is_active", category.isActive)
            put("updated_at", getIsoNow())
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "CATEGORIES",
                entityId = category.id,
                operation = operation,
                payloadJson = json.toString()
            )
        )
        pushNow("CATEGORIES", category.id)
    }

    suspend fun enqueueCoach(coach: CoachEntity, operation: String = "UPSERT") = withContext(Dispatchers.IO) {
        val categoriesArray = JSONArray()
        if (coach.assignedCategoriesJoined.isNotBlank()) {
            coach.assignedCategoriesJoined.split(";").forEach { categoriesArray.put(it) }
        }
        val json = JSONObject().apply {
            put("id", coach.id)
            put("name", coach.name)
            put("grade", coach.grade)
            put("phone", coach.phone)
            put("assigned_categories", categoriesArray)
            put("is_active", coach.isActive)
            put("sessions_count", coach.sessionsCount)
            put(
                "photo_object",
                if (coach.photoPath.isNotBlank()) SupabaseStorageService.coachPhotoKey(coach.id)
                else JSONObject.NULL
            )
            put("photo_local_path", coach.photoPath)
            put("photo_webp", JSONObject.NULL)
            put("updated_at", getIsoNow())
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "COACHES",
                entityId = coach.id,
                operation = operation,
                payloadJson = json.toString()
            )
        )
        pushNow("COACHES", coach.id)
    }

    suspend fun enqueueCoachDelete(coachId: String) = withContext(Dispatchers.IO) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "COACHES",
                entityId = coachId,
                operation = "DELETE",
                payloadJson = "{}"
            )
        )
        pushNow("COACHES", coachId)
    }

    /**
     * Generic cloud-delete operation for any tracked entity type
     * ("CATEGORIES", "PAST_SESSIONS", "ATTENDANCES", "SCHEDULED_SESSIONS", ...).
     * The row is removed from Supabase on the next [performFullSync].
     */
        /**
     * Supprime IMMEDIATEMENT la ligne distante (si le cloud est configure et joignable).
     *
     * La file de synchronisation ne garantit qu'une suppression "au prochain sync" :
     * quand on efface une seance de l'historique, l'utilisateur attend que la ligne
     * disparaisse tout de suite de Supabase. En cas d'echec/hors-ligne, l'appelant
     * doit mettre l'operation en file ([enqueueDelete]) pour reessayer plus tard.
     */
    suspend fun deleteRemoteNow(entityType: String, entityId: String): Boolean = withContext(Dispatchers.IO) {
        val cfg = _config.value
        if (!cfg.isConfigured || entityId.isBlank()) return@withContext false
        try {
            // Un UPSERT encore en file pourrait recreer la ligne apres la suppression.
            syncQueueDao.deletePendingUpserts(entityType, entityId)
            deleteFromSupabase(cfg, mapEntityTypeToSupabaseTable(entityType), entityId)
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Suppression immediate $entityType/$entityId : ${e.message}")
            false
        }
    }

    suspend fun enqueueDelete(entityType: String, entityId: String) = withContext(Dispatchers.IO) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = entityType,
                entityId = entityId,
                operation = "DELETE",
                payloadJson = "{}"
            )
        )
        pushNow(entityType, entityId)
    }

    suspend fun enqueueScheduledSession(session: ScheduledSessionEntity, operation: String = "UPSERT") = withContext(Dispatchers.IO) {
        val coachIdsArray = JSONArray()
        if (session.coachIdsJoined.isNotBlank()) {
            session.coachIdsJoined.split(";").forEach { coachIdsArray.put(it) }
        }
        val coachNamesArray = JSONArray()
        if (session.coachNamesJoined.isNotBlank()) {
            session.coachNamesJoined.split(";").forEach { coachNamesArray.put(it) }
        }
        val json = JSONObject().apply {
            put("id", session.id)
            put("category_id", session.categoryId)
            put("category_name", session.categoryName)
            put("group_code", session.groupCode)
            put("day_of_week_name", session.dayOfWeekName)
            put("date_string", session.dateString)
            put("formatted_display_date", session.formattedDisplayDate)
            put("start_time", session.startTime)
            put("end_time", session.endTime)
            put("coach_ids", coachIdsArray)
            put("coach_names", coachNamesArray)
            put("is_recurring_weekly", session.isRecurringWeekly)
            put("recurrence_end_date", session.recurrenceEndDate ?: JSONObject.NULL)
            put("series_id", session.seriesId ?: JSONObject.NULL)
            put("notes", session.notes)
            put("status", session.status)
            put("updated_at", getIsoNow())
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "SCHEDULED_SESSIONS",
                entityId = session.id,
                operation = operation,
                payloadJson = json.toString()
            )
        )
        pushNow("SCHEDULED_SESSIONS", session.id)
    }

    suspend fun enqueuePastSession(session: PastSessionEntity) = withContext(Dispatchers.IO) {
        val presentArray = JSONArray()
        if (session.presentMembersJoined.isNotBlank()) {
            session.presentMembersJoined.split(";").forEach { presentArray.put(it) }
        }
        val absentArray = JSONArray()
        if (session.absentMembersJoined.isNotBlank()) {
            session.absentMembersJoined.split(";").forEach { absentArray.put(it) }
        }
        val json = JSONObject().apply {
            put("id", session.id)
            put("category_id", session.categoryId)
            put("category_name", session.categoryName)
            put("group_code", session.groupCode)
            put("date_string", session.dateString)
            put("start_time", session.startTime)
            put("end_time", session.endTime)
            put("coach1_name", session.coach1Name)
            put("coach1_present", session.coach1Present)
            put("coach2_name", session.coach2Name)
            put("coach2_present", session.coach2Present)
            put("present_count", session.presentCount)
            put("absent_count", session.absentCount)
            put("present_member_names", presentArray)
            put("absent_member_names", absentArray)
            put("updated_at", getIsoNow())
        }
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "PAST_SESSIONS",
                entityId = session.id,
                operation = "UPSERT",
                payloadJson = json.toString()
            )
        )
        pushNow("PAST_SESSIONS", session.id)
    }

    /**
     * Backs up the settings that only live in SharedPreferences (club identity, admin profile,
     * coach profile, scheduled sessions) as a single `club_settings` row.
     *
     * The admin PIN is deliberately **not** pushed: it is a local credential that grants access
     * to the tablet and has no business being readable from the cloud backup.
     */
    suspend fun enqueueClubSettings() = withContext(Dispatchers.IO) {
        try {
            val coachProfile = JSONObject().apply {
            put("name", appPrefs.getString("coach_name", "") ?: "")
            put("title", appPrefs.getString("coach_title", "") ?: "")
            put("belt", appPrefs.getString("coach_belt", "") ?: "")
            put("dan", appPrefs.getString("coach_dan", "") ?: "")
            put("phone", appPrefs.getString("coach_phone", "") ?: "")
            put("club", appPrefs.getString("coach_club", "") ?: "")
            put("bio", appPrefs.getString("coach_bio", "") ?: "")
            put("photo_object", JSONObject.NULL)
        }

        val scheduledJson = appPrefs.getString("scheduled_sessions_json", null)

        val adminPhotoUri = appPrefs.getString("admin_photo_uri", "") ?: ""

        val json = JSONObject().apply {
            put("id", "default")
            put("club_name", appPrefs.getString("club_name", "") ?: "")
            put("club_address", appPrefs.getString("club_address", "") ?: "")
            put("club_phone", appPrefs.getString("club_phone", "") ?: "")
            put("club_hours", appPrefs.getString("club_hours", "") ?: "")
            put("club_season", appPrefs.getString("club_season", "") ?: "")
            put("official_start_date", appPrefs.getString("official_start_date", "") ?: "")
            put("sms_sender_number", appPrefs.getString("sms_sender_number", "") ?: "")
            put("admin_name", appPrefs.getString("admin_name", "") ?: "")
            put("admin_email", appPrefs.getString("admin_email", "") ?: "")
            put("admin_phone", appPrefs.getString("admin_phone", "") ?: "")
            put("admin_role", appPrefs.getString("admin_role", "") ?: "")
            put(
                "admin_photo_object",
                if (adminPhotoUri.isNotBlank()) SupabaseStorageService.ADMIN_PHOTO_KEY else JSONObject.NULL
            )
            put("coach_profile", coachProfile)
            if (scheduledJson.isNullOrBlank()) {
                put("scheduled_sessions", JSONObject.NULL)
            } else {
                put("scheduled_sessions", JSONArray(scheduledJson))
            }
            // Local-only helper: the sync engine uploads these files to Storage and strips
            // the field before posting the row.
            put("admin_photo_local_path", adminPhotoUri)
            put("admin_photo_bucket", SupabaseStorageService.BUCKET_CLUB_ASSETS)
            put("admin_photo_object_key", SupabaseStorageService.ADMIN_PHOTO_KEY)
            put("updated_at", getIsoNow())
        }

            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "CLUB_SETTINGS",
                    entityId = "default",
                    operation = "UPSERT",
                    payloadJson = json.toString()
                )
            )
            pushNow("CLUB_SETTINGS", "default")
        } catch (e: Exception) {
            Log.w("SupabaseSync", "enqueueClubSettings failed: ${e.message}")
        }
    }

    // ----------------------------------------------------
    // SYNCHRONIZATION EXECUTION (OFFLINE-FIRST TO CLOUD)
    // ----------------------------------------------------
    /**
     * Envoie immediatement une ligne precise de la file vers Supabase (sans attendre
     * la synchronisation complete). Utilise apres chaque modification d'un coach,
     * d'une categorie, d'un athlete ou d'une seance : la base cloud reflete le
     * changement en quelques centaines de millisecondes.
     */
    suspend fun pushNow(entityType: String, entityId: String) = withContext(Dispatchers.IO) {
        val cfg = _config.value
        if (!cfg.isConfigured || !isOnline()) return@withContext
        // On attend le verrou au lieu d'abandonner : une validation de seance doit partir
        // tout de suite, meme si une synchronisation complete est deja en cours. Le verrou
        // garantit qu'aucune ecriture ne se chevauche.
        syncMutex.withLock {
            try {
                pushPendingOperations(cfg) { op ->
                    op.entityType == entityType && op.entityId == entityId
                }
            } catch (e: Exception) {
                Log.w("SupabaseSync", "pushNow($entityType/$entityId) failed: ${e.message}")
            }
        }
    }

    /**
     * Vide la file locale vers Supabase. [only] permet de ne traiter qu'une ligne
     * (envoi immediat) ; null traite toute la file.
     * @return le nombre d'elements envoyes et la premiere erreur rencontree.
     */
    private suspend fun pushPendingOperations(
        cfg: SupabaseConfig,
        only: ((com.example.data.local.sync.SyncQueueEntity) -> Boolean)? = null
    ): Pair<Int, String?> {
        var itemsSent = 0
        var syncError: String? = null
        val pending = syncQueueDao.getPendingOperationsDirect().filter { op ->
            only == null || only(op)
        }
        for (op in pending) {
            try {
                syncQueueDao.updateStatus(op.id, SyncStatus.IN_PROGRESS.name)
                val table = mapEntityTypeToSupabaseTable(op.entityType)

                if (op.operation == "DELETE") {
                    val success = deleteFromSupabase(cfg, table, op.entityId)
                    deleteRemotePhoto(cfg, op.entityType, op.entityId)
                    if (success) {
                        syncQueueDao.updateStatus(op.id, SyncStatus.SYNCED.name)
                        itemsSent++
                        auditLogDao.insert(
                            SyncAuditLogEntity(
                                entityName = op.entityType,
                                entityId = op.entityId,
                                operationType = op.operation,
                                details = "Supprimé de la table $table"
                            )
                        )
                    } else {
                        val errMsg = "Suppression non appliquée côté cloud sur $table (ligne toujours présente)"
                        syncQueueDao.markError(op.id, SyncStatus.PENDING.name, errMsg)
                        if (syncError == null) syncError = errMsg
                    }
                    continue
                }

                // Upload the photo (if any) before publishing the row.
                val prepared = if (op.entityType == "CLUB_SETTINGS") {
                    prepareClubSettingsPayload(cfg, op.payloadJson)
                } else {
                    preparePayloadForPush(cfg, op.entityType, op.payloadJson)
                }
                if (!prepared.ok) {
                    val errMsg = "Photo non téléversée vers Supabase Storage"
                    syncQueueDao.markError(op.id, SyncStatus.PENDING.name, errMsg)
                    if (syncError == null) syncError = errMsg
                    continue
                }

                val success = upsertToSupabase(cfg, table, prepared.json)

                if (success) {
                    syncQueueDao.updateStatus(op.id, SyncStatus.SYNCED.name)
                    itemsSent++
                    auditLogDao.insert(
                        SyncAuditLogEntity(
                            entityName = op.entityType,
                            entityId = op.entityId,
                            operationType = op.operation,
                            details = "Synchronisé avec succès vers table $table"
                        )
                    )
                } else {
                    val errMsg = "Réponse serveur non-2xx sur la table $table"
                    syncQueueDao.markError(op.id, SyncStatus.PENDING.name, errMsg)
                    if (syncError == null) syncError = errMsg
                }
            } catch (e: Exception) {
                syncQueueDao.markError(op.id, SyncStatus.PENDING.name, e.message)
                if (syncError == null) syncError = e.message
            }
        }

        // Clean synced items from queue to keep DB minimal
        syncQueueDao.cleanSyncedOperations()
        return itemsSent to syncError
    }

    suspend fun performFullSync(onSection: (() -> Unit)? = null): SyncReport = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext SyncReport(
                isSuccess = false,
                errorMessage = "Une synchronisation est déjà en cours. Veuillez patienter.",
                pendingRemaining = syncQueueDao.countPendingOperationsDirect()
            )
        }

        val cfg = _config.value
        if (!cfg.isConfigured) {
            return@withContext SyncReport(
                isSuccess = false,
                errorMessage = "Supabase non configuré (renseignez l'URL et la clé anon dans les paramètres)",
                pendingRemaining = syncQueueDao.countPendingOperationsDirect()
            )
        }

        if (!isOnline()) {
            return@withContext SyncReport(
                isSuccess = false,
                errorMessage = "Aucune connexion Internet disponible sur la tablette. Les données restent conservées localement.",
                pendingRemaining = syncQueueDao.countPendingOperationsDirect()
            )
        }

        syncMutex.withLock {
            _isSyncing.value = true
            var itemsSent = 0
            var itemsReceived = 0
            var conflictsResolved = 0
            var syncError: String? = null

            try {
                // Ordering matters both ways:
                // - Queue EMPTY (fresh start): pull FIRST so the cloud fills the local
                //   tables before anything is pushed — pushing a brand-new Room database
                //   used to shadow real cloud rows with whatever a fresh install contained.
                // - Queue NON-EMPTY: push FIRST, so cloud DELETEs (tombstones) land before
                //   the pull runs; a pull executed first would re-add rows whose deletion
                //   is still queued.
                val queueWasPending = syncQueueDao.countPendingOperationsDirect() > 0
                var pulled: PullResult? = null

                if (!queueWasPending) {
                    try {
                        pulled = pullRemoteUpdates(cfg, onSection)
                    } catch (e: Exception) {
                        Log.w("SupabaseSync", "Pull warning: ${e.message}")
                    }
                    itemsReceived = pulled?.itemsReceived ?: 0
                    conflictsResolved = pulled?.conflictsResolved ?: 0
                    // NOTE: on ne "repousse" JAMAIS tout le contenu local quand le pull
                    // ne rapporte rien. C'est ce comportement qui ressuscitait des lignes
                    // supprimees (historique) et saturait la file avec 369 membres a
                    // chaque synchronisation. Ce qui doit monter est deja dans la file,
                    // alimentee par les mutations locales.
                }

                // Process local pending queue (Push)
                val (sentNow, pushError) = pushPendingOperations(cfg)
                itemsSent = sentNow
                if (pushError != null) syncError = pushError

                // Tail pull when the push went first: now that tombstones reached the
                // cloud, the pull cannot resurrect rows whose deletion was queued.
                if (queueWasPending) {
                    try {
                        pulled = pullRemoteUpdates(cfg, onSection)
                        itemsReceived = pulled?.itemsReceived ?: 0
                        conflictsResolved = pulled?.conflictsResolved ?: 0
                    } catch (e: Exception) {
                        Log.w("SupabaseSync", "Pull warning: ${e.message}")
                    }
                } else if (pulled == null) {
                    // The initial pull failed: report the failure without a second pass.
                    Log.w("SupabaseSync", "Initial pull failed; pull skipped this cycle")
                }

                val now = System.currentTimeMillis()
                val remaining = syncQueueDao.countPendingOperationsDirect()
                val isSuccess = remaining == 0 && syncError == null

                if (isSuccess) {
                    prefs.edit()
                        .putLong("supabase_last_sync_timestamp", now)
                        .putString("supabase_last_sync_status", "SYNCHRONISÉ")
                        .apply()
                    _config.value = loadConfig()
                }

                SyncReport(
                    isSuccess = isSuccess,
                    itemsSent = itemsSent,
                    itemsReceived = itemsReceived,
                    conflictsResolved = conflictsResolved,
                    errorMessage = if (!isSuccess) {
                        buildString {
                            append(syncError ?: "Certains éléments n'ont pas pu être synchronisés.")
                            if (remaining > 0) append(" — $remaining opération(s) encore en attente")
                        }
                    } else null,
                    pendingRemaining = remaining,
                    timestamp = now
                )
            } catch (e: Exception) {
                val remaining = syncQueueDao.countPendingOperationsDirect()
                SyncReport(
                    isSuccess = false,
                    itemsSent = itemsSent,
                    errorMessage = e.localizedMessage ?: e.message,
                    pendingRemaining = remaining
                )
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun mapEntityTypeToSupabaseTable(entityType: String): String {
        return when (entityType) {
            "MEMBERS" -> "members"
            "ATTENDANCES" -> "attendances"
            "CATEGORIES" -> "judo_categories"
            "COACHES" -> "coaches"
            "SCHEDULED_SESSIONS" -> "scheduled_sessions"
            "PAST_SESSIONS" -> "past_sessions"
            "CLUB_SETTINGS" -> "club_settings"
            else -> entityType.lowercase()
        }
    }

    /**
     * Uploads the local photo (when the payload carries one) to its Storage bucket and
     * removes the local-only helper field, so the row pushed to PostgREST only references
     * the object.
     *
     * [PreparedPayload.ok] is false when a photo could not be uploaded: the operation is
     * then kept in the queue and retried on the next sync (we never publish a row that
     * points to an object which was not stored).
     */
    private data class PreparedPayload(val json: String, val ok: Boolean)

    private suspend fun preparePayloadForPush(
        cfg: SupabaseConfig,
        entityType: String,
        payloadJson: String
    ): PreparedPayload {
        return try {
            val obj = JSONObject(payloadJson)
            val localPath = obj.optString("photo_local_path", "")
            val objectKey = obj.optString("photo_object", "")
            val bucket = bucketForEntityType(entityType)

            if (localPath.isNotBlank() && bucket != null && objectKey.isNotBlank()) {
                val bytes = PhotoCompressor.readBytes(localPath)
                if (bytes == null || bytes.isEmpty()) {
                    // Unreadable local file: do not advertise a missing object.
                    obj.put("photo_object", JSONObject.NULL)
                } else if (!storage.upload(cfg, bucket, objectKey, bytes)) {
                    return PreparedPayload(payloadJson, false)
                }
            } else if (localPath.isBlank() && bucket != null) {
                // The photo was removed locally: reflect that in the cloud as well.
                obj.put("photo_object", JSONObject.NULL)
            }

            obj.remove("photo_local_path")
            PreparedPayload(obj.toString(), true)
        } catch (e: Exception) {
            Log.w("SupabaseSync", "preparePayloadForPush: ${e.message}")
            PreparedPayload(payloadJson, true)
        }
    }

    private fun bucketForEntityType(entityType: String): String? = when (entityType) {
        "MEMBERS" -> SupabaseStorageService.BUCKET_MEMBER_PHOTOS
        "COACHES" -> SupabaseStorageService.BUCKET_COACH_PHOTOS
        "CLUB_SETTINGS" -> SupabaseStorageService.BUCKET_CLUB_ASSETS
        else -> null
    }

    /**
     * Variant of [preparePayloadForPush] for the single `club_settings` row: the optional
     * administrator avatar is uploaded to the `club-assets` bucket. This one is
     * **best-effort** (the settings themselves are the important payload), so a failed avatar
     * upload simply clears the reference instead of blocking the whole row.
     */
    private suspend fun prepareClubSettingsPayload(
        cfg: SupabaseConfig,
        payloadJson: String
    ): PreparedPayload {
        return try {
            val obj = JSONObject(payloadJson)
            val localPath = obj.optString("admin_photo_local_path", "")
            val objectKey = obj.optString("admin_photo_object_key", "")
            val bucket = obj.optString("admin_photo_bucket", SupabaseStorageService.BUCKET_CLUB_ASSETS)

            if (localPath.isNotBlank() && objectKey.isNotBlank()) {
                val bytes = PhotoCompressor.readBytes(localPath)
                if (bytes != null && bytes.isNotEmpty() && storage.upload(cfg, bucket, objectKey, bytes)) {
                    obj.put("admin_photo_object", objectKey)
                } else {
                    // Ne JAMAIS effacer la photo deja stockee dans le cloud quand la copie locale
                    // est illisible (ancien content://, fichier purge...) : on omet la colonne,
                    // l'upsert conserve donc la valeur distante existante.
                    obj.remove("admin_photo_object")
                }
            } else {
                obj.remove("admin_photo_object")
            }

            obj.remove("admin_photo_local_path")
            obj.remove("admin_photo_bucket")
            obj.remove("admin_photo_object_key")
            PreparedPayload(obj.toString(), true)
        } catch (e: Exception) {
            Log.w("SupabaseSync", "prepareClubSettingsPayload: ${e.message}")
            PreparedPayload(payloadJson, true)
        }
    }

    /** Best-effort removal of the avatar object when a member / coach is deleted. */
    private fun deleteRemotePhoto(cfg: SupabaseConfig, entityType: String, entityId: String) {
        try {
            when (entityType) {
                "MEMBERS" -> {
                    val id = entityId.toLongOrNull() ?: return
                    storage.delete(
                        cfg,
                        SupabaseStorageService.BUCKET_MEMBER_PHOTOS,
                        SupabaseStorageService.memberPhotoKey(id)
                    )
                }
                "COACHES" -> storage.delete(
                    cfg,
                    SupabaseStorageService.BUCKET_COACH_PHOTOS,
                    SupabaseStorageService.coachPhotoKey(entityId)
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun upsertToSupabase(cfg: SupabaseConfig, table: String, jsonPayload: String): Boolean {
        val url = "${cfg.url}/rest/v1/$table"
        var payload = jsonPayload

        // Si le cloud n'a pas encore une colonne (ex. `admin_photo_object` ajoutee apres coup),
        // PostgREST rejette TOUTE la ligne. On retire alors uniquement la colonne fautive et on
        // reessaie : le profil administrateur (nom, telephone, e-mail) est sauvegarde même si la
        // photo ne peut pas encore l'etre, au lieu de perdre l'ensemble du reglage.
        repeat(6) {
            val body = payload.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()

            var detail: String? = null
            var code = 0
            try {
                httpClient.newCall(request).execute().use { response ->
                    code = response.code
                    if (response.isSuccessful || response.code in 200..299) return true
                    // Le detail serveur (RLS, type de colonne, contrainte) est la seule facon de
                    // diagnostiquer un refus : on le journalise sans jamais logguer la cle.
                    detail = try {
                        response.body?.string()?.take(400)
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.w("SupabaseSync", "UPSERT $table error: ${e.message}")
                return false
            }

            Log.w("SupabaseSync", "UPSERT $table -> HTTP $code ${detail.orEmpty()}")

            val unknownColumn = Regex(
                "\"([A-Za-z0-9_]+)\" column does not exist|" +
                    "Could not find the '([A-Za-z0-9_]+)' column|" +
                    "column \"([A-Za-z0-9_]+)\" of relation"
            ).find(detail.orEmpty())?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
                ?: return false

            val obj = try {
                JSONObject(payload)
            } catch (_: Exception) {
                return false
            }
            if (!obj.has(unknownColumn)) return false
            obj.remove(unknownColumn)
            payload = obj.toString()
            Log.w("SupabaseSync", "UPSERT $table: colonne '$unknownColumn' absente du cloud, nouvel essai")
        }
        return false
    }

    /**
     * Supprime une ligne distante puis VERIFIE qu'elle a bien disparu.
     *
     * PostgREST repond 204 meme quand aucune ligne n'est touchee (id absent, RLS...) :
     * sans verification, l'app considerait la suppression "reussie" et retirait
     * l'operation de la file alors que la ligne restait dans Supabase.
     */
    private fun deleteFromSupabase(cfg: SupabaseConfig, table: String, id: String): Boolean {
        val encodedId = java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")
        val url = "${cfg.url}/rest/v1/$table?id=eq.$encodedId"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", cfg.anonKey)
            .addHeader("Authorization", "Bearer ${cfg.anonKey}")
            .addHeader("Prefer", "return=representation")
            .delete()
            .build()

        val deleted = try {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..299
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "DELETE $table/$id : ${e.message}")
            false
        }
        if (!deleted) return false

        // Verification : la ligne doit avoir reellement disparu.
        val verify = getRemoteArray(cfg, "$table?select=id&id=eq.$encodedId") ?: return true
        return verify.length() == 0
    }

    private data class PullResult(val itemsReceived: Int, val conflictsResolved: Int)

    /** GETs a PostgREST path and parses it as a JSON array (null on any error). */
    private fun getRemoteArray(cfg: SupabaseConfig, path: String): JSONArray? {
        return try {
            val request = Request.Builder()
                .url("${cfg.url}/rest/v1/$path")
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                if (body.isBlank() || !body.startsWith("[")) null else JSONArray(body)
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "GET $path failed: ${e.message}")
            null
        }
    }

    private suspend fun pullRemoteUpdates(
        cfg: SupabaseConfig,
        onSection: (() -> Unit)? = null
    ): PullResult {
        var received = 0
        var conflicts = 0

        // Pull coaches
        try {
            val url = "${cfg.url}/rest/v1/coaches?select=id,name,grade,phone,assigned_categories,is_active,sessions_count,photo_object"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isNotBlank() && bodyString.startsWith("[")) {
                        val arr = JSONArray(bodyString)
                        val localById = clubDao.getAllCoaches().associateBy { it.id }
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val id = obj.optString("id", "")
                            if (id.isBlank()) continue

                            val catsArray = obj.optJSONArray("assigned_categories") ?: JSONArray()
                            val cats = (0 until catsArray.length())
                                .mapNotNull { catsArray.optString(it) }
                                .filter { it.isNotBlank() }
                                .joinToString(";")

                            // Local is the source of truth: only restore coaches the tablet
                            // does not have yet.
                            if (localById[id] != null) continue

                            var photoPath = ""
                            val photoObject = obj.optString("photo_object", "")
                            if (photoObject.isNotBlank()) {
                                val bytes = storage.download(
                                    cfg,
                                    SupabaseStorageService.BUCKET_COACH_PHOTOS,
                                    photoObject
                                )
                                if (bytes != null) {
                                    photoPath = PhotoCompressor.saveBytes(context, "coach_$id", bytes) ?: ""
                                }
                            }

                            clubDao.insertCoach(
                                CoachEntity(
                                    id = id,
                                    name = obj.optString("name", ""),
                                    grade = obj.optString("grade", ""),
                                    phone = obj.optString("phone", ""),
                                    assignedCategoriesJoined = cats,
                                    isActive = obj.optBoolean("is_active", true),
                                    sessionsCount = obj.optInt("sessions_count", 0),
                                    photoPath = photoPath
                                )
                            )
                            received++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Error pulling coaches: ${e.message}")
        }
        // Rafraichissement progressif : le planning / les coachs / les categories doivent
        // apparaitre a l'ecran AVANT le (lourd) telechargement des athletes.
        onSection?.invoke()

        // --- Categories (source of truth = cloud) ---
        try {
            getRemoteArray(
                cfg,
                "judo_categories?select=id,name,group_code,coach1_name,coach2_name,description,is_active"
            )?.let { arr ->
                val remoteIds = HashSet<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    if (id.isBlank()) continue
                    remoteIds.add(id)
                    clubDao.insertCategory(
                        CategoryEntity(
                            id = id,
                            name = obj.optString("name", ""),
                            groupCode = obj.optString("group_code", ""),
                            coach1Name = obj.optString("coach1_name", ""),
                            coach2Name = obj.optString("coach2_name", ""),
                            description = obj.optString("description", ""),
                            isActive = obj.optBoolean("is_active", true)
                        )
                    )
                    received++
                }
                // Aucune categorie locale hors cloud : les reliquats (anciennes categories
                // integrees ou doublons) sont supprimes pour ne jamais apparaitre deux fois.
                clubDao.getAllCategories()
                    .filter { it.id !in remoteIds }
                    .forEach { clubDao.deleteCategory(it) }
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Error pulling categories: ${e.message}")
        }
        onSection?.invoke()

        // --- Scheduled sessions (planning : le cloud fait foi) ---
        try {
            getRemoteArray(
                cfg,
                "scheduled_sessions?select=id,category_id,category_name,group_code,day_of_week_name," +
                    "date_string,formatted_display_date,start_time,end_time,coach_ids,coach_names," +
                    "is_recurring_weekly,recurrence_end_date,series_id,notes,status"
            )?.let { arr ->
                if (arr.length() > 0) {
                    val remoteIds = HashSet<String>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id", "")
                        if (id.isBlank()) continue
                        remoteIds.add(id)
                        clubDao.insertScheduledSession(
                            ScheduledSessionEntity(
                                id = id,
                                categoryId = obj.optString("category_id", ""),
                                categoryName = obj.optString("category_name", ""),
                                groupCode = obj.optString("group_code", ""),
                                dayOfWeekName = obj.optString("day_of_week_name", ""),
                                dateString = obj.optString("date_string", ""),
                                formattedDisplayDate = obj.optString("formatted_display_date", ""),
                                startTime = obj.optString("start_time", ""),
                                endTime = obj.optString("end_time", ""),
                                coachIdsJoined = jsonArrayToJoined(obj.optJSONArray("coach_ids")),
                                coachNamesJoined = jsonArrayToJoined(obj.optJSONArray("coach_names")),
                                isRecurringWeekly = obj.optBoolean("is_recurring_weekly", false),
                                recurrenceEndDate = obj.optString("recurrence_end_date", "").ifBlank { null },
                                seriesId = obj.optString("series_id", "").ifBlank { null },
                                notes = obj.optString("notes", ""),
                                status = obj.optString("status", "SCHEDULED")
                            )
                        )
                        received++
                    }
                    // Le planning programme (admin > seances) remplace l'ancien : les seances
                    // locales qui n'existent plus cote cloud (demo / doublons) sont supprimees.
                    clubDao.getAllScheduledSessions()
                        .filter { it.id !in remoteIds }
                        .forEach { clubDao.deleteScheduledSessionById(it.id) }
                }
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Error pulling scheduled sessions: ${e.message}")
        }

        // --- Membres (en dernier : planning, coachs et categories sont deja a l'ecran) ---
        try {
            val (membersReceived, membersConflicts) = pullMembers(cfg)
            received += membersReceived
            conflicts += membersConflicts
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Error pulling members: ${e.message}")
        }
        onSection?.invoke()
        // --- Past sessions (le cloud fait foi sauf edition locale en attente) ---
        try {
            getRemoteArray(
                cfg,
                "past_sessions?select=id,category_id,category_name,group_code,date_string,start_time," +
                    "end_time,coach1_name,coach1_present,coach2_name,coach2_present,present_count," +
                    "absent_count,present_member_names,absent_member_names"
            )?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    if (id.isBlank()) continue
                    // Une correction faite ici mais pas encore poussee ne doit pas etre ecrasee.
                    if (syncQueueDao.countPendingForEntity("PAST_SESSIONS", id) > 0) continue
                    clubDao.insertPastSession(
                        PastSessionEntity(
                            id = id,
                            categoryId = obj.optString("category_id", ""),
                            categoryName = obj.optString("category_name", ""),
                            groupCode = obj.optString("group_code", ""),
                            dateString = obj.optString("date_string", ""),
                            startTime = obj.optString("start_time", ""),
                            endTime = obj.optString("end_time", ""),
                            coach1Name = obj.optString("coach1_name", ""),
                            coach1Present = obj.optBoolean("coach1_present", false),
                            coach2Name = obj.optString("coach2_name", ""),
                            coach2Present = obj.optBoolean("coach2_present", false),
                            presentCount = obj.optInt("present_count", 0),
                            absentCount = obj.optInt("absent_count", 0),
                            presentMembersJoined = jsonArrayToJoined(obj.optJSONArray("present_member_names")),
                            absentMembersJoined = jsonArrayToJoined(obj.optJSONArray("absent_member_names"))
                        )
                    )
                    received++
                }
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Error pulling past sessions: ${e.message}")
        }

        // --- Attendances : plus de pull ---
        // La table `attendances` a ete retiree du cloud : les presences officielles sont
        // lues dans `past_sessions` (present_members / absent_members).

        // --- Club settings (fill-missing only: blank local values are completed) ---
        try {
            getRemoteArray(cfg, "club_settings?select=*&id=eq.default")?.let { arr ->
                if (arr.length() > 0) {
                    val obj = arr.optJSONObject(0)
                    if (obj != null) {
                        val editor = appPrefs.edit()
                        var changed = false
                        fun fill(key: String, remote: String) {
                            if (remote.isNotBlank() && (appPrefs.getString(key, "") ?: "").isBlank()) {
                                editor.putString(key, remote)
                                changed = true
                            }
                        }
                        fill("club_name", obj.optString("club_name", ""))
                        fill("club_address", obj.optString("club_address", ""))
                        fill("club_phone", obj.optString("club_phone", ""))
                        fill("club_hours", obj.optString("club_hours", ""))
                        fill("club_season", obj.optString("club_season", ""))
                        fill("official_start_date", obj.optString("official_start_date", ""))
                        fill("sms_sender_number", obj.optString("sms_sender_number", ""))
                        fill("admin_name", obj.optString("admin_name", ""))
                        fill("admin_email", obj.optString("admin_email", ""))
                        fill("admin_phone", obj.optString("admin_phone", ""))
                        fill("admin_role", obj.optString("admin_role", ""))
                        // The administrator avatar is stored as an object: fetch it only when
                        // the device has no photo yet.
                        val adminPhotoObject = obj.optString("admin_photo_object", "")
                        val localAdminPhoto = appPrefs.getString("admin_photo_uri", "") ?: ""
                        val localAdminPhotoExists = localAdminPhoto
                            .removePrefix("file://")
                            .takeIf { it.isNotBlank() }
                            ?.let { java.io.File(it).exists() } == true
                        if (adminPhotoObject.isNotBlank() && !localAdminPhotoExists) {
                            val bytes = storage.download(
                                cfg,
                                SupabaseStorageService.BUCKET_CLUB_ASSETS,
                                adminPhotoObject
                            )
                            if (bytes != null) {
                                PhotoCompressor.saveBytes(context, "admin_profile", bytes)?.let {
                                    editor.putString("admin_photo_uri", it)
                                    changed = true
                                }
                            }
                        }
                        // Scheduled sessions JSON is only restored when this device has none.
                        if ((appPrefs.getString("scheduled_sessions_json", "") ?: "").isBlank()) {
                            val remoteSessions = obj.optJSONArray("scheduled_sessions")
                            if (remoteSessions != null && remoteSessions.length() > 0) {
                                editor.putString("scheduled_sessions_json", remoteSessions.toString())
                                changed = true
                            }
                        }
                        if (changed) {
                            editor.apply()
                            received++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Error pulling club settings: ${e.message}")
        }
        onSection?.invoke()

        return PullResult(received, conflicts)
    }

    /**
     * Telecharge les athletes en DERNIER : le planning, les coachs et les categories sont
     * deja appliques et affiches avant ce traitement (381 fiches + leurs photos).
     */
    private suspend fun pullMembers(cfg: SupabaseConfig): Pair<Int, Int> {
        var received = 0
        var conflicts = 0
        try {
            // Explicit column list: the legacy (potentially large) photo_webp column is never
            // transferred — photos live in Storage and are downloaded only on demand.
            val url = "${cfg.url}/rest/v1/members?select=id,qr_code,first_name,last_name,birth_date,phone,group_code,belt_name,status,registration_date,photo_object,notes"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .addHeader("Prefer", "count=exact")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isNotBlank() && bodyString.startsWith("[")) {
                        val arr = JSONArray(bodyString)
                        val remoteIds = HashSet<Long>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val id = obj.optLong("id", 0L)
                            val qrCode = obj.optString("qr_code", "")
                            if (id <= 0 || qrCode.isBlank()) continue
                            remoteIds.add(id)

                            // The cloud row wins only when this device has no pending edit for it.
                            if (syncQueueDao.countPendingForEntity("MEMBERS", id.toString()) > 0) {
                                conflicts++
                                continue
                            }

                            val photoObject = obj.optString("photo_object", "")
                            val cloud = Member(
                                id = id,
                                qrCode = qrCode,
                                firstName = obj.optString("first_name", ""),
                                lastName = obj.optString("last_name", ""),
                                birthDate = obj.optString("birth_date", ""),
                                phone = obj.optString("phone", ""),
                                groupCode = obj.optString("group_code", "U15"),
                                beltName = obj.optString("belt_name", "BLANCHE"),
                                status = obj.optString("status", "ACTIF"),
                                registrationDate = obj.optString("registration_date", "2026-09-01"),
                                photoUrl = "",
                                notes = obj.optString("notes", "")
                            )

                            val local = memberDao.getMemberByIdDirect(id)
                            if (local != null) {
                                var photoUrl = local.photoUrl
                                if (photoUrl.isBlank() && photoObject.isNotBlank()) {
                                    val bytes = storage.download(
                                        cfg,
                                        SupabaseStorageService.BUCKET_MEMBER_PHOTOS,
                                        photoObject
                                    )
                                    if (bytes != null) {
                                        photoUrl = PhotoCompressor.saveBytes(context, "member_$id", bytes) ?: ""
                                    }
                                }
                                val synced = cloud.copy(photoUrl = photoUrl)
                                if (synced != local) {
                                    memberDao.updateMember(synced)
                                    received++
                                }
                                continue
                            }

                            var photoUrl = ""
                            if (photoObject.isNotBlank()) {
                                val bytes = storage.download(
                                    cfg,
                                    SupabaseStorageService.BUCKET_MEMBER_PHOTOS,
                                    photoObject
                                )
                                if (bytes != null) {
                                    photoUrl = PhotoCompressor.saveBytes(context, "member_$id", bytes) ?: ""
                                }
                            }

                            memberDao.insertMember(cloud.copy(photoUrl = photoUrl))
                            received++
                        }

                        // Drop ghosts (rows deleted on another device). Only when the response is
                        // complete and this device has nothing left to push for members.
                        val total = response.header("Content-Range")
                            ?.substringAfter('/', "")
                            ?.toIntOrNull()
                        val complete = total == null || total == arr.length()
                        if (complete && syncQueueDao.countPendingForType("MEMBERS") == 0) {
                            val stale = memberDao.getAllMembersDirect().filterNot { it.id in remoteIds }
                            stale.forEach {
                                memberDao.deleteMember(it)
                                // Cascade : les empreintes faciales d'un athlete disparu du cloud
                                // ne doivent jamais rester sur la tablette.
                                runCatching {
                                    FaceDatabase.getInstance(context).faceTemplateDao()
                                        .deleteForMember(it.id)
                                }
                            }
                            if (stale.isNotEmpty()) {
                                Log.i("SupabaseSync", "Removed ${stale.size} member(s) absent from cloud")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SupabaseSync", "Error pulling members: ${e.message}")
        }
        return received to conflicts
    }

    // ----------------------------------------------------
    // INDEPENDENT JSON BACKUP & RESTORE
    // ----------------------------------------------------
    suspend fun generateBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val meta = JSONObject().apply {
            put("app", "Judo Club Seddouk")
            put("version", "1.0")
            put("schema_version", 2)
            put("exported_at", getIsoNow())
            put("device_id", "DOJO_TERMINAL_1")
        }
        root.put("metadata", meta)

        // Members
        val membersArr = JSONArray()
        // Collect current members from database
        val membersList = database.memberDao().let { dao ->
            // Let's get all members synchronously or query direct
            val list = mutableListOf<Member>()
            // Using SQLite query or direct list
            database.openHelper.readableDatabase.query("SELECT * FROM members").use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val qrIdx = cursor.getColumnIndexOrThrow("qrCode")
                val fnIdx = cursor.getColumnIndexOrThrow("firstName")
                val lnIdx = cursor.getColumnIndexOrThrow("lastName")
                val bdIdx = cursor.getColumnIndexOrThrow("birthDate")
                val phIdx = cursor.getColumnIndexOrThrow("phone")
                val gcIdx = cursor.getColumnIndexOrThrow("groupCode")
                val bnIdx = cursor.getColumnIndexOrThrow("beltName")
                val stIdx = cursor.getColumnIndexOrThrow("status")
                val rdIdx = cursor.getColumnIndexOrThrow("registrationDate")
                val puIdx = cursor.getColumnIndexOrThrow("photoUrl")
                val ntIdx = cursor.getColumnIndexOrThrow("notes")
                while (cursor.moveToNext()) {
                    list.add(
                        Member(
                            id = cursor.getLong(idIdx),
                            qrCode = cursor.getString(qrIdx),
                            firstName = cursor.getString(fnIdx),
                            lastName = cursor.getString(lnIdx),
                            birthDate = cursor.getString(bdIdx),
                            phone = cursor.getString(phIdx),
                            groupCode = cursor.getString(gcIdx),
                            beltName = cursor.getString(bnIdx),
                            status = cursor.getString(stIdx),
                            registrationDate = cursor.getString(rdIdx),
                            photoUrl = cursor.getString(puIdx),
                            notes = cursor.getString(ntIdx)
                        )
                    )
                }
            }
            list
        }
        for (m in membersList) {
            membersArr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("qr_code", m.qrCode)
                    put("first_name", m.firstName)
                    put("last_name", m.lastName)
                    put("birth_date", m.birthDate)
                    put("phone", m.phone)
                    put("group_code", m.groupCode)
                    put("belt_name", m.beltName)
                    put("status", m.status)
                    put("registration_date", m.registrationDate)
                    put("photo_url", m.photoUrl)
                    put("notes", m.notes)
                }
            )
        }
        root.put("members", membersArr)

        // Attendances
        val attendancesArr = JSONArray()
        database.openHelper.readableDatabase.query("SELECT * FROM attendance").use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val mIdIdx = cursor.getColumnIndexOrThrow("memberId")
            val dsIdx = cursor.getColumnIndexOrThrow("dateString")
            val tsIdx = cursor.getColumnIndexOrThrow("timeString")
            val timeIdx = cursor.getColumnIndexOrThrow("timestamp")
            val cimIdx = cursor.getColumnIndexOrThrow("checkInMethod")
            val devIdx = cursor.getColumnIndexOrThrow("deviceId")
            while (cursor.moveToNext()) {
                attendancesArr.put(
                    JSONObject().apply {
                        put("id", cursor.getLong(idIdx))
                        put("member_id", cursor.getLong(mIdIdx))
                        put("date_string", cursor.getString(dsIdx))
                        put("time_string", cursor.getString(tsIdx))
                        put("timestamp", cursor.getLong(timeIdx))
                        put("check_in_method", cursor.getString(cimIdx))
                        put("device_id", cursor.getString(devIdx))
                    }
                )
            }
        }
        root.put("attendances", attendancesArr)

        // Categories
        val categoriesArr = JSONArray()
        for (c in clubDao.getAllCategories()) {
            categoriesArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("group_code", c.groupCode)
                    put("coach1_name", c.coach1Name)
                    put("coach2_name", c.coach2Name)
                    put("description", c.description)
                    put("is_active", c.isActive)
                }
            )
        }
        root.put("categories", categoriesArr)

        // Coaches
        val coachesArr = JSONArray()
        for (co in clubDao.getAllCoaches()) {
            coachesArr.put(
                JSONObject().apply {
                    put("id", co.id)
                    put("name", co.name)
                    put("grade", co.grade)
                    put("phone", co.phone)
                    put("assigned_categories", co.assignedCategoriesJoined)
                    put("is_active", co.isActive)
                    put("sessions_count", co.sessionsCount)
                }
            )
        }
        root.put("coaches", coachesArr)

        // Scheduled sessions
        val scheduledArr = JSONArray()
        for (s in clubDao.getAllScheduledSessions()) {
            scheduledArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("category_id", s.categoryId)
                    put("category_name", s.categoryName)
                    put("group_code", s.groupCode)
                    put("day_of_week_name", s.dayOfWeekName)
                    put("date_string", s.dateString)
                    put("formatted_display_date", s.formattedDisplayDate)
                    put("start_time", s.startTime)
                    put("end_time", s.endTime)
                    put("coach_ids", s.coachIdsJoined)
                    put("coach_names", s.coachNamesJoined)
                    put("is_recurring_weekly", s.isRecurringWeekly)
                    put("recurrence_end_date", s.recurrenceEndDate ?: "")
                    put("series_id", s.seriesId ?: "")
                    put("notes", s.notes)
                    put("status", s.status)
                }
            )
        }
        root.put("scheduled_sessions", scheduledArr)

        // Past sessions
        val pastArr = JSONArray()
        for (p in clubDao.getAllPastSessions()) {
            pastArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("category_id", p.categoryId)
                    put("category_name", p.categoryName)
                    put("group_code", p.groupCode)
                    put("date_string", p.dateString)
                    put("start_time", p.startTime)
                    put("end_time", p.endTime)
                    put("coach1_name", p.coach1Name)
                    put("coach1_present", p.coach1Present)
                    put("coach2_name", p.coach2Name)
                    put("coach2_present", p.coach2Present)
                    put("present_count", p.presentCount)
                    put("absent_count", p.absentCount)
                    put("present_members", p.presentMembersJoined)
                    put("absent_members", p.absentMembersJoined)
                }
            )
        }
        root.put("past_sessions", pastArr)

        meta.put("total_members", membersList.size)
        meta.put("total_attendances", attendancesArr.length())
        meta.put("total_categories", categoriesArr.length())
        meta.put("total_coaches", coachesArr.length())
        meta.put("total_scheduled_sessions", scheduledArr.length())
        meta.put("total_past_sessions", pastArr.length())

        root.toString(2)
    }

    suspend fun restoreBackupJson(backupJson: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(backupJson)
            if (!root.has("members") && !root.has("categories")) {
                return@withContext Result.failure(Exception("Format de sauvegarde JSON invalide (clés attendues manquantes)"))
            }

            // Restore Members
            var restoredMembers = 0
            if (root.has("members")) {
                val arr = root.getJSONArray("members")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val m = Member(
                        id = obj.optLong("id", 0L),
                        qrCode = obj.optString("qr_code", "RESTORED-${UUID.randomUUID().toString().take(6)}"),
                        firstName = obj.optString("first_name", ""),
                        lastName = obj.optString("last_name", ""),
                        birthDate = obj.optString("birth_date", "2012-01-01"),
                        phone = obj.optString("phone", ""),
                        groupCode = obj.optString("group_code", "U15"),
                        beltName = obj.optString("belt_name", "BLANCHE"),
                        status = obj.optString("status", "ACTIF"),
                        registrationDate = obj.optString("registration_date", "2026-09-01"),
                        photoUrl = obj.optString("photo_url", ""),
                        notes = obj.optString("notes", "")
                    )
                    memberDao.insertMember(m)
                    restoredMembers++
                }
            }

            // Restore Attendances
            var restoredAttendances = 0
            if (root.has("attendances")) {
                val arr = root.getJSONArray("attendances")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val att = Attendance(
                        id = obj.optLong("id", 0L),
                        memberId = obj.optLong("member_id", 0L),
                        dateString = obj.optString("date_string", ""),
                        timeString = obj.optString("time_string", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        checkInMethod = obj.optString("check_in_method", "RESTORED"),
                        deviceId = obj.optString("device_id", "DOJO_TERMINAL_1")
                    )
                    if (att.memberId > 0) {
                        attendanceDao.insertAttendance(att)
                        restoredAttendances++
                    }
                }
            }

            // Restore Categories
            var restoredCategories = 0
            if (root.has("categories")) {
                val arr = root.getJSONArray("categories")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val cat = CategoryEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Catégorie"),
                        groupCode = obj.optString("group_code", "U15"),
                        coach1Name = obj.optString("coach1_name", ""),
                        coach2Name = obj.optString("coach2_name", ""),
                        description = obj.optString("description", ""),
                        isActive = obj.optBoolean("is_active", true)
                    )
                    clubDao.insertCategory(cat)
                    restoredCategories++
                }
            }

            // Log restoration
            auditLogDao.insert(
                SyncAuditLogEntity(
                    entityName = "ALL",
                    entityId = "BACKUP_RESTORE",
                    operationType = "BACKUP_RESTORE",
                    details = "Restauration terminée : $restoredMembers membres, $restoredAttendances présences, $restoredCategories catégories"
                )
            )

            Result.success("Restauration réussie : $restoredMembers adhérents, $restoredAttendances pointages réintégrés.")
        } catch (e: Exception) {
            Result.failure(Exception("Échec de la restauration : ${e.message}"))
        }
    }

    /** Joins a JSON string array back into the ";"-separated form used by the Room entities. */
    private fun jsonArrayToJoined(array: JSONArray?): String {
        if (array == null) return ""
        val parts = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "")
            if (value.isNotBlank()) parts.add(value)
        }
        return parts.joinToString(";")
    }

    private fun getIsoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}


