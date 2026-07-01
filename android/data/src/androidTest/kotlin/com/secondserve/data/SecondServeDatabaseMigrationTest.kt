package com.secondserve.data

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondserve.data.local.db.SecondServeDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SecondServeDatabaseMigrationTest {

    @get:Rule(order = 0)
    val testName: TestName = TestName()

    @get:Rule(order = 1)
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SecondServeDatabase::class.java
    )

    private val TEST_DB get() = "migration-test-${testName.methodName}"

    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        helper.createDatabase(TEST_DB, 5).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            SecondServeDatabase.MIGRATION_5_6
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7() {
        helper.createDatabase(TEST_DB, 6).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 7, true,
            SecondServeDatabase.MIGRATION_6_7
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8() {
        helper.createDatabase(TEST_DB, 7).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 8, true,
            SecondServeDatabase.MIGRATION_7_8
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9() {
        helper.createDatabase(TEST_DB, 8).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 9, true,
            SecondServeDatabase.MIGRATION_8_9
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10() {
        helper.createDatabase(TEST_DB, 9).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 10, true,
            SecondServeDatabase.MIGRATION_9_10
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11() {
        helper.createDatabase(TEST_DB, 10).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 11, true,
            SecondServeDatabase.MIGRATION_10_11
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12() {
        helper.createDatabase(TEST_DB, 11).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 12, true,
            SecondServeDatabase.MIGRATION_11_12
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        helper.createDatabase(TEST_DB, 5).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 12, true,
            SecondServeDatabase.MIGRATION_5_6,
            SecondServeDatabase.MIGRATION_6_7,
            SecondServeDatabase.MIGRATION_7_8,
            SecondServeDatabase.MIGRATION_8_9,
            SecondServeDatabase.MIGRATION_9_10,
            SecondServeDatabase.MIGRATION_10_11,
            SecondServeDatabase.MIGRATION_11_12
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7PreservesSessionRows() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("""INSERT INTO sessions (id, surface, match_format, third_set_rule, status, session_type, created_at, updated_at)
                       VALUES (1, 'CLAY', 'BEST_OF_3', 'FULL_ADVANTAGE', 'COMPLETED', 'MATCH', 1000, 2000)""")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, SecondServeDatabase.MIGRATION_6_7)
        (db.query("SELECT id, score_text FROM sessions WHERE id = 1", emptyArray<Any?>()) as Cursor).use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(it.getColumnIndexOrThrow("id")))
            assertNull(it.getString(it.getColumnIndexOrThrow("score_text")))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11PreservesSessionRows() {
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL("""INSERT INTO sessions (id, surface, match_format, third_set_rule, status, session_type, created_at, updated_at)
                       VALUES (1, 'HARD', 'BEST_OF_1', 'MATCH_TIE_BREAK', 'IN_PROGRESS', 'MATCH', 3000, 4000)""")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true, SecondServeDatabase.MIGRATION_10_11)
        (db.query("SELECT id, scheduled_at FROM sessions WHERE id = 1", emptyArray<Any?>()) as Cursor).use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(it.getColumnIndexOrThrow("id")))
            assertTrue(it.isNull(it.getColumnIndexOrThrow("scheduled_at")))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12PreservesRowsAndAddsIdentityAndStatsColumns() {
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL("""INSERT INTO sessions (id, surface, match_format, third_set_rule, status, session_type, created_at, updated_at)
                       VALUES (1, 'CLAY', 'BEST_OF_3', 'FULL_ADVANTAGE', 'COMPLETED', 'MATCH', 5000, 6000)""")
            execSQL("""INSERT INTO player_profiles (id, current_series, current_points, updated_at)
                       VALUES (1, '30/2', 100, 7000)""")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, SecondServeDatabase.MIGRATION_11_12)
        (db.query(
            "SELECT id, first_serve_percent_self, winners_opponent FROM sessions WHERE id = 1",
            emptyArray<Any?>()
        ) as Cursor).use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(it.getColumnIndexOrThrow("id")))
            assertTrue(it.isNull(it.getColumnIndexOrThrow("first_serve_percent_self")))
            assertTrue(it.isNull(it.getColumnIndexOrThrow("winners_opponent")))
        }
        (db.query(
            "SELECT id, display_name, club FROM player_profiles WHERE id = 1",
            emptyArray<Any?>()
        ) as Cursor).use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(it.getColumnIndexOrThrow("id")))
            assertTrue(it.isNull(it.getColumnIndexOrThrow("display_name")))
            assertTrue(it.isNull(it.getColumnIndexOrThrow("club")))
        }
    }
}
