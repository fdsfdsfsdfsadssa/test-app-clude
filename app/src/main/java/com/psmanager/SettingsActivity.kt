package com.psmanager

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var priceInput = ""
    private var testMode = false
    private val currencies = listOf("ج.م","ر.س","د.إ","دينار","دولار")
    private var selectedCurrency = "ج.م"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()
        db = DatabaseHelper(this)

        priceInput       = db.getSetting("price_per_hour") ?: "10"
        testMode         = db.getSetting("test_mode") == "1"
        selectedCurrency = db.getSetting("currency") ?: "ج.م"

        setupViews()
    }

    private fun setupViews() {
        val tvPriceDisplay = findViewById<TextView>(R.id.tvPriceDisplay)
        val btnTestMode    = findViewById<Button>(R.id.btnTestMode)
        val btnSave        = findViewById<Button>(R.id.btnSave)

        tvPriceDisplay.text = "$priceInput ج.م / ساعة"

        fun updateTestBtn() {
            btnTestMode.text = if (testMode) "وضع الاختبار: مفعّل (10ث = ساعة)" else "وضع الاختبار: مغلق"
            btnTestMode.setBackgroundColor(if (testMode) Color.parseColor("#884400") else Color.parseColor("#222244"))
        }
        updateTestBtn()

        // Price picker - tap display to open numpad
        tvPriceDisplay.setOnClickListener {
            NumpadDialog(this, "سعر الساعة الواحدة") { v ->
                priceInput = v
                tvPriceDisplay.text = "$v $selectedCurrency / ساعة"
            }.show()
        }

        // Currency buttons
        val currencyContainer = findViewById<LinearLayout>(R.id.currencyContainer)
        for (cur in currencies) {
            val btn = Button(this).apply {
                text = cur; textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.weight = 1f; it.marginEnd = 4 }
                setOnClickListener {
                    selectedCurrency = cur
                    tvPriceDisplay.text = "$priceInput $cur / ساعة"
                    updateCurrencyButtons(currencyContainer, cur)
                }
            }
            currencyContainer.addView(btn)
        }
        updateCurrencyButtons(currencyContainer, selectedCurrency)

        btnTestMode.setOnClickListener {
            testMode = !testMode
            updateTestBtn()
        }

        btnSave.setOnClickListener {
            val price = priceInput.toDoubleOrNull() ?: 0.0
            if (price <= 0) {
                Toast.makeText(this, "أدخل سعرًا صحيحًا", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            db.setSetting("price_per_hour", priceInput)
            db.setSetting("currency", selectedCurrency)
            db.setSetting("test_mode", if (testMode) "1" else "0")
            Toast.makeText(this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun updateCurrencyButtons(container: LinearLayout, selected: String) {
        for (i in 0 until container.childCount) {
            val btn = container.getChildAt(i) as? Button ?: continue
            btn.setBackgroundColor(
                if (btn.text == selected) android.graphics.Color.parseColor("#003087")
                else android.graphics.Color.parseColor("#1A1A3E")
            )
            btn.setTextColor(android.graphics.Color.WHITE)
        }
    }
}
