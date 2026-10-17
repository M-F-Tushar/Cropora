package com.cropora

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanDaoInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertListDetailAndDeletePreserveAllFields() = runBlocking {
        val olderId = database.scanDao().insertScan(record("Older", 1000L, null, null))
        val newerId = database.scanDao().insertScan(record("Newer", 2000L, 23.7, 90.4))

        val scans = database.scanDao().observeAllScans().first()
        assertEquals(listOf(newerId, olderId), scans.map { it.id })

        val detail = database.scanDao().getScanById(newerId)!!
        assertEquals("Model___Newer", detail.modelLabel)
        assertEquals("Newer", detail.disease)
        assertEquals(0.75f, detail.confidence, 0.0001f)
        assertEquals(23.7, detail.latitude!!, 0.0001)
        assertEquals(90.4, detail.longitude!!, 0.0001)

        assertEquals(1, database.scanDao().deleteScanById(newerId))
        assertNull(database.scanDao().getScanById(newerId))
    }

    private fun record(
        disease: String,
        timestamp: Long,
        latitude: Double?,
        longitude: Double?
    ) = ScanRecord(
        resultId = "result-$disease",
        modelLabel = "Model___$disease",
        disease = disease,
        confidence = 0.75f,
        uncertain = false,
        guidanceAvailable = true,
        symptoms = "Symptoms",
        treatment = "Treatment",
        prevention = "Prevention",
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp
    )
}
