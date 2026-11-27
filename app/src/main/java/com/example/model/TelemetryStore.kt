package com.example.model

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class SavedTelemetry(
    val recent: List<VehicleTelemetry> = emptyList(),
    val recovered: List<VehicleTelemetry> = emptyList()
)

class TelemetryStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "telemetria.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE readings (
               entry INTEGER PRIMARY KEY AUTOINCREMENT,
               id TEXT NOT NULL UNIQUE,
               received_at INTEGER NOT NULL,
               archived INTEGER NOT NULL,
               transport TEXT NOT NULL,
               json TEXT NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(host: String, telemetry: VehicleTelemetry): Boolean {
        val readingId = telemetry.readingId ?: UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", "$host/$readingId")
            put("received_at", telemetry.timestampEpochMs)
            put("archived", if (telemetry.archived) 1 else 0)
            put("transport", telemetry.sourceTransport)
            put("json", telemetry.rawJson)
        }
        return writableDatabase.insertWithOnConflict(
            "readings", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun history(limit: Int): SavedTelemetry = SavedTelemetry(
        recent = recent(limit, archivedOnly = false),
        recovered = recent(limit, archivedOnly = true)
    )

    private fun recent(limit: Int, archivedOnly: Boolean): List<VehicleTelemetry> {
        return readableDatabase.query(
            "readings", arrayOf("entry", "received_at", "transport", "json"),
            if (archivedOnly) "archived = 1" else null, null, null, null,
            "entry DESC", limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    VehicleTelemetryParser.parseJson(
                        cursor.getString(3), cursor.getString(2), cursor.getLong(0), cursor.getLong(1)
                    ).getOrNull()?.let { add(it) }
                }
            }
        }
    }
}
