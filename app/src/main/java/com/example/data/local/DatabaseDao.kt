package com.example.data.local

import androidx.room.*
import com.example.data.model.UserProfile
import com.example.data.model.UserAccount
import com.example.data.model.MacroLog
import com.example.data.model.OrderHistory
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
}
