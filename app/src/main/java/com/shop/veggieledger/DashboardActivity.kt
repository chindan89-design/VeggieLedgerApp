package com.shop.veggieledger

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val role = intent.getStringExtra("USER_ROLE") ?: "STAFF"
        supportActionBar?.title = "Mandi ($role)"
        binding.tvWelcome.text = "Veggie Mandi Live Summary"

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
        db.collection("customers").get().addOnSuccessListener { snap ->
            val totalDue = snap.sumOf { it.getDouble("currentBalance") ?: 0.0 }
            binding.tvDashCustDue.text = "Total Customer Pending: Rs. %.2f".format(totalDue)
        }

        db.collection("suppliers").get().addOnSuccessListener { snap ->
            val totalPay = snap.sumOf { it.getDouble("currentBalance") ?: 0.0 }
            binding.tvDashSupDue.text = "Total Supplier Payable: Rs. %.2f".format(totalPay)
        }

        db.collection("stock_batches").whereEqualTo("status", "ACTIVE").get().addOnSuccessListener { snap ->
            var activeBags = 0
            var activeKgs = 0.0
            for (doc in snap) {
                val bags = (doc.getLong("availableBags") ?: 0L).toInt()
                val kgs = doc.getDouble("availableKgs") ?: 0.0
                if (bags > 0 || kgs > 0) {
                    activeBags += bags
                    activeKgs += kgs
                }
            }
            binding.tvDashStockSummary.text = "Live Shop Stock: $activeBags Bags  |  %.1f Kgs".format(activeKgs)
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
            R.id.nav_cust_out -> startActivity(Intent(this, CustomerOutstandingActivity::class.java))
            R.id.nav_sup_out -> startActivity(Intent(this, SupplierOutstandingActivity::class.java))
            R.id.nav_pnl -> startActivity(Intent(this, ProfitLossActivity::class.java))
            R.id.nav_history -> startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }
}
