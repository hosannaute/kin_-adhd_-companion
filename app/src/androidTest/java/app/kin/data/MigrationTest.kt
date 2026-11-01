package app.kin.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        KinDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    @Test fun versionOneUpgradePreservesTasksGoalsXpChatAndFocus() {
        val name = "kin-migration-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO goals VALUES (7, 'Learn', 'Curiosity', 1, 1)")
            execSQL("INSERT INTO tasks VALUES (42, 'Read', 'One page', 'Study', 123456789, 10, 2, 1, 7, 1, 1, 3)")
            execSQL("INSERT INTO companion VALUES (1, 75)")
            execSQL("INSERT INTO messages VALUES (9, 'You can start small', 0, 123456789)")
            execSQL("INSERT INTO focus_sessions VALUES (8, 42, 'Read', 60000, 30000, 1000, 1, 0, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, KinDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT title, description, goalId, completed, rewarded, snoozeCount, repeatRule, skipped, nextOccurrenceId FROM tasks WHERE id=42").use {
                assertTrue(it.moveToFirst()); assertEquals("Read", it.getString(0)); assertEquals("One page", it.getString(1))
                assertEquals(7, it.getInt(2)); assertEquals(1, it.getInt(3)); assertEquals(1, it.getInt(4)); assertEquals(3, it.getInt(5))
                assertEquals("NONE", it.getString(6)); assertEquals(0, it.getInt(7)); assertTrue(it.isNull(8))
            }
            db.query("SELECT xp FROM companion").use { assertTrue(it.moveToFirst()); assertEquals(75, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM goals UNION ALL SELECT COUNT(*) FROM messages UNION ALL SELECT COUNT(*) FROM focus_sessions").use {
                while (it.moveToNext()) assertEquals(1, it.getInt(0))
            }
            db.execSQL("INSERT INTO subtasks VALUES (1, 42, 'Small step', 0, 0)")
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM tasks WHERE id=42")
            db.query("SELECT COUNT(*) FROM subtasks").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }
}
