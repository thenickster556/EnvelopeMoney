package com.example.envelopemoney;

import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private Spinner spinnerCategories;
    private EditText etAmount;
    private TextView tvTotalRemaining;
    private ListView listViewEnvelopes;
    private List<Envelope> envelopes;
    private ArrayAdapter<Envelope> listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        spinnerCategories = findViewById(R.id.spinnerCategories);
        etAmount = findViewById(R.id.etAmount);
        tvTotalRemaining = findViewById(R.id.tvTotalRemaining);
        listViewEnvelopes = findViewById(R.id.listViewEnvelopes);
        Button btnSubmit = findViewById(R.id.btnSubmit);

        // Load envelopes
        envelopes = PrefManager.getEnvelopes(this);
        updateTotalRemaining();

        // Setup Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
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

    private void updateDisplay() {
        listAdapter.notifyDataSetChanged();
        updateTotalRemaining();
        // Update spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, getEnvelopeNames());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategories.setAdapter(spinnerAdapter);
    }

    private void updateTotalRemaining() {
        double total = 0;
        for (Envelope e : envelopes) total += e.getRemaining();
        tvTotalRemaining.setText(String.format(Locale.getDefault(),
                "Total Remaining: $%.2f", total));
    }

    private List<String> getEnvelopeNames() {
        List<String> names = new ArrayList<>();
        for (Envelope e : envelopes) names.add(e.getName());
        return names;
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}