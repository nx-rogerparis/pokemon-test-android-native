package com.rogerparis.pokedex.di

import com.rogerparis.pokedex.data.repository.DefaultPokemonRepository
import com.rogerparis.pokedex.domain.repository.PokemonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPokemonRepository(impl: DefaultPokemonRepository): PokemonRepository
}
