package com.secondserve.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondserve.data.local.db.SecondServeDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SecondServeDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SecondServeDatabase::class.java
    )

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
    fun migrateAll() {
        helper.createDatabase(TEST_DB, 5).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 11, true,
            SecondServeDatabase.MIGRATION_5_6,
            SecondServeDatabase.MIGRATION_6_7,
            SecondServeDatabase.MIGRATION_7_8,
            SecondServeDatabase.MIGRATION_8_9,
            SecondServeDatabase.MIGRATION_9_10,
            SecondServeDatabase.MIGRATION_10_11
        )
    }
}
