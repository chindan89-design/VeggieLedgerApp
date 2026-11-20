package com.shop.veggieledger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityReportSheetBinding

class SupplierOutstandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportSheetBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvReportTitle.text = "SUPPLIER OUTSTANDING SHEET"

        db.collection("suppliers").get().addOnSuccessListener { snap ->
            var totalPayable = 0.0
            val sb = StringBuilder()
            var count = 1
            for (doc in snap) {
                val s = doc.toObject(Supplier::class.java)
                if (s.currentBalance != 0.0) {
                    sb.append("$count. ${s.name} [${s.shortCode}] - ${s.place}\n")
                    sb.append("   Ph: ${s.phone} | Payable: Rs. %.2f\n".format(s.currentBalance))
                    sb.append("------------------------------------\n")
                    totalPayable += s.currentBalance
                    count++
                }
            }
            binding.tvReportSummary.text = "TOTAL SUPPLIER PAYABLE: Rs. %.2f".format(totalPayable)
            binding.tvReportBody.text = if (sb.isEmpty()) "No supplier balances." else sb.toString()
        }

        binding.btnShareReport.setOnClickListener {
            val shareText = "${binding.tvReportTitle.text}\n${binding.tvReportSummary.text}\n\n${binding.tvReportBody.text}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Supplier Sheet"))
        }
    }
}
