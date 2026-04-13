package com.psmanager

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var currentDay: Day? = null
    private val orders = mutableListOf<Order>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: OrderAdapter
    private val alertedOrders = mutableSetOf<Long>()

    private val ticker = object : Runnable {
        override fun run() {
            adapter.notifyDataSetChanged()
            checkExpired()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        db = DatabaseHelper(this)

        if (db.getSetting("price_per_hour") == null) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupViews()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun setupViews() {
        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        findViewById<TextView>(R.id.tvDate).text = sdf.format(Date())

        adapter = OrderAdapter()
        val lv = findViewById<ListView>(R.id.lvOrders)
        lv.adapter = adapter

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnNewDay).setOnClickListener {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            db.createDay(d)
            loadData()
            Toast.makeText(this, "تم إنشاء يوم جديد ✅", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnAddOrder).setOnClickListener {
            val i = Intent(this, NewOrderActivity::class.java)
            i.putExtra("day_id", currentDay?.id ?: 0L)
            startActivityForResult(i, 1)
        }

        findViewById<Button>(R.id.btnEndDay).setOnClickListener {
            confirmEndDay()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) loadData()
    }

    private fun loadData() {
        currentDay = db.getActiveDay()
        orders.clear()
        currentDay?.let { orders.addAll(db.getOrdersByDay(it.id)) }

        val hasDay = currentDay != null
        val btnNew = findViewById<Button>(R.id.btnNewDay)
        val btnAdd = findViewById<Button>(R.id.btnAddOrder)
        val btnEnd = findViewById<Button>(R.id.btnEndDay)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val lvOrders = findViewById<ListView>(R.id.lvOrders)

        btnNew.visibility = if (hasDay) View.GONE else View.VISIBLE
        btnAdd.visibility = if (hasDay) View.VISIBLE else View.GONE
        btnEnd.visibility = if (hasDay) View.VISIBLE else View.GONE

        if (hasDay) {
            val active = orders.count { !it.isDone }
            tvStatus.text = "يوم نشط  •  $active جهاز شغال"
            tvStatus.setTextColor(Color.parseColor("#00CC66"))
        } else {
            tvStatus.text = "لا يوجد يوم نشط"
            tvStatus.setTextColor(Color.GRAY)
        }

        tvEmpty.visibility = if (orders.isEmpty() && hasDay) View.VISIBLE else View.GONE
        lvOrders.visibility = if (orders.isNotEmpty()) View.VISIBLE else View.GONE

        adapter.notifyDataSetChanged()
    }

    private fun confirmEndDay() {
        val day = currentDay ?: return
        val total = db.getDayTotal(day.id)
        val count = db.getDayOrderCount(day.id)
        val cur = db.getSetting("currency") ?: "ج.م"

        AlertDialog.Builder(this)
            .setTitle("إنهاء اليوم")
            .setMessage("عدد الطلبات: $count\nالإجمالي: ${fmtPrice(total, cur)}\n\nمتأكد إنك عايز تنهي اليوم؟")
            .setPositiveButton("نعم، إنهاء") { _, _ ->
                db.closeDay(day.id, total)
                showSummary(total, count, cur)
                loadData()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showSummary(total: Double, count: Int, cur: String) {
        AlertDialog.Builder(this)
            .setTitle("🎮 ملخص اليوم")
            .setMessage(
                "تم إنهاء اليوم بنجاح ✅\n\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "عدد الطلبات:  $count\n" +
                "إجمالي المبيعات:  ${fmtPrice(total, cur)}\n" +
                "━━━━━━━━━━━━━━━━"
            )
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun checkExpired() {
        val now = System.currentTimeMillis()
        orders.forEach { order ->
            if (!order.isDone && now >= order.endTime && !alertedOrders.contains(order.id)) {
                alertedOrders.add(order.id)
                order.isDone = true
                db.markOrderDone(order.id)
                runOnUiThread { showTimeUpAlert(order) }
            }
        }
    }

    private fun showTimeUpAlert(order: Order) {
        val cur = db.getSetting("currency") ?: "ج.م"
        AlertDialog.Builder(this)
            .setTitle("⏰ انتهى الوقت!")
            .setMessage(
                "جهاز رقم: ${order.deviceNumber}\n" +
                "المدة: ${order.hours} ساعة\n" +
                "السعر: ${fmtPrice(order.price, cur)}"
            )
            .setPositiveButton("حسناً", null)
            .setCancelable(false)
            .show()
    }

    private fun fmtPrice(price: Double, cur: String) = "${"%.0f".format(price)} $cur"

    inner class OrderAdapter : BaseAdapter() {
        override fun getCount() = orders.size
        override fun getItem(pos: Int): Any = orders[pos]
        override fun getItemId(pos: Int) = orders[pos].id

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order, parent, false)

            val o = orders[pos]
            val now = System.currentTimeMillis()
            val rem = o.endTime - now
            val cur = db.getSetting("currency") ?: "ج.م"

            v.findViewById<TextView>(R.id.tvDevice).text = o.deviceNumber
            v.findViewById<TextView>(R.id.tvHours).text = "${o.hours} ساعة"
            v.findViewById<TextView>(R.id.tvPrice).text = fmtPrice(o.price, cur)

            val tvRem = v.findViewById<TextView>(R.id.tvRemaining)

            if (o.isDone || rem <= 0) {
                tvRem.text = "انتهى"
                tvRem.setTextColor(Color.RED)
                v.setBackgroundColor(Color.parseColor("#1A0000"))
            } else {
                val h = TimeUnit.MILLISECONDS.toHours(rem)
                val m = TimeUnit.MILLISECONDS.toMinutes(rem) % 60
                val s = TimeUnit.MILLISECONDS.toSeconds(rem) % 60
                tvRem.text = "%02d:%02d:%02d".format(h, m, s)

                when {
                    rem < 5 * 60_000L  -> { v.setBackgroundColor(0xFF1A0000.toInt()); tvRem.setTextColor(Color.RED) }
                    rem < 15 * 60_000L -> { v.setBackgroundColor(0xFF1A1400.toInt()); tvRem.setTextColor(Color.YELLOW) }
                    else               -> { v.setBackgroundColor(0xFF001408.toInt()); tvRem.setTextColor(0xFF00FF66.toInt()) }
                }
            }
            return v
        }
    }
}
