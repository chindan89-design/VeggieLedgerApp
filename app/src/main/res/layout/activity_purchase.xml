<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#F5F7FA"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="PURCHASE BILL ENTRY FORM"
            android:textSize="18sp"
            android:textStyle="bold"
            android:textColor="#1565C0"
            android:gravity="center"
            android:layout_marginBottom="12dp" />

        <!-- Row 1: Date Picker (First) & Item Dropdown (Second) -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:weightSum="2"
            android:layout_marginBottom="10dp">

            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginEnd="6dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="1. Purchase Date *" android:textSize="11sp" android:textStyle="bold" android:textColor="#1565C0" />
                <EditText
                    android:id="@+id/etPurchaseDate"
                    android:layout_width="match_parent"
                    android:layout_height="48dp"
                    android:focusable="false"
                    android:clickable="true"
                    android:textColor="#000000"
                    android:textStyle="bold"
                    android:background="@android:drawable/edit_text"
                    android:padding="8dp"
                    android:textSize="14sp" />
            </LinearLayout>

            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="6dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="2. Select Item *" android:textSize="11sp" android:textStyle="bold" android:textColor="#1565C0" />
                <Spinner
                    android:id="@+id/spItem"
                    android:layout_width="match_parent"
                    android:layout_height="48dp"
                    android:spinnerMode="dialog"
                    android:background="@android:drawable/btn_dropdown" />
            </LinearLayout>
        </LinearLayout>

        <!-- Row 2: Filtered Searchable Supplier & Payment Mode -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:weightSum="2"
            android:layout_marginBottom="10dp">

            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginEnd="6dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="3. Supplier (Item Filtered) *" android:textSize="11sp" android:textStyle="bold" android:textColor="#1565C0" />
                <AutoCompleteTextView
                    android:id="@+id/actvSupplier"
                    android:layout_width="match_parent"
                    android:layout_height="48dp"
                    android:hint="Type supplier..."
                    android:completionThreshold="1"
                    android:textColor="#000000"
                    android:background="@android:drawable/edit_text"
                    android:padding="8dp"
                    android:textSize="14sp" />
            </LinearLayout>

            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="6dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Payment Mode *" android:textSize="11sp" android:textStyle="bold" android:textColor="#333333" />
                <Spinner
                    android:id="@+id/spPayMode"
                    android:layout_width="match_parent"
                    android:layout_height="48dp"
                    android:spinnerMode="dialog"
                    android:background="@android:drawable/btn_dropdown" />
            </LinearLayout>
        </LinearLayout>

        <!-- Row 3: Auto Bill No & Stock Lot ID -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:weightSum="2"
            android:layout_marginBottom="12dp">

            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginEnd="6dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Auto Bill No" android:textSize="11sp" android:textStyle="bold" android:textColor="#333333" />
                <EditText android:id="@+id/etBillNo" android:layout_width="match_parent" android:layout_height="48dp" android:hint="Auto Bill No" android:textColor="#000000" android:background="@android:drawable/edit_text" android:padding="8dp" android:textSize="14sp" />
            </LinearLayout>

            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="6dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Stock Lot ID" android:textSize="11sp" android:textStyle="bold" android:textColor="#333333" />
                <TextView android:id="@+id/tvGeneratedBatchId" android:layout_width="match_parent" android:layout_height="48dp" android:text="Auto Lot ID" android:gravity="center_vertical" android:background="#E3F2FD" android:textColor="#1565C0" android:textStyle="bold" android:padding="8dp" />
            </LinearLayout>
        </LinearLayout>

        <!-- Item Entry Card -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:background="#E3F2FD"
            android:padding="10dp"
            android:layout_marginBottom="12dp">

            <!-- Bags & Bag Charge Side-by-Side (Onion-க்கு மட்டும்) -->
            <LinearLayout
                android:id="@+id/layoutOnionFields"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:weightSum="2"
                android:layout_marginBottom="8dp">

                <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginEnd="6dp">
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="No. of Bags" android:textSize="12sp" android:textStyle="bold" android:textColor="#333333" />
                    <EditText android:id="@+id/etBags" android:layout_width="match_parent" android:layout_height="48dp" android:hint="0" android:textColor="#000000" android:inputType="number" android:background="@android:drawable/edit_text" android:padding="8dp" />
                </LinearLayout>

                <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="6dp">
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Bag Charge / Bag (Rs)" android:textSize="12sp" android:textStyle="bold" android:textColor="#333333" />
                    <EditText android:id="@+id/etBagRate" android:layout_width="match_parent" android:layout_height="48dp" android:text="10" android:textColor="#000000" android:inputType="numberDecimal" android:background="@android:drawable/edit_text" android:padding="8dp" />
                </LinearLayout>
            </LinearLayout>

            <!-- Purchase Kgs & Rate / Kg Side-by-Side -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:weightSum="2"
                android:layout_marginBottom="8dp">

                <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginEnd="6dp">
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Purchase Kgs *" android:textSize="12sp" android:textStyle="bold" android:textColor="#333333" />
                    <EditText android:id="@+id/etNetKgs" android:layout_width="match_parent" android:layout_height="48dp" android:hint="0.0 Kgs" android:textColor="#000000" android:inputType="numberDecimal" android:background="@android:drawable/edit_text" android:padding="8dp" />
                </LinearLayout>

                <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="6dp">
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Rate / Kg (Rs) *" android:textSize="12sp" android:textStyle="bold" android:textColor="#333333" />
                    <EditText android:id="@+id/etRatePerKg" android:layout_width="match_parent" android:layout_height="48dp" android:hint="0.00" android:textColor="#000000" android:inputType="numberDecimal" android:background="@android:drawable/edit_text" android:padding="8dp" />
                </LinearLayout>
            </LinearLayout>

            <!-- Coconut Section: Kudumi (3% Auto), Thadukki & Net Weight Display -->
            <LinearLayout
                android:id="@+id/layoutCoconutFields"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone"
                android:layout_marginBottom="10dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:weightSum="2"
                    android:layout_marginBottom="8dp">

                    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginEnd="6dp">
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Kudumi (3% Auto)" android:textSize="12sp" android:textStyle="bold" android:textColor="#D84315" />
                        <EditText android:id="@+id/etKudumi" android:layout_width="match_parent" android:layout_height="48dp" android:hint="0" android:textColor="#000000" android:inputType="numberDecimal" android:background="@android:drawable/edit_text" android:padding="8dp" />
                    </LinearLayout>

                    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="6dp">
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Thadukki" android:textSize="12sp" android:textStyle="bold" android:textColor="#D84315" />
                        <EditText android:id="@+id/etThadukki" android:layout_width="match_parent" android:layout_height="48dp" android:hint="0" android:textColor="#000000" android:inputType="numberDecimal" android:background="@android:drawable/edit_text" android:padding="8dp" />
                    </LinearLayout>
                </LinearLayout>

                <TextView
                    android:id="@+id/tvCoconutNetWeight"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Net Weight: 0.00 Kgs"
                    android:background="#FFF9C4"
                    android:textColor="#1B5E20"
                    android:textStyle="bold"
                    android:textSize="14sp"
                    android:padding="10dp" />
            </LinearLayout>

            <Button android:id="@+id/btnAddItem" android:layout_width="match_parent" android:layout_height="46dp" android:text="+ ADD ITEM TO PURCHASE BILL" android:backgroundTint="#1976D2" android:textColor="#FFFFFF" />
        </LinearLayout>

        <TextView android:id="@+id/tvAddedItemsList" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="No items added yet." android:background="#FFFFFF" android:textColor="#000000" android:padding="10dp" android:textSize="13sp" android:layout_marginBottom="10dp" />
        <TextView android:id="@+id/tvGrandTotal" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Grand Total: Rs. 0.00" android:textSize="18sp" android:textStyle="bold" android:textColor="#1565C0" android:layout_marginBottom="12dp" />
        <Button android:id="@+id/btnSavePurchase" android:layout_width="match_parent" android:layout_height="52dp" android:text="SUBMIT PURCHASE &amp; CREATE STOCK" android:backgroundTint="#1565C0" android:textColor="#FFFFFF" />

    </LinearLayout>
</ScrollView>
