package com.example.data.local.face

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FaceTemplateDao {

    @Insert
    suspend fun insert(template: FaceTemplateEntity): Long

    @Query("SELECT * FROM face_template WHERE memberId = :memberId")
    suspend fun forMember(memberId: Long): List<FaceTemplateEntity>

    @Query("SELECT * FROM face_template WHERE memberId IN (:memberIds)")
    suspend fun forMembers(memberIds: List<Long>): List<FaceTemplateEntity>

    @Query("SELECT COUNT(*) FROM face_template WHERE memberId = :memberId")
    suspend fun countForMember(memberId: Long): Int

    @Query("SELECT COUNT(*) FROM face_template")
    suspend fun countAll(): Int

    @Query("SELECT DISTINCT memberId FROM face_template")
    suspend fun enrolledMemberIds(): List<Long>

    @Query("DELETE FROM face_template WHERE memberId = :memberId")
    suspend fun deleteForMember(memberId: Long)

    @Query("DELETE FROM face_template")
    suspend fun deleteAll()
}
