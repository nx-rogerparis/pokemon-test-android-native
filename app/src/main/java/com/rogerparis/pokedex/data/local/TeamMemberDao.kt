package com.rogerparis.pokedex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: TeamMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<TeamMemberEntity>)

    @Query("DELETE FROM team_members WHERE id = :id")
    suspend fun remove(id: Int)

    @Query("SELECT COUNT(*) FROM team_members")
    suspend fun count(): Int

    @Query("SELECT * FROM team_members ORDER BY position")
    fun observeAll(): Flow<List<TeamMemberEntity>>

    @Query("SELECT * FROM team_members ORDER BY position")
    suspend fun getAllOnce(): List<TeamMemberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM team_members WHERE id = :id)")
    fun observeIsMember(id: Int): Flow<Boolean>
}
