package com.psmanager

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.*

class NumpadDialog(
    context: Context,
    private val title: String,
    private val showDecimal: Boolean = true,
    private val integerOnly: Boolean = false,
    private val onConfirm: (String) -> Unit
) : Dialog(context) {

    private var input = ""
    private lateinit var tvDisplay: TextView
    private lateinit var tvTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A3E"))
            setPadding(24, 24, 24, 24)
        }

        tvTitle = TextView(context).apply {
            text = this@NumpadDialog.title
            textSize = 16f; setTextColor(Color.parseColor("#AAAADD"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }

        tvDisplay = TextView(context).apply {
            text = "0"; textSize = 38f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A1A"))
            setPadding(20, 16, 20, 16)
        }

        root.addView(tvTitle)
        root.addView(tvDisplay)
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 12)
        })

        // Numpad grid
        val rows = listOf(
            listOf("7","8","9"),
            listOf("4","5","6"),
            listOf("1","2","3"),
            listOf("C","0","⌫")
        )

        for (row in rows) {
            val hl = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0).also {
                    it.weight = 1f; it.bottomMargin = 6
                }
            }
            for (label in row) {
                val btn = Button(context).apply {
                    text = label; textSize = 22f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT).also { it.weight = 1f; it.marginEnd = 6 }
                    setBackgroundColor(Color.parseColor(
                        if (label == "C") "#660000"
                        else if (label == "⌫") "#333366"
                        else "#222255"
                    ))
                    setOnClickListener { onNumpadKey(label) }
                }
                hl.addView(btn)
            }
            root.addView(hl, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)))
        }

        // Decimal button row (optional)
        if (showDecimal && !integerOnly) {
            val dotRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)).also { it.bottomMargin = 6 }
            }
            val dotBtn = Button(context).apply {
                text = "."; textSize = 22f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).also { it.weight = 1f; it.marginEnd = 6 }
                setBackgroundColor(Color.parseColor("#222255"))
                setOnClickListener { onNumpadKey(".") }
            }
            dotRow.addView(dotBtn)
            root.addView(dotRow)
        }

        // Confirm button
        val btnOk = Button(context).apply {
            text = "✓  تأكيد"; textSize = 18f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56)).also { it.topMargin = 8 }
            setBackgroundColor(Color.parseColor("#004400"))
            setOnClickListener {
                val v = input.ifEmpty { "0" }
                val num = v.toDoubleOrNull() ?: 0.0
                if (num > 0 || v == "0") { onConfirm(v); dismiss() }
                else { tvDisplay.text = "0"; tvDisplay.setTextColor(Color.RED) }
            }
        }
        root.addView(btnOk)

        setContentView(root)
        setCanceledOnTouchOutside(true)
        window?.setLayout((context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun onNumpadKey(key: String) {
        tvDisplay.setTextColor(Color.parseColor("#FFD700"))
        when (key) {
            "C"  -> { input = ""; tvDisplay.text = "0" }
            "⌫"  -> {
                if (input.isNotEmpty()) input = input.dropLast(1)
                tvDisplay.text = if (input.isEmpty()) "0" else input
            }
            "."  -> {
                if (!input.contains('.') && !integerOnly) {
                    input += if (input.isEmpty()) "0." else "."
                    tvDisplay.text = input
                }
            }
            else -> {
                if (input.length < 9) {
                    // Prevent leading zeros
                    if (input == "0" && key != ".") input = key
                    else input += key
                    tvDisplay.text = input
                }
            }
        }
    }

    fun setDisplayTitle(t: String) { if (::tvTitle.isInitialized) tvTitle.text = t }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
