package com.shop.veggieledger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityReportSheetBinding
import java.util.Locale

class ProfitLossActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportSheetBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Profit & Loss Statement"
        binding.tvReportTitle.text = "PROFIT & LOSS (P&L) STATEMENT"
        binding.tvReportBody.text = "Calculating Profit & Loss..."

        loadProfitAndLoss()

        binding.btnShareReport.setOnClickListener {
            val shareText = "${binding.tvReportTitle.text}\n\n${binding.tvReportBody.text}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(sendIntent, "Share P&L Report"))
        }
    }

    private fun loadProfitAndLoss() {
        var totalSales = 0.0
        var totalBagChargesCollected = 0.0
        var totalPurchase = 0.0

        var shopExpenses = 0.0
        var vehicleExpenses = 0.0
        var otherExpenses = 0.0
        var totalExtraIncome = 0.0

        var discountAllowed = 0.0
        var discountReceived = 0.0

        // 1. Fetch Sales
        db.collection("sales").get().addOnSuccessListener { salesSnap ->
            for (doc in salesSnap) {
                totalSales += doc.getDouble("grandTotal") ?: 0.0
                totalBagChargesCollected += doc.getDouble("totalBagCharges")
                    ?: doc.getDouble("bagCharges")
                    ?: 0.0
            }

            // 2. Fetch Purchases
            db.collection("purchases").get().addOnSuccessListener { purSnap ->
                for (doc in purSnap) {
                    totalPurchase += doc.getDouble("grandTotal") ?: 0.0
                }

                // 3. Fetch Expenses & Income (Properly separated by 'type' and 'category')
                db.collection("expenses_income").get().addOnSuccessListener { expSnap ->
                    for (doc in expSnap) {
                        val type = doc.getString("type")
                            ?: doc.getString("entryType")
                            ?: "Expense"
                        val category = doc.getString("category") ?: "Other Expenses"
                        val amt = doc.getDouble("amount") ?: 0.0

                        if (type.equals("Income", ignoreCase = true)) {
                            totalExtraIncome += amt
                        } else {
                            when {
                                category.equals("Shop Expenses", ignoreCase = true) -> shopExpenses += amt
                                category.equals("Vehicle Expenses", ignoreCase = true) -> vehicleExpenses += amt
                                else -> otherExpenses += amt
                            }
                        }
                    }

                    // 4. Fetch Receipt Discounts Allowed
                    db.collection("receipts").get().addOnSuccessListener { recSnap ->
                        for (doc in recSnap) {
                            discountAllowed += doc.getDouble("discount") ?: 0.0
                        }

                        // 5. Fetch Payment Discounts Received
                        db.collection("payments").get().addOnSuccessListener { paySnap ->
                            for (doc in paySnap) {
                                discountReceived += doc.getDouble("discount") ?: 0.0
                            }

                            val totalExpenses = shopExpenses + vehicleExpenses + otherExpenses
                            val totalInflow = totalSales + totalExtraIncome + discountReceived
                            val totalOutflow = totalPurchase + totalExpenses + discountAllowed
                            val netProfit = totalInflow - totalOutflow

                            binding.tvReportSummary.text = String.format(
                                Locale.getDefault(),
                                "NET PROFIT / LOSS: Rs. %.2f",
                                netProfit
                            )

                            val report = String.format(
                                Locale.getDefault(),
                                """
                                INCOME / REVENUE:
                                + Total Sales Bills      : Rs. %.2f
                                  (Includes Bag Charges) : Rs. %.2f
                                + Extra Income           : Rs. %.2f
                                + Discount Received      : Rs. %.2f
                                ------------------------------------
                                TOTAL INFLOW             : Rs. %.2f

                                COST & EXPENSES:
                                - Total Purchase Bills   : Rs. %.2f
                                - Shop Expenses          : Rs. %.2f
                                - Vehicle Expenses       : Rs. %.2f
                                - Other Expenses         : Rs. %.2f
                                - Discount Allowed       : Rs. %.2f
                                ------------------------------------
                                TOTAL OUTFLOW            : Rs. %.2f

                                ====================================
                                NET PROFIT / LOSS        : Rs. %.2f
                                ====================================
                                """.trimIndent(),
                                totalSales,
                                totalBagChargesCollected,
                                totalExtraIncome,
                                discountReceived,
                                totalInflow,
                                totalPurchase,
                                shopExpenses,
                                vehicleExpenses,
                                otherExpenses,
                                discountAllowed,
                                totalOutflow,
                                netProfit
                            )

                            binding.tvReportBody.text = report
                        }
                    }
                }
            }
        }
    }
}
