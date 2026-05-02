package com.sustain.step.data.repo.settings

import android.content.Context
import android.content.SharedPreferences
import com.sustain.step.data.database.entity.HistoryEntity
import com.sustain.step.data.repo.HistoryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

class Settings(context: Context, private val historyRepo: HistoryRepo) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    var isPurchased: Boolean
        get() = prefs.getBoolean(IS_PURCHASED, false)
        set(value) = prefs.edit().putBoolean(IS_PURCHASED, value).apply()

    var notificationsAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, value).apply()

    var activityPermissionPrePromptShown: Boolean
        get() = prefs.getBoolean(KEY_ACTIVITY_PERMISSION_PREPROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVITY_PERMISSION_PREPROMPT_SHOWN, value).apply()

    var activityPermissionSystemRequestAsked: Boolean
        get() = prefs.getBoolean(KEY_ACTIVITY_PERMISSION_SYSTEM_REQUEST_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVITY_PERMISSION_SYSTEM_REQUEST_ASKED, value).apply()

    var audioPermissionPrePromptShown: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_PERMISSION_PREPROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_PERMISSION_PREPROMPT_SHOWN, value).apply()

    var audioPermissionSystemRequestAsked: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_PERMISSION_SYSTEM_REQUEST_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_PERMISSION_SYSTEM_REQUEST_ASKED, value).apply()

    var dailyStepsGoal: Int
        get() = prefs.getInt(KEY_DAILY_STEPS_GOAL, DEFAULT_DAILY_STEPS_GOAL)
        set(value) {
            val sanitized = value.coerceAtLeast(1)
            prefs.edit().putInt(KEY_DAILY_STEPS_GOAL, sanitized).apply()
        }

    var debugCurrentDateOverride: String?
        get() = prefs.getString(KEY_DEBUG_CURRENT_DATE_OVERRIDE, null)
        set(value) = prefs.edit().putString(KEY_DEBUG_CURRENT_DATE_OVERRIDE, value).apply()

    val favoriteAudioUris: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITE_AUDIO_URIS, emptySet()).orEmpty()

    fun setAudioFavorite(uri: String, isFavorite: Boolean) {
        val updated = favoriteAudioUris.toMutableSet()
        if (isFavorite) {
            updated.add(uri)
        } else {
            updated.remove(uri)
        }
        prefs.edit().putStringSet(KEY_FAVORITE_AUDIO_URIS, updated).apply()
    }

    var ecoTask: String
        get() = prefs.getString(KEY_ECO_TASK, null) ?: emitEcoTask()
        set(value) = prefs.edit().putString(KEY_ECO_TASK, value).apply()

    var isCompletedEcoTask: Boolean
        get() = prefs.getBoolean(IS_COMPLETED_ECO_TASK, false)
        set(value) = prefs.edit().putBoolean(IS_COMPLETED_ECO_TASK, value).apply()

    suspend fun completeCurrentEcoTask() {
        withContext(Dispatchers.IO) {
            val currentDate = getCurrentDate()
            val currentTask = ecoTask
            val lastCompletedDate = prefs.getString(KEY_LAST_COMPLETED_TASK_DATE, null)
            val lastCompletedTask = prefs.getString(KEY_LAST_COMPLETED_TASK_VALUE, null)
            if (lastCompletedDate == currentDate && lastCompletedTask == currentTask) {
                isCompletedEcoTask = true
                return@withContext
            }
            historyRepo.upsertDailySummary(
                HistoryEntity(
                    steps = prefs.getInt(KEY_DAILY_STEPS, 0),
                    date = currentDate,
                    goal = dailyStepsGoal,
                    task = currentTask,
                    isPurchased = true
                )
            )
            prefs.edit()
                .putBoolean(IS_COMPLETED_ECO_TASK, false)
                .putString(KEY_LAST_COMPLETED_TASK_DATE, currentDate)
                .putString(KEY_LAST_COMPLETED_TASK_VALUE, currentTask)
                .putString(KEY_ECO_TASK, emitNextEcoTask(exclude = currentTask))
                .apply()
        }
    }

    suspend fun undoCurrentEcoTask() {
        withContext(Dispatchers.IO) {
            val lastCompletedDate = prefs.getString(KEY_LAST_COMPLETED_TASK_DATE, null) ?: return@withContext
            val lastCompletedTask = prefs.getString(KEY_LAST_COMPLETED_TASK_VALUE, null) ?: return@withContext
            historyRepo.deleteLatestByDateAndTask(lastCompletedDate, lastCompletedTask)
            prefs.edit()
                .putBoolean(IS_COMPLETED_ECO_TASK, false)
                .putString(KEY_ECO_TASK, lastCompletedTask)
                .remove(KEY_LAST_COMPLETED_TASK_DATE)
                .remove(KEY_LAST_COMPLETED_TASK_VALUE)
                .apply()
        }
    }

    suspend fun skipCurrentEcoTask() {
        withContext(Dispatchers.IO) {
            ecoTask = emitNextEcoTask(exclude = ecoTask)
            isCompletedEcoTask = false
        }
    }

    private fun emitEcoTask(): String {
        ecoTask = emitNextEcoTask()
        return ecoTask
    }

    private fun emitNextEcoTask(exclude: String? = null): String {
        if (tasks.isEmpty()) return ""
        if (tasks.size == 1) return tasks.first()
        var next = tasks[Random.nextInt(tasks.size)]
        while (next == exclude) {
            next = tasks[Random.nextInt(tasks.size)]
        }
        return next
    }

    suspend fun addSteps(steps: Int): Int {
        return withContext(Dispatchers.IO) {
            resetIfNeeded()
            val currentSteps = prefs.getInt(KEY_DAILY_STEPS, 0)
            val newSteps = currentSteps + steps
            prefs.edit()
                .putInt(KEY_DAILY_STEPS, newSteps)
                .apply()
            historyRepo.upsertDailySummary(
                HistoryEntity(
                    steps = newSteps,
                    date = getCurrentDate(),
                    goal = dailyStepsGoal,
                    task = ecoTask,
                    isPurchased = true
                )
            )
            newSteps
        }
    }

    suspend fun getDailySteps(): Int = withContext(Dispatchers.IO) {
        resetIfNeeded()
        return@withContext prefs.getInt(KEY_DAILY_STEPS, 0)
    }

    suspend fun upsertTodayHistorySummary() {
        withContext(Dispatchers.IO) {
            resetIfNeeded()
            historyRepo.upsertDailySummary(
                HistoryEntity(
                    steps = prefs.getInt(KEY_DAILY_STEPS, 0),
                    date = getCurrentDate(),
                    goal = dailyStepsGoal,
                    task = ecoTask,
                    isPurchased = true
                )
            )
        }
    }

    private suspend fun resetIfNeeded() {
        val currentDate = getCurrentDate()
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, null)
        if (currentDate != lastResetDate) {
            if (lastResetDate != null) {
                val previousDaySteps = prefs.getInt(KEY_DAILY_STEPS, 0)
                val lastCompletedDate = prefs.getString(KEY_LAST_COMPLETED_TASK_DATE, null)
                val lastCompletedTask = prefs.getString(KEY_LAST_COMPLETED_TASK_VALUE, null).orEmpty()
                val hadCompletedTaskOnPreviousDay =
                    lastCompletedDate == lastResetDate && lastCompletedTask.isNotBlank()
                val shouldSavePreviousDay = previousDaySteps > 0 || hadCompletedTaskOnPreviousDay
                if (shouldSavePreviousDay) {
                    historyRepo.upsertDailySummary(
                        HistoryEntity(
                            steps = previousDaySteps,
                            date = lastResetDate,
                            goal = dailyStepsGoal,
                            task = if (hadCompletedTaskOnPreviousDay) lastCompletedTask else ecoTask,
                            isPurchased = true
                        )
                    )
                }
            }
            prefs.edit()
                .putInt(KEY_DAILY_STEPS, 0)
                .putString(KEY_LAST_RESET_DATE, currentDate)
                .apply()
            isCompletedEcoTask = false
            ecoTask = tasks[Random.nextInt(tasks.size)]
        }
    }

    private fun getCurrentDate(): String {
        debugCurrentDateOverride?.let { override ->
            if (override.isNotBlank()) return override
        }
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is zero-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year-$month-$day"
    }

    fun consumeDailyStepsGoalMilestone(steps: Int): Int? {
        val today = getCurrentDate()
        val goal = dailyStepsGoal.coerceAtLeast(1)
        val progress = ((steps.toDouble() / goal) * 100).toInt()
        val milestone = DAILY_STEPS_GOAL_MILESTONES
            .filter { progress >= it }
            .maxOrNull() ?: return null
        val notifiedKey = "$today:$milestone"
        if (prefs.getStringSet(KEY_DAILY_STEPS_GOAL_NOTIFIED_MILESTONES, emptySet())
                .orEmpty()
                .contains(notifiedKey)
        ) {
            return null
        }
        val updated = prefs.getStringSet(KEY_DAILY_STEPS_GOAL_NOTIFIED_MILESTONES, emptySet())
            .orEmpty()
            .toMutableSet()
        updated.add(notifiedKey)
        prefs.edit()
            .putStringSet(KEY_DAILY_STEPS_GOAL_NOTIFIED_MILESTONES, updated)
            .apply()
        return milestone
    }

    companion object {
        private const val KEY_DAILY_STEPS = "DAILY_STEPS"
        private const val KEY_ECO_TASK = "KEY_ECO_TASK"
        private const val KEY_LAST_RESET_DATE = "KEY_LAST_RESET_DATE"
        private const val KEY_LAST_COMPLETED_TASK_DATE = "KEY_LAST_COMPLETED_TASK_DATE"
        private const val KEY_LAST_COMPLETED_TASK_VALUE = "KEY_LAST_COMPLETED_TASK_VALUE"
        private const val IS_PURCHASED = "IS_PURCHASED"
        private const val IS_COMPLETED_ECO_TASK = "IS_COMPLETED_ECO_TASK"
        private const val KEY_NOTIFICATIONS_ASKED = "KEY_NOTIFICATIONS_ASKED"
        private const val KEY_ACTIVITY_PERMISSION_PREPROMPT_SHOWN =
            "KEY_ACTIVITY_PERMISSION_PREPROMPT_SHOWN"
        private const val KEY_ACTIVITY_PERMISSION_SYSTEM_REQUEST_ASKED =
            "KEY_ACTIVITY_PERMISSION_SYSTEM_REQUEST_ASKED"
        private const val KEY_AUDIO_PERMISSION_PREPROMPT_SHOWN =
            "KEY_AUDIO_PERMISSION_PREPROMPT_SHOWN"
        private const val KEY_AUDIO_PERMISSION_SYSTEM_REQUEST_ASKED =
            "KEY_AUDIO_PERMISSION_SYSTEM_REQUEST_ASKED"
        private const val KEY_DAILY_STEPS_GOAL = "KEY_DAILY_STEPS_GOAL"
        private const val KEY_DAILY_STEPS_GOAL_NOTIFIED_MILESTONES =
            "KEY_DAILY_STEPS_GOAL_NOTIFIED_MILESTONES"
        private const val KEY_DEBUG_CURRENT_DATE_OVERRIDE = "KEY_DEBUG_CURRENT_DATE_OVERRIDE"
        private const val KEY_FAVORITE_AUDIO_URIS = "KEY_FAVORITE_AUDIO_URIS"
        private const val DEFAULT_DAILY_STEPS_GOAL = 7000
        private val DAILY_STEPS_GOAL_MILESTONES = listOf(50, 75, 100)

        val tasks = listOf(
            "Discover three new local spots on foot.",
            "Plant a small tree or bush in a green area.",
            "Collect and recycle at least 5 kg of plastic items.",
            "Go a full day without any plastic bags.",
            "Replace one car trip with a bike ride.",
            "Pick up litter while strolling in a park.",
            "Take a shower in under 5 minutes.",
            "Gather organic waste for composting all day.",
            "Use only a reusable water container for 24 hours.",
            "Switch off lights whenever not needed.",
            "Substitute one standard bulb with an eco-friendly one.",
            "Recycle used batteries responsibly.",
            "Unplug devices before bedtime.",
            "Spend a day without relying on plastic products.",
            "Minimize water usage while washing dishes.",
            "Choose cloth napkins instead of paper ones.",
            "Host a small swap meeting (books, clothes, etc.).",
            "Use public transit instead of personal vehicles.",
            "Avoid all disposable utensils for 24 hours.",
            "Invest in sturdy food containers.",
            "Reduce air conditioning or heating usage for one day.",
            "Clean up trash near your home or office.",
            "Join or create a community cleanup project.",
            "Walk to the closest green area and back.",
            "Install smart gadgets to optimize power usage.",
            "Bring reusable totes for every purchase.",
            "Turn the faucet off while brushing teeth.",
            "Skip throwaway cutlery for a whole day.",
            "Buy unpackaged goods whenever possible.",
            "Refill bottles at public water stations.",
            "Attend an eco-awareness workshop or seminar.",
            "Take part in an online eco-challenge.",
            "Plan a tree-planting activity with friends.",
            "Sort household garbage and deliver it to a recycling spot.",
            "Walk 5 km instead of driving.",
            "Use a bicycle for all errands today.",
            "Stop using single-use coffee cups, use a thermos.",
            "Go hiking to appreciate local natural beauty.",
            "Switch to natural cleaning solutions.",
            "Pick products marked as environmentally friendly.",
            "Try out eco-friendly toiletries and cosmetics."
        )
    }

}