package com.psmanager

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class DayHistoryActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_history)
        supportActionBar?.hide()
        db = DatabaseHelper(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val days = db.getClosedDays()
        val cur  = db.getSetting("currency") ?: "ج.م"
        val tvEmpty = findViewById<TextView>(R.id.tvNoDays)
        val lv      = findViewById<ListView>(R.id.lvDays)

        if (days.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            lv.visibility = View.GONE
            return
        }

        tvEmpty.visibility = View.GONE
        lv.visibility = View.VISIBLE

        val adapter = object : BaseAdapter() {
            override fun getCount() = days.size
            override fun getItem(pos: Int) = days[pos]
            override fun getItemId(pos: Int) = days[pos].id

            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val v = cv ?: LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
                val day = days[pos]

                val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
                val dateStr = try { sdf.format(SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).parse(day.date)!!) } catch(e:Exception) { day.date }

                v.findViewById<TextView>(R.id.tvDayDate).text = dateStr
                v.findViewById<TextView>(R.id.tvDayCount).text = "${db.getDayOrderCount(day.id)} طلب"
                v.findViewById<TextView>(R.id.tvDayTotal).text = "${"%.0f".format(day.total)} $cur"
                return v
            }
        }

        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            val day = days[pos]
            val orders = db.getOrdersByDay(day.id)
            val sb = StringBuilder()
            orders.forEach { o ->
                sb.append("جهاز ${o.deviceNumber}  •  ")
                if (o.sessionType == "open") sb.append("مفتوح  ")
                else sb.append("${o.hours}ساعة  ")
                sb.append("${"%.0f".format(o.price)} $cur\n")
            }
            AlertDialog.Builder(this)
                .setTitle("تفاصيل اليوم — إجمالي ${"%.0f".format(day.total)} $cur")
                .setMessage(if (orders.isEmpty()) "لا توجد طلبات" else sb.toString())
                .setPositiveButton("حسناً", null)
                .show()
        }
    }
}
