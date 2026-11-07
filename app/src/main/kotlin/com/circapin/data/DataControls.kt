package com.circapin.data

import android.content.Context
import android.database.Cursor
import androidx.room.withTransaction
import com.circapin.CircaPinApp
import com.circapin.DataGuard
import com.circapin.data.local.CircaPinDatabase
import com.circapin.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class DataControls @Inject constructor(
    private val database: CircaPinDatabase,
    private val reminders: ReminderScheduler,
    @ApplicationContext private val context: Context,
) : UserDataActions {
    override suspend fun export(csv: Boolean): File = withContext(Dispatchers.IO) {
        val tables = database.withTransaction {
            TABLES.associateWith { table ->
                database.openHelper.readableDatabase.query("SELECT * FROM $table").use { cursor ->
                    val rows = JSONArray()
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        cursor.columnNames.forEachIndexed { i, name ->
                            row.put(name, when (cursor.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                                else -> cursor.getString(i)
                            })
                        }
                        rows.put(row)
                    }
                    cursor.columnNames.toList() to rows
                }
            }
        }
        val prefs = context.getSharedPreferences("circapin_prefs", Context.MODE_PRIVATE).all
        val manifest = JSONObject().put("exportVersion", 1).put("databaseVersion", 2)
            .put("exportedAt", kotlin.time.Clock.System.now().toString()).put("preferences", JSONObject(prefs))
            .put("csvNullValue", "\\N")
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        // Exports are private until the user chooses a destination in Android's share sheet.
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, if (csv) "circapin-data.zip" else "circapin-data.json")
        if (csv) {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString(2).toByteArray()); zip.closeEntry()
                tables.forEach { (name, data) ->
                    val (columns, rows) = data
                    zip.putNextEntry(ZipEntry("$name.csv"))
                    zip.write((columns.joinToString(",", transform = ::csvCell) + "\r\n").toByteArray())
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        zip.write((columns.joinToString(",") { key ->
                            if (row.isNull(key)) "\\N" else csvCell(row.get(key).toString())
                        } + "\r\n").toByteArray())
                    }
                    zip.closeEntry()
                }
            }
        } else {
            tables.forEach { (name, data) -> manifest.put(name, data.second) }
            file.writeText(manifest.toString(2))
        }
        file
    }

    @android.annotation.SuppressLint("ApplySharedPref") // Reset must be flushed before returning to onboarding; runs on IO.
    override suspend fun deleteAll() {
        val app = context.applicationContext as CircaPinApp
        app.stopUpdates()
        try {
            app.sleepSync.exclusive {
                DataGuard.mutex.withLock {
                    withContext(Dispatchers.IO) {
                        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
                        reminders.cancelAll()
                        database.clearAllTables()
                        context.getSharedPreferences("circapin_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                        context.getSharedPreferences("screen_nudge", Context.MODE_PRIVATE).edit().clear().commit()
                        File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
                    }
                }
            }
        } finally { app.startUpdates() }
    }

    companion object {
        val TABLES = listOf("user_profile", "sleep_record", "deleted_sync_record", "model_state", "model_snapshot",
            "schedule_goal", "daily_plan", "energy_forecast", "check_in", "screen_time_snapshot", "sleep_score", "eating_window")

        fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    }
}
