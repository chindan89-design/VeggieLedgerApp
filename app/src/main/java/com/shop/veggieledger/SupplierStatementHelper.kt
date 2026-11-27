package com.shop.veggieledger

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

object SupplierStatementHelper {

    data class StatementRow(
        val sortKey: Long,
        val dateStr: String,
        val supplierName: String,
        val opBal: Double,
        val purchaseLabel: String,
        val qty: Double,
        val suit: Double,
        val kudumi: Double,
        val rate: Double,
        val amount: Double,
        val payment: Double,
        val discount: Double,
        val outstanding: Double
    )

    fun parseDateSortKey(rawDate: String, fallbackMillis: Long = 0L): Long {
        val clean = rawDate.trim()
        val formats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                val dt = fmt.parse(clean)
                if (dt != null) {
                    val ymd = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(dt).toLong()
                    return (ymd * 1_000_000L) + ((fallbackMillis / 1000L) % 1_000_000L)
                }
            } catch (_: Exception) {
            }
        }
        return fallbackMillis
    }

    fun buildSortedStatementRows(
        supplierOpeningBalance: Double,
        purchaseDocs: List<DocumentSnapshot>,
        paymentDocs: List<DocumentSnapshot>,
        selectedSupplierFilter: String = "All Suppliers"
    ): List<StatementRow> {
        val rawEvents = arrayListOf<StatementRow>()

        // 1. Purchase entries
        for (doc in purchaseDocs) {
            val supName = (doc.getString("supplierName") ?: "").trim()
            if (supName.isEmpty()) continue
            if (selectedSupplierFilter != "All Suppliers" &&
                !supName.equals(selectedSupplierFilter, ignoreCase = true)
            ) continue

            val dateStr = (doc.getString("date") ?: "").trim()
            val ts = doc.getLong("timestamp") ?: 0L
            val sortKey = parseDateSortKey(dateStr, ts)
            val billNo = (doc.getString("billNo") ?: "").trim()
            val batchId = (doc.getString("batchId") ?: "").trim()
            val purTag = when {
                billNo.isNotEmpty() && batchId.isNotEmpty() -> "$billNo ($batchId)"
                billNo.isNotEmpty() -> billNo
                batchId.isNotEmpty() -> batchId
                else -> "Purchase"
            }

            val rawItems = doc.get("items") as? List<*>
            if (rawItems != null && rawItems.isNotEmpty()) {
                for (entry in rawItems) {
                    val m = entry as? Map<*, *> ?: continue
                    val itemName = m["itemName"]?.toString()?.trim() ?: "Item"
                    val nos = (m["nos"] as? Number)?.toDouble() ?: 0.0
                    val bags = (m["bags"] as? Number)?.toDouble() ?: 0.0
                    val purKgs = (m["purchaseKgs"] as? Number)?.toDouble() ?: 0.0
                    val netKgs = (m["netKgs"] as? Number)?.toDouble() ?: 0.0

                    val qtyVal = when {
                        purKgs > 0.0 -> purKgs
                        netKgs > 0.0 -> netKgs
                        bags > 0.0 -> bags
                        else -> nos
                    }

                    val suitVal = (m["suit"] as? Number)?.toDouble()
                        ?: (m["thadukki"] as? Number)?.toDouble()
                        ?: 0.0
                    val kudumiVal = (m["kudumi"] as? Number)?.toDouble() ?: 0.0

                    val rateVal = (m["ratePerKg"] as? Number)?.toDouble()
                        ?: (m["rate"] as? Number)?.toDouble()
                        ?: 0.0

                    val computedNet = if (purKgs > 0.0) (purKgs - suitVal - kudumiVal).coerceAtLeast(0.0) else qtyVal
                    val lineAmt = (m["lineTotal"] as? Number)?.toDouble()
                        ?: (m["amount"] as? Number)?.toDouble()
                        ?: (computedNet * rateVal)

                    rawEvents.add(
                        StatementRow(
                            sortKey = sortKey,
                            dateStr = dateStr,
                            supplierName = supName,
                            opBal = 0.0,
                            purchaseLabel = "$purTag - $itemName",
                            qty = qtyVal,
                            suit = suitVal,
                            kudumi = kudumiVal,
                            rate = rateVal,
                            amount = lineAmt,
                            payment = 0.0,
                            discount = 0.0,
                            outstanding = 0.0
                        )
                    )
                }
            } else {
                val purKgs = (doc.getDouble("purchaseKgs") ?: doc.getDouble("netKgs") ?: 0.0)
                val suitVal = (doc.getDouble("suit") ?: doc.getDouble("thadukki") ?: 0.0)
                val kudumiVal = (doc.getDouble("kudumi") ?: 0.0)
                val rateVal = (doc.getDouble("ratePerKg") ?: doc.getDouble("rate") ?: 0.0)
                val amtVal = (doc.getDouble("grandTotal") ?: doc.getDouble("amount") ?: (purKgs * rateVal))

                rawEvents.add(
                    StatementRow(
                        sortKey = sortKey,
                        dateStr = dateStr,
                        supplierName = supName,
                        opBal = 0.0,
                        purchaseLabel = purTag,
                        qty = purKgs,
                        suit = suitVal,
                        kudumi = kudumiVal,
                        rate = rateVal,
                        amount = amtVal,
                        payment = 0.0,
                        discount = 0.0,
                        outstanding = 0.0
                    )
                )
            }
        }

        // 2. Supplier Payment & Discount entries
        for (doc in paymentDocs) {
            val supName = (doc.getString("supplierName")
                ?: doc.getString("partyName")
                ?: doc.getString("name")
                ?: "").trim()
            if (supName.isEmpty()) continue
            if (selectedSupplierFilter != "All Suppliers" &&
                !supName.equals(selectedSupplierFilter, ignoreCase = true)
            ) continue

            val dateStr = (doc.getString("date") ?: "").trim()
            val ts = doc.getLong("timestamp") ?: 0L
            val sortKey = parseDateSortKey(dateStr, ts)

            val paidAmt = (doc.getDouble("amount")
                ?: doc.getDouble("paidAmount")
                ?: doc.getDouble("payment")
                ?: 0.0)
            val discAmt = (doc.getDouble("discount")
                ?: doc.getDouble("discAmount")
                ?: doc.getDouble("deduction")
                ?: 0.0)

            val refNo = (doc.getString("receiptNo")
                ?: doc.getString("voucherNo")
                ?: doc.getString("payMode")
                ?: "Payment").trim()

            rawEvents.add(
                StatementRow(
                    sortKey = sortKey,
                    dateStr = dateStr,
                    supplierName = supName,
                    opBal = 0.0,
                    purchaseLabel = refNo,
                    qty = 0.0,
                    suit = 0.0,
                    kudumi = 0.0,
                    rate = 0.0,
                    amount = 0.0,
                    payment = paidAmt,
                    discount = discAmt,
                    outstanding = 0.0
                )
            )
        }

        // 3. Sort strictly by Date (Ascending), then by original timestamp
        val sortedEvents = rawEvents.sortedWith(
            compareBy<StatementRow> { it.sortKey }
        )

        // 4. Compute exact running Op.Bal and Closing Outstanding row-by-row
        var runningBalance = supplierOpeningBalance
        val finalRows = ArrayList<StatementRow>(sortedEvents.size)

        for (ev in sortedEvents) {
            val rowOpBal = runningBalance
            val rowClosingOut = (rowOpBal + ev.amount - ev.payment - ev.discount).coerceAtLeast(0.0)
            finalRows.add(
                ev.copy(
                    opBal = rowOpBal,
                    outstanding = rowClosingOut
                )
            )
            runningBalance = rowClosingOut
        }

        return finalRows
    }

    fun renderStatementTable(tableLayout: TableLayout, rows: List<StatementRow>): Double {
        tableLayout.removeAllViews()

        val headers = listOf(
            "Date",
            "Name",
            "Op.Bal",
            "Purchase",
            "Qty",
            "Suit",
            "Kudumi",
            "Rate",
            "Amount",
            "Payment",
            "Discount",
            "Outstanding"
        )

        val headerRow = TableRow(tableLayout.context)
        headerRow.setBackgroundColor(Color.parseColor("#1B5E20"))
        for (h in headers) {
            val tv = TextView(tableLayout.context)
            tv.text = h
            tv.setTextColor(Color.WHITE)
            tv.setTypeface(null, Typeface.BOLD)
            tv.textSize = 12f
            tv.setPadding(12, 10, 12, 10)
            tv.gravity = if (h == "Date" || h == "Name" || h == "Purchase") Gravity.START else Gravity.END
            headerRow.addView(tv)
        }
        tableLayout.addView(headerRow)

        var totalPurchaseAmt = 0.0
        var totalPaidAmt = 0.0
        var totalDiscAmt = 0.0
        var latestOutstanding = 0.0

        rows.forEachIndexed { idx, r ->
            totalPurchaseAmt += r.amount
            totalPaidAmt += r.payment
            totalDiscAmt += r.discount
            latestOutstanding = r.outstanding

            val tr = TableRow(tableLayout.context)
            tr.setBackgroundColor(if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))

            val cells = listOf(
                r.dateStr,
                r.supplierName,
                "%.2f".format(Locale.getDefault(), r.opBal),
                r.purchaseLabel,
                if (r.qty > 0.0) "%.2f".format(Locale.getDefault(), r.qty) else "-",
                if (r.suit > 0.0) "%.2f".format(Locale.getDefault(), r.suit) else "-",
                if (r.kudumi > 0.0) "%.2f".format(Locale.getDefault(), r.kudumi) else "-",
                if (r.rate > 0.0) "%.2f".format(Locale.getDefault(), r.rate) else "-",
                if (r.amount > 0.0) "%.2f".format(Locale.getDefault(), r.amount) else "-",
                if (r.payment > 0.0) "%.2f".format(Locale.getDefault(), r.payment) else "-",
                if (r.discount > 0.0) "%.2f".format(Locale.getDefault(), r.discount) else "-",
                "%.2f".format(Locale.getDefault(), r.outstanding)
            )

            cells.forEachIndexed { cIdx, txt ->
                val tv = TextView(tableLayout.context)
                tv.text = txt
                tv.setTextColor(
                    when (cIdx) {
                        11 -> Color.parseColor("#C62828")
                        9 -> Color.parseColor("#1B5E20")
                        10 -> Color.parseColor("#1565C0")
                        else -> Color.BLACK
                    }
                )
                tv.textSize = 12f
                tv.setPadding(12, 10, 12, 10)
                tv.gravity = if (cIdx == 0 || cIdx == 1 || cIdx == 3) Gravity.START else Gravity.END
                if (cIdx == 0 || cIdx == 1 || cIdx == 8 || cIdx == 11) {
                    tv.setTypeface(null, Typeface.BOLD)
                }
                tr.addView(tv)
            }
            tableLayout.addView(tr)
        }

        // Total / Closing Summary Row
        val summaryRow = TableRow(tableLayout.context)
        summaryRow.setBackgroundColor(Color.parseColor("#FFF9C4"))
        val summaryCells = listOf(
            "TOTAL",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "%.2f".format(Locale.getDefault(), totalPurchaseAmt),
            "%.2f".format(Locale.getDefault(), totalPaidAmt),
            "%.2f".format(Locale.getDefault(), totalDiscAmt),
            "%.2f".format(Locale.getDefault(), latestOutstanding)
        )
        summaryCells.forEachIndexed { cIdx, txt ->
            val tv = TextView(tableLayout.context)
            tv.text = txt
            tv.setTextColor(Color.parseColor("#000000"))
            tv.setTypeface(null, Typeface.BOLD)
            tv.textSize = 12f
            tv.setPadding(12, 12, 12, 12)
            tv.gravity = if (cIdx == 0 || cIdx == 1 || cIdx == 3) Gravity.START else Gravity.END
            summaryRow.addView(tv)
        }
        tableLayout.addView(summaryRow)

        return latestOutstanding
    }
}
