package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DatabaseDao {

    // User Account Queries
    @Query("SELECT * FROM user_account WHERE email = :email LIMIT 1")
    suspend fun getUserAccount(email: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAccount(user: UserAccount)

    @Update
    suspend fun updateUserAccount(user: UserAccount)

    // User Profile Queries
    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    fun getUserProfileFlow(userId: String = "default_user"): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    suspend fun getUserProfile(userId: String = "default_user"): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    // Macro Log Queries
    @Query("SELECT * FROM macro_log WHERE userId = :userId AND dateString = :dateString ORDER BY timestamp DESC")
    fun getMacroLogsFlow(userId: String = "default_user", dateString: String): Flow<List<MacroLog>>

    @Query("SELECT * FROM macro_log WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllMacroLogsFlow(userId: String = "default_user"): Flow<List<MacroLog>>

    @Query("SELECT * FROM macro_log WHERE userId = :userId AND dateString = :dateString")
    suspend fun getMacroLogs(userId: String = "default_user", dateString: String): List<MacroLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacroLog(log: MacroLog)

    @Delete
    suspend fun deleteMacroLog(log: MacroLog)

    @Query("UPDATE macro_log SET mealSlot = :mealSlot WHERE id = :id")
    suspend fun updateMealSlot(id: Int, mealSlot: String?)

    @Query("DELETE FROM macro_log WHERE id = :id")
    suspend fun deleteMacroLogById(id: Int)

    @Query("DELETE FROM macro_log WHERE userId = :userId")
    suspend fun clearAllMacroLogs(userId: String = "default_user")

    // Order History Queries
    @Query("SELECT * FROM order_history WHERE userId = :userId ORDER BY createdAt DESC")
    fun getOrderHistoryFlow(userId: String = "default_user"): Flow<List<OrderHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderHistory(order: OrderHistory)

    @Query("DELETE FROM order_history WHERE userId = :userId")
    suspend fun clearOrderHistory(userId: String = "default_user")

    // --- Dietary Log / Budget (schema v4) ---

    /** Logs in an inclusive yyyy-MM-dd range; string comparison is safe for this fixed format. */
    @Query("SELECT * FROM macro_log WHERE userId = :userId AND dateString BETWEEN :from AND :to ORDER BY timestamp ASC")
    fun getMacroLogsBetweenFlow(userId: String, from: String, to: String): Flow<List<MacroLog>>

    @Query("SELECT * FROM day_record WHERE userId = :userId AND dateString BETWEEN :from AND :to ORDER BY dateString ASC")
    fun getDayRecordsBetweenFlow(userId: String, from: String, to: String): Flow<List<DayRecord>>

    @Query("SELECT * FROM day_record WHERE userId = :userId AND dateString = :dateString LIMIT 1")
    suspend fun getDayRecord(userId: String, dateString: String): DayRecord?

    /** Snapshot a day's targets only once — later goal changes must not rewrite past days. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDayRecordIfAbsent(record: DayRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayRecord(record: DayRecord)

    @Query("DELETE FROM day_record WHERE userId = :userId")
    suspend fun clearDayRecords(userId: String)

    // Weight Log Queries
    @Query("SELECT * FROM weight_log WHERE userId = :userId ORDER BY date ASC")
    fun getWeightLogsFlow(userId: String = "default_user"): Flow<List<WeightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLog)

    /** One weigh-in per day: replaces any earlier entry for the same date. */
    @Transaction
    suspend fun upsertWeightForDay(log: WeightLog) {
        deleteWeightForDay(log.userId, log.date)
        insertWeightLog(log)
    }

    @Query("DELETE FROM weight_log WHERE userId = :userId AND date = :date")
    suspend fun deleteWeightForDay(userId: String, date: String)

    @Query("DELETE FROM weight_log WHERE userId = :userId")
    suspend fun clearWeightLogs(userId: String)

    // Outside Spend Queries (outside orders, Swiggy fees/taxes, month reconcile)
    @Query("SELECT * FROM outside_spend_log WHERE userId = :userId ORDER BY date DESC")
    fun getOutsideSpendsFlow(userId: String = "default_user"): Flow<List<OutsideSpendLog>>

    @Query("SELECT * FROM outside_spend_log WHERE userId = :userId AND date BETWEEN :from AND :to ORDER BY date ASC")
    fun getOutsideSpendsBetweenFlow(userId: String, from: String, to: String): Flow<List<OutsideSpendLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutsideSpend(spend: OutsideSpendLog)

    @Query("DELETE FROM outside_spend_log WHERE id = :id")
    suspend fun deleteOutsideSpend(id: Int)

    @Query("DELETE FROM outside_spend_log WHERE userId = :userId")
    suspend fun clearOutsideSpends(userId: String)
}
