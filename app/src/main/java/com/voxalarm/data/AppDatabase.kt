package com.voxalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Alarm::class, HistoryEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * The database lives in device-protected storage so alarms can be read and
         * rescheduled right after a reboot, before the user unlocks the phone (Direct Boot).
         */
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext.createDeviceProtectedStorageContext(),
                AppDatabase::class.java,
                "voxalarm.db"
            ).build().also { instance = it }
        }
    }
}
