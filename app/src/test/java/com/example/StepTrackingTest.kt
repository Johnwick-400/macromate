package com.example

import com.example.data.repository.AccelStepDetector
import com.example.data.repository.StepMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StepTrackingTest {

    @Test
    fun counterDelta_handlesFirstReadingNormalGrowthAndReboot() {
        assertEquals(0, StepMath.counterDelta(last = -1, current = 5_000))
        assertEquals(120, StepMath.counterDelta(last = 5_000, current = 5_120))
        assertEquals(40, StepMath.counterDelta(last = 5_120, current = 40)) // counter reset by a reboot
    }

    @Test
    fun caloriesBurned_scalesWithStepsAndBodyWeight() {
        val tenK = StepMath.caloriesBurned(10_000, weightKg = 70.0, heightCm = 175.0)
        assertTrue("10k steps for a 70kg/175cm adult should land in a realistic range: $tenK", tenK in 220..320)
        assertEquals(0, StepMath.caloriesBurned(0, 70.0, 175.0))
        assertTrue(StepMath.caloriesBurned(5_000, 90.0, 175.0) > StepMath.caloriesBurned(5_000, 60.0, 175.0))
    }

    @Test
    fun accelDetector_countsOneStepPerWalkingCycle_andNoneWhenStill() {
        var steps = 0
        val walking = AccelStepDetector { steps++ }
        val stepHz = 2.0
        val sampleHz = 50
        for (i in 0 until sampleHz * 10) { // 10 seconds of walking at 2 steps/s
            val t = i.toDouble() / sampleHz
            val z = (9.81 + 3.0 * sin(2 * PI * stepHz * t)).toFloat()
            walking.onSample(0f, 0f, z, (t * 1e9).toLong())
        }
        assertTrue("expected ~20 steps, got $steps", steps in 18..22)

        var stillSteps = 0
        val still = AccelStepDetector { stillSteps++ }
        for (i in 0 until sampleHz * 10) still.onSample(0f, 0f, 9.81f, (i * 20_000_000L))
        assertEquals(0, stillSteps)
    }
}
