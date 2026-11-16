package br.com.lunelli.lulucoreef.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.KSerializer

class Converters {
    @TypeConverter fun intsToString(v: List<Int>?): String? = v?.joinToString(",")
    @TypeConverter fun stringToInts(v: String?): List<Int>? =
        v?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
}

@Database(
    entities = [TankConfig::class, ParamRange::class, Product::class, Equipment::class, WaterTest::class, WaterChange::class, Dosing::class, Routine::class, RoutineLog::class, Livestock::class, HealthCase::class, HealthEntry::class, Event::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun tankConfig(): TankConfigDao
    abstract fun paramRanges(): ParamRangeDao
    abstract fun products(): ProductDao
    abstract fun equipment(): EquipmentDao
    abstract fun waterTests(): WaterTestDao
    abstract fun waterChanges(): WaterChangeDao
    abstract fun dosings(): DosingDao
    abstract fun routines(): RoutineDao
    abstract fun routineLogs(): RoutineLogDao
    abstract fun livestock(): LivestockDao
    abstract fun healthCases(): HealthCaseDao
    abstract fun healthEntries(): HealthEntryDao
    abstract fun events(): EventDao

    /** Tabelas na ordem de envio (respeita chaves estrangeiras). */
    fun syncTables(): List<SyncTable<*>> = listOf(
        SyncTable("tank_config", tankConfig(), TankConfig.serializer()),
        SyncTable("param_ranges", paramRanges(), ParamRange.serializer()),
        SyncTable("products", products(), Product.serializer()),
        SyncTable("equipment", equipment(), Equipment.serializer()),
        SyncTable("water_tests", waterTests(), WaterTest.serializer()),
        SyncTable("water_changes", waterChanges(), WaterChange.serializer()),
        SyncTable("dosings", dosings(), Dosing.serializer()),
        SyncTable("routines", routines(), Routine.serializer()),
        SyncTable("routine_logs", routineLogs(), RoutineLog.serializer()),
        SyncTable("livestock", livestock(), Livestock.serializer()),
        SyncTable("health_cases", healthCases(), HealthCase.serializer()),
        SyncTable("health_entries", healthEntries(), HealthEntry.serializer()),
        SyncTable("events", events(), Event.serializer()),
    )

    companion object {
        fun build(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "luluco.db").build()
    }
}

class SyncTable<T : Syncable>(val name: String, val dao: SyncDao<T>, val serializer: KSerializer<T>)
