package br.com.lunelli.lulucoreef.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// GERADO junto com Entities.kt.

interface SyncDao<T> {
    @Upsert suspend fun upsert(item: T)
    suspend fun pending(): List<T>
    suspend fun get(id: String): T?
    suspend fun markSynced(id: String, updatedAt: Long)
    suspend fun pendingCount(): Int
}

@Dao
interface TankConfigDao : SyncDao<TankConfig> {
    @Query("SELECT * FROM tank_config WHERE deletedAt IS NULL ORDER BY createdAt ASC") fun all(): Flow<List<TankConfig>>
    @Query("SELECT * FROM tank_config WHERE deletedAt IS NULL") suspend fun list(): List<TankConfig>
    @Query("SELECT * FROM tank_config WHERE pending = 1") override suspend fun pending(): List<TankConfig>
    @Query("SELECT * FROM tank_config WHERE id = :id") override suspend fun get(id: String): TankConfig?
    @Query("UPDATE tank_config SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM tank_config WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface ParamRangeDao : SyncDao<ParamRange> {
    @Query("SELECT * FROM param_ranges WHERE deletedAt IS NULL ORDER BY parametro ASC") fun all(): Flow<List<ParamRange>>
    @Query("SELECT * FROM param_ranges WHERE pending = 1") override suspend fun pending(): List<ParamRange>
    @Query("SELECT * FROM param_ranges WHERE id = :id") override suspend fun get(id: String): ParamRange?
    @Query("UPDATE param_ranges SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM param_ranges WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface WaterTestDao : SyncDao<WaterTest> {
    @Query("SELECT * FROM water_tests WHERE deletedAt IS NULL ORDER BY medidoEm DESC") fun all(): Flow<List<WaterTest>>
    @Query("SELECT * FROM water_tests WHERE pending = 1") override suspend fun pending(): List<WaterTest>
    @Query("SELECT * FROM water_tests WHERE id = :id") override suspend fun get(id: String): WaterTest?
    @Query("UPDATE water_tests SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM water_tests WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface WaterChangeDao : SyncDao<WaterChange> {
    @Query("SELECT * FROM water_changes WHERE deletedAt IS NULL ORDER BY feitaEm DESC") fun all(): Flow<List<WaterChange>>
    @Query("SELECT * FROM water_changes WHERE pending = 1") override suspend fun pending(): List<WaterChange>
    @Query("SELECT * FROM water_changes WHERE id = :id") override suspend fun get(id: String): WaterChange?
    @Query("UPDATE water_changes SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM water_changes WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface ProductDao : SyncDao<Product> {
    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY nome ASC") fun all(): Flow<List<Product>>
    @Query("SELECT * FROM products WHERE pending = 1") override suspend fun pending(): List<Product>
    @Query("SELECT * FROM products WHERE id = :id") override suspend fun get(id: String): Product?
    @Query("UPDATE products SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM products WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface EquipmentDao : SyncDao<Equipment> {
    @Query("SELECT * FROM equipment WHERE deletedAt IS NULL ORDER BY tipo ASC") fun all(): Flow<List<Equipment>>
    @Query("SELECT * FROM equipment WHERE pending = 1") override suspend fun pending(): List<Equipment>
    @Query("SELECT * FROM equipment WHERE id = :id") override suspend fun get(id: String): Equipment?
    @Query("UPDATE equipment SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM equipment WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface DosingDao : SyncDao<Dosing> {
    @Query("SELECT * FROM dosings WHERE deletedAt IS NULL ORDER BY dosadoEm DESC") fun all(): Flow<List<Dosing>>
    @Query("SELECT * FROM dosings WHERE pending = 1") override suspend fun pending(): List<Dosing>
    @Query("SELECT * FROM dosings WHERE id = :id") override suspend fun get(id: String): Dosing?
    @Query("UPDATE dosings SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM dosings WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface RoutineDao : SyncDao<Routine> {
    @Query("SELECT * FROM routines WHERE deletedAt IS NULL ORDER BY horario ASC, titulo ASC") fun all(): Flow<List<Routine>>
    @Query("SELECT * FROM routines WHERE pending = 1") override suspend fun pending(): List<Routine>
    @Query("SELECT * FROM routines WHERE id = :id") override suspend fun get(id: String): Routine?
    @Query("UPDATE routines SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM routines WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface RoutineLogDao : SyncDao<RoutineLog> {
    @Query("SELECT * FROM routine_logs WHERE deletedAt IS NULL ORDER BY previstoPara DESC") fun all(): Flow<List<RoutineLog>>
    @Query("SELECT * FROM routine_logs WHERE pending = 1") override suspend fun pending(): List<RoutineLog>
    @Query("SELECT * FROM routine_logs WHERE id = :id") override suspend fun get(id: String): RoutineLog?
    @Query("UPDATE routine_logs SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM routine_logs WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface LivestockDao : SyncDao<Livestock> {
    @Query("SELECT * FROM livestock WHERE deletedAt IS NULL ORDER BY tipo ASC, nomePopular ASC") fun all(): Flow<List<Livestock>>
    @Query("SELECT * FROM livestock WHERE pending = 1") override suspend fun pending(): List<Livestock>
    @Query("SELECT * FROM livestock WHERE id = :id") override suspend fun get(id: String): Livestock?
    @Query("UPDATE livestock SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM livestock WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface HealthCaseDao : SyncDao<HealthCase> {
    @Query("SELECT * FROM health_cases WHERE deletedAt IS NULL ORDER BY abertoEm DESC") fun all(): Flow<List<HealthCase>>
    @Query("SELECT * FROM health_cases WHERE pending = 1") override suspend fun pending(): List<HealthCase>
    @Query("SELECT * FROM health_cases WHERE id = :id") override suspend fun get(id: String): HealthCase?
    @Query("UPDATE health_cases SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM health_cases WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface HealthEntryDao : SyncDao<HealthEntry> {
    @Query("SELECT * FROM health_entries WHERE deletedAt IS NULL ORDER BY registradoEm DESC") fun all(): Flow<List<HealthEntry>>
    @Query("SELECT * FROM health_entries WHERE pending = 1") override suspend fun pending(): List<HealthEntry>
    @Query("SELECT * FROM health_entries WHERE id = :id") override suspend fun get(id: String): HealthEntry?
    @Query("UPDATE health_entries SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM health_entries WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface EventDao : SyncDao<Event> {
    @Query("SELECT * FROM events WHERE deletedAt IS NULL ORDER BY ocorridoEm DESC") fun all(): Flow<List<Event>>
    @Query("SELECT * FROM events WHERE pending = 1") override suspend fun pending(): List<Event>
    @Query("SELECT * FROM events WHERE id = :id") override suspend fun get(id: String): Event?
    @Query("UPDATE events SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM events WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface SpeciesInfoDao : SyncDao<SpeciesInfo> {
    @Query("SELECT * FROM species_info WHERE deletedAt IS NULL ORDER BY geradaEm DESC") fun all(): Flow<List<SpeciesInfo>>
    @Query("SELECT * FROM species_info WHERE pending = 1") override suspend fun pending(): List<SpeciesInfo>
    @Query("SELECT * FROM species_info WHERE id = :id") override suspend fun get(id: String): SpeciesInfo?
    @Query("UPDATE species_info SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM species_info WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface AiAnalysisDao : SyncDao<AiAnalysis> {
    @Query("SELECT * FROM ai_analyses WHERE deletedAt IS NULL ORDER BY geradaEm DESC") fun all(): Flow<List<AiAnalysis>>
    @Query("SELECT * FROM ai_analyses WHERE pending = 1") override suspend fun pending(): List<AiAnalysis>
    @Query("SELECT * FROM ai_analyses WHERE id = :id") override suspend fun get(id: String): AiAnalysis?
    @Query("UPDATE ai_analyses SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM ai_analyses WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface FinanceEntryDao : SyncDao<FinanceEntry> {
    @Query("SELECT * FROM finance_entries WHERE deletedAt IS NULL ORDER BY lancadoEm DESC") fun all(): Flow<List<FinanceEntry>>
    @Query("SELECT * FROM finance_entries WHERE pending = 1") override suspend fun pending(): List<FinanceEntry>
    @Query("SELECT * FROM finance_entries WHERE id = :id") override suspend fun get(id: String): FinanceEntry?
    @Query("UPDATE finance_entries SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM finance_entries WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface ShoppingItemDao : SyncDao<ShoppingItem> {
    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL ORDER BY createdAt DESC") fun all(): Flow<List<ShoppingItem>>
    @Query("SELECT * FROM shopping_items WHERE pending = 1") override suspend fun pending(): List<ShoppingItem>
    @Query("SELECT * FROM shopping_items WHERE id = :id") override suspend fun get(id: String): ShoppingItem?
    @Query("UPDATE shopping_items SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM shopping_items WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface AlarmDao : SyncDao<Alarm> {
    @Query("SELECT * FROM alarms WHERE deletedAt IS NULL ORDER BY hora, minuto") fun all(): Flow<List<Alarm>>
    @Query("SELECT * FROM alarms WHERE deletedAt IS NULL") suspend fun list(): List<Alarm>
    @Query("SELECT * FROM alarms WHERE id = :id") override suspend fun get(id: String): Alarm?
    @Query("SELECT * FROM alarms WHERE pending = 1") override suspend fun pending(): List<Alarm>
    @Query("UPDATE alarms SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM alarms WHERE pending = 1") override suspend fun pendingCount(): Int
}

@Dao
interface TimerDao : SyncDao<TimerRow> {
    @Query("SELECT * FROM timers WHERE deletedAt IS NULL AND encerradoEm IS NULL ORDER BY terminaEm") suspend fun ativos(): List<TimerRow>
    @Query("SELECT * FROM timers WHERE id = :id") override suspend fun get(id: String): TimerRow?
    @Query("SELECT * FROM timers WHERE pending = 1") override suspend fun pending(): List<TimerRow>
    @Query("UPDATE timers SET pending = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markSynced(id: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM timers WHERE pending = 1") override suspend fun pendingCount(): Int
}
