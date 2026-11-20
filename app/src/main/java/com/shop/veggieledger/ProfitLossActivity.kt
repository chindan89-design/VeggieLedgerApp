package com.shop.veggieledger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityReportSheetBinding

class ProfitLossActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportSheetBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvReportTitle.text = "PROFIT & LOSS (P&L) STATEMENT"

        var totalSales = 0.0
        var totalPurchase = 0.0
        var totalBagChargesCollected = 0.0
        var totalExpenses = 0.0
        var totalExtraIncome = 0.0

        db.collection("transactions").get().addOnSuccessListener { txSnap ->
            for (doc in txSnap) {
                val tx = doc.toObject(BillTransaction::class.java)
                if (tx.type == "SALES") {
                    totalSales += tx.grandTotal
                    totalBagChargesCollected += tx.totalBagCharges
                } else if (tx.type == "PURCHASE") {
                    totalPurchase += tx.grandTotal
                }
            }

            db.collection("expenses_income").get().addOnSuccessListener { expSnap ->
                for (doc in expSnap) {
                    val ei = doc.toObject(ExpenseIncomeEntry::class.java)
                    if (ei.entryType == "EXPENSE") totalExpenses += ei.amount
                    else totalExtraIncome += ei.amount
                }

                val netProfit = (totalSales + totalExtraIncome) - (totalPurchase + totalExpenses)

                binding.tvReportSummary.text = "NET PROFIT / LOSS: Rs. %.2f".format(netProfit)

                val report = """
                    INCOME / REVENUE:
                    + Total Sales Bills      : Rs. %.2f
                      (Includes Bag Charges) : Rs. %.2f
                    + Extra Income           : Rs. %.2f
                    ------------------------------------
                    TOTAL INFLOW             : Rs. %.2f

                    COST & EXPENSES:
                    - Total Purchase Bills   : Rs. %.2f
                    - Shop Expenses          : Rs. %.2f
                    ------------------------------------
                    TOTAL OUTFLOW            : Rs. %.2f

                    ====================================
                    NET PROFIT / LOSS        : Rs. %.2f
                    ====================================
                """.trimIndent().format(
                    totalSales,
                    totalBagChargesCollected,
                    totalExtraIncome,
                    (totalSales + totalExtraIncome),
                    totalPurchase,
                    totalExpenses,
                    (totalPurchase + totalExpenses),
                    netProfit
                )

                binding.tvReportBody.text = report
            }
        }

        binding.btnShareReport.setOnClickListener {
            val shareText = "${binding.tvReportTitle.text}\n\n${binding.tvReportBody.text}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(sendIntent, "Share P&L Report"))
        }
    }
}
