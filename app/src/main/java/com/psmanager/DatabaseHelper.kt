package com.psmanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.TimeUnit

data class Day(val id:Long, val date:String, val createdAt:Long, val isClosed:Boolean, val total:Double)

data class Order(
    val id: Long = 0, val dayId: Long,
    val deviceNumber: String,
    val sessionType: String,   // "fixed" or "open"
    val hours: Int,
    val price: Double, val paid: Double, val changeAmount: Double,
    val startTime: Long,
    val endTime: Long,         // 0 for open
    val isDone: Boolean = false
)

class DatabaseHelper(ctx: Context) : SQLiteOpenHelper(ctx, "psm.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE days (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "date TEXT NOT NULL, created_at INTEGER NOT NULL," +
            "is_closed INTEGER NOT NULL DEFAULT 0, total REAL NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE orders (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "day_id INTEGER NOT NULL, device_number TEXT NOT NULL," +
            "session_type TEXT NOT NULL DEFAULT 'fixed'," +
            "hours INTEGER NOT NULL DEFAULT 0, price REAL NOT NULL DEFAULT 0," +
            "paid REAL NOT NULL DEFAULT 0, change_amount REAL NOT NULL DEFAULT 0," +
            "start_time INTEGER NOT NULL, end_time INTEGER NOT NULL DEFAULT 0," +
            "is_done INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            try { db.execSQL("ALTER TABLE orders ADD COLUMN session_type TEXT NOT NULL DEFAULT 'fixed'") } catch (e: Exception) {}
        }
    }

    fun getSetting(key: String): String? =
        readableDatabase.query("settings", arrayOf("value"), "key=?", arrayOf(key), null, null, null)
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun setSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict("settings", null,
            ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun createDay(date: String): Long =
        writableDatabase.insert("days", null, ContentValues().apply {
            put("date", date); put("created_at", System.currentTimeMillis())
        })

    private fun cursorToDay(c: android.database.Cursor) = Day(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        date = c.getString(c.getColumnIndexOrThrow("date")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        isClosed = c.getInt(c.getColumnIndexOrThrow("is_closed")) == 1,
        total = c.getDouble(c.getColumnIndexOrThrow("total"))
    )

    fun getActiveDay(): Day? =
        readableDatabase.query("days", null, "is_closed=0", null, null, null, "created_at DESC", "1")
            .use { c -> if (c.moveToFirst()) cursorToDay(c) else null }

    fun getClosedDays(): List<Day> {
        val list = mutableListOf<Day>()
        readableDatabase.query("days", null, "is_closed=1", null, null, null, "created_at DESC")
            .use { c -> while (c.moveToNext()) list.add(cursorToDay(c)) }
        return list
    }

    fun closeDay(dayId: Long, total: Double) {
        writableDatabase.update("days",
            ContentValues().apply { put("is_closed", 1); put("total", total) },
            "id=?", arrayOf(dayId.toString()))
    }

    fun createOrder(order: Order): Long =
        writableDatabase.insert("orders", null, ContentValues().apply {
            put("day_id", order.dayId); put("device_number", order.deviceNumber)
            put("session_type", order.sessionType); put("hours", order.hours)
            put("price", order.price); put("paid", order.paid)
            put("change_amount", order.changeAmount); put("start_time", order.startTime)
            put("end_time", order.endTime)
        })

    fun getOrdersByDay(dayId: Long): List<Order> {
        val list = mutableListOf<Order>()
        readableDatabase.query("orders", null, "day_id=?", arrayOf(dayId.toString()),
            null, null, "start_time ASC").use { c ->
            while (c.moveToNext()) list.add(Order(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                dayId = dayId,
                deviceNumber = c.getString(c.getColumnIndexOrThrow("device_number")),
                sessionType = c.getString(c.getColumnIndexOrThrow("session_type")),
                hours = c.getInt(c.getColumnIndexOrThrow("hours")),
                price = c.getDouble(c.getColumnIndexOrThrow("price")),
                paid = c.getDouble(c.getColumnIndexOrThrow("paid")),
                changeAmount = c.getDouble(c.getColumnIndexOrThrow("change_amount")),
                startTime = c.getLong(c.getColumnIndexOrThrow("start_time")),
                endTime = c.getLong(c.getColumnIndexOrThrow("end_time")),
                isDone = c.getInt(c.getColumnIndexOrThrow("is_done")) == 1
            ))
        }
        return list
    }

    fun markOrderPaid(orderId: Long, finalPrice: Double, paid: Double, change: Double) {
        writableDatabase.update("orders", ContentValues().apply {
            put("is_done", 1); put("price", finalPrice)
            put("paid", paid); put("change_amount", change)
        }, "id=?", arrayOf(orderId.toString()))
    }

    fun addHoursToOrder(orderId: Long, extraHours: Int, pricePerHour: Double) {
        readableDatabase.query("orders", arrayOf("end_time","price","hours"),
            "id=?", arrayOf(orderId.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) {
                val oldEnd  = c.getLong(0); val oldPrice = c.getDouble(1); val oldHrs = c.getInt(2)
                val base    = if (oldEnd > System.currentTimeMillis()) oldEnd else System.currentTimeMillis()
                writableDatabase.update("orders", ContentValues().apply {
                    put("end_time", base + extraHours * 3_600_000L)
                    put("price", oldPrice + extraHours * pricePerHour)
                    put("hours", oldHrs + extraHours)
                    put("is_done", 0)
                }, "id=?", arrayOf(orderId.toString()))
            }
        }
    }

    fun getDayTotal(dayId: Long): Double =
        readableDatabase.rawQuery("SELECT SUM(price) FROM orders WHERE day_id=?",
            arrayOf(dayId.toString())).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

    fun getDayOrderCount(dayId: Long): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM orders WHERE day_id=?",
            arrayOf(dayId.toString())).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    companion object {
        fun calcOpenPrice(startTime: Long, pricePerHour: Double, testMode: Boolean): Double {
            val elapsed = System.currentTimeMillis() - startTime
            val halfMs  = if (testMode) 5_000L else 1_800_000L
            val units   = elapsed / halfMs
            return units * (pricePerHour / 2.0)
        }
        fun formatElapsed(startTime: Long): String {
            val ms = System.currentTimeMillis() - startTime
            return "%02d:%02d:%02d".format(
                TimeUnit.MILLISECONDS.toHours(ms),
                TimeUnit.MILLISECONDS.toMinutes(ms) % 60,
                TimeUnit.MILLISECONDS.toSeconds(ms) % 60)
        }
        fun formatCountdown(endTime: Long): String {
            val rem = endTime - System.currentTimeMillis()
            if (rem <= 0) return "انتهى"
            return "%02d:%02d:%02d".format(
                TimeUnit.MILLISECONDS.toHours(rem),
                TimeUnit.MILLISECONDS.toMinutes(rem) % 60,
                TimeUnit.MILLISECONDS.toSeconds(rem) % 60)
        }
    }
}
