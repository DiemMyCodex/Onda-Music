package com.onda.music.core.design

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun rememberDeviceTiltOffset(
    enabled: Boolean = true,
    maxDegrees: Float = 10f
): State<Offset> {
    val reducedMotion = OndaTheme.reducedMotion
    val context = LocalContext.current
    val tiltState = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(context, enabled, reducedMotion, maxDegrees) {
        if (!enabled || reducedMotion) {
            tiltState.value = Offset.Zero
            onDispose {}
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            if (sensorManager == null || rotationSensor == null) {
                tiltState.value = Offset.Zero
                onDispose {}
            } else {
                val maxRadians = Math.toRadians(maxDegrees.coerceAtLeast(1f).toDouble()).toFloat()
                val rawQuat = FloatArray(4)
                var hasBaseline = false
                var bw = 1f
                var bx = 0f
                var by = 0f
                var bz = 0f
                var smoothedX = 0f
                var smoothedY = 0f

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.values.size < 3) return
                        SensorManager.getQuaternionFromVector(rawQuat, event.values)
                        var cw = rawQuat[0]
                        var cx = rawQuat[1]
                        var cy = rawQuat[2]
                        var cz = rawQuat[3]
                        val cNorm = sqrt(cw * cw + cx * cx + cy * cy + cz * cz)
                        if (cNorm <= 1e-6f) return
                        cw /= cNorm
                        cx /= cNorm
                        cy /= cNorm
                        cz /= cNorm

                        if (!hasBaseline) {
                            hasBaseline = true
                            bw = cw
                            bx = cx
                            by = cy
                            bz = cz
                            return
                        }

                        val dot = bw * cw + bx * cx + by * cy + bz * cz
                        if (dot < 0f) {
                            cw = -cw
                            cx = -cx
                            cy = -cy
                            cz = -cz
                        }

                        val rw = bw * cw + bx * cx + by * cy + bz * cz
                        val rx = bw * cx - bx * cw - by * cz + bz * cy
                        val ry = bw * cy + bx * cz - by * cw - bz * cx

                        val vecNorm = sqrt(rx * rx + ry * ry)
                        val pitchRad: Float
                        val rollRad: Float
                        val rz = bw * cz - bx * cy + by * cx - bz * cw
                        val fullVecNorm = sqrt(rx * rx + ry * ry + rz * rz)
                        if (fullVecNorm > 1e-6f && vecNorm > 0f) {
                            val halfAngle = atan2(fullVecNorm, abs(rw))
                            val sign = if (rw < 0f) -1f else 1f
                            val scale = (2f * halfAngle * sign) / fullVecNorm
                            pitchRad = rx * scale
                            rollRad = ry * scale
                        } else {
                            pitchRad = 0f
                            rollRad = 0f
                        }

                        val baselineDrift = 0.015f
                        val keep = 1f - baselineDrift
                        val nbw = keep * bw + baselineDrift * cw
                        val nbx = keep * bx + baselineDrift * cx
                        val nby = keep * by + baselineDrift * cy
                        val nbz = keep * bz + baselineDrift * cz
                        val bNorm = sqrt(nbw * nbw + nbx * nbx + nby * nby + nbz * nbz)
                        if (bNorm > 1e-6f) {
                            bw = nbw / bNorm
                            bx = nbx / bNorm
                            by = nby / bNorm
                            bz = nbz / bNorm
                        }

                        val targetX = (rollRad / maxRadians).coerceIn(-1f, 1f)
                        val targetY = (pitchRad / maxRadians).coerceIn(-1f, 1f)
                        val smoothing = 0.18f
                        smoothedX += (targetX - smoothedX) * smoothing
                        smoothedY += (targetY - smoothedY) * smoothing

                        val nextOffset = Offset(
                            x = smoothedX.coerceIn(-1f, 1f),
                            y = smoothedY.coerceIn(-1f, 1f)
                        )
                        val current = tiltState.value
                        if (abs(nextOffset.x - current.x) > 0.002f ||
                            abs(nextOffset.y - current.y) > 0.002f
                        ) {
                            tiltState.value = nextOffset
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                sensorManager.registerListener(
                    listener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
                onDispose {
                    sensorManager.unregisterListener(listener)
                    tiltState.value = Offset.Zero
                }
            }
        }
    }

    return tiltState
}
