package com.example.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.storage.dao.ConfigDao
import com.example.storage.dao.ConfigSourceDao
import com.example.storage.dao.DiagnosticDao
import com.example.storage.dao.ProxyDao
import com.example.storage.entities.ConfigEntity
import com.example.storage.entities.ConfigSourceCrossRef
import com.example.storage.entities.ConfigSourceEntity
import com.example.storage.entities.DiagnosticLogEntity
import com.example.storage.entities.ProxyEntity

@Database(
    entities = [
        ConfigEntity::class,
        ConfigSourceEntity::class,
        ConfigSourceCrossRef::class,
        DiagnosticLogEntity::class,
        ProxyEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AzadiDatabase : RoomDatabase() {

    abstract fun configDao(): ConfigDao
    abstract fun configSourceDao(): ConfigSourceDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun proxyDao(): ProxyDao

    companion object {
        @Volatile
        private var INSTANCE: AzadiDatabase? = null

        fun getInstance(context: Context): AzadiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AzadiDatabase::class.java,
                    "azadiyar.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
