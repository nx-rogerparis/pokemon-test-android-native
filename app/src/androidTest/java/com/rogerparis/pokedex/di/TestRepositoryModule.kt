package com.rogerparis.pokedex.di

import com.rogerparis.pokedex.domain.repository.PokemonRepository
import com.rogerparis.pokedex.fake.FakePokemonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFakeRepository(impl: FakePokemonRepository): PokemonRepository
}
