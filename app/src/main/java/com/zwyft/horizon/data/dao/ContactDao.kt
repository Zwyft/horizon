package com.zwyft.horizon.data.dao

import androidx.room.*
import com.zwyft.horizon.data.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>): List<Long>

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE monitored = 1 ORDER BY name ASC")
    suspend fun getMonitored(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE normalizedPhoneNumber = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phoneNumber = :raw LIMIT 1")
    suspend fun findByRaw(raw: String): ContactEntity?

    @Query("UPDATE contacts SET monitored = :monitored WHERE id = :id")
    suspend fun setMonitored(id: Long, monitored: Boolean)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE monitored = 1")
    suspend fun countMonitored(): Int

    // Flow for UI
    @Query("SELECT * FROM contacts WHERE monitored = 1 ORDER BY name ASC")
    fun observeMonitored(): Flow<List<ContactEntity>>
}
