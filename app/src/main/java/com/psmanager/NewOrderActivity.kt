package com.psmanager

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class NewOrderActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var dayId       = 0L
    private var hours       = 1
    private var pricePerHour = 10.0
    private var currency    = "ج.م"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_order)
        supportActionBar?.hide()

        db           = DatabaseHelper(this)
        dayId        = intent.getLongExtra("day_id", 0L)
        pricePerHour = db.getSetting("price_per_hour")?.toDoubleOrNull() ?: 10.0
        currency     = db.getSetting("currency") ?: "ج.م"

        setupViews()
    }

    private fun setupViews() {
        val etDevice  = findViewById<EditText>(R.id.etDeviceNumber)
        val btnMinus  = findViewById<Button>(R.id.btnHourMinus)
        val btnPlus   = findViewById<Button>(R.id.btnHourPlus)
        val tvHours   = findViewById<TextView>(R.id.tvSelectedHours)
        val tvPrice   = findViewById<TextView>(R.id.tvCalculatedPrice)
        val etPaid    = findViewById<EditText>(R.id.etAmountPaid)
        val tvChange  = findViewById<TextView>(R.id.tvChange)
        val btnOk     = findViewById<Button>(R.id.btnConfirm)
        val btnBack   = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        fun refresh() {
            tvHours.text = "$hours"
            val p = hours * pricePerHour
            tvPrice.text = "${fmtN(p)} $currency"
            recalcChange(etPaid.text.toString(), p, tvChange)
        }

        btnMinus.setOnClickListener { if (hours > 1)  { hours--; refresh() } }
        btnPlus.setOnClickListener  { if (hours < 24) { hours++; refresh() } }

        etPaid.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                recalcChange(s.toString(), hours * pricePerHour, tvChange)
            }
        })

        refresh()

        btnOk.setOnClickListener {
            val device = etDevice.text.toString().trim()
            val paidStr = etPaid.text.toString().trim()

            if (device.isEmpty()) { etDevice.error = "أدخل رقم الجهاز"; return@setOnClickListener }

            val price  = hours * pricePerHour
            val paid   = paidStr.toDoubleOrNull() ?: 0.0

            if (paid < price) { etPaid.error = "المبلغ أقل من السعر"; return@setOnClickListener }

            val change  = paid - price
            val now     = System.currentTimeMillis()
            val endTime = now + hours * 3_600_000L

            db.createOrder(Order(
                dayId = dayId, deviceNumber = device, hours = hours,
                price = price, paid = paid, changeAmount = change,
                startTime = now, endTime = endTime
            ))

            AlertDialog.Builder(this)
                .setTitle("✅ تم إضافة الجهاز")
                .setMessage(
                    "الجهاز:  $device\n" +
                    "المدة:  $hours ساعة\n" +
                    "السعر:  ${fmtN(price)} $currency\n" +
                    "دفع:  ${fmtN(paid)} $currency\n\n" +
                    "الباقي للزبون:  ${fmtN(change)} $currency"
                )
                .setPositiveButton("حسناً") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun recalcChange(paidStr: String, price: Double, tv: TextView) {
        val paid   = paidStr.toDoubleOrNull() ?: 0.0
        val change = paid - price
        when {
            paid <= 0  -> { tv.text = ""; }
            change >= 0 -> { tv.text = "الباقي: ${fmtN(change)} $currency"; tv.setTextColor(Color.parseColor("#00CC66")) }
            else        -> { tv.text = "ناقص: ${fmtN(-change)} $currency";  tv.setTextColor(Color.RED) }
        }
    }

    private fun fmtN(n: Double) = "%.0f".format(n)
}
