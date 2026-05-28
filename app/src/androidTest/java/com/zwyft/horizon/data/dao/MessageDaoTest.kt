package com.zwyft.horizon.data.dao

import android.content.Context
 import androidx.arch.core.executor.ArchTaskExecutor
 import androidx.arch.core.executor.TaskExecutor
 import androidx.room.Room
 import androidx.test.core.app.ApplicationProvider
 import androidx.test.ext.junit.runners.AndroidJUnit4
 import com.zwyft.horizon.data.HorizonDatabase
 import com.zwyft.horizon.data.entity.MessageEntity
 import kotlinx.coroutines.ExperimentalCoroutinesApi
 import kotlinx.coroutines.test.runTest
 import org.junit.After
 import org.junit.Before
 import org.junit.Test
 import org.junit.runner.RunWith
 import java.util.Date

 @OptIn(ExperimentalCoroutinesApi::class)
 @RunWith(AndroidJUnit4::class)
 class MessageDaoTest {

     private lateinit var db: HorizonDatabase
     private lateinit var dao: MessageDao

     @Before
     fun setup() {
         val context = ApplicationProvider.getApplicationContext<Context>()
         db = Room.inMemoryDatabaseBuilder(context, HorizonDatabase::class.java)
             .allowMainThreadQueries()
             .build()
         dao = db.messageDao()
     }

     @After
     fun teardown() {
         db.close()
     }

     @Test
     fun insert_and_getById() = runTest {
         val msg = MessageEntity(
             messageId = 12345L,
             threadId = 1L,
             address = "+15551234567",
             contactName = "Mom",
             body = "Hello from Mom",
             date = Date(),
             type = 1
         )

         val id = dao.insert(msg)
         assert(id > 0)

         val retrieved = dao.getById(id)
         assert(retrieved != null)
         assert(retrieved?.address == "+15551234567")
         assert(retrieved?.body == "Hello from Mom")
     }

     @Test
     fun getMonitoredInRange returns_only_monitored() = runTest {
         val now = Date()
         val yesterday = Date(now.time - 86_400_000L)

         dao.insert(MessageEntity(messageId = 1, threadId = 1, address = "a", body = "monitored", date = now, type = 1, monitored = true))
         dao.insert(MessageEntity(messageId = 2, threadId = 1, address = "b", body = "not monitored", date = now, type = 1, monitored = false))
         dao.insert(MessageEntity(messageId = 3, threadId = 1, address = "c", body = "old monitored", date = yesterday, type = 1, monitored = true))

         val results = dao.getMonitoredInRange(yesterday, now)
         assert(results.size == 2)
         assert(results.all { it.monitored })
     }

     @Test
     fun searchBody finds_text() = runTest {
         dao.insert(MessageEntity(messageId = 10, threadId = 1, address = "a", body = "pickup on Friday", date = Date(), type = 1, monitored = true))
         dao.insert(MessageEntity(messageId = 11, threadId = 1, address = "b", body = "no match here", date = Date(), type = 1, monitored = true))

         val results = dao.searchBody("Friday", limit = 100)
         assert(results.size == 1)
         assert(results[0].body?.contains("Friday") == true)
     }

     @Test
     fun markJournalProcessed works() = runTest {
         val msg = MessageEntity(messageId = 20, threadId = 1, address = "a", body = "test", date = Date(), type = 1, monitored = true, journalProcessed = false)
         val id = dao.insert(msg)
         val inserted = dao.getById(id)!!

         dao.markJournalProcessed(listOf(id))
         val updated = dao.getById(id)!!
         assert(updated.journalProcessed == true)
     }
 }
