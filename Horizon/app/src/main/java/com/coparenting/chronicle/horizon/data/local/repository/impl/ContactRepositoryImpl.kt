package com.coparenting.chronicle.horizon.data.local.repository.impl

import com.coparenting.chronicle.horizon.data.local.database.dao.ContactDao
import com.coparenting.chronicle.horizon.domain.model.Contact
import com.coparenting.chronicle.horizon.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {
    
    override suspend fun saveContact(contact: Contact): Long {
        return contactDao.insertContact(contact)
    }
    
    override suspend fun saveContacts(contacts: List<Contact>): List<Long> {
        return contactDao.insertContacts(contacts)
    }
    
    override fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts()
    }
    
    override suspend fun getContactById(contactId: String): Contact? {
        return contactDao.getContactById(contactId)
    }
    
    override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        return contactDao.getContactByPhoneNumber(phoneNumber)
    }
    
    override fun getStarredContacts(): Flow<List<Contact>> {
        return contactDao.getStarredContacts()
    }
    
    override fun getRecentContacts(): Flow<List<Contact>> {
        return contactDao.getRecentContacts()
    }
    
    override suspend fun getTotalContactCount(): Int {
        return contactDao.getTotalContactCount()
    }
    
    override suspend fun updateContactLastContact(
        contactId: String,
        lastContactDate: LocalDateTime,
        lastMessageText: String,
        lastMessageTimestamp: LocalDateTime
    ): Int {
        return contactDao.updateContactLastContact(contactId, lastContactDate, lastMessageText, lastMessageTimestamp)
    }
    
    override suspend fun updateContactStarred(contactId: String, isStarred: Boolean): Int {
        return contactDao.updateContactStarred(contactId, isStarred)
    }
    
    override suspend fun updateContactNote(contactId: String, note: String?): Int {
        return contactDao.updateContactNote(contactId, note)
    }
    
    override suspend fun deleteContact(contactId: String): Int {
        return contactDao.deleteContact(contactId)
    }
    
    override fun searchContacts(query: String): Flow<List<Contact>> {
        return contactDao.searchContacts(query)
    }
    
    override suspend fun getActiveContactCountSince(since: LocalDateTime): Int {
        return contactDao.getActiveContactCountSince(since)
    }
    
    override fun getMostActiveContacts(limit: Int): Flow<List<Contact>> {
        return contactDao.getMostActiveContacts(limit)
    }
    
    override suspend fun getParents(parentNames: List<String>): List<Contact> {
        return contactDao.getParents(parentNames)
    }
}
