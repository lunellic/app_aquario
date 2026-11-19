package br.com.lunelli.lulucoreef.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.KSerializer

class Converters {
    @TypeConverter fun intsToString(v: List<Int>?): String? = v?.joinToString(",")
    @TypeConverter fun stringToInts(v: String?): List<Int>? =
        v?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
}

@Database(
    entities = [TankConfig::class, ParamRange::class, Product::class, Equipment::class, WaterTest::class, WaterChange::class, Dosing::class, Routine::class, RoutineLog::class, Livestock::class, HealthCase::class, HealthEntry::class, Event::class, SpeciesInfo::class, AiAnalysis::class, FinanceEntry::class, ShoppingItem::class],
    version = 4,
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
    abstract fun speciesInfo(): SpeciesInfoDao
    abstract fun aiAnalyses(): AiAnalysisDao
    abstract fun finance(): FinanceEntryDao
    abstract fun shopping(): ShoppingItemDao

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
        SyncTable("species_info", speciesInfo(), SpeciesInfo.serializer()),
        SyncTable("ai_analyses", aiAnalyses(), AiAnalysis.serializer()),
        SyncTable("shopping_items", shopping(), ShoppingItem.serializer()),
        SyncTable("finance_entries", finance(), FinanceEntry.serializer()),
    )

    companion object {
        fun build(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "luluco.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        /** v2: water_tests.baixaConfianca. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE water_tests ADD COLUMN baixaConfianca INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4: forma de pagamento e parcelas. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE finance_entries ADD COLUMN `forma` TEXT NOT NULL DEFAULT 'pix'")
                db.execSQL("ALTER TABLE finance_entries ADD COLUMN `parcela` INTEGER")
                db.execSQL("ALTER TABLE finance_entries ADD COLUMN `parcelas` INTEGER")
                db.execSQL("ALTER TABLE finance_entries ADD COLUMN `grupo` TEXT")
            }
        }

        /** v3: financas e lista de compras. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `finance_entries` (`lancadoEm` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `categoria` TEXT NOT NULL, `descricao` TEXT NOT NULL, `valor` REAL NOT NULL, `aquario` TEXT NOT NULL, `shoppingItemId` TEXT, `observacao` TEXT, `id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `pending` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (`nome` TEXT NOT NULL, `categoria` TEXT NOT NULL, `valorMedio` REAL, `prioridade` TEXT NOT NULL, `aquario` TEXT NOT NULL, `observacao` TEXT, `compradoEm` INTEGER, `valorPago` REAL, `id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `pending` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}

class SyncTable<T : Syncable>(val name: String, val dao: SyncDao<T>, val serializer: KSerializer<T>)
