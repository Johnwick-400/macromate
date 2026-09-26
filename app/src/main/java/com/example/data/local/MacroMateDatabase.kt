package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*

@Database(
    entities = [
        UserAccount::class,
        UserProfile::class,
        MacroLog::class,
        OrderHistory::class,
        DailyTargetSnapshot::class,
        WeightLog::class,
        OutsideSpendLog::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MacroMateDatabase : RoomDatabase() {
    abstract fun dao(): DatabaseDao

    companion object {
        @Volatile
        private var INSTANCE: MacroMateDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to user_profile
                db.execSQL("ALTER TABLE user_profile ADD COLUMN monthlyBudgetInr REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN mealsPerDay INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN wakeTimeMinutes INTEGER NOT NULL DEFAULT 420")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN sleepTimeMinutes INTEGER NOT NULL DEFAULT 1380")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN workoutTimeMinutes INTEGER NOT NULL DEFAULT 1080")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN isEarlyHeavyCut INTEGER NOT NULL DEFAULT 0")

                // Create daily_target_snapshot table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_target_snapshot (
                        date TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        targetCalories INTEGER NOT NULL,
                        targetProteinG INTEGER NOT NULL,
                        targetCarbsG INTEGER NOT NULL,
                        targetFatG INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        estimatedCalories INTEGER,
                        estimatedProtein INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create weight_log table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS weight_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        date TEXT NOT NULL,
                        weightKg REAL NOT NULL,
                        trendWeightKg REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create outside_spend_log table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS outside_spend_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        date TEXT NOT NULL,
                        amountInr REAL NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): MacroMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MacroMateDatabase::class.java,
                    "macromate_database"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
