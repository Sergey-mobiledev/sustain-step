package com.sustain.step.data.repo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.sustain.step.data.repo.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class StepsRepo(
    private val context: Context,
    private val settings: Settings
) : SensorEventListener {

    private var stepCount = 0

    private val stepsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val _stepsData = MutableStateFlow<StepsRepositoryData?>(null)
    val stepsData: SharedFlow<StepsRepositoryData?> = _stepsData

    @Volatile
    private var isCounting = false

    suspend fun emitActualData() {
        stepCount = settings.getDailySteps()
        _stepsData.value = StepsRepositoryData(
            isCounting = isCounting,
            stepsCount = stepCount
        )
    }

    @Synchronized
    fun startCounting() {
        if (isCounting || stepSensor == null) return
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, mainHandler)
        isCounting = true
    }

    @Synchronized
    fun stopCounting() {
        if (!isCounting) return
        isCounting = false
        sensorManager.unregisterListener(this, stepSensor)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR || !isCounting) return
        stepsScope.launch {
            val newStepsCount = settings.addSteps(1)
            stepCount = newStepsCount
            _stepsData.value = StepsRepositoryData(
                isCounting = isCounting,
                stepsCount = newStepsCount,
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }


}

data class StepsRepositoryData(
    val isCounting: Boolean = false,
    val stepsCount: Int = 0
)