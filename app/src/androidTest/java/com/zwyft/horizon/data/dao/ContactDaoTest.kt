package com.zwyft.horizon.data.dao

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.ContactEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ContactDaoTest {

    private lateinit var db: HorizonDatabase
    private lateinit var dao: ContactDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HorizonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.contactDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_and_getAll() = runTest {
        val contact = ContactEntity(
            name = "Mom",
            phoneNumber = "+155****4567",
            normalizedPhoneNumber = "155****4567",
            relationship = "mom",
            monitored = true
        )
        val id = dao.insert(contact)
        assert(id > 0)

        val all = dao.getAll()
        assert(all.size == 1)
        assert(all[0].name == "Mom")
    }

    @Test
    fun `getMonitored_returns_only_monitored`() = runTest {
        dao.insert(ContactEntity(name = "Mom", phoneNumber = "1", normalizedPhoneNumber = "1", monitored = true))
        dao.insert(ContactEntity(name = "Dad", phoneNumber = "2", normalizedPhoneNumber = "2", monitored = false))

        val monitored = dao.getMonitored()
        assert(monitored.size == 1)
        assert(monitored[0].name == "Mom")
    }

    @Test
    fun `setMonitored_toggles_flag`() = runTest {
        val id = dao.insert(ContactEntity(name = "Test", phoneNumber = "1", normalizedPhoneNumber = "1", monitored = true))
        dao.setMonitored(id, false)

        val result = dao.getById(id)
        assert(result?.monitored == false)
    }

    @Test
    fun `findByNormalized_finds_contact`() = runTest {
        dao.insert(ContactEntity(name = "Mom", phoneNumber = "+155****4567", normalizedPhoneNumber = "155****4567", monitored = true))

        val found = dao.findByNormalized("155****4567")
        assert(found != null)
        assert(found?.name == "Mom")
    }
}
