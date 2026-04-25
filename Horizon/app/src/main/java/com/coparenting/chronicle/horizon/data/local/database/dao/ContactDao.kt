package com.coparenting.chronicle.horizon.data.local.database.dao

import androidx.room.*
import com.coparenting.chronicle.horizon.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ContactDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<Contact>): List<Long>
    
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>
    
    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: String): Contact?
    
    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber")
    suspend fun getContactByPhoneNumber(phoneNumber: String): Contact?
    
    @Query("SELECT * FROM contacts WHERE isStarred = 1 ORDER BY name ASC")
    fun getStarredContacts(): Flow<List<Contact>>
    
    @Query("SELECT * FROM contacts WHERE lastContactDate IS NOT NULL ORDER BY lastContactDate DESC")
    fun getRecentContacts(): Flow<List<Contact>>
    
    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getTotalContactCount(): Int
    
    @Query("UPDATE contacts SET lastContactDate = :lastContactDate, lastMessageText = :lastMessageText, lastMessageTimestamp = :lastMessageTimestamp, messageCount = messageCount + 1 WHERE id = :contactId")
    suspend fun updateContactLastContact(
        contactId: String,
        lastContactDate: LocalDateTime,
        lastMessageText: String,
        lastMessageTimestamp: LocalDateTime
    ): Int
    
    @Query("UPDATE contacts SET isStarred = :isStarred WHERE id = :contactId")
    suspend fun updateContactStarred(contactId: String, isStarred: Boolean): Int
    
    @Query("UPDATE contacts SET note = :note WHERE id = :contactId")
    suspend fun updateContactNote(contactId: String, note: String?): Int
    
    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContact(contactId: String): Int
    
    @Query("SELECT * FROM contacts WHERE name LIKE :query OR phoneNumber LIKE :query")
    fun searchContacts(query: String): Flow<List<Contact>>
    
    @Query("SELECT COUNT(*) FROM contacts WHERE lastContactDate >= :since")
    suspend fun getActiveContactCountSince(since: LocalDateTime): Int
    
    @Query("SELECT * FROM contacts WHERE messageCount > 0 ORDER BY messageCount DESC LIMIT :limit")
    fun getMostActiveContacts(limit: Int): Flow<List<Contact>>
    
    @Query("SELECT * FROM contacts WHERE name IN (:parentNames)")
    suspend fun getParents(parentNames: List<String>): List<Contact>
}
