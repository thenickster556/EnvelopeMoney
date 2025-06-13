package com.example.envelopemoney;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import java.lang.reflect.Field;

public class MainActivity extends AppCompatActivity {
    private ListView listViewEnvelopes;
    private List<Envelope> envelopes;
    private ListView listViewTransactions;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private EnvelopeAdapter envelopeAdapter;
    private TextView tvTransactionsTotal;
    private String currentMonth;
    private final Boolean TEST = false;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        ImageButton btnAddTransaction = findViewById(R.id.btnAddTransaction);
        btnAddTransaction.setOnClickListener(v -> showNewTransactionDialog());

        ImageButton btnAddEnvelope = findViewById(R.id.btnAddEnvelope);
        btnAddEnvelope.setOnClickListener(v -> showEnvelopeDialog(null));
        listViewEnvelopes = findViewById(R.id.listViewEnvelopes);
        listViewTransactions = findViewById(R.id.listViewTransactions);

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
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
            Date date = sdf.parse(currentMonth);
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
        } catch (ParseException e) {
            return false;
        }
    }

    private boolean hasNextMonth() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date current = sdf.parse(currentMonth);
            Date now = new Date();
            return current.before(now);
        } catch (ParseException e) {
            return false;
        }
    }
    private void handleNewMonth(boolean carryOver) {
        String newMonth = MonthTracker.formatMonth(new Date());

        for (Envelope env : envelopes) {
            if (carryOver) {
                // 1) pick up last month's leftover (manual preferred)
                double lastLeftover = env.getManualRemaining() != null
                        ? env.getManualRemaining()
                        : env.getRemaining();

                // 2) roll it into this month's allowance
                double newTotal = env.getOriginalLimit() + lastLeftover;
                env.setLimit(newTotal);
                env.setManualRemaining(newTotal);
                env.setRemaining(newTotal);
            } else {
                // clear any manual override and reset to base allowance
                env.setLimit(env.getOriginalLimit());
                env.setManualRemaining(null);
                env.setRemaining(env.getLimit());
            }

            // 3) (optional) seed your month-by-month history
            env.initializeMonth(newMonth, carryOver);
        }

        // persist & refresh UI
        MonthTracker.setCurrentMonth(this, newMonth);
        currentMonth = newMonth;
        PrefManager.saveEnvelopes(this, envelopes);
        updateDisplay();
    }

    private void changeMonth(int direction) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = sdf.parse(currentMonth);
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
        } catch (ParseException e) {
            e.printStackTrace();
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

        // Populate spinner with envelope names
        ArrayAdapter<String> envelopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getEnvelopeNames());
        envelopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnvelope.setAdapter(envelopeAdapter);

        // Optionally set default date to 'today'
        Calendar calendar = Calendar.getInstance();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
        etDate.setText(today);

        // Setup date picker on click
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

        builder.setView(dialogView)
                .setTitle("New Transaction")
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        // Parse fields
                        String envelopeName = spinnerEnvelope.getSelectedItem().toString();
                        double amount = Double.parseDouble(etAmount.getText().toString());
                        String comment = etComment.getText().toString();
                        String date = etDate.getText().toString();

                        // Create a new Transaction
                        Transaction newTransaction = new Transaction(
                                envelopeName, amount, date, comment
                        );

                        // Add to the chosen Envelope
                        Envelope env = findEnvelopeByName(envelopeName);
                        if (env != null) {
                            env.addTransaction(newTransaction,currentMonth);
                        }

                        // Save & refresh
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
        allTransactions.clear();

        TextView tvStartDate = findViewById(R.id.tvStartDate);
        TextView tvEndDate = findViewById(R.id.tvEndDate);
        final SimpleDateFormat sdfDisplay = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date startDate = null, endDate = null;
        try {
            startDate = sdfDisplay.parse(tvStartDate.getText().toString());
            endDate = sdfDisplay.parse(tvEndDate.getText().toString());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        // Filter transactions from selected envelopes by date range
        for (Envelope envelope : envelopes) {
            if (envelope.isSelected()) {
                for (Transaction t : envelope.getTransactions()) {
                    try {
                        Date txDate = sdf.parse(t.getDate());
                        if (txDate != null && !txDate.before(startDate) && !txDate.after(endDate)) {
                            allTransactions.add(t);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Sort transactions (newest first)
        Collections.sort(allTransactions, (t1, t2) -> {
            String d1 = t1.getDate() != null ? t1.getDate() : "";
            String d2 = t2.getDate() != null ? t2.getDate() : "";
            return d2.compareTo(d1);
        });

        // Add a placeholder if empty
        if (allTransactions.isEmpty()) {
            allTransactions.add(new Transaction(
                    "No transactions yet",
                    0,
                    new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date()),
                    "Start by adding your first transaction"
            ));
        }

        // Update total
        double total = 0;
        for (Transaction t : allTransactions) {
            total += t.getAmount();
        }
        tvTransactionsTotal.setText(String.format(Locale.getDefault(), "Total: $%.2f", total));

        transactionAdapter.notifyDataSetChanged();
    }



    private void updateDisplay() {
        envelopeAdapter.notifyDataSetChanged();
        updateTransactionHistory();

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

            // Set values
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

                        envelopeToEdit.setName(name);
                        if(limit != envelopeToEdit.getLimit()) {
                            envelopeToEdit.adjustLimit(limit, currentMonth);
                        }
                        if (remainder != remaining) {
                            // Set the manual override values:
                            envelopeToEdit.setManualOverrideRemaining(remainder); // store the limit at override time
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
                                oldEnvelope.calculateRemaining(currentMonth);
                            }
                            if (newEnvelope != null) {
                                // Deduct new amount and add transaction to new envelope
                                newEnvelope.getTransactions().add(transactionToEdit);
                                newEnvelope.calculateRemaining(currentMonth);
                            }
                            // You'll need a setter or directly update the field:
                            transactionToEdit.setEnvelopeName(newEnvelopeName);
                        } else {
                            // If envelope is the same, adjust remaining based on the difference
                            Envelope envelope = findEnvelopeByName(newEnvelopeName);
                            if (envelope != null) {
                                envelope.updateTransaction(transactionToEdit, newAmount, currentMonth);
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


    @RequiresApi(api = Build.VERSION_CODES.N)
    private void deleteTransaction(Transaction transaction) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage("Delete this transaction?")
                .setPositiveButton("Delete", (d, w) -> {
                    Envelope envelope = findEnvelopeByName(transaction.getEnvelopeName());
                    if(envelope != null){
                        envelope.removeTransaction(transaction, currentMonth);
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


    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}