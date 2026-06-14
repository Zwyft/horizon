package com.zwyft.horizon.repository

import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing monitored contacts.
 *
 * Responsibilities:
 * - CRUD for ContactEntity
 * - Normalize phone numbers (strip non-digits)
 * - Sync monitored flag to existing messages (tag past messages)
 */
@Singleton
class ContactRepository @Inject constructor(private val db: HorizonDatabase) {

    private val dao = db.contactDao()
    private val msgDao = db.messageDao()

    /**
     * Add a new monitored contact.
     * Also tags all past messages from this number as monitored.
     */
    suspend fun addContact(
        name: String,
        phoneNumber: String,
        relationship: String? = null
    ): Long {
        val normalized = normalize(phoneNumber)
        val entity = ContactEntity(
            name = name,
            phoneNumber = phoneNumber,
            normalizedPhoneNumber = normalized,
            relationship = relationship,
            monitored = true
        )
        val id = dao.insert(entity)

        // Tag past messages as monitored
        msgDao.setMonitoredByAddresses(listOf(phoneNumber, normalized), true)

        return id
    }

    /**
     * Remove a contact (or just un-monitor it).
     */
    suspend fun removeContact(contact: ContactEntity) {
        dao.delete(contact)
        // Un-tag messages
        msgDao.setMonitoredByAddresses(
            listOf(contact.phoneNumber, contact.normalizedPhoneNumber),
            false
        )
    }

    /**
     * Toggle monitored flag for a contact.
     */
    suspend fun toggleMonitored(contactId: Long, monitored: Boolean) {
        dao.setMonitored(contactId, monitored)
        val contact = dao.getById(contactId) ?: return
        msgDao.setMonitoredByAddresses(
            listOf(contact.phoneNumber, contact.normalizedPhoneNumber),
            monitored
        )
    }

    /**
     * Get all monitored contacts (Flow for UI).
     */
    fun getMonitoredContacts(): Flow<List<ContactEntity>> =
        dao.observeMonitored()

    /**
     * Get all contacts (for settings).
     */
    suspend fun getAllContacts(): List<ContactEntity> = dao.getAll()

    /**
     * Seed default contacts (mom, her parents, grandparents, babysitter Amanda).
     * Call once on first launch.
     */
    suspend fun seedDefaults() {
        if (dao.count() > 0) return
        val defaults = listOf(
            ContactEntity(name = "Mom", phoneNumber = "", normalizedPhoneNumber = "", relationship = "mom", monitored = true),
            ContactEntity(name = "Dad", phoneNumber = "", normalizedPhoneNumber = "", relationship = "dad", monitored = true),
            ContactEntity(name = "Grandma", phoneNumber = "", normalizedPhoneNumber = "", relationship = "grandparent", monitored = true),
            ContactEntity(name = "Grandpa", phoneNumber = "", normalizedPhoneNumber = "", relationship = "grandparent", monitored = true),
            ContactEntity(name = "Amanda", phoneNumber = "", normalizedPhoneNumber = "", relationship = "babysitter", monitored = true),
        )
        dao.insertAll(defaults)
    }

    companion object {
        fun normalize(phone: String): String =
            phone.replace(Regex("[^0-9]"), "")
    }
}
