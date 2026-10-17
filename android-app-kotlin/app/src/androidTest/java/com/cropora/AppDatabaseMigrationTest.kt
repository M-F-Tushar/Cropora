package com.cropora

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cropora.database.AppDatabase
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrationOneToTwoPreservesRowAndAddsNullableLocation() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            insert(
                "scan_history",
                0,
                ContentValues().apply {
                    put("result_id", "result-migration")
                    put("model_label", "Tomato___Early_blight")
                    put("disease", "Tomato Early Blight")
                    put("confidence", 0.87f)
                    put("uncertain", 0)
                    put("guidance_available", 1)
                    put("symptoms", "Symptoms")
                    put("treatment", "Treatment")
                    put("prevention", "Prevention")
                    put("timestamp", 1000L)
                }
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )
        migrated.query(
            "SELECT disease, latitude, longitude FROM scan_history"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Tomato Early Blight", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        migrated.close()
    }

    companion object {
        private const val TEST_DATABASE = "week11-migration-test"
    }
}
