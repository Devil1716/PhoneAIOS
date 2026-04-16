package com.phoneaios.app

import androidx.room.*
import android.content.Context

@Entity(tableName = "memory_graph")
data class MemoryTriple(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String,
    val predicate: String,
    val objectValue: String
)

@Dao
interface TripleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(triple: MemoryTriple)

    @Query("SELECT * FROM memory_graph WHERE subject = :subject")
    suspend fun getTriplesBySubject(subject: String): List<MemoryTriple>

    @Query("SELECT * FROM memory_graph WHERE subject = :subject AND predicate = :predicate")
    suspend fun findObject(subject: String, predicate: String): MemoryTriple?

    @Query("SELECT * FROM memory_graph")
    suspend fun getAllTriples(): List<MemoryTriple>
}

@Database(entities = [MemoryTriple::class], version = 1)
abstract class MemoryGraphDatabase : RoomDatabase() {
    abstract fun tripleDao(): TripleDao

    companion object {
        @Volatile
        private var INSTANCE: MemoryGraphDatabase? = null

        fun getDatabase(context: Context): MemoryGraphDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoryGraphDatabase::class.java,
                    "phoneai_memory"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
