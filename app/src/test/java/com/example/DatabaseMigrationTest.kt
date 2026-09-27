package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MacroMateDatabase
import com.example.data.model.DayStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades real on-disk databases through the shipped migrations and lets Room validate the
 * resulting schema against the current entities. Proves existing users keep their data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    /** The schema the app shipped at version 2 (before the Dietary Log work). */
    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_account` (`email` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `name` TEXT NOT NULL, `hasCompletedOnboarding` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`email`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`userId` TEXT NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `goal` TEXT NOT NULL, `dailyCalories` INTEGER NOT NULL, `dailyProteinG` INTEGER NOT NULL, `dailyCarbsG` INTEGER NOT NULL, `dailyFatG` INTEGER NOT NULL, `allergiesJson` TEXT NOT NULL, `cuisinePrefsJson` TEXT NOT NULL, `llmProvider` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `macro_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `dateString` TEXT NOT NULL, `name` TEXT NOT NULL, `restaurant` TEXT NOT NULL, `proteinG` INTEGER NOT NULL, `carbsG` INTEGER NOT NULL, `fatG` INTEGER NOT NULL, `calories` INTEGER NOT NULL, `price` REAL NOT NULL, `source` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `order_history` (`orderId` TEXT NOT NULL, `userId` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, `totalProtein` INTEGER NOT NULL, `totalCarbs` INTEGER NOT NULL, `totalFat` INTEGER NOT NULL, `totalCalories` INTEGER NOT NULL, `totalPrice` REAL NOT NULL, `swiggyOrderId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`orderId`))")

        db.execSQL("INSERT INTO user_account VALUES ('a@x.com','pw','Ananya',1,1)")
        db.execSQL("INSERT INTO user_profile VALUES ('a@x.com','Ananya',27,62.0,165.0,'Cut',1360,111,145,37,'[\"vegetarian\"]','[]','Gemini',1)")
        db.execSQL("INSERT INTO macro_log (userId,dateString,name,restaurant,proteinG,carbsG,fatG,calories,price,source,timestamp) VALUES ('a@x.com','2026-09-20','Paneer Bowl','EatFit',25,45,12,390,249.0,'swiggy',1)")
        db.execSQL("INSERT INTO order_history VALUES ('MM-1','a@x.com','[]',25,45,12,390,249.0,'SW-1',1)")
    }

    private fun openRaw(version: Int, create: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            create(db)
            db.version = version
        }
    }

    private fun openCurrent(): MacroMateDatabase =
        Room.databaseBuilder(context, MacroMateDatabase::class.java, dbName)
            .addMigrations(MacroMateDatabase.MIGRATION_2_3, MacroMateDatabase.MIGRATION_3_4, MacroMateDatabase.MIGRATION_4_5, MacroMateDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `v2 user keeps profile, logs and orders after upgrading to the latest version`() = runBlocking {
        openRaw(2, ::createV2)
        val db = openCurrent()
        val dao = db.dao()

        val profile = dao.getUserProfile("a@x.com")
        assertNotNull(profile)
        assertEquals("Cut", profile!!.goal)
        assertEquals(1360, profile.dailyCalories)
        assertEquals("[\"vegetarian\"]", profile.allergiesJson)
        // New fields come in with safe defaults: budget off, 4 meals, no workout anchor.
        assertEquals(0.0, profile.monthlyBudgetInr, 0.0)
        assertEquals(4, profile.mealsPerDay)
        assertFalse(profile.workoutEnabled)
        assertEquals(4, profile.dayEndsAtHour)
        assertEquals("", profile.sex) // existing accounts: not set until the user picks it
        assertFalse(profile.isPregnantOrLactating || profile.onDiabetesMeds || profile.edHistory)

        val logs = dao.getMacroLogs("a@x.com", "2026-09-20")
        assertEquals(1, logs.size)
        assertEquals(249.0, logs[0].price, 0.0)
        assertFalse(logs[0].isShared)

        assertEquals(1, dao.getOrderHistoryFlow("a@x.com").first().size)
        db.close()
    }

    @Test
    fun `day records are keyed per user so two accounts can share a date`() = runBlocking {
        openRaw(2, ::createV2)
        val db = openCurrent()
        val dao = db.dao()
        dao.insertDayRecordIfAbsent(
            com.example.data.model.DayRecord("a@x.com", "2026-09-21", DayStatus.FASTED.name, null, 1360, 111, 145, 37, "Cut")
        )
        dao.insertDayRecordIfAbsent(
            com.example.data.model.DayRecord("b@x.com", "2026-09-21", DayStatus.AUTO.name, null, 2400, 130, 300, 70, "Bulk")
        )
        // Same date, two users: both rows exist (the v3 date-only key would have kept one).
        assertEquals(DayStatus.FASTED.name, dao.getDayRecord("a@x.com", "2026-09-21")?.status)
        assertEquals(2400, dao.getDayRecord("b@x.com", "2026-09-21")?.targetKcal)
        db.close()
    }

    @Test
    fun `v3 daily_target_snapshot rows are copied into day_record`() = runBlocking {
        openRaw(3) { db ->
            createV2(db)
            // The teammate's v2 -> v3 schema, exactly as MIGRATION_2_3 creates it.
            db.execSQL("ALTER TABLE user_profile ADD COLUMN monthlyBudgetInr REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE user_profile ADD COLUMN mealsPerDay INTEGER NOT NULL DEFAULT 4")
            db.execSQL("ALTER TABLE user_profile ADD COLUMN wakeTimeMinutes INTEGER NOT NULL DEFAULT 420")
            db.execSQL("ALTER TABLE user_profile ADD COLUMN sleepTimeMinutes INTEGER NOT NULL DEFAULT 1380")
            db.execSQL("ALTER TABLE user_profile ADD COLUMN workoutTimeMinutes INTEGER NOT NULL DEFAULT 1080")
            db.execSQL("ALTER TABLE user_profile ADD COLUMN isEarlyHeavyCut INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS daily_target_snapshot (date TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, targetCalories INTEGER NOT NULL, targetProteinG INTEGER NOT NULL, targetCarbsG INTEGER NOT NULL, targetFatG INTEGER NOT NULL, status TEXT NOT NULL, estimatedCalories INTEGER, estimatedProtein INTEGER, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS weight_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, date TEXT NOT NULL, weightKg REAL NOT NULL, trendWeightKg REAL NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS outside_spend_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, date TEXT NOT NULL, amountInr REAL NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO daily_target_snapshot VALUES ('2026-09-22','a@x.com',1360,111,145,37,'FASTED',NULL,NULL,1)")
            db.execSQL("INSERT INTO daily_target_snapshot VALUES ('2026-09-23','a@x.com',1360,111,145,37,'',NULL,NULL,1)")
            db.execSQL("INSERT INTO weight_log (userId,date,weightKg,trendWeightKg,createdAt) VALUES ('a@x.com','2026-09-22',62.4,62.4,1)")
            db.execSQL("INSERT INTO outside_spend_log (userId,date,amountInr,note,createdAt) VALUES ('a@x.com','2026-09-22',180.0,'canteen',1)")
        }

        val db = openCurrent()
        val dao = db.dao()
        assertEquals(DayStatus.FASTED.name, dao.getDayRecord("a@x.com", "2026-09-22")?.status)
        assertEquals(DayStatus.AUTO.name, dao.getDayRecord("a@x.com", "2026-09-23")?.status) // '' -> AUTO
        assertEquals("Cut", dao.getDayRecord("a@x.com", "2026-09-22")?.goal)
        assertEquals(0, dao.getDayRecord("a@x.com", "2026-09-22")?.waterGlasses)
        assertEquals(62.4, dao.getWeightLogsFlow("a@x.com").first().single().weightKg, 0.0)
        val spend = dao.getOutsideSpendsFlow("a@x.com").first().single()
        assertEquals(180.0, spend.amountInr, 0.0)
        assertEquals("OUTSIDE_ORDER", spend.kind)
        db.close()
    }
}
