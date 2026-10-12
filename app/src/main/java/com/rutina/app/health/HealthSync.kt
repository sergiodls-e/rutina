package com.rutina.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.HabitLog
import com.rutina.app.data.TrackingType
import com.rutina.app.domain.HabitLogic
import com.rutina.app.domain.HealthMetric
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

enum class HealthStatus { UNAVAILABLE, NEEDS_UPDATE, NO_PERMISSION, READY }

/**
 * Lee de Health Connect (en el propio teléfono, sin Internet) los totales por día
 * y los guarda como registros locales de los hábitos de tipo Salud.
 */
class HealthSync(private val app: Context, private val db: AppDatabase) {

    private val mutex = Mutex()
    private val _status = MutableStateFlow(HealthStatus.UNAVAILABLE)
    val status: StateFlow<HealthStatus> = _status
    private val _lastSync = MutableStateFlow<Long?>(null)
    val lastSync: StateFlow<Long?> = _lastSync

    private val client: HealthConnectClient? by lazy {
        if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(app) else null
    }

    private fun sdkStatus(): Int = runCatching { HealthConnectClient.getSdkStatus(app) }
        .getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    /** Permisos de solo lectura que pide la app. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    /** Contrato para pedir permisos desde una pantalla. */
    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    /** Abre la tienda para instalar o actualizar Health Connect. */
    fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun refreshStatus(): HealthStatus {
        val s = when (sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val granted = runCatching { client?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()
                if (granted.isEmpty()) HealthStatus.NO_PERMISSION else HealthStatus.READY
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_UPDATE
            else -> HealthStatus.UNAVAILABLE
        }
        _status.value = s
        return s
    }

    /** Sincroniza los últimos [days] días de todos los hábitos de Salud. */
    suspend fun syncAll(days: Long = 14) = mutex.withLock {
        val habits = db.habits().observeActive().first().filter { it.type == TrackingType.HEALTH }
        if (habits.isEmpty()) return@withLock
        if (refreshStatus() != HealthStatus.READY) return@withLock
        val c = client ?: return@withLock
        val granted = runCatching { c.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

        val today = LocalDate.now()
        val start = today.minusDays(days - 1).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()

        for (h in habits) {
            val metric = HealthMetric.of(h)
            if (permissionFor(metric) !in granted) continue
            val buckets = runCatching {
                c.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(aggregateFor(metric)),
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                )
            }.getOrNull() ?: continue
            val byDay = buckets.associate { it.startTime.toLocalDate() to read(metric, it.result) }
            var d = start.toLocalDate()
            while (d <= today) {
                val from = HabitLogic.startOfDay(d)
                val to = HabitLogic.startOfDay(d.plusDays(1))
                db.habits().deleteLogsBetween(h.id, from, to)
                val v = byDay[d] ?: 0.0
                if (v > 0) {
                    val at = if (d == today) System.currentTimeMillis().coerceAtMost(to - 1)
                    else d.atTime(LocalTime.of(23, 59)).atZone(HabitLogic.zone).toInstant().toEpochMilli()
                    db.habits().insertLog(HabitLog(habitId = h.id, timestamp = at, value = v, note = NOTE))
                }
                d = d.plusDays(1)
            }
        }
        _lastSync.value = System.currentTimeMillis()
    }

    private fun permissionFor(m: HealthMetric): String = when (m) {
        HealthMetric.STEPS -> HealthPermission.getReadPermission(StepsRecord::class)
        HealthMetric.DISTANCE -> HealthPermission.getReadPermission(DistanceRecord::class)
        HealthMetric.CALORIES -> HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        HealthMetric.FLOORS -> HealthPermission.getReadPermission(FloorsClimbedRecord::class)
        HealthMetric.EXERCISE -> HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    }

    private fun aggregateFor(m: HealthMetric): AggregateMetric<*> = when (m) {
        HealthMetric.STEPS -> StepsRecord.COUNT_TOTAL
        HealthMetric.DISTANCE -> DistanceRecord.DISTANCE_TOTAL
        HealthMetric.CALORIES -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
        HealthMetric.FLOORS -> FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
        HealthMetric.EXERCISE -> ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
    }

    private fun read(m: HealthMetric, r: AggregationResult): Double = when (m) {
        HealthMetric.STEPS -> r[StepsRecord.COUNT_TOTAL]?.toDouble()
        HealthMetric.DISTANCE -> r[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.let { Math.round(it * 100) / 100.0 }
        HealthMetric.CALORIES -> r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.let { Math.round(it).toDouble() }
        HealthMetric.FLOORS -> r[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]?.let { Math.round(it).toDouble() }
        HealthMetric.EXERCISE -> r[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()?.toDouble()
    } ?: 0.0

    companion object {
        /** Marca de los registros que vienen de Health Connect (no se muestra). */
        const val NOTE = "health"
    }
}
