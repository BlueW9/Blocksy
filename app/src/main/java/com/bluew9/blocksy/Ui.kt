/*
 * Blocksy - offline call blocker
 * Copyright (C) 2026 BlueW9
 * Licensed under the GNU General Public License v3.0 or later.
 * See <https://www.gnu.org/licenses/gpl-3.0.html>.
 */
package com.bluew9.blocksy

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

object Colors {
    val BG = Color.parseColor("#0B0F14")
    val SURFACE = Color.parseColor("#151C24")
    val TEXT = Color.parseColor("#E6EDF3")
    val DIM = Color.parseColor("#7F8C99")
    val ACCENT = Color.parseColor("#0A4A78")
    val OK = Color.parseColor("#3FB27F")
    val WARN = Color.parseColor("#E5A93D")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun roundedBg(color: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radiusPx.toFloat()
}

fun Context.styledButton(label: String, filled: Boolean = true, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Colors.TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = roundedBg(if (filled) Colors.ACCENT else Colors.SURFACE, dp(10))
        setOnClickListener { onClick() }
    }

fun Activity.applyDarkWindow() {
    window.statusBarColor = Colors.BG
    window.navigationBarColor = Colors.BG
    window.decorView.setBackgroundColor(Colors.BG)
}
