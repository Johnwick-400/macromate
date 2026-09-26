package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class StepSource { NONE, STEP_COUNTER, ACCELEROMETER }

data class StepState(val date: String = "", val steps: Int = 0, val source: StepSource = StepSource.NONE)

object StepMath {
    const val DAILY_STEP_GOAL = 10_000

    /** Steps since the previous reading of a since-boot cumulative counter; a drop means a reboot reset it. */
    fun counterDelta(last: Long, current: Long): Int = when {
        last < 0 -> 0
        current >= last -> (current - last).toInt()
        else -> current.toInt()
    }

    /** Stride ≈ 0.414 × height; walking burns ≈ 0.53 kcal per kg per km. */
    fun caloriesBurned(steps: Int, weightKg: Double, heightCm: Double): Int {
        val strideMeters = heightCm * 0.414 / 100.0
        val km = steps * strideMeters / 1000.0
        return (km * weightKg * 0.53).roundToInt()
    }
}

/** Counts a step on each upward crossing of the gravity-removed acceleration magnitude. */
internal class AccelStepDetector(private val onStep: () -> Unit) {
    private var baseline = 9.81f
    private var prevDynamic = 0f
    private var lastStepNs = -MIN_INTERVAL_NS

    fun onSample(x: Float, y: Float, z: Float, timestampNs: Long) {
        val magnitude = sqrt(x * x + y * y + z * z)
        baseline += BASELINE_ALPHA * (magnitude - baseline) // slow low-pass ≈ steady gravity
        val dynamic = magnitude - baseline
        if (dynamic > THRESHOLD && prevDynamic <= THRESHOLD && timestampNs - lastStepNs >= MIN_INTERVAL_NS) {
            lastStepNs = timestampNs
            onStep()
        }
        prevDynamic = dynamic
    }

    private companion object {
        const val THRESHOLD = 1.2f // m/s² above the gravity baseline
        const val BASELINE_ALPHA = 0.02f
        const val MIN_INTERVAL_NS = 300_000_000L // ≤ ~3.3 steps/s
    }
}

/**
 * Counts steps from the phone's motion sensors. Prefers the hardware step counter (the chip
 * derives it from the accelerometer, keeps counting while the app is closed, and catches up on
 * the next [start]); falls back to accelerometer peak detection when that sensor is missing or
 * the ACTIVITY_RECOGNITION permission isn't granted. Today's count is persisted locally.
 */
object StepTracker : SensorEventListener {
    private const val PREFS = "step_tracker"
    private const val KEY_DATE = "date"
    private const val KEY_STEPS = "steps"
    private const val KEY_LAST_COUNTER = "last_counter"

    private lateinit var prefs: SharedPreferences
    private var sensorManager: SensorManager? = null
    private var detector: AccelStepDetector? = null

    private val _state = MutableStateFlow(StepState())
    val state: StateFlow<StepState> = _state.asStateFlow()

    fun start(context: Context) {
        val app = context.applicationContext
        stop()
        if (!::prefs.isInitialized) prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm

        val today = today()
        val savedSteps = if (prefs.getString(KEY_DATE, null) == today) prefs.getInt(KEY_STEPS, 0) else 0

        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        val counter = if (permitted) sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) else null
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val source = when {
            counter != null -> {
                sm?.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL)
                StepSource.STEP_COUNTER
            }
            accel != null -> {
                // Forget the counter baseline so a later switch to the hardware counter
                // doesn't re-count steps this detector already counted.
                prefs.edit().putLong(KEY_LAST_COUNTER, -1).apply()
                detector = AccelStepDetector { addSteps(1) }
                sm?.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
                StepSource.ACCELEROMETER
            }
            else -> StepSource.NONE
        }
        _state.value = StepState(today, savedSteps, source)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        detector = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val value = event.values[0].toLong()
                val last = prefs.getLong(KEY_LAST_COUNTER, -1)
                prefs.edit().putLong(KEY_LAST_COUNTER, value).apply()
                addSteps(StepMath.counterDelta(last, value))
            }
            Sensor.TYPE_ACCELEROMETER ->
                detector?.onSample(event.values[0], event.values[1], event.values[2], event.timestamp)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun addSteps(count: Int) {
        val today = today()
        val current = _state.value
        val steps = (if (current.date == today) current.steps else 0) + count
        _state.value = current.copy(date = today, steps = steps)
        prefs.edit().putString(KEY_DATE, today).putInt(KEY_STEPS, steps).apply()
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
