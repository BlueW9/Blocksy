/*
 * Blocksy - offline call blocker
 * Copyright (C) 2026 BlueW9
 * Licensed under the GNU General Public License v3.0 or later.
 * See <https://www.gnu.org/licenses/gpl-3.0.html>.
 */
package com.bluew9.blocksy

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
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

class MainActivity : Activity() {

    private lateinit var db: BlockDb
    private lateinit var statusText: TextView
    private lateinit var enableButton: TextView
    private lateinit var countText: TextView
    private lateinit var adapter: EntryAdapter
    private val rows = ArrayList<Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDarkWindow()
        db = BlockDb.get(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Blocksy"
            setTextColor(Colors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Offline call blocker. No internet, no tracking."
            setTextColor(Colors.DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(2), 0, dp(16))
        })

        statusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBg(Colors.SURFACE, dp(10))
        }
        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        enableButton = styledButton("Turn on call blocking") { requestRole() }
        root.addView(enableButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(styledButton("Add from contacts") {
            startActivity(Intent(this, ContactsActivity::class.java))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { rightMargin = dp(5) })
        buttons.addView(styledButton("Add number", filled = false) { showAddNumber() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(5) })
        root.addView(buttons, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        countText = TextView(this).apply {
            setTextColor(Colors.DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(countText)

        adapter = EntryAdapter()
        val list = ListView(this).apply {
            divider = ColorDrawable(Colors.BG)
            dividerHeight = dp(6)
            this.adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, position, _ -> confirmUnblock(rows[position]) }
        }
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(styledButton("Clear all", filled = false) { confirmClear() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refresh()
    }

    private fun isActive(): Boolean {
        val rm = getSystemService(RoleManager::class.java)
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Call screening is not available on this phone", Toast.LENGTH_LONG).show()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 1)
    }

    private fun refresh() {
        if (isActive()) {
            statusText.text = "Call blocking is ON"
            statusText.setTextColor(Colors.OK)
            enableButton.visibility = View.GONE
        } else {
            statusText.text = "Call blocking is OFF"
            statusText.setTextColor(Colors.WARN)
            enableButton.visibility = View.VISIBLE
        }
        Thread {
            val list = db.all()
            runOnUiThread {
                rows.clear()
                rows.addAll(list)
                adapter.notifyDataSetChanged()
                countText.text = if (list.isEmpty()) {
                    "No blocked numbers yet. Add some from your contacts."
                } else {
                    "${list.size} blocked  •  tap a number to unblock"
                }
            }
        }.start()
    }

    private fun showAddNumber() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "Phone number"
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Block a number")
            .setView(input)
            .setPositiveButton("Block") { _, _ ->
                val raw = input.text.toString().trim()
                val key = Normalizer.key(raw)
                if (key == null) {
                    Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show()
                } else {
                    Thread {
                        db.add(listOf(Entry(key, raw, "")))
                        runOnUiThread { refresh() }
                    }.start()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmUnblock(e: Entry) {
        val who = if (e.name.isEmpty()) e.number else "${e.name} (${e.number})"
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Unblock?")
            .setMessage(who)
            .setPositiveButton("Unblock") { _, _ ->
                Thread {
                    db.remove(e.key)
                    runOnUiThread { refresh() }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClear() {
        if (rows.isEmpty()) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Clear the whole list?")
            .setMessage("All ${rows.size} numbers will be unblocked.")
            .setPositiveButton("Clear") { _, _ ->
                Thread {
                    db.clear()
                    runOnUiThread { refresh() }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class EntryAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                setTextColor(Colors.TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = roundedBg(Colors.SURFACE, dp(10))
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val e = rows[position]
            tv.text = if (e.name.isEmpty()) e.number else "${e.name}\n${e.number}"
            return tv
        }
    }
}
