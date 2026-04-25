package com.coparenting.chronicle.horizon.data.remote.sms

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.coparenting.chronicle.horizon.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset

class SmsDataSource(private val context: Context) {
    
    data class SmsMessage(
        val id: Long,
        val address: String,
        val body: String,
        val timestamp: LocalDateTime,
        val type: MessageType,
        val contactName: String?,
        val threadId: String,
        val messageType: MessageType?,
        val isRead: Boolean,
        val folder: String,
        val attachmentCount: Int = 0
    )
    
    data class SmsContact(
        val name: String?,
        val phoneNumber: String
    )
    
    suspend fun getSmsMessages(): List<SmsMessage> {
        return withContext(Dispatchers.IO) {
            val smsList = mutableListOf<SmsMessage>()
            
            val uri = Uri.parse("content://sms")
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.READ,
                Telephony.Sms.SEEN
            )
            
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val smsMessage = cursorToSmsMessage(cursor)
                    smsList.add(smsMessage)
                }
            }
            
            smsList
        }
    }
    
    suspend fun getMmsMessages(): List<SmsMessage> {
        return withContext(Dispatchers.IO) {
            val mmsList = mutableListOf<SmsMessage>()
            
            val uri = Uri.parse("content://mms")
            val projection = arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.DATE,
                Telephony.Mms.READ,
                Telephony.Mms.THREAD_ID
            )
            
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Mms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mmsMessage = cursorToMmsMessage(cursor)
                    if (mmsMessage != null) {
                        mmsList.add(mmsMessage)
                    }
                }
            }
            
            mmsList
        }
    }
    
    suspend fun getSmsContacts(): List<SmsContact> {
        return withContext(Dispatchers.IO) {
            val contacts = mutableSetOf<SmsContact>()
            
            val uri = Uri.parse("content://sms")
            val projection = arrayOf(
                Telephony.Sms.ADDRESS
            )
            
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    if (phoneNumber.isNotEmpty()) {
                        contacts.add(SmsContact(null, phoneNumber))
                    }
                }
            }
            
            contacts.toList()
        }
    }
    
    private fun cursorToSmsMessage(cursor: Cursor): SmsMessage {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
        val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
        val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
        val timestamp = LocalDateTime.ofEpochSecond(
            cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)) / 1000,
            0
        )
        val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
        val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
        val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
        
        val smsType = when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageType.INCOMING
            Telephony.Sms.MESSAGE_TYPE_SENT -> MessageType.OUTGOING
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageType.DRAFT
            else -> MessageType.UNKNOWN
        }
        
        val contactName = getContactName(address)
        
        return SmsMessage(
            id = id,
            address = address,
            body = body,
            timestamp = timestamp,
            type = smsType,
            contactName = contactName,
            threadId = threadId,
            messageType = MessageType.TEXT,
            isRead = isRead,
            folder = "inbox"
        )
    }
    
    private fun cursorToMmsMessage(cursor: Cursor): SmsMessage? {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
        val timestamp = LocalDateTime.ofEpochSecond(
            cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)) / 1000,
            0
        )
        val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID))
        val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1
        
        val address = getMmsAddress(id)
        val body = getMmsBody(id)
        
        return if (address != null && body != null) {
            val contactName = getContactName(address)
            SmsMessage(
                id = id,
                address = address,
                body = body,
                timestamp = timestamp,
                type = MessageType.INCOMING,
                contactName = contactName,
                threadId = threadId,
                messageType = MessageType.MMS,
                isRead = isRead,
                folder = "inbox"
            )
        } else {
            null
        }
    }
    
    private fun getContactName(phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(Telephony.Cont.CONTENT_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(Telephony.Cont.DISPLAY_NAME)
        
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Cont.DISPLAY_NAME))
            }
        }
        return null
    }
    
    private fun getMmsAddress(mmsId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val projection = arrayOf(Telephony.Mms.Addr.ADDRESS)
        var address: String? = null
        
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS))
            }
        }
        
        return address
    }
    
    private fun getMmsBody(mmsId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsId/part")
        val projection = arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT)
        var body: String? = null
        
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val contentType = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE))
                if (contentType.startsWith("text/")) {
                    body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT))
                    break
                }
            }
        }
        
        return body
    }
}
