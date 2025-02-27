package com.example.envelopemoney;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private Spinner spinnerCategories;
    private EditText etAmount;
    private EditText etComment;
    private ListView listViewEnvelopes;
    private List<Envelope> envelopes;
    private ListView listViewTransactions;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private EnvelopeAdapter envelopeAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        spinnerCategories = findViewById(R.id.spinnerCategories);
        etAmount = findViewById(R.id.etAmount);
        etComment = findViewById(R.id.etComment);
        Button btnSubmit = findViewById(R.id.btnSubmit);
        listViewEnvelopes = findViewById(R.id.listViewEnvelopes);
        listViewTransactions = findViewById(R.id.listViewTransactions);

        // Load envelopes
        envelopes = PrefManager.getEnvelopes(this);

        // Initialize adapters
        transactionAdapter = new TransactionAdapter(this, allTransactions);
        listViewTransactions.setAdapter(transactionAdapter);
        envelopeAdapter = new EnvelopeAdapter(this, envelopes);
        listViewEnvelopes.setAdapter(envelopeAdapter);

        // Setup Spinner
        spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                getEnvelopeNames());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategories.setAdapter(spinnerAdapter);

        // Setup FAB
        FloatingActionButton fab = findViewById(R.id.fabAddEnvelope);
        fab.setOnClickListener(v -> showEnvelopeDialog(null));

        // Submit button handler
        btnSubmit.setOnClickListener(v -> handleTransactionSubmission());

        updateTransactionHistory();
    }

    private void handleTransactionSubmission() {
        try {
            double amount = Double.parseDouble(etAmount.getText().toString());
            String comment = etComment.getText().toString();
            int position = spinnerCategories.getSelectedItemPosition();
            Envelope selected = envelopes.get(position);

            if (amount > selected.getRemaining()) {
                showError("Amount exceeds remaining funds!");
                return;
            }

            // Create transaction
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String date = sdf.format(new Date());
            Transaction transaction = new Transaction(
                    selected.getName(),
                    amount,
                    date,
                    comment
            );

            // Update envelope
            selected.addTransaction(transaction);
            selected.setRemaining(selected.getRemaining() - amount);

            // Clear inputs
            etAmount.setText("");
            etComment.setText("");
            etComment.clearFocus();

            // Persist changes
            PrefManager.saveEnvelopes(this, envelopes);
            updateDisplay();
        } catch (NumberFormatException e) {
            showError("Invalid amount entered!");
        }
    }

    private void updateTransactionHistory() {
        allTransactions.clear();

        // Filter transactions from selected envelopes
        for (Envelope envelope : envelopes) {
            if (envelope.isSelected()) {
                allTransactions.addAll(envelope.getTransactions());
            }
        }

        // Sort transactions
        Collections.sort(allTransactions, (t1, t2) -> {
            String d1 = t1.getDate() != null ? t1.getDate() : "";
            String d2 = t2.getDate() != null ? t2.getDate() : "";
            return d2.compareTo(d1);
        });

        // Add placeholder if empty
        if (allTransactions.isEmpty()) {
            allTransactions.add(new Transaction(
                    "No transactions yet",
                    0,
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()),
                    "Start by adding your first transaction"
            ));
        }

        transactionAdapter.notifyDataSetChanged();
    }

    private void updateDisplay() {
        envelopeAdapter.notifyDataSetChanged();
        updateTransactionHistory();

        // Update spinner
        spinnerAdapter.clear();
        spinnerAdapter.addAll(getEnvelopeNames());
        spinnerAdapter.notifyDataSetChanged();
    }

    // Adapter classes and helper methods below
    private class TransactionAdapter extends ArrayAdapter<Transaction> {
        public TransactionAdapter(Context context, List<Transaction> transactions) {
            super(context, 0, transactions);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Transaction transaction = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            String amountText = String.format(Locale.getDefault(),
                    "%s - $%.2f", transaction.getEnvelopeName(), transaction.getAmount());
            String details = transaction.getDate() +
                    (!transaction.getComment().isEmpty() ? " | " + transaction.getComment() : "");

            text1.setText(amountText);
            text2.setText(details);

            return convertView;
        }
    }

    private class EnvelopeAdapter extends ArrayAdapter<Envelope> {
        public EnvelopeAdapter(Context context, List<Envelope> envelopes) {
            super(context, R.layout.item_envelope, envelopes);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Envelope envelope = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_envelope, parent, false);
            }

            CheckBox cbSelect = convertView.findViewById(R.id.cbSelect);
            TextView tvName = convertView.findViewById(R.id.tvName);
            TextView tvAmounts = convertView.findViewById(R.id.tvAmounts);
            ImageButton btnOptions = convertView.findViewById(R.id.btnOptions);

            // Set values
            tvName.setText(envelope.getName());
            tvAmounts.setText(String.format(Locale.getDefault(),
                    "Limit: $%.2f | Remaining: $%.2f",
                    envelope.getLimit(),
                    envelope.getRemaining()));

            // Checkbox state
            cbSelect.setChecked(envelope.isSelected());

            // Checkbox listener
            cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                envelope.setSelected(isChecked);
                updateTransactionHistory();
                PrefManager.saveEnvelopes(MainActivity.this, envelopes);
            });

            // Options button listener
            btnOptions.setOnClickListener(v -> showEnvelopeOptionsDialog(position));

            return convertView;
        }
    }

    // Rest of helper methods (showEnvelopeOptionsDialog, showEnvelopeDialog,
    // getEnvelopeNames, showError) remain identical to your original implementation


    private void showEnvelopeOptionsDialog(int position) {
        Envelope envelope = envelopes.get(position);
        new AlertDialog.Builder(this)
                .setTitle("Envelope Options")
                .setItems(new CharSequence[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        showEnvelopeDialog(envelope);
                    } else {
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage("Delete this envelope?")
                                .setPositiveButton("Delete", (d, w) -> {
                                    envelopes.remove(position);
                                    PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                                    updateDisplay();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void showEnvelopeDialog(@Nullable Envelope envelopeToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_envelope, null);
        EditText etName = dialogView.findViewById(R.id.etEnvelopeName);
        EditText etLimit = dialogView.findViewById(R.id.etEnvelopeLimit);

        if (envelopeToEdit != null) {
            etName.setText(envelopeToEdit.getName());
            etLimit.setText(String.valueOf(envelopeToEdit.getLimit()));
        }

        builder.setView(dialogView)
                .setTitle(envelopeToEdit == null ? "New Envelope" : "Edit Envelope")
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString();
                    String limitStr = etLimit.getText().toString();

                    if (name.isEmpty() || limitStr.isEmpty()) {
                        showError("Please fill all fields");
                        return;
                    }

                    double limit = Double.parseDouble(limitStr);

                    if (envelopeToEdit == null) {
                        // Create new
                        envelopes.add(new Envelope(name, limit));
                    } else {
                        // Update existing
                        double oldLimit = envelopeToEdit.getLimit();
                        double remaining = envelopeToEdit.getRemaining();
                        double spent = oldLimit - remaining;

                        envelopeToEdit.setName(name);
                        envelopeToEdit.setLimit(limit);
                        envelopeToEdit.setRemaining(Math.max(limit - spent, 0));
                    }

                    PrefManager.saveEnvelopes(this, envelopes);
                    updateDisplay();
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }

    private List<String> getEnvelopeNames() {
        List<String> names = new ArrayList<>();
        for (Envelope e : envelopes) {
            names.add(e.getName());
        }
        return names;
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}