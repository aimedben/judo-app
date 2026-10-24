package com.example.data.repository

import com.example.data.local.AttendanceDao
import com.example.data.local.MemberDao
import com.example.data.model.Attendance
import com.example.data.model.AttendanceWithMember
import com.example.data.model.Member
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.data.local.ClubDao
import com.example.data.local.CategoryEntity
import com.example.data.local.CoachEntity
import com.example.data.local.ScheduledSessionEntity
import com.example.data.local.PastSessionEntity
import com.example.data.sync.SupabaseSyncService

sealed class CheckInResult {
    data class Success(val member: Member, val attendance: Attendance) : CheckInResult()
    data class AlreadyCheckedIn(val member: Member, val existingAttendance: Attendance) : CheckInResult()
    data class UnknownQr(val rawCode: String) : CheckInResult()
    data class Error(val message: String) : CheckInResult()

    /**
     * La fiche de l'athlète vient d'être créée : ce n'est PAS un pointage.
     * L'athlète reste « Absent / Non pointé » jusqu'à un scan ultérieur.
     */
    data class Registered(val member: Member) : CheckInResult()

    /**
     * L'athlete existe mais n'appartient pas a la categorie de la seance en cours.
     * Aucune presence n'est enregistree : le pointage est refuse.
     */
    data class WrongCategory(
        val member: Member,
        val sessionCategoryName: String,
        val memberGroupCode: String
    ) : CheckInResult()
}

data class MemberAttendanceStat(
    val member: Member,
    val totalAttendances: Int,
    val attendancePercentage: Int, // e.g. 98%
    val rank: Int = 0
)

