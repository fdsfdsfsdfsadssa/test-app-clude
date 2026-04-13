package com.psmanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Day(
    val id: Long = 0,
    val date: String,
    val createdAt: Long,
    var isClosed: Boolean = false,
    var total: Double = 0.0
)

data class Order(
    val id: Long = 0,
    val dayId: Long,
    val deviceNumber: String,
    val hours: Int,
    val price: Double,
    val paid: Double,
    val changeAmount: Double,
    val startTime: Long,
    val endTime: Long,
    var isDone: Boolean = false
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "psmanager.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE days (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "date TEXT NOT NULL, " +
            "created_at INTEGER NOT NULL, " +
            "is_closed INTEGER NOT NULL DEFAULT 0, " +
            "total REAL NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE orders (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "day_id INTEGER NOT NULL, " +
            "device_number TEXT NOT NULL, " +
            "hours INTEGER NOT NULL, " +
            "price REAL NOT NULL, " +
            "paid REAL NOT NULL, " +
            "change_amount REAL NOT NULL, " +
            "start_time INTEGER NOT NULL, " +
            "end_time INTEGER NOT NULL, " +
            "is_done INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL("DROP TABLE IF EXISTS orders")
        db.execSQL("DROP TABLE IF EXISTS days")
        db.execSQL("DROP TABLE IF EXISTS settings")
        onCreate(db)
    }

    fun getSetting(key: String): String? {
        val c = readableDatabase.query("settings", arrayOf("value"), "key=?", arrayOf(key), null, null, null)
        return if (c.moveToFirst()) c.getString(0).also { c.close() } else { c.close(); null }
    }

    fun setSetting(key: String, value: String) {
        val cv = ContentValues().apply { put("key", key); put("value", value) }
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun createDay(date: String): Long {
        val cv = ContentValues().apply {
            put("date", date)
            put("created_at", System.currentTimeMillis())
            put("is_closed", 0)
            put("total", 0.0)
        }
        return writableDatabase.insert("days", null, cv)
    }

    fun getActiveDay(): Day? {
        val c = readableDatabase.query("days", null, "is_closed=0", null, null, null, "created_at DESC", "1")
        return if (c.moveToFirst()) Day(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            date = c.getString(c.getColumnIndexOrThrow("date")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            isClosed = false
        ).also { c.close() } else { c.close(); null }
    }

    fun closeDay(dayId: Long, total: Double) {
        val cv = ContentValues().apply { put("is_closed", 1); put("total", total) }
        writableDatabase.update("days", cv, "id=?", arrayOf(dayId.toString()))
    }

    fun createOrder(order: Order): Long {
        val cv = ContentValues().apply {
            put("day_id", order.dayId)
            put("device_number", order.deviceNumber)
            put("hours", order.hours)
            put("price", order.price)
            put("paid", order.paid)
            put("change_amount", order.changeAmount)
            put("start_time", order.startTime)
            put("end_time", order.endTime)
            put("is_done", 0)
        }
        return writableDatabase.insert("orders", null, cv)
    }

    fun getOrdersByDay(dayId: Long): List<Order> {
        val c = readableDatabase.query("orders", null, "day_id=?", arrayOf(dayId.toString()), null, null, "start_time ASC")
        val list = mutableListOf<Order>()
        while (c.moveToNext()) {
            list.add(Order(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                dayId = c.getLong(c.getColumnIndexOrThrow("day_id")),
                deviceNumber = c.getString(c.getColumnIndexOrThrow("device_number")),
                hours = c.getInt(c.getColumnIndexOrThrow("hours")),
                price = c.getDouble(c.getColumnIndexOrThrow("price")),
                paid = c.getDouble(c.getColumnIndexOrThrow("paid")),
                changeAmount = c.getDouble(c.getColumnIndexOrThrow("change_amount")),
                startTime = c.getLong(c.getColumnIndexOrThrow("start_time")),
                endTime = c.getLong(c.getColumnIndexOrThrow("end_time")),
                isDone = c.getInt(c.getColumnIndexOrThrow("is_done")) == 1
            ))
        }
        c.close()
        return list
    }

    fun markOrderDone(orderId: Long) {
        val cv = ContentValues().apply { put("is_done", 1) }
        writableDatabase.update("orders", cv, "id=?", arrayOf(orderId.toString()))
    }

    fun getDayTotal(dayId: Long): Double {
        val c = readableDatabase.rawQuery(
            "SELECT SUM(price) FROM orders WHERE day_id=?", arrayOf(dayId.toString())
        )
        return if (c.moveToFirst()) c.getDouble(0).also { c.close() } else { c.close(); 0.0 }
    }

    fun getDayOrderCount(dayId: Long): Int {
        val c = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM orders WHERE day_id=?", arrayOf(dayId.toString())
        )
        return if (c.moveToFirst()) c.getInt(0).also { c.close() } else { c.close(); 0 }
    }
}
