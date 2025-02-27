package com.example.envelopemoney;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
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
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
    private Toolbar toolbar;

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

        // Hook up the Toolbar as your ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
                        .inflate(R.layout.item_transaction, parent, false);
            }

            TextView tvAmount = convertView.findViewById(R.id.tvTransactionAmount);
            TextView tvDetails = convertView.findViewById(R.id.tvTransactionDetails);
            ImageButton btnOptions = convertView.findViewById(R.id.btnTransactionOptions);

            // Populate data
            String amountText = String.format(Locale.getDefault(),
                    "%s - $%.2f", transaction.getEnvelopeName(), transaction.getAmount());
            tvAmount.setText(amountText);

            String details = transaction.getDate();
            if (transaction.getComment() != null && !transaction.getComment().isEmpty()) {
                details += " | " + transaction.getComment();
            }
            tvDetails.setText(details);

            // Handle Options button click
            btnOptions.setOnClickListener(v -> showTransactionOptionsDialog(transaction));

            return convertView;
        }
    }
    private void showTransactionOptionsDialog(Transaction transaction) {
        // We'll show an AlertDialog with "Edit" and "Delete" options
        new AlertDialog.Builder(this)
                .setTitle("Transaction Options")
                .setItems(new CharSequence[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        // Edit
                        showTransactionDialog(transaction);
                    } else {
                        // Delete
                        deleteTransaction(transaction);
                    }
                })
                .show();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reset) {
            showResetConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showResetConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reset_confirmation, null);

        CheckBox cbCarryOver = dialogView.findViewById(R.id.cbCarryOver);
        EditText etConfirmation = dialogView.findViewById(R.id.etConfirmation);

        builder.setView(dialogView)
                .setTitle("Confirm Monthly Reset")
                .setPositiveButton("Reset", (dialog, which) -> {
                    String confirmation = etConfirmation.getText().toString().trim();
                    if ("DEL".equalsIgnoreCase(confirmation)) {
                        performMonthlyReset(
                                cbCarryOver.isChecked()
                        );
                    } else {
                        showError("Confirmation failed. Reset canceled.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performMonthlyReset(boolean carryOver) {
        for (Envelope envelope : envelopes) {
            envelope.reset(carryOver);
        }

        PrefManager.saveEnvelopes(this, envelopes);
        updateDisplay();
        showError("Monthly reset completed successfully!");
    }
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

    private void showTransactionDialog(Transaction transactionToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction, null);

        // Get references
        Spinner spinnerEnvelope = dialogView.findViewById(R.id.spinnerEditEnvelope);
        EditText etDate = dialogView.findViewById(R.id.etEditTransactionDate);
        EditText etAmount = dialogView.findViewById(R.id.etEditTransactionAmount);
        EditText etComment = dialogView.findViewById(R.id.etEditTransactionComment);

        // Setup Spinner with envelope names
        ArrayAdapter<String> envelopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getEnvelopeNames());
        envelopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnvelope.setAdapter(envelopeAdapter);

        // Preselect the envelope matching the transaction's current envelope
        int envelopeIndex = getEnvelopeNames().indexOf(transactionToEdit.getEnvelopeName());
        if (envelopeIndex >= 0) {
            spinnerEnvelope.setSelection(envelopeIndex);
        }

        // Setup the date field – set current date and disable direct text input
        etDate.setText(transactionToEdit.getDate());
        etDate.setOnClickListener(v -> {
            // Use current date as default; you can parse etDate.getText() if needed
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        // Format date as yyyy-MM-dd (adjust as needed)
                        String selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                year, month + 1, dayOfMonth);
                        etDate.setText(selectedDate);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        // Pre-fill amount and comment fields
        etAmount.setText(String.valueOf(transactionToEdit.getAmount()));
        etComment.setText(transactionToEdit.getComment());

        // Build the dialog
        builder.setView(dialogView)
                .setTitle("Edit Transaction")
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        double oldAmount = transactionToEdit.getAmount();
                        double newAmount = Double.parseDouble(etAmount.getText().toString());
                        String newComment = etComment.getText().toString();
                        String newDate = etDate.getText().toString();
                        String newEnvelopeName = spinnerEnvelope.getSelectedItem().toString();

                        // Update envelope if it has changed
                        if (!transactionToEdit.getEnvelopeName().equals(newEnvelopeName)) {
                            Envelope oldEnvelope = findEnvelopeByName(transactionToEdit.getEnvelopeName());
                            Envelope newEnvelope = findEnvelopeByName(newEnvelopeName);
                            if (oldEnvelope != null) {
                                // Refund old amount in old envelope and remove transaction
                                oldEnvelope.getTransactions().remove(transactionToEdit);
                                oldEnvelope.setRemaining(oldEnvelope.getRemaining() + oldAmount);
                            }
                            if (newEnvelope != null) {
                                // Deduct new amount and add transaction to new envelope
                                newEnvelope.setRemaining(newEnvelope.getRemaining() - newAmount);
                                newEnvelope.getTransactions().add(transactionToEdit);
                            }
                            // You'll need a setter or directly update the field:
                            transactionToEdit.setEnvelopeName(newEnvelopeName);
                        } else {
                            // If envelope is the same, adjust remaining based on the difference
                            Envelope envelope = findEnvelopeByName(newEnvelopeName);
                            if (envelope != null) {
                                double diff = newAmount - oldAmount;
                                if (diff > envelope.getRemaining()) {
                                    showError("Insufficient funds in envelope!");
                                    return;
                                }
                                envelope.setRemaining(envelope.getRemaining() - diff);
                            }
                        }

                        // Update transaction fields
                        transactionToEdit.setAmount(newAmount);
                        transactionToEdit.setComment(newComment);
                        transactionToEdit.setDate(newDate);

                        // Persist changes and refresh UI
                        PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                        updateDisplay();
                    } catch (NumberFormatException e) {
                        showError("Invalid amount entered!");
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }


    private void deleteTransaction(Transaction transaction) {
        Envelope envelope = findEnvelopeByName(transaction.getEnvelopeName());
        if (envelope == null) {
            showError("Envelope not found!");
            return;
        }

        double oldAmount = transaction.getAmount();
        // Give back the spent amount
        envelope.setRemaining(envelope.getRemaining() + oldAmount);

        // Remove from the envelope’s transaction list
        envelope.getTransactions().remove(transaction);

        // Save and refresh
        PrefManager.saveEnvelopes(MainActivity.this, envelopes);
        updateDisplay();
    }


    private Envelope findEnvelopeByName(String envelopeName) {
        for (Envelope env : envelopes) {
            if (env.getName().equals(envelopeName)) {
                return env;
            }
        }
        return null;
    }


    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}