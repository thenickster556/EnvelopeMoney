package com.example.envelopemoney;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private ListView listViewEnvelopes;
    private List<Envelope> envelopes;
    private boolean monthRolloverInProgress = false;
    private ListView listViewTransactions;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private EnvelopeAdapter envelopeAdapter;
    private TextView tvTransactionsTotal;
    private LinearLayout layoutTransferTotals;
    private Spinner spinnerTransferTotals;
    private TextView tvTransferTotalsSummary;
    private String currentMonth;
    private final Boolean TEST = false;
    private boolean showTransfers = false;
    private int selectedTransferTotalsIndex = 0;


    private static class TransferTotalsOption {
        final String envelopeName;
        final double total;

        TransferTotalsOption(String envelopeName, double total) {
            this.envelopeName = envelopeName;
            this.total = total;
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        ImageButton btnAddTransaction = findViewById(R.id.btnAddTransaction);
        btnAddTransaction.setOnClickListener(v -> showNewTransactionDialog());

        ImageButton btnToggleTransfers = findViewById(R.id.btnToggleTransfers);
        updateTransferToggleButton(btnToggleTransfers);
        btnToggleTransfers.setOnClickListener(v -> {
            showTransfers = !showTransfers;
            updateTransferToggleButton(btnToggleTransfers);
            updateDisplay();
        });

        ImageButton btnAddEnvelope = findViewById(R.id.btnAddEnvelope);
        btnAddEnvelope.setOnClickListener(v -> showEnvelopeDialog(null));

        listViewEnvelopes = findViewById(R.id.listViewEnvelopes);
        listViewTransactions = findViewById(R.id.listViewTransactions);
        layoutTransferTotals = findViewById(R.id.layoutTransferTotals);
        spinnerTransferTotals = findViewById(R.id.spinnerTransferTotals);
        tvTransferTotalsSummary = findViewById(R.id.tvTransferTotalsSummary);
        // Hook up the Toolbar as your ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Load envelopes
        envelopes = PrefManager.getEnvelopes(this);
        if(TEST){
            addData();
        }

        if (MonthTracker.isFirstMonth(this)) {
            // Initialize with current transactions
            String currentMonth = MonthTracker.formatMonth(new Date());
            for (Envelope env : envelopes) {
                env.initializeMonth(currentMonth, false);
                env.migrateLegacyTransactions(currentMonth);
            }
        }
        // Initialize total view
        tvTransactionsTotal = findViewById(R.id.tvTransactionsTotal);
        currentMonth = MonthTracker.getCurrentMonth(this);
        // Check for new month
        if (MonthTracker.isNewMonth(this)) {
            handleNewMonth(true); // Auto-reset with carry-over
        }
        setupMonthNavigation();
        setupDatePickers();

        // Initialize adapters
        transactionAdapter = new TransactionAdapter(this, allTransactions);
        listViewTransactions.setAdapter(transactionAdapter);
        envelopeAdapter = new EnvelopeAdapter(this, envelopes);
        listViewEnvelopes.setAdapter(envelopeAdapter);



        updateTransactionHistory();
    }
    private void addData() {
        // Dummy transactions for "Emergency Fund"
//        Transaction janEmergencyTransaction = new Transaction("Emergency Fund", 100.0, "2025-01-10", "January expense");
//        setTransactionMonth(janEmergencyTransaction, "2025-01");
//
//        Transaction febEmergencyTransaction = new Transaction("Emergency Fund", 75.0, "2025-02-05", "February expense");
//        setTransactionMonth(febEmergencyTransaction, "2025-02");
//
////        // Dummy transactions for "Vacation Fund"
//        Transaction janVacationTransaction = new Transaction("Vacation Fund", 200.0, "2025-01-20", "January booking");
//        setTransactionMonth(janVacationTransaction, "2025-01");
////
//        Transaction febVacationTransaction = new Transaction("Vacation Fund", 150.0, "2025-02-12", "February booking");
//        setTransactionMonth(febVacationTransaction, "2025-02");
//        Envelope emergencyFund = findEnvelopeByName("Emergency Fund");
//        if (emergencyFund != null) {
//            emergencyFund.addTransaction(janEmergencyTransaction, currentMonth);
//            emergencyFund.addTransaction(febEmergencyTransaction, currentMonth);
//        }
//
//        Envelope vacationFund = findEnvelopeByName("Vacation Fund");
//        if (vacationFund != null) {
//            vacationFund.addTransaction(janVacationTransaction, currentMonth);
//            vacationFund.addTransaction(febVacationTransaction, currentMonth);
//        }
//        emergencyFund.initializeMonth("2025-01", false);
//        emergencyFund.initializeMonth("2025-02", false);
//        vacationFund.initializeMonth("2025-01", false);
//        vacationFund.initializeMonth("2025-02", false);


    }

    private void setTransactionMonth(Transaction transaction, String month) {
        try {
            Field monthField = Transaction.class.getDeclaredField("month");
            monthField.setAccessible(true);
            monthField.set(transaction, month);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupMonthNavigation() {
        // Initialize current month data first
        String currentMonth = MonthTracker.getCurrentMonth(this);
        for (Envelope env : envelopes) {
            env.initializeMonth(currentMonth, true);
        }

        TextView tvMonth = findViewById(R.id.tvCurrentMonth);
        ImageButton btnPrev = findViewById(R.id.btnPrevMonth);
        ImageButton btnNext = findViewById(R.id.btnNextMonth);
        TextView tvStartDate = findViewById(R.id.tvStartDate);
        TextView tvEndDate = findViewById(R.id.tvEndDate);

        tvMonth.setText(formatDisplayMonth(currentMonth));
        tvStartDate.setText(getFirstDayOfMonth(currentMonth));
        tvEndDate.setText(getLastDayOfMonth(currentMonth));
        // Disable previous button if no earlier months
//        btnPrev.setEnabled(hasPreviousMonth());

        // Disable next button if current month is present or future
//        btnNext.setEnabled(hasNextMonth());
        btnPrev.setOnClickListener(v -> changeMonth(-1));
        btnNext.setOnClickListener(v -> changeMonth(1));
    }

    private String getFirstDayOfMonth(String monthStr) {
        // monthStr is in "yyyy-MM", so the first day is simply:
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = sdf.parse(monthStr);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return displayFormat.format(cal.getTime());
        } catch (ParseException e) {
            return monthStr;
        }
    }

    private String getLastDayOfMonth(String monthStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = sdf.parse(monthStr);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            cal.set(Calendar.DAY_OF_MONTH, lastDay);
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return displayFormat.format(cal.getTime());
        } catch (ParseException e) {
            return monthStr;
        }
    }

    private boolean hasPreviousMonth() {
        if (currentMonth == null || currentMonth.isEmpty()) {
            return false;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = sdf.parse(currentMonth);
            if (date == null) {
                return false;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, -1);

            String prevMonth = sdf.format(cal.getTime());
            for (Envelope e : envelopes) {
                if (e.hasDataForMonth(prevMonth)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    private boolean hasNextMonth() {
        if (currentMonth == null || currentMonth.isEmpty()) {
            return false;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date current = sdf.parse(currentMonth);
            if (current == null) {
                return false;
            }
            Date now = new Date();
            return current.before(now);
        } catch (Exception e) {
            return false;
        }
    }

    private void handleNewMonth(boolean carryOver) {
        if (monthRolloverInProgress) return;                 // prevent re-entry
        monthRolloverInProgress = true;
        try {
            final String newMonth = MonthTracker.formatMonth(new Date());
            final String alreadySet = MonthTracker.getCurrentMonth(this);
            if (newMonth.equals(alreadySet)) return;         // nothing to do (idempotent)

            // Work on a snapshot to avoid ConcurrentModification during UI binds
            List<Envelope> snapshot = new ArrayList<>(envelopes);

            for (Envelope env : snapshot) {
                // ---- null-safety (see section 2) ----
                double orig = safe(env.getOriginalLimit());
                double rem  = safe(env.getRemaining());
                Double manual = env.getManualRemaining();
                double manualVal = (manual != null) ? manual : rem;

                if (carryOver) {
                    double newTotal = orig + manualVal;      // base + leftover (manual preferred)
                    // Seed *both* manual + baselines so later recomputes are correct
                    env.setManualRemaining(newTotal);
                    // If you have baseline fields, seed them here too
                    if (env.hasBaseline()) {
                        env.setBaselineRemaining(newTotal);
                        env.setBaselineLimit(newTotal);     // or env.getLimit() if that’s your anchor
                    }
                    env.setRemaining(newTotal);
                } else {
                    env.setManualRemaining(null);
                    if (env.hasBaseline()) {
                        env.setBaselineRemaining(orig);
                        env.setBaselineLimit(orig);
                    }
                    env.setRemaining(orig);
                }

                // Ensure monthlyData exists for newMonth
                env.initializeMonth(newMonth, carryOver);
            }

            // Commit month AFTER envelopes are stable
            currentMonth = newMonth;
            MonthTracker.setCurrentMonth(this, newMonth);

            // One clean UI refresh at the end
            PrefManager.saveEnvelopes(this, envelopes);
            if (envelopeAdapter != null && transactionAdapter != null) {
                updateDisplay();
            }

        } catch (Throwable t) {
            // Don’t crash-loop: surface a message and swallow the exception
//            showError("We hit a rollover issue: " + t.getClass().getSimpleName());
            // Optional: Log.d("EnvelopeMoney", "Rollover crash", t);
            Log.d("EnvelopeMoney", "Rollover crash", t);
        } finally {
            monthRolloverInProgress = false;
        }
    }
    private static double safe(Double v) {
        if (v == null) return 0d;
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0d;
        return v;
    }


    private void changeMonth(int direction) {
        if (currentMonth == null || currentMonth.isEmpty()) {
            return;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = sdf.parse(currentMonth);
            if (date == null) {
                return;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, direction);
            String newMonth = sdf.format(cal.getTime());

            // Prevent navigating to future months
            if (newMonth.compareTo(MonthTracker.formatMonth(new Date())) > 0) {
                return;
            }
            currentMonth = newMonth;
            MonthTracker.setCurrentMonth(this, newMonth);
            refreshDataForMonth();
            setupMonthNavigation();
            updateDisplay();
        } catch (Exception e) {
            Log.d("EnvelopeMoney", "Month navigation failed", e);
        }
    }
    private void refreshDataForMonth() {
        // Load data for current month
        for (Envelope env : envelopes) {
            env.getMonthlyData(currentMonth); // Initialize if needed
        }
        updateDisplay();
    }
    private String formatDisplayMonth(String month) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
            Date date = inputFormat.parse(month);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return month;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void showNewTransactionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_transaction, null);

        Spinner spinnerEnvelope = dialogView.findViewById(R.id.spinnerEditEnvelope);
        EditText etDate = dialogView.findViewById(R.id.etEditTransactionDate);
        EditText etAmount = dialogView.findViewById(R.id.etEditTransactionAmount);
        EditText etComment = dialogView.findViewById(R.id.etEditTransactionComment);
        CheckBox cbIsTransfer = dialogView.findViewById(R.id.cbIsTransfer);
        TextView tvTransferToLabel = dialogView.findViewById(R.id.tvTransferToLabel);
        Spinner spinnerTransferDestination = dialogView.findViewById(R.id.spinnerTransferDestination);

        ArrayAdapter<String> envelopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getEnvelopeNames());
        envelopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnvelope.setAdapter(envelopeAdapter);

        Calendar calendar = Calendar.getInstance();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
        etDate.setText(today);

        etDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        String selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                year, month + 1, dayOfMonth);
                        etDate.setText(selectedDate);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        spinnerEnvelope.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String source = parent.getItemAtPosition(position).toString();
                populateTransferDestinationSpinner(spinnerTransferDestination, source, null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        if (spinnerEnvelope.getSelectedItem() != null) {
            populateTransferDestinationSpinner(spinnerTransferDestination, spinnerEnvelope.getSelectedItem().toString(), null);
        }

        cbIsTransfer.setOnCheckedChangeListener((buttonView, isChecked) ->
                setTransferControlsVisibility(isChecked, tvTransferToLabel, spinnerTransferDestination));
        setTransferControlsVisibility(false, tvTransferToLabel, spinnerTransferDestination);

        builder.setView(dialogView)
                .setTitle("New Transaction")
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        String envelopeName = spinnerEnvelope.getSelectedItem().toString();
                        double amount = Double.parseDouble(etAmount.getText().toString());
                        String comment = etComment.getText().toString();
                        String date = etDate.getText().toString();

                        Transaction newTransaction = new Transaction(envelopeName, amount, date, comment);
                        Envelope env = findEnvelopeByName(envelopeName);
                        if (env == null) {
                            showError("Envelope not found");
                            return;
                        }

                        String destination = null;
                        if (cbIsTransfer.isChecked()) {
                            if (spinnerTransferDestination.getSelectedItem() == null) {
                                showError("Select where this transfer goes");
                                return;
                            }
                            destination = spinnerTransferDestination.getSelectedItem().toString();
                            if (destination.equals(envelopeName)) {
                                showError("Transfer destination must be a different envelope");
                                return;
                            }
                        }

                        env.addTransaction(newTransaction, currentMonth);
                        if (destination != null) {
                            upsertTransferForTransaction(newTransaction, envelopeName, destination, Math.abs(amount));
                        }
                        PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                        updateDisplay();
                    } catch (NumberFormatException e) {
                        showError("Invalid amount entered!");
                    }
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateTransactionHistory() {
        ensureMirrorTransactionsForExistingTransfers();
        allTransactions.clear();

        TextView tvStartDate = findViewById(R.id.tvStartDate);
        TextView tvEndDate = findViewById(R.id.tvEndDate);
        final SimpleDateFormat sdfDisplay = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        Date startDate = null;
        Date endDate = null;
        try {
            startDate = sdfDisplay.parse(tvStartDate.getText().toString());
            endDate = sdfDisplay.parse(tvEndDate.getText().toString());
        } catch (ParseException e) {
            Log.d("EnvelopeMoney", "Date range parse failed", e);
        }
        if (startDate == null || endDate == null) {
            startDate = new Date(0L);
            endDate = new Date(Long.MAX_VALUE);
        }

        List<Transaction> filteredTransactions = new ArrayList<>();
        Map<String, Double> transferTotalsByEnvelope = new HashMap<>();
        double grossTotal = 0;
        double outgoingTransferTotal = 0;
        double incomingTransferTotal = 0;

        for (Envelope envelope : envelopes) {
            if (!envelope.isSelected()) {
                continue;
            }
            for (Transaction transaction : envelope.getTransactions()) {
                try {
                    Date txDate = sdf.parse(transaction.getDate());
                    if (txDate == null || txDate.before(startDate) || txDate.after(endDate)) {
                        continue;
                    }
                    filteredTransactions.add(transaction);
                    grossTotal += transaction.getAmount();

                    String transferId = transaction.getTransferId();
                    if (transferId != null && !transferId.isEmpty()) {
                        Envelope.TransferData transfer = findTransferById(transferId);
                        if (transfer != null && transfer.getToEnvelope() != null && !transfer.getToEnvelope().isEmpty()) {
                            double amount = Math.abs(transaction.getAmount());
                            Envelope ownerEnvelope = findTransferOwner(transferId);
                            boolean isSourceSide = ownerEnvelope != null && Objects.equals(ownerEnvelope.getName(), transaction.getEnvelopeName());
                            Envelope destinationEnvelope = findEnvelopeByName(transfer.getToEnvelope());
                            boolean destinationSelected = destinationEnvelope != null && destinationEnvelope.isSelected();

                            if (isSourceSide) {
                                outgoingTransferTotal += amount;
                            } else {
                                incomingTransferTotal += amount;
                            }

                            // Show transfer totals for both directions so opposite envelopes appear,
                            // and cancel to zero when both sides are selected.
                            double running = transferTotalsByEnvelope.getOrDefault(transfer.getToEnvelope(), 0d);
                            if (isSourceSide) {
                                running += amount;
                            }
                            if (destinationSelected) {
                                running -= amount;
                            }
                            transferTotalsByEnvelope.put(transfer.getToEnvelope(), running);
                        }
                    }
                } catch (ParseException e) {
                    Log.d("EnvelopeMoney", "Transaction date parse failed", e);
                }
            }
        }

        allTransactions.addAll(filteredTransactions);
        Collections.sort(allTransactions, (t1, t2) -> {
            String d1 = t1.getDate() != null ? t1.getDate() : "";
            String d2 = t2.getDate() != null ? t2.getDate() : "";
            return d2.compareTo(d1);
        });

        if (allTransactions.isEmpty()) {
            allTransactions.add(new Transaction(
                    "No transactions yet",
                    0,
                    new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date()),
                    "Start by adding your first transaction"
            ));
        }

        double displayTotal = showTransfers ? (grossTotal - outgoingTransferTotal + incomingTransferTotal) : grossTotal;
        tvTransactionsTotal.setText(String.format(Locale.getDefault(), "Total: $%.2f", displayTotal));
        updateTransferTotalsPanel(transferTotalsByEnvelope);

        transactionAdapter.notifyDataSetChanged();
    }

    private void updateDisplay() {
        if (envelopeAdapter != null) {
            envelopeAdapter.notifyDataSetChanged();
        }
        if (transactionAdapter != null) {
            updateTransactionHistory();
        }
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
    @RequiresApi(api = Build.VERSION_CODES.N)
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

            tvName.setText(envelope.getName());
            tvAmounts.setText(String.format(Locale.getDefault(),
                    "Limit: $%.2f | Remaining: $%.2f",
                    envelope.getLimit(),
                    envelope.getRemaining()));


            cbSelect.setOnCheckedChangeListener(null);
            cbSelect.setChecked(envelope.isSelected());
            cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                envelope.setSelected(isChecked);
                updateTransactionHistory();
                PrefManager.saveEnvelopes(getContext(), envelopes);
            });

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

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Recalculate Balances?")
                .setMessage("This will recompute each envelope’s remaining balance from your existing transactions. Continue?")
                .setPositiveButton("Recalculate", (dialog, which) -> {
                    performMonthlyReset();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void setupDatePickers() {
        // Assume these TextViews are already defined in your layout and have IDs tvStartDate and tvEndDate.
        TextView tvStartDate = findViewById(R.id.tvStartDate);
        TextView tvEndDate = findViewById(R.id.tvEndDate);
        final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        // Set click listener for Start Date
        tvStartDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            try {
                // Parse current text to a date so that DatePickerDialog starts at that date.
                Date currentDate = sdf.parse(tvStartDate.getText().toString());
                calendar.setTime(currentDate);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            DatePickerDialog dpd = new DatePickerDialog(MainActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        // month is 0-indexed, so add 1.
                        calendar.set(year, month, dayOfMonth);
                        tvStartDate.setText(sdf.format(calendar.getTime()));
                        // Refresh your transaction history based on the new filter
                        updateTransactionHistory();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        // Set click listener for End Date
        tvEndDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            try {
                Date currentDate = sdf.parse(tvEndDate.getText().toString());
                calendar.setTime(currentDate);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            DatePickerDialog dpd = new DatePickerDialog(MainActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(year, month, dayOfMonth);
                        tvEndDate.setText(sdf.format(calendar.getTime()));
                        updateTransactionHistory();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void performMonthlyReset() {
        for (Envelope envelope : envelopes) {
            // Recompute remaining = limit – sum(transactions)
            envelope.reset(false);
            envelope.calculateRemaining(currentMonth);
        }
        PrefManager.saveEnvelopes(this, envelopes);
        updateDisplay();
        showError("Balances recalculated successfully!");
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
                                    removeTransferReferencesToEnvelope(envelope.getName());
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
        EditText etRemainder = dialogView.findViewById(R.id.etEnvelopeRemainder);
        TextView etReminderLabel = dialogView.findViewById(R.id.etEnvelopeRemainderLabel);
        if(envelopeToEdit == null){
            etReminderLabel.setVisibility(View.GONE);
            etRemainder.setVisibility(View.GONE);
        }
        if (envelopeToEdit != null) {
            etName.setText(envelopeToEdit.getName());
            etLimit.setText(String.valueOf(envelopeToEdit.getLimit()));
            etRemainder.setText(String.valueOf(envelopeToEdit.getRemaining()));
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
                    if(envelopeToEdit == null) {
                        // Create new
                        envelopes.add(new Envelope(name, limit));
                    }
                    else {
                        String remainderStr = etRemainder.getText().toString();
                        double remainder;
                        if (remainderStr.startsWith("+")) {
                            // e.g. "+50" means limit + 50
                            remainder = limit + Double.parseDouble(remainderStr.substring(1));
                        } else if (remainderStr.startsWith("-")) {
                            // e.g. "-30" means limit - 30
                            remainder = limit - Double.parseDouble(remainderStr.substring(1));
                        } else {
                            // Otherwise, treat as an absolute value
                            remainder = Double.parseDouble(remainderStr);
                        }

                        // Update existing
                        double remaining = envelopeToEdit.getRemaining();

                        String oldName = envelopeToEdit.getName();
                        envelopeToEdit.setName(name);
                        if(limit != envelopeToEdit.getLimit()) {
                            envelopeToEdit.adjustLimit(limit, currentMonth);
                        }
                        if (remainder != remaining) {
                            // Set the manual override values:
                            envelopeToEdit.setManualOverrideRemaining(remainder); // store the limit at override time
                        }
                        if (!oldName.equals(name)) {
                            renameTransferReferences(oldName, name);
                        }
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
        final Transaction editTransaction = resolveTransferAnchorTransaction(transactionToEdit);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction, null);

        Spinner spinnerEnvelope = dialogView.findViewById(R.id.spinnerEditEnvelope);
        EditText etDate = dialogView.findViewById(R.id.etEditTransactionDate);
        EditText etAmount = dialogView.findViewById(R.id.etEditTransactionAmount);
        EditText etComment = dialogView.findViewById(R.id.etEditTransactionComment);
        CheckBox cbIsTransfer = dialogView.findViewById(R.id.cbIsTransfer);
        TextView tvTransferToLabel = dialogView.findViewById(R.id.tvTransferToLabel);
        Spinner spinnerTransferDestination = dialogView.findViewById(R.id.spinnerTransferDestination);

        ArrayAdapter<String> envelopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getEnvelopeNames());
        envelopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnvelope.setAdapter(envelopeAdapter);

        int envelopeIndex = getEnvelopeNames().indexOf(editTransaction.getEnvelopeName());
        if (envelopeIndex >= 0) {
            spinnerEnvelope.setSelection(envelopeIndex);
        }

        String selectedDestination = null;
        boolean editingMirrorTransfer = false;
        if (editTransaction.getTransferId() != null) {
            Envelope.TransferData transferData = findTransferById(editTransaction.getTransferId());
            Envelope transferOwner = findTransferOwner(editTransaction.getTransferId());
            if (transferData != null) {
                selectedDestination = transferData.getToEnvelope();
            }
            editingMirrorTransfer = transferOwner != null
                    && !Objects.equals(transferOwner.getName(), editTransaction.getEnvelopeName());
            if (editingMirrorTransfer && transferOwner != null) {
                selectedDestination = transferOwner.getName();
            }
        }

        final boolean finalEditingMirrorTransfer = editingMirrorTransfer;
        final String initialDestination = selectedDestination;
        spinnerEnvelope.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String source = parent.getItemAtPosition(position).toString();
                populateTransferDestinationSpinner(spinnerTransferDestination, source, initialDestination);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        if (spinnerEnvelope.getSelectedItem() != null) {
            populateTransferDestinationSpinner(spinnerTransferDestination, spinnerEnvelope.getSelectedItem().toString(), initialDestination);
        }

        etDate.setText(editTransaction.getDate());
        etDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        String selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                year, month + 1, dayOfMonth);
                        etDate.setText(selectedDate);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        etAmount.setText(String.valueOf(editTransaction.getAmount()));
        etComment.setText(editTransaction.getComment());

        boolean isTransfer = editTransaction.getTransferId() != null && !editTransaction.getTransferId().isEmpty();
        cbIsTransfer.setChecked(isTransfer);
        setTransferControlsVisibility(isTransfer, tvTransferToLabel, spinnerTransferDestination);
        cbIsTransfer.setOnCheckedChangeListener((buttonView, checked) ->
                setTransferControlsVisibility(checked, tvTransferToLabel, spinnerTransferDestination));

        builder.setView(dialogView)
                .setTitle("Edit Transaction")
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        double newAmount = Double.parseDouble(etAmount.getText().toString());
                        String newComment = etComment.getText().toString();
                        String newDate = etDate.getText().toString();
                        String oldEnvelopeName = editTransaction.getEnvelopeName();
                        String newEnvelopeName = spinnerEnvelope.getSelectedItem().toString();

                        if (!oldEnvelopeName.equals(newEnvelopeName)) {
                            Envelope oldEnvelope = findEnvelopeByName(oldEnvelopeName);
                            Envelope newEnvelope = findEnvelopeByName(newEnvelopeName);
                            if (oldEnvelope != null) {
                                oldEnvelope.getTransactions().remove(editTransaction);
                                oldEnvelope.calculateRemaining(currentMonth);
                            }
                            if (newEnvelope != null) {
                                newEnvelope.getTransactions().add(editTransaction);
                                newEnvelope.calculateRemaining(currentMonth);
                            }
                            editTransaction.setEnvelopeName(newEnvelopeName);
                        } else {
                            Envelope envelope = findEnvelopeByName(newEnvelopeName);
                            if (envelope != null) {
                                envelope.updateTransaction(editTransaction, newAmount, currentMonth);
                            }
                        }

                        editTransaction.setAmount(newAmount);
                        editTransaction.setComment(newComment);
                        editTransaction.setDate(newDate);

                        if (cbIsTransfer.isChecked()) {
                            if (spinnerTransferDestination.getSelectedItem() == null) {
                                showError("Select where this transfer goes");
                                return;
                            }
                            String destination = spinnerTransferDestination.getSelectedItem().toString();
                            if (destination.equals(newEnvelopeName)) {
                                showError("Transfer destination must be a different envelope");
                                return;
                            }
                            String sourceEnvelopeNameForTransfer = finalEditingMirrorTransfer ? destination : newEnvelopeName;
                            String destinationEnvelopeNameForTransfer = finalEditingMirrorTransfer ? newEnvelopeName : destination;
                            upsertTransferForTransaction(editTransaction,
                                    sourceEnvelopeNameForTransfer,
                                    destinationEnvelopeNameForTransfer,
                                    Math.abs(newAmount));
                        } else {
                            detachTransferFromTransaction(editTransaction);
                        }

                        PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                        updateDisplay();
                    } catch (NumberFormatException e) {
                        showError("Invalid amount entered!");
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void deleteTransaction(Transaction transaction) {
        final Transaction targetTransaction = resolveTransferAnchorTransaction(transaction);
        new AlertDialog.Builder(MainActivity.this)
                .setMessage("Delete this transaction?")
                .setPositiveButton("Delete", (d, w) -> {
                    Envelope envelope = findEnvelopeByName(targetTransaction.getEnvelopeName());
                    if(envelope != null){
                        detachTransferFromTransaction(targetTransaction);
                        envelope.removeTransaction(targetTransaction, currentMonth);
                        // Save and refresh
                        PrefManager.saveEnvelopes(this, envelopes);
                        updateDisplay();
                    };
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private Envelope findEnvelopeByName(String envelopeName) {
        for (Envelope env : envelopes) {
            if (env.getName().equals(envelopeName)) {
                return env;
            }
        }
        return null;
    }


    private Envelope findTransferOwner(String transferId) {
        for (Envelope envelope : envelopes) {
            for (Envelope.TransferData transfer : envelope.getTransfers()) {
                if (Objects.equals(transfer.getId(), transferId)) {
                    return envelope;
                }
            }
        }
        return null;
    }

    private Envelope.TransferData findTransferById(String transferId) {
        for (Envelope envelope : envelopes) {
            for (Envelope.TransferData transfer : envelope.getTransfers()) {
                if (Objects.equals(transfer.getId(), transferId)) {
                    return transfer;
                }
            }
        }
        return null;
    }

    private void removeTransferById(String transferId) {
        Envelope owner = findTransferOwner(transferId);
        if (owner != null) {
            owner.removeTransfer(transferId);
        }
    }

    private void removeTransferReferencesToEnvelope(String envelopeName) {
        for (Envelope envelope : envelopes) {
            envelope.getTransfers().removeIf(transfer -> Objects.equals(transfer.getToEnvelope(), envelopeName));
        }
    }

    private void renameTransferReferences(String oldName, String newName) {
        for (Envelope envelope : envelopes) {
            for (Envelope.TransferData transfer : envelope.getTransfers()) {
                if (Objects.equals(transfer.getToEnvelope(), oldName)) {
                    transfer.setToEnvelope(newName);
                }
            }
        }
    }

    private void populateTransferDestinationSpinner(Spinner spinner, String sourceEnvelopeName, @Nullable String selectedDestination) {
        List<String> destinations = new ArrayList<>();
        for (Envelope env : envelopes) {
            if (!Objects.equals(env.getName(), sourceEnvelopeName)) {
                destinations.add(env.getName());
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, destinations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selectedDestination != null) {
            int index = destinations.indexOf(selectedDestination);
            if (index >= 0) {
                spinner.setSelection(index);
            }
        }
    }

    private void setTransferControlsVisibility(boolean visible, TextView label, Spinner spinner) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        label.setVisibility(visibility);
        spinner.setVisibility(visibility);
    }

    private void upsertTransferForTransaction(Transaction transaction, String sourceEnvelopeName, String destinationEnvelopeName, double amount) {
        String transferId = transaction.getTransferId();
        if (transferId == null || transferId.isEmpty()) {
            transferId = UUID.randomUUID().toString();
            transaction.setTransferId(transferId);
        }

        Envelope sourceEnvelope = findEnvelopeByName(sourceEnvelopeName);
        if (sourceEnvelope == null) {
            return;
        }

        Envelope currentOwner = findTransferOwner(transferId);
        if (currentOwner != null && !Objects.equals(currentOwner.getName(), sourceEnvelopeName)) {
            currentOwner.removeTransfer(transferId);
            currentOwner = null;
        }

        if (currentOwner == null) {
            sourceEnvelope.addTransfer(transferId, destinationEnvelopeName, amount);
        } else {
            currentOwner.updateTransfer(transferId, destinationEnvelopeName, amount);
        }

        Transaction sourceTransaction = resolveTransferAnchorTransaction(transaction);
        if (sourceTransaction == null) {
            sourceTransaction = transaction;
        }

        Envelope sourceHolder = findEnvelopeByName(sourceTransaction.getEnvelopeName());
        if (sourceHolder != null && sourceHolder != sourceEnvelope) {
            sourceHolder.getTransactions().remove(sourceTransaction);
            sourceHolder.calculateRemaining(currentMonth);
            sourceEnvelope.addTransaction(sourceTransaction, currentMonth);
        }

        sourceTransaction.setEnvelopeName(sourceEnvelopeName);
        sourceTransaction.setDate(transaction.getDate());
        sourceTransaction.setComment(transaction.getComment());
        sourceEnvelope.updateTransaction(sourceTransaction, Math.abs(amount), currentMonth);

        syncMirrorTransferTransaction(sourceTransaction, sourceEnvelopeName, destinationEnvelopeName, amount);
    }

    private void detachTransferFromTransaction(Transaction transaction) {
        String transferId = transaction.getTransferId();
        if (transferId == null || transferId.isEmpty()) {
            return;
        }

        removeTransferById(transferId);
        removeMirrorTransactions(transferId, transaction);
        transaction.setTransferId(null);
    }

    private Transaction resolveTransferAnchorTransaction(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        String transferId = transaction.getTransferId();
        if (transferId == null || transferId.isEmpty()) {
            return transaction;
        }

        Envelope owner = findTransferOwner(transferId);
        if (owner == null) {
            return transaction;
        }

        for (Transaction candidate : owner.getTransactions()) {
            if (Objects.equals(candidate.getTransferId(), transferId)) {
                return candidate;
            }
        }

        return transaction;
    }

    private void syncMirrorTransferTransaction(Transaction sourceTransaction,
                                               String sourceEnvelopeName,
                                               String destinationEnvelopeName,
                                               double amount) {
        String transferId = sourceTransaction.getTransferId();
        if (transferId == null || transferId.isEmpty()) {
            return;
        }

        Envelope destinationEnvelope = findEnvelopeByName(destinationEnvelopeName);
        if (destinationEnvelope == null) {
            return;
        }

        Transaction mirror = null;
        Envelope mirrorEnvelope = null;
        for (Envelope envelope : envelopes) {
            Iterator<Transaction> iterator = envelope.getTransactions().iterator();
            while (iterator.hasNext()) {
                Transaction candidate = iterator.next();
                if (!Objects.equals(candidate.getTransferId(), transferId) || candidate == sourceTransaction) {
                    continue;
                }

                if (Objects.equals(candidate.getEnvelopeName(), destinationEnvelopeName)) {
                    mirror = candidate;
                    mirrorEnvelope = envelope;
                } else {
                    iterator.remove();
                    if (Objects.equals(candidate.getMonth(), currentMonth)) {
                        envelope.calculateRemaining(currentMonth);
                    }
                }
            }
        }

        String sourceComment = sourceTransaction.getComment();
        String mirrorComment = (sourceComment == null || sourceComment.isEmpty())
                ? "Transfer from " + sourceEnvelopeName
                : "Transfer from " + sourceEnvelopeName + " | " + sourceComment;

        if (mirror == null) {
            mirror = new Transaction(destinationEnvelopeName, -Math.abs(amount), sourceTransaction.getDate(), mirrorComment);
            mirror.setTransferId(transferId);
            destinationEnvelope.addTransaction(mirror, currentMonth);
            return;
        }

        if (mirrorEnvelope != null && mirrorEnvelope != destinationEnvelope) {
            mirrorEnvelope.getTransactions().remove(mirror);
            mirrorEnvelope.calculateRemaining(currentMonth);
            destinationEnvelope.addTransaction(mirror, currentMonth);
        }

        mirror.setEnvelopeName(destinationEnvelopeName);
        mirror.setDate(sourceTransaction.getDate());
        mirror.setComment(mirrorComment);
        destinationEnvelope.updateTransaction(mirror, -Math.abs(amount), currentMonth);
    }

    private void removeMirrorTransactions(String transferId, Transaction anchorTransaction) {
        for (Envelope envelope : envelopes) {
            Iterator<Transaction> iterator = envelope.getTransactions().iterator();
            while (iterator.hasNext()) {
                Transaction candidate = iterator.next();
                if (!Objects.equals(candidate.getTransferId(), transferId)) {
                    continue;
                }
                if (candidate == anchorTransaction) {
                    continue;
                }

                iterator.remove();
                if (Objects.equals(candidate.getMonth(), currentMonth)) {
                    envelope.calculateRemaining(currentMonth);
                }
            }
        }
    }
    private void ensureMirrorTransactionsForExistingTransfers() {
        for (Envelope owner : envelopes) {
            for (Envelope.TransferData transfer : owner.getTransfers()) {
                if (transfer.getId() == null || transfer.getId().isEmpty()) {
                    continue;
                }

                Transaction sourceTransaction = null;
                for (Transaction candidate : owner.getTransactions()) {
                    if (Objects.equals(candidate.getTransferId(), transfer.getId())) {
                        sourceTransaction = candidate;
                        break;
                    }
                }

                if (sourceTransaction == null) {
                    continue;
                }

                syncMirrorTransferTransaction(
                        sourceTransaction,
                        owner.getName(),
                        transfer.getToEnvelope(),
                        Math.abs(sourceTransaction.getAmount())
                );
            }
        }
    }
    private void updateTransferTotalsPanel(Map<String, Double> totalsByEnvelope) {
        if (layoutTransferTotals == null || spinnerTransferTotals == null || tvTransferTotalsSummary == null) {
            return;
        }

        if (!showTransfers) {
            layoutTransferTotals.setVisibility(View.GONE);
            spinnerTransferTotals.setOnItemSelectedListener(null);
            return;
        }

        layoutTransferTotals.setVisibility(View.VISIBLE);

        List<TransferTotalsOption> options = new ArrayList<>();
        for (Map.Entry<String, Double> entry : totalsByEnvelope.entrySet()) {
            options.add(new TransferTotalsOption(entry.getKey(), entry.getValue()));
        }
        options.sort((a, b) -> a.envelopeName.compareToIgnoreCase(b.envelopeName));

        if (options.isEmpty()) {
            spinnerTransferTotals.setOnItemSelectedListener(null);
            spinnerTransferTotals.setVisibility(View.GONE);
            tvTransferTotalsSummary.setText("No transfers in range");
            selectedTransferTotalsIndex = 0;
            return;
        }

        List<String> labels = new ArrayList<>();
        for (TransferTotalsOption option : options) {
            labels.add("To " + option.envelopeName);
        }

        if (selectedTransferTotalsIndex < 0 || selectedTransferTotalsIndex >= options.size()) {
            selectedTransferTotalsIndex = 0;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTransferTotals.setAdapter(adapter);

        if (options.size() > 1) {
            spinnerTransferTotals.setVisibility(View.VISIBLE);
            spinnerTransferTotals.setSelection(selectedTransferTotalsIndex, false);
            tvTransferTotalsSummary.setText(formatTransferTotalsSummary(options.get(selectedTransferTotalsIndex)));
            spinnerTransferTotals.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedTransferTotalsIndex = position;
                    tvTransferTotalsSummary.setText(formatTransferTotalsSummary(options.get(position)));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        } else {
            spinnerTransferTotals.setOnItemSelectedListener(null);
            spinnerTransferTotals.setVisibility(View.GONE);
            tvTransferTotalsSummary.setText(formatTransferTotalsSummary(options.get(0)));
            selectedTransferTotalsIndex = 0;
        }
    }

    private String formatTransferTotalsSummary(TransferTotalsOption option) {
        return String.format(Locale.getDefault(), "To %s: $%.2f", option.envelopeName, option.total);
    }
    private void updateTransferToggleButton(ImageButton button) {
        int color = showTransfers
                ? ContextCompat.getColor(this, android.R.color.holo_green_dark)
                : ContextCompat.getColor(this, android.R.color.darker_gray);
        button.setColorFilter(color);
        button.setAlpha(showTransfers ? 1.0f : 0.65f);
    }
    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}











