class JudoRepository(
    private val memberDao: MemberDao,
    private val attendanceDao: AttendanceDao,
    private val clubDao: ClubDao? = null,
    private val syncService: SupabaseSyncService? = null
) {
    val allMembers: Flow<List<Member>> = memberDao.getAllMembers()
    val activeMembersCount: Flow<Int> = memberDao.countActiveMembersFlow()
    val allAttendance: Flow<List<AttendanceWithMember>> = attendanceDao.getAllAttendanceWithMembers()
    val recentAttendance: Flow<List<AttendanceWithMember>> = attendanceDao.getRecentAttendance(15)

    /** Seances validees : source de verite de l' historique (present|absent par nom). */
    fun getAllPastSessionsFlow(): Flow<List<PastSessionEntity>> =
        clubDao?.getAllPastSessionsFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getTodayAttendance(): Flow<List<AttendanceWithMember>> {
        return attendanceDao.getAttendanceForDate(getTodayDateString())
    }

    fun getAttendanceForDate(dateString: String): Flow<List<AttendanceWithMember>> {
        return attendanceDao.getAttendanceForDate(dateString)
    }

    fun getAttendanceForMember(memberId: Long): Flow<List<Attendance>> {
        return attendanceDao.getAttendanceForMember(memberId)
    }

    fun getMemberById(memberId: Long): Flow<Member?> {
        return memberDao.getMemberById(memberId)
    }

    suspend fun getMemberByQr(qrCode: String): Member? {
        return memberDao.getMemberByQrCode(qrCode.trim())
    }

    suspend fun processCheckIn(
        rawCode: String,
        method: String = "QR_SCAN",
        sessionStartTimestamp: Long? = null,
        sessionCategoryName: String? = null,
        sessionGroupCode: String? = null
    ): CheckInResult {
        val cleanCode = rawCode.trim().replace("\n", "").replace("\r", "").replace("\t", "")
        if (cleanCode.isBlank()) return CheckInResult.Error("QR vide")

        var member = memberDao.getMemberByQrCode(cleanCode)

        if (member == null) {
            // Parse the payload WITH its line breaks: a multi-line card QR
            // ("JUDO CLUB SEDDOUK\nN: 121\nAdherent: ...") can only be read field by field while
            // the lines are still separated — flattening it used to hide the "N:" number.
            val extracted = com.example.util.CardDataParser.parseCardData(rawCode.trim())
            if (extracted.qrCode.isNotBlank()) {
                member = memberDao.getMemberByQrCode(extracted.qrCode.trim())
            }
            if (member == null && extracted.cardNumber.isNotBlank()) {
                val cardNum = extracted.cardNumber.trim()
                member = memberDao.getMemberByQrCode(cardNum)
                    ?: memberDao.getMemberByQrCode("JCS-$cardNum")
            }
        }

        if (member == null) {
            return CheckInResult.UnknownQr(cleanCode)
        }

        // Règle : sans séance active, aucun pointage n'est enregistré (une carte lue
        // au hasard ne doit jamais créer de présence dans une séance inexistante).
        if (sessionStartTimestamp == null) {
            return CheckInResult.Error("Aucune séance en cours : démarrez une séance avant de pointer.")
        }

        // Règle : un athlete d'une AUTRE categorie ne peut jamais etre pointe dans cette
        // seance. Le refus intervient AVANT toute ecriture : aucune presence n'est creee.
        val wrongCategory = checkCategory(member, sessionCategoryName, sessionGroupCode)
        if (wrongCategory != null) return wrongCategory

        // Portée séance : un athlète pointé plus tôt dans une AUTRE séance de la même
        // journée n'est pas « déjà pointé » — chaque séance a son propre pointage.
        val today = getTodayDateString()
        val existing = attendanceDao.findAttendanceSince(member.id, today, sessionStartTimestamp)
        if (existing != null) {
            return CheckInResult.AlreadyCheckedIn(member, existing)
        }

        val nowTime = getCurrentTimeString()
        val attendance = Attendance(
            memberId = member.id,
            dateString = today,
            timeString = nowTime,
            timestamp = System.currentTimeMillis(),
            checkInMethod = method
        )
        val insertedId = attendanceDao.insertAttendance(attendance)
        val finalAtt = attendance.copy(id = insertedId)
        // Pointage 100 % local : plus de sauvegarde cloud (les presences sont resumees
        // par seance dans past_sessions).
        return CheckInResult.Success(member, finalAtt)
    }

    suspend fun manualCheckIn(
        memberId: Long,
        sessionStartTimestamp: Long? = null,
        sessionCategoryName: String? = null,
        sessionGroupCode: String? = null
    ): CheckInResult {
        val member = memberDao.getMemberByIdDirect(memberId) ?: return CheckInResult.Error("Membre introuvable")

        // Règle : le pointage manuel appartient lui aussi à une séance active.
        if (sessionStartTimestamp == null) {
            return CheckInResult.Error("Aucune séance en cours : démarrez une séance avant de pointer.")
        }

        val wrongCategory = checkCategory(member, sessionCategoryName, sessionGroupCode)
        if (wrongCategory != null) return wrongCategory

        val today = getTodayDateString()
        val existing = attendanceDao.findAttendanceSince(member.id, today, sessionStartTimestamp)
        if (existing != null) {
            return CheckInResult.AlreadyCheckedIn(member, existing)
        }
        val attendance = Attendance(
            memberId = member.id,
            dateString = today,
            timeString = getCurrentTimeString(),
            timestamp = System.currentTimeMillis(),
            checkInMethod = "MANUAL"
        )
        val insertedId = attendanceDao.insertAttendance(attendance)
        val finalAtt = attendance.copy(id = insertedId)
        return CheckInResult.Success(member, finalAtt)
    }

    /**
     * Controle d'appartenance a la categorie de la seance.
     * Retourne `null` quand le pointage est autorise (categorie de seance inconnue :
     * on ne bloque pas), sinon le refus explicite a afficher au coach.
     */
    private fun checkCategory(
        member: Member,
        sessionCategoryName: String?,
        sessionGroupCode: String?
    ): CheckInResult.WrongCategory? {
        if (sessionGroupCode.isNullOrBlank()) return null
        if (member.groupCode.isBlank()) return null
        if (com.example.util.GroupCodes.matches(sessionGroupCode, member.groupCode)) return null
        return CheckInResult.WrongCategory(
            member = member,
            sessionCategoryName = sessionCategoryName.orEmpty(),
            memberGroupCode = member.groupCode
        )
    }

    suspend fun associateQrCodeToMember(memberId: Long, qrCode: String) {
        val clean = com.example.util.MemberQr.normalize(qrCode)
        if (clean.isBlank()) return
        memberDao.updateMemberQrCode(memberId, clean)
        memberDao.getMemberByIdDirect(memberId)?.let {
            syncService?.enqueueMember(it, "UPDATE")
        }
    }

    suspend fun insertMember(member: Member): Long {
        // Le code QR est toujours normalise AVANT d'atteindre la base : on ne stocke
        // jamais le contenu brut d'une carte sportive ("JUDO CLUB SEDDOUK N: 355 ...").
        val safeMember = member.withNormalizedQr()
        // Garde-fou anti-doublon : l'index unique sur `qrCode` combine a
        // OnConflictStrategy.REPLACE effacerait silencieusement la fiche existante.
        // On refuse donc l'insertion si un AUTRE athlete possede deja ce code QR.
        val normalizedQr = safeMember.qrCode.trim()
        if (normalizedQr.isNotBlank()) {
            memberDao.getMemberByQrCode(normalizedQr)?.let { existing ->
                if (existing.id != safeMember.id) return existing.id
            }
        }
        // Garde-fou central : jamais deux fiches pour le meme athlete, quel que soit
        // le chemin d'ecriture (formulaire, carte scannee, restauration...).
        com.example.util.MemberDuplicates.find(memberDao.getAllMembersDirect(), safeMember)?.let { existing ->
            return existing.id
        }
        val id = memberDao.insertMember(safeMember)
        val saved = safeMember.copy(id = id)
        syncService?.enqueueMember(saved, "UPSERT")
        return id
    }

    /**
     * Repare les fiches dont `qr_code` contient une charge utile brute de carte
     * (anciennes versions de l'application). Retourne le nombre de fiches corrigees.
     */
    suspend fun repairMalformedQrCodes(): Int {
        var fixed = 0
        memberDao.getAllMembersDirect().forEach { member ->
            val clean = com.example.util.MemberQr.normalize(member.qrCode)
            if (clean.isNotBlank() && clean != member.qrCode) {
                val repaired = member.copy(qrCode = clean)
                memberDao.updateMember(repaired)
                syncService?.enqueueMember(repaired, "UPDATE")
                fixed++
            }
        }
        return fixed
    }

    /** Applique la normalisation du code QR (prefixe JCS- + numero de carte). */
    private fun Member.withNormalizedQr(): Member {
        val clean = com.example.util.MemberQr.normalize(qrCode)
        val finalCode = clean.ifBlank {
            // Aucun identifiant exploitable : on genere un code lisible, jamais le texte brut.
            "JCS-" + System.currentTimeMillis().toString().takeLast(4)
        }
        return if (finalCode == qrCode) this else copy(qrCode = finalCode)
    }

    suspend fun updateMember(member: Member) {
        val safeMember = member.withNormalizedQr()
        memberDao.updateMember(safeMember)
        syncService?.enqueueMember(safeMember, "UPDATE")
    }

    suspend fun deleteMember(member: Member) {
        memberDao.deleteMember(member)
        syncService?.enqueueMember(member, "DELETE")
    }

    suspend fun deleteAttendance(attendanceId: Long) {
        attendanceDao.deleteAttendanceById(attendanceId)
    }

    suspend fun recordAttendanceForDate(
        memberId: Long,
        dateString: String,
        timeString: String = "18:00"
    ): Attendance {
        val existing = attendanceDao.findTodayAttendance(memberId, dateString)
        if (existing != null) return existing

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = try {
            sdf.parse(dateString)
        } catch (_: Exception) {
            null
        }
        val timestamp = date?.time ?: System.currentTimeMillis()

        val att = Attendance(
            memberId = memberId,
            dateString = dateString,
            timeString = timeString,
            timestamp = timestamp,
            checkInMethod = "CALENDAR"
        )
        val id = attendanceDao.insertAttendance(att)
        return att.copy(id = id)
    }

    suspend fun deleteAttendanceForMemberAndDate(memberId: Long, dateString: String) {
        val existing = attendanceDao.findTodayAttendance(memberId, dateString)
        if (existing != null) {
            attendanceDao.deleteAttendanceById(existing.id)
        }
    }

    /**
     * Deletes history entries locally AND removes their footprint from the cloud:
     * - the attendance rows recorded for the session's [PastSessionEntity.groupCode]
     *   on [PastSessionEntity.dateString] are removed locally (the cloud no longer
     *   stores attendances: they live in `past_sessions.present_members`),
     * - each [PastSessionEntity.id] is deleted immediately from the cloud
     *   `past_sessions` table, with a queued retry when offline,
     * - finally the local rows disappear from Room.
     */
    suspend fun deletePastSessionsCascade(sessions: List<PastSessionEntity>) {
        if (sessions.isEmpty()) return

        // 1. Collect the attendance ids that belong to these sessions BEFORE any
        //    deletion, so a cloud DELETE can be queued per id.
        val attendanceIds = mutableSetOf<Long>()
        for (session in sessions) {
            attendanceDao
                .getAttendanceIdsForGroupAndDate(
                    com.example.util.GroupCodes.split(session.groupCode),
                    session.dateString
                )
                .forEach { attendanceIds.add(it) }
        }

        syncService?.let { svc ->
            // La table cloud `attendances` n'existe plus : les presences officielles
            // vivent dans `past_sessions`. Inutile de mettre des DELETE en file pour elle.
            for (session in sessions) {
                // Suppression immediate cote cloud : l'historique doit disparaitre de
                // Supabase tout de suite, pas au prochain cycle de synchronisation.
                val deleted = svc.deleteRemoteNow("PAST_SESSIONS", session.id)
                if (!deleted) {
                    // Hors-ligne / erreur reseau : on garde un reessai automatique.
                    svc.enqueueDelete("PAST_SESSIONS", session.id)
                }
            }
        }

        // 2. Local wipe: the attendances die with their session, then the rows.
        if (attendanceIds.isNotEmpty()) {
            attendanceDao.deleteAttendancesByIds(attendanceIds.toList())
        }
        clubDao?.deletePastSessionsByIds(sessions.map { it.id })
    }

    suspend fun clearEntireDatabase() {
        attendanceDao.deleteAllAttendance()
        memberDao.deleteAllMembers()
    }
}
