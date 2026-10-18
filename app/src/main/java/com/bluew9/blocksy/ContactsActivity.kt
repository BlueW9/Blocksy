/*
 * Blocksy - offline call blocker
 * Copyright (C) 2026 BlueW9
 * Licensed under the GNU General Public License v3.0 or later.
 * See <https://www.gnu.org/licenses/gpl-3.0.html>.
 */
package com.bluew9.blocksy

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class ContactsActivity : Activity() {

    private lateinit var db: BlockDb
    private lateinit var infoText: TextView
    private lateinit var blockButton: TextView
    private lateinit var adapter: ContactAdapter

    private var all: List<Entry> = emptyList()
    private val shown = ArrayList<Entry>()
    private val selected = HashSet<String>()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDarkWindow()
        db = BlockDb.get(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Add from contacts"
            setTextColor(Colors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(null, Typeface.BOLD)
        })

        val search = EditText(this).apply {
            hint = "Search name or number"
            setHintTextColor(Colors.DIM)
            setTextColor(Colors.TEXT)
            setSingleLine()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBg(Colors.SURFACE, dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim().orEmpty()
                    applyFilter()
                }
            })
        }
        root.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(8))
        }
        infoText = TextView(this).apply {
            setTextColor(Colors.DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        bar.addView(infoText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(styledButton("Select all", filled = false) { toggleAll() })
        root.addView(bar)

        adapter = ContactAdapter()
        val list = ListView(this).apply {
            divider = ColorDrawable(Colors.BG)
            dividerHeight = dp(6)
            this.adapter = this@ContactsActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                val key = shown[position].key
                if (!selected.add(key)) selected.remove(key)
                this@ContactsActivity.adapter.notifyDataSetChanged()
                updateInfo()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        blockButton = styledButton("Block selected") { blockSelected() }
        root.addView(blockButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        setContentView(root)
        updateInfo()

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            load()
        } else {
            infoText.text = "Contacts permission is needed to list your contacts."
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 10)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            load()
        } else {
            infoText.text = "Permission denied. You can still add numbers by typing them."
        }
    }

    private fun load() {
        infoText.text = "Loading contacts…"
        Thread {
            val blockedKeys = db.allKeys()
            val map = LinkedHashMap<String, Entry>()
            contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                null, null,
                Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndexOrThrow(Phone.NUMBER)
                while (c.moveToNext()) {
                    val num = c.getString(numIdx) ?: continue
                    val key = Normalizer.key(num) ?: continue
                    if (key in blockedKeys || map.containsKey(key)) continue
                    map[key] = Entry(key, num.trim(), c.getString(nameIdx) ?: "")
                }
            }
            runOnUiThread {
                all = map.values.toList()
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val q = query.lowercase()
        val qDigits = query.filter { it.isDigit() }
        shown.clear()
        for (e in all) {
            if (q.isEmpty() || e.name.lowercase().contains(q) ||
                (qDigits.isNotEmpty() && e.number.filter { it.isDigit() }.contains(qDigits))
            ) {
                shown.add(e)
            }
        }
        adapter.notifyDataSetChanged()
        updateInfo()
    }

    private fun toggleAll() {
        val allShownSelected = shown.isNotEmpty() && shown.all { it.key in selected }
        if (allShownSelected) shown.forEach { selected.remove(it.key) }
        else shown.forEach { selected.add(it.key) }
        adapter.notifyDataSetChanged()
        updateInfo()
    }

    private fun updateInfo() {
        if (all.isNotEmpty() || selected.isNotEmpty()) {
            infoText.text = "${shown.size} contacts  •  ${selected.size} selected"
        }
        blockButton.text = if (selected.isEmpty()) "Block selected" else "Block selected (${selected.size})"
    }

    private fun blockSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one contact", Toast.LENGTH_SHORT).show()
            return
        }
        val chosen = all.filter { it.key in selected }
        Thread {
            val added = db.add(chosen)
            runOnUiThread {
                Toast.makeText(this, "$added blocked", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    private inner class ContactAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(this@ContactsActivity).apply {
                setTextColor(Colors.TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val e = shown[position]
            val on = e.key in selected
            tv.background = roundedBg(if (on) Colors.ACCENT else Colors.SURFACE, dp(10))
            val mark = if (on) "☑  " else "☐  "
            tv.text = if (e.name.isEmpty()) mark + e.number else "$mark${e.name}\n      ${e.number}"
            return tv
        }
    }
}
