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

    // Daily Target Snapshot Queries
    @Query("SELECT * FROM daily_target_snapshot WHERE userId = :userId AND date = :date LIMIT 1")
    fun getDailyTargetSnapshotFlow(userId: String = "default_user", date: String): Flow<DailyTargetSnapshot?>

    @Query("SELECT * FROM daily_target_snapshot WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun getDailyTargetSnapshot(userId: String = "default_user", date: String): DailyTargetSnapshot?

    @Query("SELECT * FROM daily_target_snapshot WHERE userId = :userId ORDER BY date DESC")
    fun getAllDailyTargetSnapshotsFlow(userId: String = "default_user"): Flow<List<DailyTargetSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyTargetSnapshot(snapshot: DailyTargetSnapshot)

    // Weight Log Queries
    @Query("SELECT * FROM weight_log WHERE userId = :userId ORDER BY date ASC")
    fun getWeightLogsFlow(userId: String = "default_user"): Flow<List<WeightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLog)

    // Outside Spend Queries
    @Query("SELECT * FROM outside_spend_log WHERE userId = :userId ORDER BY date DESC")
    fun getOutsideSpendsFlow(userId: String = "default_user"): Flow<List<OutsideSpendLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutsideSpend(spend: OutsideSpendLog)
}
