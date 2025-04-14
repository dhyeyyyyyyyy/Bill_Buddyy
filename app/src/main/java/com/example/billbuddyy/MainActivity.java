package com.example.billbuddyy;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private EditText etName, etContact, etQuantity;
    private Spinner spinnerItems;
    private Button btnAddItem, btnGenerateBill;
    private TextView tvTotalAmount, tvMonthlySales;
    private ListView lvSelectedItems;
    private List<String> selectedItems = new ArrayList<>();
    private ArrayAdapter<String> billAdapter;
    private HashMap<String, Integer> itemPrices;
    private double totalAmount = 0.0;
    private double monthlySales = 0.0;
    private String currentMonth;
    private String customerName = "", contactNumber = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etName = findViewById(R.id.etName);
        etContact = findViewById(R.id.etContact);
        spinnerItems = findViewById(R.id.spinnerItems);
        etQuantity = findViewById(R.id.etQuantity);
        btnAddItem = findViewById(R.id.btnAddItem);
        btnGenerateBill = findViewById(R.id.btnGenerateBill);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvMonthlySales = findViewById(R.id.tvMonthlySales);
        lvSelectedItems = findViewById(R.id.lvSelectedItems);

        itemPrices = new HashMap<>();
        loadStationeryItems();

        populateSpinnerWithPrices();

        billAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, selectedItems);
        lvSelectedItems.setAdapter(billAdapter);

        btnAddItem.setOnClickListener(view -> addItem());
        btnGenerateBill.setOnClickListener(view -> {
            customerName = etName.getText().toString().trim();
            contactNumber = etContact.getText().toString().trim();

            if (customerName.isEmpty() || contactNumber.isEmpty()) {
                Toast.makeText(this, "Please enter name and contact number", Toast.LENGTH_SHORT).show();
            } else if (selectedItems.isEmpty()) {
                Toast.makeText(this, "No items in the bill", Toast.LENGTH_SHORT).show();
            } else {
                generatePDF();
                updateMonthlySales();
            }
        });

        lvSelectedItems.setOnItemClickListener((parent, view, position, id) -> confirmItemRemoval(position));

        currentMonth = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(new Date());
        tvMonthlySales.setText("Total Sales (" + currentMonth + "): ₹0.00");
    }

    // Load items and their prices
    private void loadStationeryItems() {
        itemPrices.put("Pens (pack of 10)", 100);
        itemPrices.put("Notebooks (100 pages)", 150);
        itemPrices.put("Calculators (basic)", 300);
        itemPrices.put("Pencil Sharpeners (manual)", 75);
        itemPrices.put("Correction Fluid (20ml)", 35);
        itemPrices.put("Staplers (desktop)", 200);
        itemPrices.put("Staple Pins (pack of 1000)", 75);
        itemPrices.put("Paper Clips (pack of 100)", 15);
        itemPrices.put("Files (hanging)", 75);
        itemPrices.put("File Folders (pack of 10)", 150);
    }

    // Populate spinner with items
    private void populateSpinnerWithPrices() {
        List<String> itemListWithPrices = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : itemPrices.entrySet()) {
            String itemWithPrice = entry.getKey() + " - ₹" + entry.getValue();
            itemListWithPrices.add(itemWithPrice);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, itemListWithPrices);
        spinnerItems.setAdapter(adapter);
    }

    // Add selected item with quantity to the bill
    private void addItem() {
        String selectedItemWithPrice = spinnerItems.getSelectedItem().toString();
        String selectedItem = selectedItemWithPrice.split(" - ₹")[0];
        String quantityStr = etQuantity.getText().toString();

        if (!quantityStr.isEmpty()) {
            int quantity = Integer.parseInt(quantityStr);
            double price = itemPrices.get(selectedItem) * quantity;

            String itemWithDetails = String.format(Locale.ENGLISH, "%s x %d (₹%d each) = ₹%.2f",
                    selectedItem, quantity, itemPrices.get(selectedItem), price);

            selectedItems.add(itemWithDetails);
            totalAmount += price;
            tvTotalAmount.setText("Total: ₹" + String.format(Locale.ENGLISH, "%.2f", totalAmount));
            billAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Enter quantity!", Toast.LENGTH_SHORT).show();
        }
    }

    // Confirm item removal
    private void confirmItemRemoval(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Item")
                .setMessage("Are you sure you want to remove this item?")
                .setPositiveButton("Yes", (dialog, which) -> removeItem(position))
                .setNegativeButton("No", null)
                .show();
    }

    // Remove item and update the total
    private void removeItem(int position) {
        String selectedItem = selectedItems.get(position);
        String[] parts = selectedItem.split("=");

        if (parts.length >= 2) {
            String priceStr = parts[1].trim().replace("₹", "").trim();
            try {
                double price = Double.parseDouble(priceStr);
                totalAmount -= price;
                selectedItems.remove(position);
                tvTotalAmount.setText("Total: ₹" + String.format(Locale.ENGLISH, "%.2f", totalAmount));
                billAdapter.notifyDataSetChanged();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Error parsing item price", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Generate and save PDF bill
    private void generatePDF() {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(600, 800, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        paint.setTextSize(20);
        paint.setFakeBoldText(true);
        canvas.drawText("BILL BUDDY", 220, 40, paint);

        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        canvas.drawText("Date: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(new Date()), 400, 60, paint);

        // Customer Name and Contact
        paint.setTextSize(14);
        canvas.drawText("Customer Name: " + customerName, 50, 80, paint);
        canvas.drawText("Contact: " + contactNumber, 50, 100, paint);

        canvas.drawLine(50, 120, 550, 120, paint);
        int y = 140;

        // Table Header
        paint.setTextSize(14);
        paint.setFakeBoldText(true);
        canvas.drawText("Item", 50, y, paint);
        canvas.drawText("Qty", 300, y, paint);
        canvas.drawText("Price (₹)", 450, y, paint);
        y += 20;
        canvas.drawLine(50, y, 550, y, paint);
        y += 20;

        paint.setFakeBoldText(false);

        // Draw each item in the bill
        for (String item : selectedItems) {
            String[] parts = item.split("=");
            if (parts.length >= 2) {
                String itemDetails = parts[0].trim();
                String priceDetails = parts[1].trim().replace("₹", "");

                String[] itemParts = itemDetails.split("x");
                if (itemParts.length >= 2) {
                    String itemName = itemParts[0].trim();
                    String itemQty = itemParts[1].replaceAll("[^0-9]", "").trim();

                    canvas.drawText(itemName, 50, y, paint);
                    canvas.drawText(itemQty, 320, y, paint);
                    canvas.drawText("₹" + priceDetails, 450, y, paint);
                    y += 20;
                }
            }
        }

        y += 10;
        canvas.drawLine(50, y, 550, y, paint);
        y += 20;

        paint.setFakeBoldText(true);
        canvas.drawText("TOTAL:", 300, y, paint);
        canvas.drawText("₹" + String.format(Locale.ENGLISH, "%.2f", totalAmount), 450, y, paint);

        paint.setTextSize(12);
        canvas.drawText("Thank you for choosing Bill Buddy!", 180, y + 40, paint);

        document.finishPage(page);
        savePdf(document);
    }

    // Save generated PDF to storage
    private void savePdf(PdfDocument document) {
        try {
            OutputStream fos;
            Uri uri = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, "Bill_Buddy_" + System.currentTimeMillis() + ".pdf");
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);
                uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
                fos = resolver.openOutputStream(uri);
            } else {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Bill_Buddy_" + System.currentTimeMillis() + ".pdf");
                fos = new FileOutputStream(file);
            }

            if (fos != null) {
                document.writeTo(fos);
                document.close();
                fos.close();
                Toast.makeText(this, "PDF Saved", Toast.LENGTH_LONG).show();
                if (uri != null) {
                    sharePDF(uri);
                }
            }
        } catch (IOException e) {
            Log.e("PDF", "Error writing PDF", e);
            Toast.makeText(this, "Error saving PDF", Toast.LENGTH_SHORT).show();
        }
    }

    // Update monthly sales
    private void updateMonthlySales() {
        monthlySales += totalAmount;
        tvMonthlySales.setText("Total Sales (" + currentMonth + "): ₹" + String.format(Locale.ENGLISH, "%.2f", monthlySales));
        totalAmount = 0;
        selectedItems.clear();
        billAdapter.notifyDataSetChanged();
        tvTotalAmount.setText("Total: ₹0.00");
    }

    // Share PDF via Intent
    private void sharePDF(Uri pdfUri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share PDF via"));
    }
}
