package com.shop.veggieledger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shop.veggieledger.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val role = intent.getStringExtra("USER_ROLE") ?: "STAFF"
        binding.tvWelcome.text = "Veggie Mandi ($role)"

        binding.btnHistory.setOnClickListener { startActivity(Intent(this, TransactionHistoryActivity::class.java)) }
                

        // Masters
        binding.btnCustomers.setOnClickListener { startActivity(Intent(this, CustomerMasterActivity::class.java)) }
        binding.btnSuppliers.setOnClickListener { startActivity(Intent(this, SupplierMasterActivity::class.java)) }
        binding.btnItems.setOnClickListener { startActivity(Intent(this, ItemMasterActivity::class.java)) }

        // Transactions
        binding.btnPurchase.setOnClickListener { startActivity(Intent(this, PurchaseActivity::class.java)) }
        binding.btnSales.setOnClickListener { startActivity(Intent(this, SalesActivity::class.java)) }
        binding.btnSalesReceipt.setOnClickListener { startActivity(Intent(this, SalesReceiptActivity::class.java)) }
        binding.btnPurchasePayment.setOnClickListener { startActivity(Intent(this, PurchasePaymentActivity::class.java)) }
        binding.btnExpenseIncome.setOnClickListener { startActivity(Intent(this, ExpenseIncomeActivity::class.java)) }

        // Reports
        binding.btnCustomerOutstanding.setOnClickListener { startActivity(Intent(this, CustomerOutstandingActivity::class.java)) }
        binding.btnSupplierOutstanding.setOnClickListener { startActivity(Intent(this, SupplierOutstandingActivity::class.java)) }
        binding.btnProfitLoss.setOnClickListener { startActivity(Intent(this, ProfitLossActivity::class.java)) }
    }
}
