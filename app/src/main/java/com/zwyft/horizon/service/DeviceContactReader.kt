package com.zwyft.horizon.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's system address book.
 *
 * Two uses:
 *  - [readAll]: enumerate all contacts (for a multi-select import screen)
 *  - [lookupName]: resolve a single phone number → display name
 *    (used by MessageSyncManager to label messages during sync)
 */
class DeviceContactReader(private val context: Context) {

    /** True if the app currently holds READ_CONTACTS permission. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * All device contacts with at least one phone number, normalized.
     * Returns the deduplicated primary name + a set of phone variants so
     * the caller can pick which to insert as a monitored contact.
     */
    suspend fun readAll(): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val results = LinkedHashMap<Long, DeviceContact>()

        // First, list all contacts
        val contactsUri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} ASC"

        context.contentResolver.query(contactsUri, projection, null, null, sortOrder)?.use { c ->
            val idIdx    = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx  = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhone = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (c.moveToNext()) {
                val id = if (idIdx >= 0) c.getLong(idIdx) else continue
                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                if (name.isBlank()) continue
                val has = if (hasPhone >= 0) c.getInt(hasPhone) > 0 else false
                if (!has) continue

                results[id] = DeviceContact(
                    contactId = id,
                    displayName = name,
                    phones = emptyList()
                )
            }
        }

        // Then, fetch every phone number and bucket by contact id
        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(phoneUri, phoneProjection, null, null, null)?.use { c ->
            val cidIdx  = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numIdx  = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val cid = if (cidIdx >= 0) c.getLong(cidIdx) else continue
                val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                if (num.isBlank()) continue
                val existing = results[cid] ?: continue
                val trimmed = num.trim()
                if (existing.phones.none { it.raw == trimmed }) {
                    results[cid] = existing.copy(
                        phones = existing.phones + DevicePhone(raw = trimmed)
                    )
                }
            }
        }

        results.values
            .filter { it.phones.isNotEmpty() }
            .toList()
    }

    /**
     * Resolve a single phone number to a display name via [ContactsContract.PhoneLookup].
     * Returns null if no match or if permission is missing.
     *
     * This is the cheapest way to label messages during sync.
     */
    suspend fun lookupName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (!hasPermission() || phoneNumber.isBlank()) return@withContext null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        return@withContext try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class DeviceContact(
    val contactId: Long,
    val displayName: String,
    val phones: List<DevicePhone>
)

data class DevicePhone(
    val raw: String,
    val normalized: String = raw.replace(Regex("[^0-9]"), "")
)
