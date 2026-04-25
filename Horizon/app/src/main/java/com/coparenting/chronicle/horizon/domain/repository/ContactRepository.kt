package com.coparenting.chronicle.horizon.domain.repository

import com.coparenting.chronicle.horizon.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface ContactRepository {
    
    suspend fun saveContact(contact: Contact): Long
    
    suspend fun saveContacts(contacts: List<Contact>): List<Long>
    
    fun getAllContacts(): Flow<List<Contact>>
    
    suspend fun getContactById(contactId: String): Contact?
    
    suspend fun getContactByPhoneNumber(phoneNumber: String): Contact?
    
    fun getStarredContacts(): Flow<List<Contact>>
    
    fun getRecentContacts(): Flow<List<Contact>>
    
    suspend fun getTotalContactCount(): Int
    
    suspend fun updateContactLastContact(
        contactId: String,
        lastContactDate: LocalDateTime,
        lastMessageText: String,
        lastMessageTimestamp: LocalDateTime
    ): Int
    
    suspend fun updateContactStarred(contactId: String, isStarred: Boolean): Int
    
    suspend fun updateContactNote(contactId: String, note: String?): Int
    
    suspend fun deleteContact(contactId: String): Int
    
    fun searchContacts(query: String): Flow<List<Contact>>
    
    suspend fun getActiveContactCountSince(since: LocalDateTime): Int
    
    fun getMostActiveContacts(limit: Int): Flow<List<Contact>>
    
    suspend fun getParents(parentNames: List<String>): List<Contact>
}
