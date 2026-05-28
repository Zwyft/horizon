package com.zwyft.horizon.data.dao

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.SettingEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingDaoTest {

    private lateinit var db: HorizonDatabase
    private lateinit var dao: SettingDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HorizonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.settingDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_and_get() = runTest {
        val setting = SettingEntity(key = "test_key", value = "test_value", label = "Test")
        dao.upsert(setting)

        val retrieved = dao.get("test_key")
        assert(retrieved != null)
        assert(retrieved?.value == "test_value")
    }

    @Test
    fun setValue_and_getValue() = runTest {
        dao.setValue("api_key", "sk-12345")

        val value = dao.getValue("api_key")
        assert(value == "sk-12345")
    }

    @Test
    fun getAll_returns_all_settings() = runTest {
        dao.upsert(SettingEntity(key = "k1", value = "v1"))
        dao.upsert(SettingEntity(key = "k2", value = "v2"))

        val all = dao.getAll()
        assert(all.size == 2)
    }

    @Test
    fun delete_removes_setting() = runTest {
        dao.upsert(SettingEntity(key = "temp", value = "x"))
        dao.delete("temp")

        val result = dao.get("temp")
        assert(result == null)
    }

    @Test
    fun getBasic_returns_only_non_advanced() = runTest {
        dao.upsert(SettingEntity(key = "basic1", value = "a", advanced = false))
        dao.upsert(SettingEntity(key = "advanced1", value = "b", advanced = true))

        val basic = dao.getBasic()
        assert(basic.size == 1)
        assert(basic[0].key == "basic1")
    }
}
