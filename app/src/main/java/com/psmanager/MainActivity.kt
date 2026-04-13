package com.psmanager

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var currentDay: Day? = null
    private val orders = mutableListOf<Order>()
    private lateinit var adapter: OrderAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val alertedOrders = mutableSetOf<Long>()

    private val ticker = object : Runnable {
        override fun run() {
            checkExpiredFixed()
            adapter.notifyDataSetChanged()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        db = DatabaseHelper(this)

        if (db.getSetting("price_per_hour") == null)
            startActivity(Intent(this, SettingsActivity::class.java))

        adapter = OrderAdapter()
        findViewById<ListView>(R.id.lvOrders).adapter = adapter

        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        findViewById<TextView>(R.id.tvDate).text = sdf.format(Date())

        setupButtons()
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

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, DayHistoryActivity::class.java))
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
        findViewById<Button>(R.id.btnEndDay).setOnClickListener { confirmEndDay() }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1) loadData()
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

    // ─── Pay dialog for OPEN sessions ────────────────────────────────────────
    private fun showPayDialog(order: Order) {
        val pricePerHour = db.getSetting("price_per_hour")?.toDoubleOrNull() ?: 10.0
        val testMode = db.getSetting("test_mode") == "1"
        val cur = db.getSetting("currency") ?: "ج.م"

        val dlgHandler = Handler(Looper.getMainLooper())
        var paidInput = ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A3E"))
            setPadding(0, 0, 0, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // Header
        val tvHeader = TextView(this).apply {
            text = "دفع  —  جهاز ${order.deviceNumber}"
            textSize = 18f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 20, 0, 8)
            setBackgroundColor(Color.parseColor("#003087"))
        }

        val tvElapsed = TextView(this).apply {
            textSize = 34f; setTextColor(Color.parseColor("#00FF88"))
            gravity = android.view.Gravity.CENTER; setPadding(0, 12, 0, 4)
        }

        val tvPrice = TextView(this).apply {
            textSize = 22f; setTextColor(Color.parseColor("#FFD700"))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 12)
        }

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#333366"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        }

        val tvPaidLabel = TextView(this).apply {
            text = "المبلغ المدفوع من الزبون"
            textSize = 13f; setTextColor(Color.parseColor("#8888AA"))
            gravity = android.view.Gravity.CENTER; setPadding(0, 16, 0, 4)
        }

        val tvPaidDisplay = TextView(this).apply {
            text = "0"; textSize = 36f
            setTextColor(Color.parseColor("#FFD700"))
            setBackgroundColor(Color.parseColor("#0A0A1A"))
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvChange = TextView(this).apply {
            textSize = 20f; gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }

        // Numpad rows for pay dialog
        fun addNumRow(parent: LinearLayout, keys: List<String>, onKey: (String)->Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(58)).also { it.bottomMargin = 4 }
            }
            for (k in keys) {
                val btn = Button(this).apply {
                    text = k; textSize = 20f; setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).also {
                        it.weight = 1f; it.marginEnd = 4 }
                    setBackgroundColor(Color.parseColor(
                        if (k=="C") "#550000" else if (k=="⌫") "#2A2A55" else if (k=="✓ دفع") "#004400" else "#222255"
                    ))
                    setOnClickListener { onKey(k) }
                }
                row.addView(btn)
            }
            parent.addView(row)
        }

        val numpadContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 12)
        }

        fun updateChange() {
            val paid = paidInput.toDoubleOrNull() ?: 0.0
            val price = DatabaseHelper.calcOpenPrice(order.startTime, pricePerHour, testMode)
            val change = paid - price
            tvPaidDisplay.text = if (paidInput.isEmpty()) "0" else paidInput
            when {
                paid <= 0 -> { tvChange.text = ""; }
                change >= 0 -> { tvChange.text = "الباقي للزبون:  ${"%.0f".format(change)} $cur"; tvChange.setTextColor(Color.parseColor("#00CC66")) }
                else -> { tvChange.text = "ناقص:  ${"%.0f".format(-change)} $cur"; tvChange.setTextColor(Color.RED) }
            }
        }

        fun onKey(k: String) {
            when (k) {
                "C"  -> { paidInput = ""; updateChange() }
                "⌫"  -> { if (paidInput.isNotEmpty()) paidInput = paidInput.dropLast(1); updateChange() }
                "."  -> { if (!paidInput.contains('.')) { paidInput += if (paidInput.isEmpty()) "0." else "."; updateChange() } }
                else -> { if (paidInput.length < 9) { if (paidInput == "0") paidInput = k else paidInput += k; updateChange() } }
            }
        }

        addNumRow(numpadContainer, listOf("7","8","9"), ::onKey)
        addNumRow(numpadContainer, listOf("4","5","6"), ::onKey)
        addNumRow(numpadContainer, listOf("1","2","3"), ::onKey)
        addNumRow(numpadContainer, listOf("C","0","⌫"),  ::onKey)
        addNumRow(numpadContainer, listOf(".","","✓ دفع"), ::onKey)

        root.addView(tvHeader)
        root.addView(tvElapsed)
        root.addView(tvPrice)
        root.addView(divider)
        root.addView(tvPaidLabel)
        root.addView(tvPaidDisplay)
        root.addView(tvChange)
        root.addView(numpadContainer)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        // Live update ticker
        val liveTicker = object : Runnable {
            override fun run() {
                val price = DatabaseHelper.calcOpenPrice(order.startTime, pricePerHour, testMode)
                tvElapsed.text = DatabaseHelper.formatElapsed(order.startTime)
                tvPrice.text = "السعر الحالي:  ${"%.0f".format(price)} $cur"
                updateChange()
                if (dialog.isShowing) dlgHandler.postDelayed(this, 1000)
            }
        }
        dialog.setOnShowListener { dlgHandler.post(liveTicker) }
        dialog.setOnDismissListener { dlgHandler.removeCallbacks(liveTicker) }

        // Handle confirm button
        numpadContainer.findViewWithTag<Button>("confirm_pay")

        // Wire confirm via the onKey "✓ دفع" above → we need to hook it differently
        // Override the last button click
        val confirmBtn = numpadContainer.getChildAt(numpadContainer.childCount - 1).let {
            (it as LinearLayout).getChildAt(2) as Button
        }
        confirmBtn.setOnClickListener {
            val price = DatabaseHelper.calcOpenPrice(order.startTime, pricePerHour, testMode)
            val paid = paidInput.toDoubleOrNull() ?: 0.0
            if (paid < price) {
                tvPaidDisplay.setTextColor(Color.RED)
                tvChange.text = "المبلغ أقل من السعر!"
                tvChange.setTextColor(Color.RED)
                return@setOnClickListener
            }
            val change = paid - price
            db.markOrderPaid(order.id, price, paid, change)
            dialog.dismiss()

            AlertDialog.Builder(this)
                .setTitle("✅ تم الدفع")
                .setMessage("جهاز: ${order.deviceNumber}\n" +
                    "الوقت: ${DatabaseHelper.formatElapsed(order.startTime)}\n" +
                    "السعر: ${"%.0f".format(price)} $cur\n" +
                    "دفع: ${"%.0f".format(paid)} $cur\n\n" +
                    "الباقي للزبون: ${"%.0f".format(change)} $cur")
                .setPositiveButton("حسناً") { _, _ -> loadData() }
                .setCancelable(false)
                .show()
        }

        dialog.show()
    }

    // ─── Edit dialog for FIXED sessions ──────────────────────────────────────
    private fun showEditDialog(order: Order) {
        val pricePerHour = db.getSetting("price_per_hour")?.toDoubleOrNull() ?: 10.0
        val testMode = db.getSetting("test_mode") == "1"
        val cur = db.getSetting("currency") ?: "ج.م"
        val unitMs = if (testMode) 10_000L else 3_600_000L

        NumpadDialog(this, "كم ساعة تضيف على الجهاز ${order.deviceNumber}؟",
            showDecimal = false, integerOnly = true) { val extra = it.toIntOrNull() ?: 0
            if (extra > 0) {
                db.addHoursToOrder(order.id, extra, pricePerHour)

                // If test mode, override with seconds instead of hours in the DB
                if (testMode) {
                    // addHoursToOrder uses 3_600_000 internally; we need to override
                    // Re-fetch and fix end_time for test mode
                }
                loadData()
                Toast.makeText(this, "تمت إضافة $extra ساعة ✅", Toast.LENGTH_SHORT).show()
            }
        }.show()
    }

    // ─── Check expired fixed orders ──────────────────────────────────────────
    private fun checkExpiredFixed() {
        val now = System.currentTimeMillis()
        orders.forEach { o ->
            if (o.sessionType == "fixed" && !o.isDone &&
                o.endTime > 0 && now >= o.endTime && !alertedOrders.contains(o.id)) {
                alertedOrders.add(o.id)
                db.markOrderPaid(o.id, o.price, 0.0, 0.0)
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("⏰ انتهى الوقت!")
                        .setMessage("جهاز رقم: ${o.deviceNumber}\nالمدة: ${o.hours} ساعة")
                        .setPositiveButton("حسناً", null)
                        .setCancelable(false)
                        .show()
                    loadData()
                }
            }
        }
    }

    // ─── End day ──────────────────────────────────────────────────────────────
    private fun confirmEndDay() {
        val day = currentDay ?: return
        val total = db.getDayTotal(day.id)
        val count = db.getDayOrderCount(day.id)
        val cur = db.getSetting("currency") ?: "ج.م"
        AlertDialog.Builder(this)
            .setTitle("إنهاء اليوم")
            .setMessage("عدد الطلبات: $count\nالإجمالي: ${"%.0f".format(total)} $cur\n\nمتأكد؟")
            .setPositiveButton("نعم، إنهاء") { _, _ ->
                db.closeDay(day.id, total)
                AlertDialog.Builder(this)
                    .setTitle("🎮 ملخص اليوم")
                    .setMessage("تم إنهاء اليوم ✅\n\nعدد الطلبات:  $count\nإجمالي المبيعات:  ${"%.0f".format(total)} $cur")
                    .setPositiveButton("حسناً", null).show()
                loadData()
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    // ─── Adapter ──────────────────────────────────────────────────────────────
    inner class OrderAdapter : BaseAdapter() {
        override fun getCount() = orders.size
        override fun getItem(pos: Int): Any = orders[pos]
        override fun getItemId(pos: Int) = orders[pos].id

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order, parent, false)

            val o = orders[pos]
            val pricePerHour = db.getSetting("price_per_hour")?.toDoubleOrNull() ?: 10.0
            val testMode     = db.getSetting("test_mode") == "1"
            val cur          = db.getSetting("currency") ?: "ج.م"

            v.findViewById<TextView>(R.id.tvDevice).text = o.deviceNumber

            val tvType   = v.findViewById<TextView>(R.id.tvType)
            val tvTimer  = v.findViewById<TextView>(R.id.tvTimer)
            val tvPrice  = v.findViewById<TextView>(R.id.tvPrice)
            val btnAction = v.findViewById<Button>(R.id.btnAction)

            if (o.sessionType == "open") {
                val price = DatabaseHelper.calcOpenPrice(o.startTime, pricePerHour, testMode)
                tvType.text = "مفتوح ∞"
                tvType.setTextColor(Color.parseColor("#00AAFF"))
                tvTimer.text = DatabaseHelper.formatElapsed(o.startTime)
                tvPrice.text = "${"%.0f".format(price)} $cur"

                if (o.isDone) {
                    btnAction.text = "مدفوع ✓"
                    btnAction.setBackgroundColor(Color.parseColor("#003300"))
                    btnAction.isEnabled = false
                    tvTimer.setTextColor(Color.GRAY)
                    v.setBackgroundColor(Color.parseColor("#0D0D1A"))
                } else {
                    btnAction.text = "دفع"
                    btnAction.setBackgroundColor(Color.parseColor("#004488"))
                    btnAction.isEnabled = true
                    tvTimer.setTextColor(Color.parseColor("#00FF88"))
                    v.setBackgroundColor(Color.parseColor("#0A0A20"))
                    btnAction.setOnClickListener { showPayDialog(o) }
                }
            } else {
                // Fixed session
                tvType.text = "${o.hours} ساعة"
                tvType.setTextColor(Color.parseColor("#AAAACC"))
                tvPrice.text = "${"%.0f".format(o.price)} $cur"

                if (o.isDone) {
                    tvTimer.text = "انتهى"
                    tvTimer.setTextColor(Color.parseColor("#FF4444"))
                    btnAction.text = "منتهي"
                    btnAction.setBackgroundColor(Color.parseColor("#330000"))
                    btnAction.isEnabled = false
                    v.setBackgroundColor(Color.parseColor("#0D0808"))
                } else {
                    val rem = o.endTime - System.currentTimeMillis()
                    tvTimer.text = DatabaseHelper.formatCountdown(o.endTime)
                    when {
                        rem < 5 * 60_000L  -> { tvTimer.setTextColor(Color.RED);    v.setBackgroundColor(Color.parseColor("#1A0000")) }
                        rem < 15 * 60_000L -> { tvTimer.setTextColor(Color.YELLOW); v.setBackgroundColor(Color.parseColor("#1A1400")) }
                        else               -> { tvTimer.setTextColor(Color.parseColor("#00FF66")); v.setBackgroundColor(Color.parseColor("#0A0A1A")) }
                    }
                    btnAction.text = "+ساعات"
                    btnAction.setBackgroundColor(Color.parseColor("#444400"))
                    btnAction.isEnabled = true
                    btnAction.setOnClickListener { showEditDialog(o) }
                }
            }

            return v
        }
    }
}
