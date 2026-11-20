package com.shop.veggieledger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityReportSheetBinding

class CustomerOutstandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportSheetBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvReportTitle.text = "CUSTOMER OUTSTANDING SHEET"

        db.collection("customers").get().addOnSuccessListener { snap ->
            var totalDue = 0.0
            val sb = StringBuilder()
            var count = 1
            for (doc in snap) {
                val c = doc.toObject(Customer::class.java)
                if (c.currentBalance != 0.0) {
                    sb.append("$count. ${c.name} (${c.area})\n")
                    sb.append("   Ph: ${c.phone} | Due: Rs. %.2f\n".format(c.currentBalance))
                    sb.append("------------------------------------\n")
                    totalDue += c.currentBalance
                    count++
                }
            }
            binding.tvReportSummary.text = "TOTAL CUSTOMER PENDING: Rs. %.2f".format(totalDue)
            binding.tvReportBody.text = if (sb.isEmpty()) "No pending balances." else sb.toString()
        }

        binding.btnShareReport.setOnClickListener {
            val shareText = "${binding.tvReportTitle.text}\n${binding.tvReportSummary.text}\n\n${binding.tvReportBody.text}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Outstanding"))
        }
    }
}
