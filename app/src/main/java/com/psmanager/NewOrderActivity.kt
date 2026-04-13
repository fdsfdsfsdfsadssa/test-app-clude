package com.psmanager

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class NewOrderActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var dayId = 0L
    private var sessionType = "fixed"  // "fixed" or "open"
    private var deviceNumber = ""
    private var hours = 1
    private var paidAmount = 0.0
    private var hasPaid = false
    private var pricePerHour = 10.0
    private var testMode = false
    private var currency = "ج.م"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_order)
        supportActionBar?.hide()

        db           = DatabaseHelper(this)
        dayId        = intent.getLongExtra("day_id", 0L)
        pricePerHour = db.getSetting("price_per_hour")?.toDoubleOrNull() ?: 10.0
        testMode     = db.getSetting("test_mode") == "1"
        currency     = db.getSetting("currency") ?: "ج.م"

        setupViews()
    }

    private fun calcFixedPrice() = hours * pricePerHour

    private fun updateUI() {
        val tvDevice  = findViewById<TextView>(R.id.tvDeviceDisplay)
        val tvHours   = findViewById<TextView>(R.id.tvSelectedHours)
        val tvPrice   = findViewById<TextView>(R.id.tvCalcPrice)
        val tvPaid    = findViewById<TextView>(R.id.tvPaidDisplay)
        val tvChange  = findViewById<TextView>(R.id.tvChange)
        val layFixed  = findViewById<LinearLayout>(R.id.layoutFixedOptions)
        val layPay    = findViewById<LinearLayout>(R.id.layoutPayNow)
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)

        tvDevice.text = if (deviceNumber.isEmpty()) "اضغط لاختيار رقم الجهاز" else "جهاز  $deviceNumber"
        tvDevice.setTextColor(if (deviceNumber.isEmpty()) Color.parseColor("#555577") else Color.WHITE)

        layFixed.visibility = if (sessionType == "fixed") View.VISIBLE else View.GONE
        layPay.visibility   = if (sessionType == "fixed") View.VISIBLE else View.GONE

        tvHours.text = "$hours"

        val price = calcFixedPrice()
        tvPrice.text = "${"%.0f".format(price)} $currency"

        if (hasPaid && sessionType == "fixed") {
            tvPaid.text = "${"%.0f".format(paidAmount)} $currency"
            tvPaid.setTextColor(Color.parseColor("#FFD700"))
            val change = paidAmount - price
            if (change >= 0) {
                tvChange.text = "الباقي للزبون:  ${"%.0f".format(change)} $currency"
                tvChange.setTextColor(Color.parseColor("#00CC66"))
            } else {
                tvChange.text = "ناقص:  ${"%.0f".format(-change)} $currency"
                tvChange.setTextColor(Color.RED)
            }
        } else if (sessionType == "fixed") {
            tvPaid.text = "اضغط لإدخال المبلغ"
            tvPaid.setTextColor(Color.parseColor("#555577"))
            tvChange.text = ""
        }

        btnConfirm.isEnabled = deviceNumber.isNotEmpty() &&
            (sessionType == "open" || (hasPaid && paidAmount >= calcFixedPrice()))
        btnConfirm.setBackgroundColor(if (btnConfirm.isEnabled) Color.parseColor("#004400")
            else Color.parseColor("#222233"))
    }

    private fun setupViews() {
        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Session type toggle
        val btnFixed = findViewById<Button>(R.id.btnTypeFixed)
        val btnOpen  = findViewById<Button>(R.id.btnTypeOpen)

        fun updateTypeButtons() {
            btnFixed.setBackgroundColor(if (sessionType=="fixed") Color.parseColor("#003087") else Color.parseColor("#1A1A3E"))
            btnOpen.setBackgroundColor(if (sessionType=="open")  Color.parseColor("#003087") else Color.parseColor("#1A1A3E"))
        }

        btnFixed.setOnClickListener { sessionType = "fixed"; updateTypeButtons(); hasPaid = false; paidAmount = 0.0; updateUI() }
        btnOpen.setOnClickListener  { sessionType = "open";  updateTypeButtons(); updateUI() }
        updateTypeButtons()

        // Device number picker
        findViewById<TextView>(R.id.tvDeviceDisplay).setOnClickListener {
            NumpadDialog(this, "رقم الجهاز", showDecimal = false, integerOnly = true) { v ->
                deviceNumber = v.trimStart('0').ifEmpty { v }
                updateUI()
            }.show()
        }

        // Hours picker (fixed session)
        findViewById<Button>(R.id.btnHourMinus).setOnClickListener {
            if (hours > 1) { hours--; hasPaid = false; paidAmount = 0.0; updateUI() }
        }
        findViewById<Button>(R.id.btnHourPlus).setOnClickListener {
            if (hours < 24) { hours++; hasPaid = false; paidAmount = 0.0; updateUI() }
        }

        // Paid amount picker
        findViewById<TextView>(R.id.tvPaidDisplay).setOnClickListener {
            if (sessionType == "fixed") {
                NumpadDialog(this, "المبلغ المدفوع من الزبون") { v ->
                    paidAmount = v.toDoubleOrNull() ?: 0.0
                    hasPaid = true
                    updateUI()
                }.show()
            }
        }

        // Confirm
        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            if (deviceNumber.isEmpty()) return@setOnClickListener

            val now = System.currentTimeMillis()
            val unitMs = if (testMode) 10_000L else 3_600_000L

            val order = when (sessionType) {
                "open" -> Order(
                    dayId = dayId, deviceNumber = deviceNumber,
                    sessionType = "open", hours = 0,
                    price = 0.0, paid = 0.0, changeAmount = 0.0,
                    startTime = now, endTime = 0L
                )
                else -> {
                    val price = calcFixedPrice()
                    val change = paidAmount - price
                    Order(
                        dayId = dayId, deviceNumber = deviceNumber,
                        sessionType = "fixed", hours = hours,
                        price = price, paid = paidAmount, changeAmount = change,
                        startTime = now, endTime = now + hours * unitMs
                    )
                }
            }

            db.createOrder(order)

            val msg = if (sessionType == "open")
                "جهاز $deviceNumber  —  جلسة مفتوحة ∞\nالعداد بدأ يحسب!"
            else {
                "جهاز $deviceNumber  —  ${hours} ساعة\n" +
                "السعر: ${"%.0f".format(calcFixedPrice())} $currency\n" +
                "الباقي للزبون: ${"%.0f".format(paidAmount - calcFixedPrice())} $currency"
            }

            AlertDialog.Builder(this)
                .setTitle("✅ تمت إضافة الجهاز")
                .setMessage(msg)
                .setPositiveButton("حسناً") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }

        updateUI()
    }
}
