package com.example.envelopemoney;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
    private ArrayAdapter<Envelope> listAdapter;
    private ListView listViewTransactions;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        spinnerCategories = findViewById(R.id.spinnerCategories);
        etAmount = findViewById(R.id.etAmount);
        etComment = findViewById(R.id.etComment);
        listViewEnvelopes = findViewById(R.id.listViewEnvelopes);
        Button btnSubmit = findViewById(R.id.btnSubmit);

        // Initialize transaction list view
        listViewTransactions = findViewById(R.id.listViewTransactions);
        transactionAdapter = new TransactionAdapter(this, allTransactions);
        listViewTransactions.setAdapter(transactionAdapter);

        // Load envelopes
        envelopes = PrefManager.getEnvelopes(this);

        // Setup Spinner
        spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                getEnvelopeNames());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategories.setAdapter(spinnerAdapter);

        // Setup ListView
        listAdapter = new ArrayAdapter<Envelope>(this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                envelopes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);

                Envelope envelope = envelopes.get(position);
                text1.setText(envelope.getName());
                text2.setText(String.format(Locale.getDefault(),
                        "Limit: $%.2f | Remaining: $%.2f",
                        envelope.getLimit(),
                        envelope.getRemaining()));
                return view;
            }
        };
        listViewEnvelopes.setAdapter(listAdapter);

        btnSubmit.setOnClickListener(v -> {
            try {
                double amount = Double.parseDouble(etAmount.getText().toString());
                String comment = etComment.getText().toString();
                int position = spinnerCategories.getSelectedItemPosition();
                Envelope selected = envelopes.get(position);

                if (amount > selected.getRemaining()) {
                    showError("Amount exceeds remaining funds!");
                    return;
                }

                selected.setRemaining(selected.getRemaining() - amount);
                PrefManager.saveEnvelopes(this, envelopes);
                updateDisplay();
                etAmount.setText("");
                // Create transaction
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String date = sdf.format(new Date());
                Transaction transaction = new Transaction(
                        selected.getName(),
                        amount,
                        date,
                        comment
                );

                selected.addTransaction(transaction);
                updateTransactionHistory();
                PrefManager.saveEnvelopes(this, envelopes);
                updateDisplay();
                etAmount.setText("");
                etComment.setText("");
                etComment.clearFocus();
            } catch (NumberFormatException e) {
                showError("Invalid amount entered!");
            }
        });
        FloatingActionButton fab = findViewById(R.id.fabAddEnvelope);
        fab.setOnClickListener(v -> showEnvelopeDialog(null));

        listViewEnvelopes.setOnItemLongClickListener((parent, view, position, id) -> {
            showEnvelopeOptionsDialog(position);
            return true;
        });

    }
    private void updateTransactionHistory() {
        allTransactions.clear();

        // Safely collect transactions
        for (Envelope envelope : envelopes) {
            List<Transaction> envelopeTransactions = envelope.getTransactions();
            if (envelopeTransactions != null) {
                allTransactions.addAll(envelopeTransactions);
            }
        }

        // Safe sorting with null checks
        Collections.sort(allTransactions, (t1, t2) -> {
            String d1 = t1.getDate() != null ? t1.getDate() : "";
            String d2 = t2.getDate() != null ? t2.getDate() : "";
            return d2.compareTo(d1); // Descending order
        });

        // Add default message if empty
        if (allTransactions.isEmpty()) {
            Transaction placeholder = new Transaction(
                    "No transactions yet",
                    0,
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()),
                    "Start by adding your first transaction"
            );
            allTransactions.add(placeholder);
        }

        transactionAdapter.notifyDataSetChanged();
    }

    private void updateDisplay() {
        // Update envelopes list
        listAdapter.notifyDataSetChanged();

        // Update transactions list
        updateTransactionHistory();

        // Update spinner
        spinnerAdapter.clear();
        spinnerAdapter.addAll(getEnvelopeNames());
        spinnerAdapter.notifyDataSetChanged();
    }
    // Add this new adapter class
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
            String detailsText = transaction.getDate();

            // Add comment if exists
            if (!transaction.getComment().isEmpty()) {
                detailsText += " - " + transaction.getComment();
            }

            text1.setText(amountText);
            text2.setText(detailsText);

            return convertView;
        }
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

    private void showEnvelopeOptionsDialog(int position) {
        Envelope envelope = envelopes.get(position);
        new AlertDialog.Builder(this)
                .setTitle(envelope.getName())
                .setItems(new CharSequence[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        // Edit
                        showEnvelopeDialog(envelope);
                    } else {
                        // Delete
                        new AlertDialog.Builder(this)
                                .setMessage("Delete this envelope?")
                                .setPositiveButton("Delete", (d, w) -> {
                                    envelopes.remove(position);
                                    PrefManager.saveEnvelopes(this, envelopes);
                                    updateDisplay();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
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