package com.focusguard.core.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusguard.core.data.local.FocusGuardDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * The real storage the repositories run on, for Robolectric tests: an in-memory Room database,
 * a DataStore file in a temp directory and Robolectric's package manager. Call [close] after
 * each test.
 */
class DataTestEnvironment {
    val context: Context = ApplicationProvider.getApplicationContext()

    val db: FocusGuardDatabase =
        Room
            .inMemoryDatabaseBuilder(context, FocusGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStoreDir = File.createTempFile("datastore", "").apply { delete() }

    val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(dataStoreDir, "settings.preferences_pb") }

    val packageManager: PackageManager = context.packageManager

    fun installApp(
        packageName: String,
        label: String,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
        system: Boolean = false,
    ) {
        val appInfo =
            ApplicationInfo().apply {
                this.packageName = packageName
                nonLocalizedLabel = label
                this.category = category
                flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            }
        shadowOf(packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = appInfo
            },
        )
    }

    fun close() {
        db.close()
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }
}
