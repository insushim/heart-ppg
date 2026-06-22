package com.heart.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.heart.core.model.MeasurementResult
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val bpm: Double,
    val rmssdMs: Double,
    val sdnnMs: Double,
    val pnn50: Double,
    val perfusionIndex: Double,
    val snrDb: Double,
    val quality: String,
    val autonomic: String,
    val respirationBpm: Double?,
    val irregularRhythm: Boolean,
)

fun MeasurementResult.toEntity(timestamp: Long) = MeasurementEntity(
    timestamp = timestamp,
    bpm = bpm,
    rmssdMs = rmssdMs,
    sdnnMs = sdnnMs,
    pnn50 = pnn50,
    perfusionIndex = perfusionIndex,
    snrDb = snrDb,
    quality = quality.name,
    autonomic = autonomic.name,
    respirationBpm = respirationBpm,
    irregularRhythm = irregularRhythm,
)

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insert(entity: MeasurementEntity): Long

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MeasurementEntity>
}

@Database(entities = [MeasurementEntity::class], version = 1, exportSchema = false)
abstract class HeartDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        fun create(context: Context): HeartDatabase =
            Room.databaseBuilder(context, HeartDatabase::class.java, "heart.db").build()
    }
}
