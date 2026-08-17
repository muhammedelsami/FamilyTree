package com.familytree.core.billing.di

import com.familytree.core.billing.PlayPremiumRepository
import com.familytree.core.domain.repository.PremiumRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindsPremiumRepository(impl: PlayPremiumRepository): PremiumRepository
}
