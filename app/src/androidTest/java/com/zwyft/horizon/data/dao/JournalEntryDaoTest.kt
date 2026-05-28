package com.zwyft.horizon.data.dao

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.JournalEntryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class JournalEntryDaoTest {

    private lateinit var db: HorizonDatabase
    private lateinit var dao: JournalEntryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HorizonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.journalEntryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_and_getById() = runTest {
        val entry = JournalEntryEntity(
            title = "Test Entry",
            body = "This is a test journal entry.",
            dateStart = Date(System.currentTimeMillis() - 86400000),
            dateEnd = Date()
        )
        val id = dao.insert(entry)
        assert(id > 0)

        val retrieved = dao.getById(id)
        assert(retrieved != null)
        assert(retrieved?.title == "Test Entry")
        assert(retrieved?.body == "This is a test journal entry.")
    }

    @Test
    fun getInRange_filters_by_date() = runTest {
        val now = Date()
        val yesterday = Date(now.time - 86400000)
        val lastWeek = Date(now.time - 604800000)

        dao.insert(JournalEntryEntity(title = "Old", body = "old", dateStart = lastWeek, dateEnd = yesterday))
        dao.insert(JournalEntryEntity(title = "Recent", body = "recent", dateStart = yesterday, dateEnd = now))

        val results = dao.getInRange(yesterday, now)
        assert(results.size == 1)
        assert(results[0].title == "Recent")
    }

    @Test
    fun setBookmarked_toggles_flag() = runTest {
        val id = dao.insert(JournalEntryEntity(title = "Test", body = "test", dateStart = Date(), dateEnd = Date()))
        dao.setBookmarked(id, true)

        val updated = dao.getById(id)
        assert(updated?.bookmarked == true)

        dao.setBookmarked(id, false)
        val updated2 = dao.getById(id)
        assert(updated2?.bookmarked == false)
    }

    @Test
    fun getBookmarked_returns_only_bookmarked() = runTest {
        dao.insert(JournalEntryEntity(title = "B1", body = "b1", dateStart = Date(), dateEnd = Date(), bookmarked = true))
        dao.insert(JournalEntryEntity(title = "B2", body = "b2", dateStart = Date(), dateEnd = Date(), bookmarked = false))

        val bookmarked = dao.getBookmarked()
        assert(bookmarked.size == 1)
        assert(bookmarked[0].title == "B1")
    }
}
