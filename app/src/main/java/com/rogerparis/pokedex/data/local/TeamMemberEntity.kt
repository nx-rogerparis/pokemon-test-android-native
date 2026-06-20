package com.rogerparis.pokedex.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val artworkUrl: String,
    val position: Int,
)
