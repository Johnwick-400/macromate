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
        DayRecord::class,
        WeightLog::class,
        OutsideSpendLog::class
    ],
    version = 6,
    exportSchema = true
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

        /**
         * v3 -> v4. Additive, plus one table swap:
         * - daily_target_snapshot keyed rows by date only, so two accounts on one phone
         *   overwrote each other. It becomes day_record keyed by (userId, dateString);
         *   existing rows are copied across (unknown status -> AUTO).
         * - profile: budget start date, workout on/off, day boundary, reminders, health check.
         * - macro_log: meal slot + shared flag. outside_spend_log: kind (outside/fees/reconcile).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `budgetStartDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `workoutEnabled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `dayEndsAtHour` INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `remindersEnabled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `isPregnantOrLactating` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `onDiabetesMeds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `edHistory` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `macro_log` ADD COLUMN `mealSlot` TEXT")
                db.execSQL("ALTER TABLE `macro_log` ADD COLUMN `isShared` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `outside_spend_log` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'OUTSIDE_ORDER'")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_record` (`userId` TEXT NOT NULL, `dateString` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `estimatedKcal` INTEGER, `targetKcal` INTEGER NOT NULL, " +
                        "`targetProteinG` INTEGER NOT NULL, `targetCarbsG` INTEGER NOT NULL, `targetFatG` INTEGER NOT NULL, " +
                        "`goal` TEXT NOT NULL, `noSpendConfirmed` INTEGER NOT NULL, PRIMARY KEY(`userId`, `dateString`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `day_record` (userId, dateString, status, estimatedKcal, targetKcal, " +
                        "targetProteinG, targetCarbsG, targetFatG, goal, noSpendConfirmed) " +
                        "SELECT s.userId, s.date, CASE WHEN s.status IN ('FASTED','ESTIMATED','COMPLETE','SKIPPED') " +
                        "THEN s.status ELSE 'AUTO' END, s.estimatedCalories, s.targetCalories, s.targetProteinG, " +
                        "s.targetCarbsG, s.targetFatG, COALESCE(p.goal, 'Maintain'), 0 " +
                        "FROM daily_target_snapshot s LEFT JOIN user_profile p ON p.userId = s.userId"
                )
                db.execSQL("DROP TABLE IF EXISTS `daily_target_snapshot`")
            }
        }

        /** v4 -> v5: water glasses per day, so Dietary Log analytics can count water. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `day_record` ADD COLUMN `waterGlasses` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5 -> v6: sex (Male/Female) for the exact Mifflin-St Jeor BMR; "" for existing accounts. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `sex` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): MacroMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MacroMateDatabase::class.java,
                    "macromate_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                // Only pre-release v1 databases may be wiped; v2+ always migrates with data kept.
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
