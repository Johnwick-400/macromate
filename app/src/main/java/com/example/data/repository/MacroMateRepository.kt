package com.example.data.repository

import com.example.data.local.DatabaseDao
import com.example.data.model.UserProfile
import com.example.data.model.UserAccount
import com.example.data.model.MacroLog
import com.example.data.model.OrderHistory
import com.example.data.model.DishItem
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MacroMateRepository(private val dao: DatabaseDao) {

    // --- User Account Queries ---
    suspend fun getUserAccount(email: String): UserAccount? {
        return dao.getUserAccount(email)
    }

    suspend fun saveUserAccount(user: UserAccount) {
        dao.insertUserAccount(user)
    }

    suspend fun updateUserAccount(user: UserAccount) {
        dao.updateUserAccount(user)
    }

    // --- User Profile Queries ---
    fun getUserProfile(userId: String): Flow<UserProfile?> {
        return dao.getUserProfileFlow(userId)
    }

    suspend fun getProfileSync(userId: String): UserProfile? {
        return dao.getUserProfile(userId)
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        dao.insertUserProfile(profile)
    }

    // --- Macro Log Queries ---
    // dateString supplied by caller so the ViewModel can re-query on midnight rollover.
    fun getTodayMacroLogs(userId: String, dateString: String): Flow<List<MacroLog>> {
        return dao.getMacroLogsFlow(userId, dateString)
    }

    fun getAllMacroLogs(userId: String): Flow<List<MacroLog>> {
        return dao.getAllMacroLogsFlow(userId)
    }

    suspend fun saveMacroLog(log: MacroLog) {
        dao.insertMacroLog(log)
    }

    suspend fun deleteMacroLogById(id: Int) {
        dao.deleteMacroLogById(id)
    }

    suspend fun clearMacroLogs(userId: String) {
        dao.clearAllMacroLogs(userId)
    }

    // --- Order History Queries ---
    fun getOrderHistory(userId: String): Flow<List<OrderHistory>> {
        return dao.getOrderHistoryFlow(userId)
    }

    suspend fun saveOrderHistory(order: OrderHistory) {
        dao.insertOrderHistory(order)
    }

    suspend fun clearOrderHistory(userId: String) {
        dao.clearOrderHistory(userId)
    }

    /**
     * Delegates to the Intelligent ReAct Agent.
     */
    fun runAgent(
        query: String,
        profile: UserProfile,
        remainingCalories: Int,
        remainingProtein: Int,
        remainingCarbs: Int,
        remainingFat: Int
    ): Flow<AgentState> {
        return MacroMateAgent.searchAndRankMeals(
            query,
            profile,
            remainingCalories,
            remainingProtein,
            remainingCarbs,
            remainingFat
        )
    }
}
