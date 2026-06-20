package com.rogerparis.pokedex.di

import android.content.Context
import androidx.room.Room
import com.rogerparis.pokedex.data.local.FavoriteDao
import com.rogerparis.pokedex.data.local.MIGRATION_1_2
import com.rogerparis.pokedex.data.local.MIGRATION_2_3
import com.rogerparis.pokedex.data.local.MIGRATION_3_4
import com.rogerparis.pokedex.data.local.MIGRATION_4_5
import com.rogerparis.pokedex.data.local.PokedexDatabase
import com.rogerparis.pokedex.data.local.PokemonDao
import com.rogerparis.pokedex.data.local.PokemonIndexDao
import com.rogerparis.pokedex.data.local.TeamMemberDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PokedexDatabase =
        Room.databaseBuilder(context, PokedexDatabase::class.java, "pokedex.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFavoriteDao(database: PokedexDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providePokemonDao(database: PokedexDatabase): PokemonDao = database.pokemonDao()

    @Provides
    fun providePokemonIndexDao(database: PokedexDatabase): PokemonIndexDao = database.pokemonIndexDao()

    @Provides
    fun provideTeamMemberDao(database: PokedexDatabase): TeamMemberDao = database.teamMemberDao()
}
