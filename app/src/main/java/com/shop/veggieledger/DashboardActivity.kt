package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityDashboardBinding
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val db = FirebaseFirestore.getInstance()
    private var selectedDateStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val role = intent.getStringExtra("USER_ROLE") ?: "STAFF"
        supportActionBar?.title = "Mandi ($role)"
        binding.tvWelcome.text = "Veggie Mandi Live Summary"

        // Date Picker Setup (Defaults to Today)
        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        binding.etDashDate.setText(selectedDateStr)

        binding.etDashDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etDashDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // View Button Click refreshes data for chosen date
        binding.btnDashViewDate.setOnClickListener {
            loadLiveSummary()
        }

        // Quick Strip Bar Clicks
        binding.stripSales.setOnClickListener { startActivity(Intent(this, SalesActivity::class.java)) }
        binding.stripPurchase.setOnClickListener { startActivity(Intent(this, PurchaseActivity::class.java)) }
        binding.stripReceipt.setOnClickListener { startActivity(Intent(this, SalesReceiptActivity::class.java)) }
        binding.stripPayment.setOnClickListener { startActivity(Intent(this, PurchasePaymentActivity::class.java)) }
        binding.stripExpense.setOnClickListener { startActivity(Intent(this, ExpenseIncomeActivity::class.java)) }
        binding.stripHistory.setOnClickListener { startActivity(Intent(this, TransactionHistoryActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        loadLiveSummary()
    }

    private fun loadLiveSummary() {
        binding.tvDashDateSectionTitle.text = "2. SUMMARY FOR $selectedDateStr"

        // 1. Live Available Stock Item-wise (Hide any item where stock is 0)
        db.collection("stock_batches").get().addOnSuccessListener { snap ->
            val itemBagsMap = HashMap<String, Int>()
            val itemKgsMap = HashMap<String, Double>()

            for (doc in snap) {
                val itemName = doc.getString("itemName") ?: continue
                val bags = (doc.getLong("availableBags") ?: 0L).toInt()
                val kgs = doc.getDouble("availableKgs") ?: 0.0

                if (bags > 0 || kgs > 0.0) {
                    val key = itemName.trim()
                    itemBagsMap[key] = (itemBagsMap[key] ?: 0) + bags
                    itemKgsMap[key] = (itemKgsMap[key] ?: 0.0) + kgs
                }
            }

            binding.llDashStockItems.removeAllViews()
            var hasAnyStock = false

            val sortedItems = itemKgsMap.keys.sorted()
            for (item in sortedItems) {
                val totalBags = itemBagsMap[item] ?: 0
                val totalKgs = itemKgsMap[item] ?: 0.0

                // Display ONLY if stock > 0
                if (totalBags > 0 || totalKgs > 0.001) {
                    hasAnyStock = true
                    val rowTv = TextView(this)
                    val bagText = if (totalBags > 0) "$totalBags Bags  |  " else ""
                    rowTv.text = "$item :  $bagText%.2f Kgs".format(totalKgs)
                    rowTv.setTextColor(Color.parseColor("#1B5E20"))
                    rowTv.setTypeface(null, Typeface.BOLD)
                    rowTv.textSize = 14f
                    rowTv.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    rowTv.setPadding(24, 20, 24, 20)

                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 0, 0, 8)
                    rowTv.layoutParams = lp
                    binding.llDashStockItems.addView(rowTv)
                }
            }

            if (!hasAnyStock) {
                val emptyTv = TextView(this)
                emptyTv.text = "No stock available currently."
                emptyTv.setTextColor(Color.GRAY)
                emptyTv.textSize = 13f
                emptyTv.setPadding(16, 16, 16, 16)
                binding.llDashStockItems.addView(emptyTv)
            }
        }

        // 2. Selected Date Summary: Purchase, Sales, Receipt, Payment, Income, Expenses
        db.collection("purchases").whereEqualTo("date", selectedDateStr).get().addOnSuccessListener { snap ->
            var dayPur = 0.0
            for (doc in snap) {
                dayPur += doc.getDouble("grandTotal") ?: 0.0
            }
            binding.tvDayPurchase.text = "Purchase\nRs. %.2f".format(dayPur)
        }

        db.collection("sales").whereEqualTo("date", selectedDateStr).get().addOnSuccessListener { snap ->
            var daySales = 0.0
            for (doc in snap) {
                daySales += doc.getDouble("grandTotal") ?: 0.0
            }
            binding.tvDaySales.text = "Sales\nRs. %.2f".format(daySales)
        }

        db.collection("receipts").whereEqualTo("date", selectedDateStr).get().addOnSuccessListener { snap ->
            var dayRec = 0.0
            for (doc in snap) {
                dayRec += doc.getDouble("amount") ?: 0.0
            }
            binding.tvDayReceipt.text = "Receipt\nRs. %.2f".format(dayRec)
        }

        db.collection("payments").whereEqualTo("date", selectedDateStr).get().addOnSuccessListener { snap ->
            var dayPay = 0.0
            for (doc in snap) {
                dayPay += doc.getDouble("amount") ?: 0.0
            }
            binding.tvDayPayment.text = "Payment\nRs. %.2f".format(dayPay)
        }

        db.collection("expenses_income").whereEqualTo("date", selectedDateStr).get().addOnSuccessListener { snap ->
            var dayInc = 0.0
            var dayExp = 0.0
            for (doc in snap) {
                val type = doc.getString("type") ?: ""
                val amt = doc.getDouble("amount") ?: 0.0
                if (type.equals("Income", ignoreCase = true)) dayInc += amt
                if (type.equals("Expense", ignoreCase = true)) dayExp += amt
            }
            binding.tvDayIncome.text = "Income\nRs. %.2f".format(dayInc)
            binding.tvDayExpense.text = "Expenses\nRs. %.2f".format(dayExp)
        }

        // 3. Overall Total Collectable & Total Payable
        db.collection("customers").get().addOnSuccessListener { snap ->
            var totalCollectable = 0.0
            for (doc in snap) {
                totalCollectable += doc.getDouble("currentBalance") ?: 0.0
            }
            binding.tvDashCustDue.text = "Total Collectable\nRs. %.2f".format(totalCollectable)
        }

        db.collection("suppliers").get().addOnSuccessListener { snap ->
            var totalPayable = 0.0
            for (doc in snap) {
                totalPayable += doc.getDouble("currentBalance") ?: 0.0
            }
            binding.tvDashSupDue.text = "Total Payable\nRs. %.2f".format(totalPayable)
        }
    }

    // Inflate Top Menu Strip
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_menu_strip, menu)
        return true
    }

        // Handle Top Menu Strip Item Clicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_customers -> startActivity(Intent(this, CustomerMasterActivity::class.java))
            R.id.nav_suppliers -> startActivity(Intent(this, SupplierMasterActivity::class.java))
            R.id.nav_items -> startActivity(Intent(this, ItemMasterActivity::class.java))
            R.id.nav_sales -> startActivity(Intent(this, SalesActivity::class.java))
            R.id.nav_purchase -> startActivity(Intent(this, PurchaseActivity::class.java))
            R.id.nav_sales_receipt -> startActivity(Intent(this, SalesReceiptActivity::class.java))
            R.id.nav_purchase_payment -> startActivity(Intent(this, PurchasePaymentActivity::class.java))
            R.id.nav_expense_income -> startActivity(Intent(this, ExpenseIncomeActivity::class.java))

            // Purchase Reports Sub-Menu
            R.id.nav_pur_supplier_rep -> openReportHub("PUR_SUPPLIER")
            R.id.nav_pur_details -> openReportHub("PUR_DETAILS")
            R.id.nav_pur_itemwise -> openReportHub("PUR_ITEMWISE")
            R.id.nav_pur_sup_statement -> openReportHub("PUR_STATEMENT")
            R.id.nav_sup_out -> startActivity(Intent(this, SupplierOutstandingActivity::class.java))

            // Sales Reports Sub-Menu
            R.id.nav_sal_customer_rep -> openReportHub("SAL_CUSTOMER")
            R.id.nav_sal_details -> openReportHub("SAL_DETAILS")
            R.id.nav_sal_itemwise -> openReportHub("SAL_ITEMWISE")
            R.id.nav_sal_cust_statement -> openReportHub("SAL_STATEMENT")
            R.id.nav_cust_out -> startActivity(Intent(this, CustomerOutstandingActivity::class.java))

            // Others Sub-Menu
            R.id.nav_pnl -> startActivity(Intent(this, ProfitLossActivity::class.java))
            R.id.nav_history -> startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openReportHub(type: String) {
        val intent = Intent(this, ReportsHubActivity::class.java)
        intent.putExtra("REPORT_TYPE", type)
        startActivity(intent)
    }
    
}
