package com.focusguard.core.data.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.focusguard.core.data.local.FocusGuardDatabase
import com.focusguard.core.data.local.dao.AppBlockRuleDao
import com.focusguard.core.data.local.dao.UsageDao
import com.focusguard.core.data.local.dao.WebBlockRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FocusGuardDatabase =
        Room
            .databaseBuilder(context, FocusGuardDatabase::class.java, "focusguard.db")
            .build()

    @Provides
    fun provideAppBlockRuleDao(db: FocusGuardDatabase): AppBlockRuleDao = db.appBlockRuleDao()

    @Provides
    fun provideWebBlockRuleDao(db: FocusGuardDatabase): WebBlockRuleDao = db.webBlockRuleDao()

    @Provides
    fun provideUsageDao(db: FocusGuardDatabase): UsageDao = db.usageDao()

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "datastore/focusguard_settings.preferences_pb") },
        )

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences.create(
            context,
            "focusguard_pin",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
