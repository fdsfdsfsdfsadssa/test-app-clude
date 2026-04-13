package com.psmanager

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()
        db = DatabaseHelper(this)

        val etPrice    = findViewById<EditText>(R.id.etPricePerHour)
        val etCurrency = findViewById<EditText>(R.id.etCurrency)
        val btnSave    = findViewById<Button>(R.id.btnSave)
        val btnBack    = findViewById<ImageButton>(R.id.btnBack)

        etPrice.setText(db.getSetting("price_per_hour") ?: "10")
        etCurrency.setText(db.getSetting("currency") ?: "ج.م")

        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val price = etPrice.text.toString().trim()
            val cur   = etCurrency.text.toString().trim()

            if (price.isEmpty() || price.toDoubleOrNull() == null || price.toDouble() <= 0) {
                etPrice.error = "أدخل سعرًا صحيحًا"
                return@setOnClickListener
            }

            db.setSetting("price_per_hour", price)
            db.setSetting("currency", cur.ifEmpty { "ج.م" })

            Toast.makeText(this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
