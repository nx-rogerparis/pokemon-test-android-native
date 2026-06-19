package com.rogerparis.pokedex.data.local

import androidx.room.TypeConverter
import com.rogerparis.pokedex.domain.model.Stat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromStatList(value: List<Stat>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStatList(value: String): List<Stat> = Json.decodeFromString(value)
}
