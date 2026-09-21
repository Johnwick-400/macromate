package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.UserProfile
import com.example.data.model.UserAccount
import com.example.data.model.MacroLog
import com.example.data.model.OrderHistory

@Database(entities = [UserAccount::class, UserProfile::class, MacroLog::class, OrderHistory::class], version = 2, exportSchema = false)
abstract class MacroMateDatabase : RoomDatabase() {
    abstract fun dao(): DatabaseDao

    companion object {
        @Volatile
        private var INSTANCE: MacroMateDatabase? = null

        fun getDatabase(context: Context): MacroMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MacroMateDatabase::class.java,
                    "macromate_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
