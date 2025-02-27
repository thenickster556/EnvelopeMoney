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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
    }

    private void updateDisplay() {
        listAdapter.notifyDataSetChanged();
        updateTotalRemaining();
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