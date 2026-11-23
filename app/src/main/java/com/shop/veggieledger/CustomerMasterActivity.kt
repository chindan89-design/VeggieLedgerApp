package com.shop.veggieledger

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class CustomerMasterActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etName: EditText
    private lateinit var etContact: EditText
    private lateinit var etArea: EditText
    private lateinit var etOpBal: EditText
    private lateinit var spGroup: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnBulkImport: Button
    private lateinit var etSearch: EditText
    private lateinit var llCustomerList: LinearLayout

    private var editingCustomerId: String = ""
    private val allCustomersJson = JSONArray()
    private val groups = arrayListOf("Group A", "Group B", "Group C", "Group D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Customer Master"

        buildUi()
        loadCustomersFromDb()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#F5F7FA"))
        scroll.setPadding(24, 24, 24, 24)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        scroll.addView(root)

        val title = TextView(this)
        title.text = "CUSTOMER MASTER & BULK IMPORT"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#1B5E20"))
        title.gravity = Gravity.CENTER
        title.setPadding(0, 0, 0, 14)
        root.addView(title)

        // One-Click Import Button for 22-11-2026 Sheet (181 Customers)
        btnBulkImport = Button(this)
        btnBulkImport.text = "IMPORT 181 CUSTOMERS (22-11-2026 LEDGER)"
        btnBulkImport.setBackgroundColor(Color.parseColor("#E65100"))
        btnBulkImport.setTextColor(Color.WHITE)
        btnBulkImport.setTypeface(null, Typeface.BOLD)
        val impLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        impLp.setMargins(0, 0, 0, 16)
        btnBulkImport.layoutParams = impLp
        btnBulkImport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Import 181 Customers")
                .setMessage("Import all 181 customers with Phone No & Closing Balance (as Opening Balance) from 22-11-2026 sheet into Firebase?")
                .setPositiveButton("IMPORT NOW") { _, _ -> runBulkImport() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        root.addView(btnBulkImport)

        // Form Card
        val formCard = LinearLayout(this)
        formCard.orientation = LinearLayout.VERTICAL
        formCard.setBackgroundColor(Color.WHITE)
        formCard.setPadding(20, 20, 20, 20)
        val formLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        formLp.setMargins(0, 0, 0, 16)
        formCard.layoutParams = formLp

        etName = EditText(this)
        etName.hint = "Customer Name *"
        etName.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etName)

        etContact = EditText(this)
        etContact.hint = "Phone / Contact No"
        etContact.inputType = android.text.InputType.TYPE_CLASS_PHONE
        etContact.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etContact)

        etArea = EditText(this)
        etArea.hint = "Area / Market Block"
        etArea.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etArea)

        spGroup = Spinner(this)
        val grpAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groups)
        grpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spGroup.adapter = grpAdapter
        spGroup.setBackgroundResource(android.R.drawable.btn_dropdown)
        formCard.addView(spGroup)

        etOpBal = EditText(this)
        etOpBal.hint = "Opening / Pending Balance (Rs)"
        etOpBal.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        etOpBal.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etOpBal)

        btnSave = Button(this)
        btnSave.text = "SAVE CUSTOMER"
        btnSave.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnSave.setTextColor(Color.WHITE)
        btnSave.setTypeface(null, Typeface.BOLD)
        btnSave.setOnClickListener { saveOrUpdateCustomer() }
        formCard.addView(btnSave)

        root.addView(formCard)

        // Search Box
        etSearch = EditText(this)
        etSearch.hint = "Search saved customers..."
        etSearch.setBackgroundResource(android.R.drawable.edit_text)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderCustomerList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(etSearch)

        llCustomerList = LinearLayout(this)
        llCustomerList.orientation = LinearLayout.VERTICAL
        root.addView(llCustomerList)

        setContentView(scroll)
    }

    private fun saveOrUpdateCustomer() {
        val name = etName.text.toString().trim()
        val contact = etContact.text.toString().trim()
        val area = etArea.text.toString().trim()
        val grp = spGroup.selectedItem?.toString() ?: "Group A"
        val opBal = etOpBal.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            Toast.makeText(this, "Enter Customer Name", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        if (editingCustomerId.isNotEmpty()) {
            val upd = hashMapOf<String, Any>(
                "name" to name,
                "contact" to contact,
                "area" to area,
                "group" to grp,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal
            )
            db.collection("customers").document(editingCustomerId).update(upd)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Updated!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadCustomersFromDb()
                }
                .addOnFailureListener { btnSave.isEnabled = true }
        } else {
            val docId = db.collection("customers").document().id
            val data = hashMapOf<String, Any>(
                "id" to docId,
                "name" to name,
                "contact" to contact,
                "area" to area,
                "group" to grp,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal
            )
            db.collection("customers").document(docId).set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Saved!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadCustomersFromDb()
                }
                .addOnFailureListener { btnSave.isEnabled = true }
        }
    }

    private fun loadCustomersFromDb() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            while (allCustomersJson.length() > 0) allCustomersJson.remove(0)
            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getString("name") ?: "").lowercase(Locale.getDefault())
            }
            for (doc in sortedDocs) {
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", doc.getString("name") ?: "")
                obj.put("contact", doc.getString("contact") ?: "")
                obj.put("area", doc.getString("area") ?: "")
                obj.put("group", doc.getString("group") ?: "Group A")
                obj.put("opbal", doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0)
                obj.put("bal", doc.getDouble("currentBalance") ?: 0.0)
                allCustomersJson.put(obj)
            }
            renderCustomerList(etSearch.text.toString())
        }
    }

    private fun renderCustomerList(query: String) {
        llCustomerList.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())

        for (i in 0 until allCustomersJson.length()) {
            val obj = allCustomersJson.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val contact = obj.getString("contact")
            val area = obj.getString("area")
            val grp = obj.getString("group")
            val op = obj.getDouble("opbal")
            val bal = obj.getDouble("bal")

            if (q.isNotEmpty() && !name.lowercase(Locale.getDefault()).contains(q) && !contact.contains(q)) {
                continue
            }

            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setBackgroundColor(Color.WHITE)
            card.setPadding(18, 14, 18, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 8)
            card.layoutParams = lp

            val phText = if (contact.isNotEmpty()) " | Ph: $contact" else ""
            val info = TextView(this)
            info.text = String.format(Locale.getDefault(), "%s%s\nBal: Rs. %.2f", name, phText, bal)
            info.setTextColor(Color.BLACK)
            info.setTypeface(null, Typeface.BOLD)
            info.textSize = 13f
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val editBtn = TextView(this)
            editBtn.text = "EDIT"
            editBtn.setTextColor(Color.WHITE)
            editBtn.setBackgroundColor(Color.parseColor("#1565C0"))
            editBtn.setTypeface(null, Typeface.BOLD)
            editBtn.setPadding(22, 12, 22, 12)
            editBtn.setOnClickListener {
                editingCustomerId = id
                etName.setText(name)
                etContact.setText(contact)
                etArea.setText(area)
                etOpBal.setText(String.format(Locale.getDefault(), "%.2f", op))
                val gIdx = groups.indexOf(grp)
                if (gIdx >= 0) spGroup.setSelection(gIdx)
                btnSave.text = "UPDATE CUSTOMER"
            }

            card.addView(info)
            card.addView(editBtn)
            llCustomerList.addView(card)
        }
    }

    private fun resetForm() {
        editingCustomerId = ""
        etName.setText("")
        etContact.setText("")
        etArea.setText("")
        etOpBal.setText("")
        spGroup.setSelection(0)
        btnSave.text = "SAVE CUSTOMER"
        btnSave.isEnabled = true
    }

    private fun runBulkImport() {
        btnBulkImport.isEnabled = false
        btnBulkImport.text = "IMPORTING 181 CUSTOMERS..."

        // Exact data extracted from 22-11-2026 Ledger (Name | Phone | Closing Balance)
        val rawRecords = arrayListOf(
            "A-110 PANDIAN(COCONUT)|8778830000|56253.0",
            "AH SENTHIL|8778830000|60880.0",
            "A60-Umashankar(Coconut)|9884330000|6040.0",
            "AM (Coconut)||2535.0",
            "Ambathur Pandi||6720.0",
            "Annapoorna||40700.0",
            "AMMAN||7280.0",
            "Anbu-Ponneri (Coconut)|9087420000|20650.0",
            "AnjapparHotel(Coconut)|9941980000|94350.0",
            "AnjapparHotel(Coconut)OLD|9047680000|14790.0",
            "Apollo(Coconut)|9493880000|0.0",
            "Arokiyaraj(coconut)|9176580000|65885.0",
            "ArulRaj||336.0",
            "ArulTraders(Coconut)|9952040000|174985.0",
            "Arumugam(Coconut)||2720.0",
            "Arun-Mylapore(Coconut)|9566110000|33385.0",
            "ATE(Coconut)||756.0",
            "ATE-Kannan(Coconut)||58.0",
            "ATR(Coconut)|9944330000|7684.0",
            "AN||0.0",
            "BALAJI HOTEL||2750.0",
            "Banu||0.0",
            "B105 Suresh||3270.0",
            "BAFL|9003580000|2371400.0",
            "Bakiyalakshmi(Coconut)|9444360000|34730.0",
            "BAKIYALAKSHMI-2|9444360000|27520.0",
            "Bala(Coconut)|9176720000|136809.0",
            "Baskaran(Coconut)|7299610000|90010.0",
            "Boyammal(Coconut)||1390.0",
            "C-88 JOTHI|8925590000|2075.0",
            "Chindan(Coconut)|6383630000|410.0",
            "Chellammal||0.0",
            "D-1 SAKKARAI|9840180000|580055.0",
            "D-102 Kannan|9840770000|980.0",
            "D-104 KIRAN|9347770000|80900.0",
            "D-75 Muthu(Coconut)|9884710000|126938.0",
            "D-99||83000.0",
            "Devi(Coconut)|9094740000|0.0",
            "Dinakaran(Coconut)|8838070000|18620.0",
            "Deva||140580.0",
            "Dinesh.V.Canteen||2460.0",
            "Dhanalakshmi||550.0",
            "Dharani||0.0",
            "E-36 Samy|7904550000|74250.0",
            "E88/5 KUMAR||10249.0",
            "ENNORE-MURALI(COCONUT)|9884210000|500.0",
            "Eshwari(Coconut)||150.0",
            "Ethiraj(Coconut)|9444420000|750.0",
            "FreshGreen(Coconut)|9840660000|0.0",
            "FreshRoots|9444710000|393651.0",
            "G-127 Muthukumar(Coconut)|9282180000|52800.0",
            "Ganesh-Sudhakar(Coconut)|6369420000|115436.0",
            "Ganesh Thambaram|6369420000|0.0",
            "GMR (COCONUT)|9840110000|2107230.0",
            "Gopal_Parrys(coconut)|9884310000|124740.0",
            "GuindySuresh(Coconut)|8526340000|8270.0",
            "Gurumoorthi(Coconut)||385.0",
            "Gunasekar||10000.0",
            "Hari(Coconut)|9840300000|6460.0",
            "Hari Prasad||35670.0",
            "HSB(Coconut)||1480920.0",
            "H78 Siva||1820.0",
            "Indian||0.0",
            "ISS(Coconut)||80.0",
            "J16||40800.0",
            "J-59|6380440000|282976.0",
            "JJ||0.0",
            "JKV||0.0",
            "Jagan E-88|9677030000|263607.0",
            "Jagan Poorvika|9677030000|500300.0",
            "Jawahar(coconut)|9952320000|8000.0",
            "Jaya(Coconut)||2070.0",
            "Jayakodi(Coconut)|9444530000|1000.0",
            "Jayalakshmi(Coconut)||10.0",
            "Jeeva||550.0",
            "JohnPeter(Coconut)|9840810000|762800.0",
            "JothiOmSakthi(Coconut)|9841130000|3210.0",
            "K Ganaesan (Coconut)|9962070000|4884.0",
            "kalpana||825.0",
            "K-66(Coconut)||597059.0",
            "K-85 SHANKAR|9940360000|4000.0",
            "Kamatchi(Coconut)|7200440000|3920.0",
            "KannanATE(Coconut)||150.0",
            "Kanniyappan(Coconut)||38050.0",
            "KOLATHUR SEKAR (COCONUT)|8939170000|16710.0",
            "KPFruits(Coconut)|9884050000|98060.0",
            "KRB||44100.0",
            "KS(Coconut)||315.0",
            "Kumar(Coconut)|7358350000|66442.0",
            "L-23 SRI MURUGAN TRADERS|7338780000|111105.0",
            "Lakshmanan(coconut)||700.0",
            "Lakshmi(Coconut)||1160.0",
            "LemonSathish(Coconut)||2500.0",
            "Mani Thambaram(Coconut)||429.0",
            "Mani(Coconut)||1040.0",
            "ManoCocoProducts|7550110000|781115.0",
            "MKS||0.0",
            "Mariappan(Coconut)||560.0",
            "Meenakshmi Sundharam(Coconut)|9790710000|175917.0",
            "Micheal Amma(COCONUT)|9840330000|0.0",
            "Nalapakkam||1890.0",
            "Mohan||5820.0",
            "Moorthy(Coconut)|9003290000|3800.0",
            "Mugalivakkam|9345850000|1000.0",
            "MU Lakshmi||0.0",
            "MURUGAN(LOADMAN)||130.0",
            "N 25 BABU||18510.0",
            "Pandi-ATE(Coconut)||136.0",
            "Padi Balaji||0.0",
            "Paramasivam(Coconut)|7904400000|609.0",
            "Perumal(Coconut)||1920.0",
            "Ponnusamy(coconut)|9865690000|22330.0",
            "PR & CO|9942150000|95970.0",
            "PKS||560.0",
            "PVR||104000.0",
            "Prakash(Coconut)||1339.0",
            "Prasad||1520.0",
            "Quarter Govindhan||0.0",
            "RKGK||7150.0",
            "RAGHUL(COCONUT)|9500050000|647681.0",
            "Rahim(Coconut)||5665.0",
            "Rajesh(Coconut)||8330.0",
            "Raja||60.0",
            "Ramajayam-ADM(Coconut)|8883820000|482.0",
            "Ramu K|6374540000|0.0",
            "Ravi RSN||0.0",
            "Rangan||2750.0",
            "RaviAyyapakkam(Coconut)|7200750000|5790.0",
            "Roopan|7200750000|0.0",
            "RSN(Coconut)||22750.0",
            "S.SELVAM||5500.0",
            "Seenu||1910.0",
            "Sadhik(Coconut)||3750.0",
            "Sampath||1100.0",
            "Sai Catering (Coconut)||1680.0",
            "Saravana SPS||122850.0",
            "SaravanaStores|7823990000|46254.0",
            "Sathya-Coconut||1120.0",
            "Selam RR(Coconut)|9176620000|1900.0",
            "Selvam(Coconut)|9444310000|4680.0",
            "SheikBhai(Coconut)||30.0",
            "SivaCini(Coconut)||1000.0",
            "SK(Coconut)|8778190000|3492.0",
            "SKM M90(Coconut)|6382330000|20560.0",
            "SomaSubbaiah(Coconut)|9003110000|201958.0",
            "Sooriya Hostel||11000.0",
            "SPS Pandi(Coconut)|9444510000|182985.0",
            "SPS-2|9444510000|19160.0",
            "SR & co|9962760000|56950.0",
            "SR(COCONUT)||1100.0",
            "SriBalajiHotel||68064.0",
            "SRT MAHESH(COCONUT)||69840.0",
            "SRT(Coconut)|6382480000|11250.0",
            "SS Pandi (Coconut)|9159860000|6904.0",
            "SSS(Coconut)|9884250000|707.0",
            "STP SHANKAR(COCONUT)|9003130000|1043175.0",
            "Sudha(coconut)||4350.0",
            "Sudhakar(Coconut)|9176430000|1606.0",
            "Sumathi(Coconut)||30.0",
            "Sumathi_C||405.0",
            "SV&Co|9094480000|0.0",
            "S2B|9094480000|5400.0",
            "Thangavel(Coconut)|9444010000|173035.0",
            "Tamil|9444010000|0.0",
            "Thirumazhasai(Coconut)|9944280000|3560.0",
            "Thiruvasagam(Coconut)||206.0",
            "TVS Karthik(Coconut)|9940070000|345064.0",
            "Udhayakumar(COCONUT)||360.0",
            "VADAMALAI(COCONUT)||138294.0",
            "Venus(Coconut)|9042660000|118752.0",
            "Vijaya(Coconut)||680.0",
            "vijayakumar-G Block||148845.0",
            "VIJI(COCONUT)||103630.0",
            "VIJI(MKS)||109300.0",
            "Vimala(coconut)||12927.0",
            "Vinayagam(Coconut)|9791780000|27545.0",
            "Vinoth(coconut)||2800.0",
            "VPT(Coconut)||258535.0",
            "WillRaman(Coconut)||4950.0",
            "Winner(Coconut)|7200480000|50005.0",
            "YOUVARAJ(SPS)||138469.0"
        )

        // Fetch existing customers first so duplicates are updated cleanly instead of duplicated
        db.collection("customers").get().addOnSuccessListener { existingSnap ->
            val existingMap = HashMap<String, String>()
            for (doc in existingSnap) {
                val n = (doc.getString("name") ?: "").trim().lowercase(Locale.getDefault())
                if (n.isNotEmpty()) existingMap[n] = doc.id
            }

            val batch = db.batch()
            for (line in rawRecords) {
                val parts = line.split("|")
                val cName = parts[0].trim()
                val cPhone = parts[1].trim()
                val cBal = parts[2].toDoubleOrNull() ?: 0.0

                val key = cName.lowercase(Locale.getDefault())
                val docId = existingMap[key] ?: db.collection("customers").document().id
                val docRef = db.collection("customers").document(docId)

                val data = hashMapOf<String, Any>(
                    "id" to docId,
                    "name" to cName,
                    "contact" to cPhone,
                    "group" to "Group A",
                    "opbal" to cBal,
                    "openingBalance" to cBal,
                    "currentBalance" to cBal
                )
                batch.set(docRef, data)
            }

            batch.commit()
                .addOnSuccessListener {
                    btnBulkImport.isEnabled = true
                    btnBulkImport.text = "IMPORT COMPLETED (181 CUSTOMERS)"
                    Toast.makeText(this, "All 181 Customers Imported Successfully!", Toast.LENGTH_LONG).show()
                    loadCustomersFromDb()
                }
                .addOnFailureListener {
                    btnBulkImport.isEnabled = true
                    btnBulkImport.text = "IMPORT 181 CUSTOMERS (22-11-2026 LEDGER)"
                    Toast.makeText(this, "Import Failed! Check internet.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
