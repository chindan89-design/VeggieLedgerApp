package com.shop.veggieledger

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.*

class SupplierStatementActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var spSupplierFilter: Spinner
    private lateinit var tvSummaryBanner: TextView
    private lateinit var tlStatementGrid: TableLayout

    private val supplierList = arrayListOf<SupplierInfo>()
    private val supplierSpinnerNames = arrayListOf("All Suppliers")
    private lateinit var supplierAdapter: ArrayAdapter<String>

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private var isUpdatingSpinner: Boolean = false

    data class SupplierInfo(
        val id: String,
        val name: String,
        val openingBalance: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Supplier Statement"

        val rootView = buildScreenLayout()
        setContentView(rootView)

        val cal = Calendar.getInstance()
        toDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, -30)
        fromDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)

        etFromDate.setText(fromDateStr)
        etToDate.setText(toDateStr)

        etFromDate.setOnClickListener {
            showDatePicker(etFromDate) { picked ->
                fromDateStr = picked
                loadSupplierStatement()
            }
        }

        etToDate.setOnClickListener {
            showDatePicker(etToDate) { picked ->
                toDateStr = picked
                loadSupplierStatement()
            }
        }

        supplierAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, supplierSpinnerNames)
        supplierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSupplierFilter.adapter = supplierAdapter

        spSupplierFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (!isUpdatingSpinner) {
                    loadSupplierStatement()
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        loadSuppliersThenStatement()
    }

    private fun buildScreenLayout(): ScrollView {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            setPadding(24, 24, 24, 32)
        }

        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTv = TextView(this).apply {
            text = "SUPPLIER STATEMENT (DATE SORTED)"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        mainCol.addView(titleTv)

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 12)
        }

        etFromDate = EditText(this).apply {
            hint = "From Date"
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }

        etToDate = EditText(this).apply {
            hint = "To Date"
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
        }

        dateRow.addView(etFromDate)
        dateRow.addView(etToDate)
        mainCol.addView(dateRow)

        val lblSup = TextView(this).apply {
            text = "Select Supplier"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            setPadding(4, 0, 0, 4)
        }
        mainCol.addView(lblSup)

        spSupplierFilter = Spinner(this).apply {
            setBackgroundResource(android.R.drawable.btn_dropdown)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (46 * resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }
        mainCol.addView(spSupplierFilter)

        val btnRefresh = Button(this).apply {
            text = "REFRESH STATEMENT"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
            setOnClickListener { loadSupplierStatement() }
        }
        mainCol.addView(btnRefresh)

        tvSummaryBanner = TextView(this).apply {
            text = "Total Outstanding: Rs. 0.00"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C62828"))
            setBackgroundColor(Color.parseColor("#FFF9C4"))
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
        }
        mainCol.addView(tvSummaryBanner)

        val hScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tlStatementGrid = TableLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CFD8DC"))
            setPadding(2, 2, 2, 2)
        }
        hScroll.addView(tlStatementGrid)
        mainCol.addView(hScroll)

        scrollView.addView(mainCol)
        return scrollView
    }

    private fun showDatePicker(targetEt: EditText, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        try {
            val parsed = sdf.parse(targetEt.text.toString().trim())
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {
        }

        DatePickerDialog(
            this,
            { _, y, m, d ->
                cal.set(y, m, d)
                val formatted = sdf.format(cal.time)
                targetEt.setText(formatted)
                onPicked(formatted)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadSuppliersThenStatement() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            supplierList.clear()
            isUpdatingSpinner = true
            supplierSpinnerNames.clear()
            supplierSpinnerNames.add("All Suppliers")

            for (doc in snap) {
                val name = (doc.getString("name") ?: "").trim()
                if (name.isEmpty()) continue
                val opBal = doc.getDouble("openingBalance")
                    ?: doc.getDouble("opBal")
                    ?: doc.getDouble("currentBalance")
                    ?: 0.0
                supplierList.add(SupplierInfo(doc.id, name, opBal))
                if (supplierSpinnerNames.none { it.equals(name, ignoreCase = true) }) {
                    supplierSpinnerNames.add(name)
                }
            }

            supplierAdapter.notifyDataSetChanged()
            isUpdatingSpinner = false
            loadSupplierStatement()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load suppliers", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSupplierStatement() {
        val selectedSupplier = spSupplierFilter.selectedItem?.toString() ?: "All Suppliers"

        val purchasesTask = db.collection("purchases").get()
        val supplierPaymentsTask = db.collection("supplier_payments").get()
        val paymentsTask = db.collection("payments").get()

        Tasks.whenAllComplete(purchasesTask, supplierPaymentsTask, paymentsTask)
            .addOnSuccessListener {
                val purchaseDocs = if (purchasesTask.isSuccessful) {
                    (purchasesTask.result as? QuerySnapshot)?.documents.orEmpty()
                } else emptyList()

                val combinedPaymentDocs = arrayListOf<DocumentSnapshot>()
                if (supplierPaymentsTask.isSuccessful) {
                    combinedPaymentDocs.addAll((supplierPaymentsTask.result as? QuerySnapshot)?.documents.orEmpty())
                }
                if (paymentsTask.isSuccessful) {
                    for (doc in (paymentsTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val partyType = (doc.getString("partyType") ?: doc.getString("type") ?: "").trim()
                        val hasSupplierField = !(doc.getString("supplierName") ?: "").isNullOrBlank()
                        if (partyType.equals("supplier", ignoreCase = true) || hasSupplierField) {
                            combinedPaymentDocs.add(doc)
                        }
                    }
                }

                val fromKey = dateToYmdNumber(fromDateStr)
                val toKey = dateToYmdNumber(toDateStr)

                val allRowsSorted = if (selectedSupplier == "All Suppliers") {
                    // Group per supplier so each supplier's running Op.Bal & Outstanding is exact
                    val groupedRows = arrayListOf<SupplierStatementHelper.StatementRow>()
                    for (sup in supplierList) {
                        val supRows = SupplierStatementHelper.buildSortedStatementRows(
                            supplierOpeningBalance = sup.openingBalance,
                            purchaseDocs = purchaseDocs,
                            paymentDocs = combinedPaymentDocs,
                            selectedSupplierFilter = sup.name
                        )
                        groupedRows.addAll(supRows)
                    }
                    groupedRows.sortedBy { it.sortKey }
                } else {
                    val matchedSup = supplierList.firstOrNull { it.name.equals(selectedSupplier, ignoreCase = true) }
                    SupplierStatementHelper.buildSortedStatementRows(
                        supplierOpeningBalance = matchedSup?.openingBalance ?: 0.0,
                        purchaseDocs = purchaseDocs,
                        paymentDocs = combinedPaymentDocs,
                        selectedSupplierFilter = selectedSupplier
                    )
                }

                val filteredByDate = allRowsSorted.filter { row ->
                    val rowYmd = row.sortKey / 1_000_000L
                    (fromKey == Long.MIN_VALUE || rowYmd >= fromKey) &&
                        (toKey == Long.MAX_VALUE || rowYmd <= toKey)
                }

                val latestOut = SupplierStatementHelper.renderStatementTable(tlStatementGrid, filteredByDate)
                tvSummaryBanner.text = String.format(
                    Locale.getDefault(),
                    "%s | Closing Outstanding: Rs. %.2f",
                    selectedSupplier,
                    latestOut
                )
            }
    }

    private fun dateToYmdNumber(rawDate: String): Long {
        val clean = rawDate.trim()
        if (clean.isEmpty()) return Long.MIN_VALUE
        val formats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                val dt = fmt.parse(clean)
                if (dt != null) {
                    return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(dt).toLong()
                }
            } catch (_: Exception) {
            }
        }
        return Long.MIN_VALUE
    }
}
