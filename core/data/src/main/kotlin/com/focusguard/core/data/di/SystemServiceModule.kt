package com.focusguard.core.data.di

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Raw Android system-service providers with no persistence or repository-binding concerns of their own. */
@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {
    @Provides
    fun providePackageManager(
        @ApplicationContext context: Context,
    ): PackageManager = context.packageManager
}
