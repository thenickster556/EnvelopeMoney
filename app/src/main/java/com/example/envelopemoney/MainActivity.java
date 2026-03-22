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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private LinearLayout layoutEnvelopesSection;
    private ImageButton btnToggleEnvelopes;
    private boolean envelopesCollapsed = false;


    private static class TransferTotalsOption {
        final String optionKey;
        final String labelPrefix;
        final String envelopeName;
        final double total;

        TransferTotalsOption(String optionKey, String labelPrefix, String envelopeName, double total) {
            this.optionKey = optionKey;
            this.labelPrefix = labelPrefix;
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

        btnToggleEnvelopes = findViewById(R.id.btnToggleEnvelopes);
        listViewEnvelopes = findViewById(R.id.listViewEnvelopes);
        layoutEnvelopesSection = findViewById(R.id.layoutEnvelopesSection);
        envelopesCollapsed = PrefManager.isEnvelopesCollapsed(this);
        applyEnvelopesCollapsedState();
        btnToggleEnvelopes.setOnClickListener(v -> {
            envelopesCollapsed = !envelopesCollapsed;
            PrefManager.setEnvelopesCollapsed(MainActivity.this, envelopesCollapsed);
            applyEnvelopesCollapsedState();
        });
        listViewTransactions = findViewById(R.id.listViewTransactions);
        layoutTransferTotals = findViewById(R.id.layoutTransferTotals);
        spinnerTransferTotals = findViewById(R.id.spinnerTransferTotals);
        tvTransferTotalsSummary = findViewById(R.id.tvTransferTotalsSummary);
        // Hook up the Toolbar as your ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Load envelopes through the rollover repair path so startup only adopts sanitized state.
        envelopes = PrefManager.getEnvelopes(this);
        if(TEST){
            addData();
        }

        MonthRolloverHelper.Result launchState = MonthRolloverHelper.prepareForLaunch(
                envelopes,
                MonthTracker.getStoredMonthOrNull(this),
                MonthTracker.getRealCurrentMonth(),
                true
        );
        envelopes = launchState.getEnvelopes();
        // Initialize total view
        tvTransactionsTotal = findViewById(R.id.tvTransactionsTotal);
        currentMonth = launchState.getActiveMonth();
        MonthTracker.setCurrentMonth(this, currentMonth);
        if (launchState.requiresPersistence()) {
            PrefManager.saveEnvelopes(this, envelopes);
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

    private void applyEnvelopesCollapsedState() {
        if (layoutEnvelopesSection == null || btnToggleEnvelopes == null) {
            return;
        }
        layoutEnvelopesSection.setVisibility(envelopesCollapsed ? View.GONE : View.VISIBLE);
        btnToggleEnvelopes.setImageResource(envelopesCollapsed ? android.R.drawable.arrow_down_float : android.R.drawable.arrow_up_float);
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
        if (monthRolloverInProgress) return;
        monthRolloverInProgress = true;
        try {
            MonthRolloverHelper.Result rolloverResult = MonthRolloverHelper.prepareForLaunch(
                    envelopes,
                    currentMonth,
                    MonthTracker.getRealCurrentMonth(),
                    carryOver
            );
            envelopes = rolloverResult.getEnvelopes();
            currentMonth = rolloverResult.getActiveMonth();
            MonthTracker.setCurrentMonth(this, currentMonth);
            PrefManager.saveEnvelopes(this, envelopes);
            if (envelopeAdapter != null && transactionAdapter != null) {
                envelopeAdapter = new EnvelopeAdapter(this, envelopes);
                listViewEnvelopes.setAdapter(envelopeAdapter);
                updateDisplay();
            }
        } catch (RuntimeException exception) {
            Log.d("EnvelopeMoney", "Rollover recovery failed", exception);
        } finally {
            monthRolloverInProgress = false;
        }
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

    private static double safe(Double v) {
        if (v == null) return 0d;
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0d;
        return v;
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
        CheckBox cbIsRecurring = dialogView.findViewById(R.id.cbIsRecurring);
        TextView tvRecurringFrequencyLabel = dialogView.findViewById(R.id.tvRecurringFrequencyLabel);
        LinearLayout layoutRecurringFrequencyOptions = dialogView.findViewById(R.id.layoutRecurringFrequencyOptions);
        TextView btnRecurringWeekly = dialogView.findViewById(R.id.btnRecurringWeekly);
        TextView btnRecurringBiWeekly = dialogView.findViewById(R.id.btnRecurringBiWeekly);
        TextView btnRecurringMonthly = dialogView.findViewById(R.id.btnRecurringMonthly);
        TextView tvRecurringDaysLabel = dialogView.findViewById(R.id.tvRecurringDaysLabel);
        LinearLayout layoutRecurringWeekdayButtons = dialogView.findViewById(R.id.layoutRecurringWeekdayButtons);
        TextView btnRecurringDayMon = dialogView.findViewById(R.id.btnRecurringDayMon);
        TextView btnRecurringDayTue = dialogView.findViewById(R.id.btnRecurringDayTue);
        TextView btnRecurringDayWed = dialogView.findViewById(R.id.btnRecurringDayWed);
        TextView btnRecurringDayThu = dialogView.findViewById(R.id.btnRecurringDayThu);
        TextView btnRecurringDayFri = dialogView.findViewById(R.id.btnRecurringDayFri);
        TextView btnRecurringDaySat = dialogView.findViewById(R.id.btnRecurringDaySat);
        TextView tvRecurringDaysValue = dialogView.findViewById(R.id.tvRecurringDaysValue);

        ArrayAdapter<String> envelopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getEnvelopeNames());
        envelopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnvelope.setAdapter(envelopeAdapter);
        String savedSourceEnvelope = PrefManager.getLastAddTransactionEnvelope(this);
        List<String> envelopeNames = getEnvelopeNames();
        if (savedSourceEnvelope != null) {
            int savedSourceIndex = envelopeNames.indexOf(savedSourceEnvelope);
            if (savedSourceIndex >= 0) {
                spinnerEnvelope.setSelection(savedSourceIndex);
            }
        }

        List<Integer> selectedRecurringDays = new ArrayList<>();
        final String[] selectedRecurringFrequency = new String[]{"weekly"};
        Map<Integer, TextView> recurringDayButtons = createRecurringWeekdayButtonMap(
                btnRecurringDayMon,
                btnRecurringDayTue,
                btnRecurringDayWed,
                btnRecurringDayThu,
                btnRecurringDayFri,
                btnRecurringDaySat);
        applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
        applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
        updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
        setRecurringWeekdayButtonHandlers(recurringDayButtons, selectedRecurringDays, () ->
                updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays));

        btnRecurringWeekly.setOnClickListener(v -> {
            selectedRecurringFrequency[0] = "weekly";
            selectedRecurringDays.clear();
            applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
            applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
            updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
            setRecurringControlsVisibility(cbIsRecurring.isChecked(),
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });
        btnRecurringBiWeekly.setOnClickListener(v -> {
            selectedRecurringFrequency[0] = "bi-weekly";
            selectedRecurringDays.clear();
            applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
            applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
            updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
            setRecurringControlsVisibility(cbIsRecurring.isChecked(),
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });
        btnRecurringMonthly.setOnClickListener(v -> {
            selectedRecurringFrequency[0] = "monthly";
            selectedRecurringDays.clear();
            applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
            applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
            updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
            setRecurringControlsVisibility(cbIsRecurring.isChecked(),
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });

        tvRecurringDaysValue.setOnClickListener(v -> {
            if (!"monthly".equals(selectedRecurringFrequency[0])) {
                return;
            }
            showRecurringDayPickerDialog(
                    selectedRecurringFrequency[0],
                    selectedRecurringDays,
                    () -> updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays)
            );
        });

        cbIsRecurring.setOnCheckedChangeListener((buttonView, checked) ->
                setRecurringControlsVisibility(checked,
                        tvRecurringFrequencyLabel,
                        layoutRecurringFrequencyOptions,
                        tvRecurringDaysLabel,
                        layoutRecurringWeekdayButtons,
                        tvRecurringDaysValue,
                        selectedRecurringFrequency[0]));
        setRecurringControlsVisibility(false,
                tvRecurringFrequencyLabel,
                layoutRecurringFrequencyOptions,
                tvRecurringDaysLabel,
                layoutRecurringWeekdayButtons,
                tvRecurringDaysValue,
                selectedRecurringFrequency[0]);
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
                String savedDestination = PrefManager.getLastAddTransferDestination(MainActivity.this, source);
                populateTransferDestinationSpinner(spinnerTransferDestination, source, savedDestination);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        if (spinnerEnvelope.getSelectedItem() != null) {
            String source = spinnerEnvelope.getSelectedItem().toString();
            String savedDestination = PrefManager.getLastAddTransferDestination(this, source);
            populateTransferDestinationSpinner(spinnerTransferDestination, source, savedDestination);
        }

        cbIsTransfer.setOnCheckedChangeListener((buttonView, isChecked) ->
                setTransferControlsVisibility(isChecked, tvTransferToLabel, spinnerTransferDestination));
        setTransferControlsVisibility(false, tvTransferToLabel, spinnerTransferDestination);

        builder.setView(dialogView)
                .setTitle("New Transaction")
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String envelopeName = spinnerEnvelope.getSelectedItem().toString();
                double amount = Double.parseDouble(etAmount.getText().toString());
                String comment = etComment.getText().toString();
                String date = etDate.getText().toString();

                Transaction newTransaction = new Transaction(envelopeName, amount, date, comment);
                if (cbIsRecurring.isChecked()) {
                    if (selectedRecurringDays.isEmpty()) {
                        showError("Recurring requires at least one selected day");
                        return;
                    }
                    newTransaction.setRecurring(true);
                    newTransaction.setRecurringFrequency(selectedRecurringFrequency[0]);
                    newTransaction.setRecurringDays(selectedRecurringDays);
                    newTransaction.setRecurringSeriesId(UUID.randomUUID().toString());
                    newTransaction.setRecurringTemplate(true);
                }

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

                PrefManager.setLastAddTransactionEnvelope(MainActivity.this, envelopeName);
                if (destination != null) {
                    PrefManager.setLastAddTransferDestination(MainActivity.this, envelopeName, destination);
                }

                env.addTransaction(newTransaction, currentMonth);
                if (destination != null) {
                    upsertTransferForTransaction(newTransaction, envelopeName, destination, Math.abs(amount));
                }
                PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                updateDisplay();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                showError("Invalid amount entered!");
            }
        }));
        dialog.show();
    }
    private void updateTransactionHistory() {
        ensureRecurringTransactionsForCurrentMonth();
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
        Map<String, TransferTotalsOption> transferTotalsByEnvelope = new HashMap<>();
        double grossTotal = 0;
        double outgoingTransferTotal = 0;
        double incomingTransferTotal = 0;

        for (Envelope envelope : envelopes) {
            boolean envelopeSelected = envelope.isSelected();
            for (Transaction transaction : envelope.getTransactions()) {
                try {
                    Date txDate = sdf.parse(transaction.getDate());
                    if (txDate == null || txDate.before(startDate) || txDate.after(endDate)) {
                        continue;
                    }

                    String transferId = transaction.getTransferId();
                    Envelope.TransferData transfer = null;
                    Envelope ownerEnvelope = null;
                    Envelope destinationEnvelope = null;
                    boolean isTransfer = transferId != null && !transferId.isEmpty();
                    boolean isSourceSide = false;
                    boolean destinationSelected = false;
                    boolean ownerSelected = false;
                    boolean includeTransaction = envelopeSelected;

                    if (isTransfer) {
                        transfer = findTransferById(transferId);
                        if (transfer != null && transfer.getToEnvelope() != null && !transfer.getToEnvelope().isEmpty()) {
                            ownerEnvelope = findTransferOwner(transferId);
                            destinationEnvelope = findEnvelopeByName(transfer.getToEnvelope());
                            isSourceSide = ownerEnvelope != null
                                    && Objects.equals(ownerEnvelope.getName(), transaction.getEnvelopeName());
                            destinationSelected = destinationEnvelope != null && destinationEnvelope.isSelected();
                            ownerSelected = ownerEnvelope != null && ownerEnvelope.isSelected();
                            if (!includeTransaction && showTransfers && (ownerSelected || destinationSelected)) {
                                includeTransaction = true;
                            }
                        }
                    }

                    if (!includeTransaction) {
                        continue;
                    }

                    filteredTransactions.add(transaction);
                    grossTotal += transaction.getAmount();

                    if (transfer != null && transfer.getToEnvelope() != null && !transfer.getToEnvelope().isEmpty()) {
                        double amount = Math.abs(transaction.getAmount());

                        if (isSourceSide) {
                            outgoingTransferTotal += amount;
                        } else {
                            incomingTransferTotal += amount;
                        }

                        String summaryKey;
                        String labelPrefix;
                        String relatedEnvelopeName;
                        if (isSourceSide) {
                            summaryKey = "to:" + transfer.getToEnvelope();
                            labelPrefix = "To";
                            relatedEnvelopeName = transfer.getToEnvelope();
                        } else {
                            String ownerName = ownerEnvelope != null ? ownerEnvelope.getName() : transfer.getToEnvelope();
                            summaryKey = "from:" + ownerName;
                            labelPrefix = "From";
                            relatedEnvelopeName = ownerName;
                        }

                        TransferTotalsOption existing = transferTotalsByEnvelope.get(summaryKey);
                        double running = existing != null ? existing.total : 0d;
                        if (isSourceSide) {
                            running += amount;
                            if (destinationSelected) {
                                running -= amount;
                            }
                        } else {
                            running += amount;
                            if (ownerSelected) {
                                running -= amount;
                            }
                        }
                        transferTotalsByEnvelope.put(summaryKey,
                                new TransferTotalsOption(summaryKey, labelPrefix, relatedEnvelopeName, running));
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
        updateTransferTotalsPanel(new ArrayList<>(transferTotalsByEnvelope.values()));

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
            String envelopeDisplayName = transaction.getEnvelopeName();
            if (transaction.isRecurring()) {
                String frequencyLabel = "Recurring";
                if (transaction.getRecurringFrequency() != null && !transaction.getRecurringFrequency().isEmpty()) {
                    frequencyLabel = recurringFrequencyDisplay(normalizeRecurringFrequency(transaction.getRecurringFrequency()));
                }
                envelopeDisplayName += " (" + frequencyLabel + ")";
            }
            String amountText = String.format(Locale.getDefault(),
                    "%s - $%.2f", envelopeDisplayName, transaction.getAmount());
            tvAmount.setText(amountText);

            String details = transaction.getDate();
            if (transaction.getComment() != null && !transaction.getComment().isEmpty()) {
                details += " | " + transaction.getComment();
            }
            if (transaction.isRecurring() && transaction.getRecurringFrequency() != null && !transaction.getRecurringFrequency().isEmpty()) {
                details += " | " + recurringFrequencyDisplay(normalizeRecurringFrequency(transaction.getRecurringFrequency()));
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
        final boolean wasRecurringBefore = editTransaction.isRecurring();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction, null);

        Spinner spinnerEnvelope = dialogView.findViewById(R.id.spinnerEditEnvelope);
        EditText etDate = dialogView.findViewById(R.id.etEditTransactionDate);
        EditText etAmount = dialogView.findViewById(R.id.etEditTransactionAmount);
        EditText etComment = dialogView.findViewById(R.id.etEditTransactionComment);
        CheckBox cbIsTransfer = dialogView.findViewById(R.id.cbIsTransfer);
        TextView tvTransferToLabel = dialogView.findViewById(R.id.tvTransferToLabel);
        Spinner spinnerTransferDestination = dialogView.findViewById(R.id.spinnerTransferDestination);
        CheckBox cbIsRecurring = dialogView.findViewById(R.id.cbIsRecurring);
        TextView tvRecurringFrequencyLabel = dialogView.findViewById(R.id.tvRecurringFrequencyLabel);
        LinearLayout layoutRecurringFrequencyOptions = dialogView.findViewById(R.id.layoutRecurringFrequencyOptions);
        TextView btnRecurringWeekly = dialogView.findViewById(R.id.btnRecurringWeekly);
        TextView btnRecurringBiWeekly = dialogView.findViewById(R.id.btnRecurringBiWeekly);
        TextView btnRecurringMonthly = dialogView.findViewById(R.id.btnRecurringMonthly);
        TextView tvRecurringDaysLabel = dialogView.findViewById(R.id.tvRecurringDaysLabel);
        LinearLayout layoutRecurringWeekdayButtons = dialogView.findViewById(R.id.layoutRecurringWeekdayButtons);
        TextView btnRecurringDayMon = dialogView.findViewById(R.id.btnRecurringDayMon);
        TextView btnRecurringDayTue = dialogView.findViewById(R.id.btnRecurringDayTue);
        TextView btnRecurringDayWed = dialogView.findViewById(R.id.btnRecurringDayWed);
        TextView btnRecurringDayThu = dialogView.findViewById(R.id.btnRecurringDayThu);
        TextView btnRecurringDayFri = dialogView.findViewById(R.id.btnRecurringDayFri);
        TextView btnRecurringDaySat = dialogView.findViewById(R.id.btnRecurringDaySat);
        TextView tvRecurringDaysValue = dialogView.findViewById(R.id.tvRecurringDaysValue);

        ArrayAdapter<String> envelopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, getEnvelopeNames());
        envelopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnvelope.setAdapter(envelopeAdapter);

        int envelopeIndex = getEnvelopeNames().indexOf(editTransaction.getEnvelopeName());
        if (envelopeIndex >= 0) {
            spinnerEnvelope.setSelection(envelopeIndex);
        }

        List<Integer> selectedRecurringDays = new ArrayList<>(editTransaction.getRecurringDays());
        final String[] selectedRecurringFrequency = new String[]{
                editTransaction.getRecurringFrequency() == null ? "weekly" : editTransaction.getRecurringFrequency()
        };
        Map<Integer, TextView> recurringDayButtons = createRecurringWeekdayButtonMap(
                btnRecurringDayMon,
                btnRecurringDayTue,
                btnRecurringDayWed,
                btnRecurringDayThu,
                btnRecurringDayFri,
                btnRecurringDaySat);

        applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
        applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
        updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
        setRecurringWeekdayButtonHandlers(recurringDayButtons, selectedRecurringDays, () ->
                updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays));

        btnRecurringWeekly.setOnClickListener(v -> {
            selectedRecurringFrequency[0] = "weekly";
            selectedRecurringDays.clear();
            applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
            applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
            updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
            setRecurringControlsVisibility(cbIsRecurring.isChecked(),
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });
        btnRecurringBiWeekly.setOnClickListener(v -> {
            selectedRecurringFrequency[0] = "bi-weekly";
            selectedRecurringDays.clear();
            applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
            applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
            updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
            setRecurringControlsVisibility(cbIsRecurring.isChecked(),
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });
        btnRecurringMonthly.setOnClickListener(v -> {
            selectedRecurringFrequency[0] = "monthly";
            selectedRecurringDays.clear();
            applyRecurringFrequencyButtonSelection(btnRecurringWeekly, btnRecurringBiWeekly, btnRecurringMonthly, selectedRecurringFrequency[0]);
            applyRecurringWeekdayButtonSelection(recurringDayButtons, selectedRecurringDays);
            updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays);
            setRecurringControlsVisibility(cbIsRecurring.isChecked(),
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });

        tvRecurringDaysValue.setOnClickListener(v -> {
            if (!"monthly".equals(selectedRecurringFrequency[0])) {
                return;
            }
            showRecurringDayPickerDialog(
                    selectedRecurringFrequency[0],
                    selectedRecurringDays,
                    () -> updateRecurringDaysSummaryView(tvRecurringDaysValue, selectedRecurringFrequency[0], selectedRecurringDays)
            );
        });

        cbIsRecurring.setChecked(editTransaction.isRecurring());
        setRecurringControlsVisibility(editTransaction.isRecurring(),
                tvRecurringFrequencyLabel,
                layoutRecurringFrequencyOptions,
                tvRecurringDaysLabel,
                layoutRecurringWeekdayButtons,
                tvRecurringDaysValue,
                selectedRecurringFrequency[0]);
        cbIsRecurring.setOnCheckedChangeListener((buttonView, checked) ->
                setRecurringControlsVisibility(checked,
                        tvRecurringFrequencyLabel,
                        layoutRecurringFrequencyOptions,
                        tvRecurringDaysLabel,
                        layoutRecurringWeekdayButtons,
                        tvRecurringDaysValue,
                        selectedRecurringFrequency[0]));

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
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double newAmount = Double.parseDouble(etAmount.getText().toString());
                String newComment = etComment.getText().toString();
                String newDate = etDate.getText().toString();
                String oldEnvelopeName = editTransaction.getEnvelopeName();
                String newEnvelopeName = spinnerEnvelope.getSelectedItem().toString();
                String previousMonth = resolveTransactionMonth(editTransaction);

                if (cbIsRecurring.isChecked() && selectedRecurringDays.isEmpty()) {
                    showError("Recurring requires at least one selected day");
                    return;
                }

                String destination = null;
                if (cbIsTransfer.isChecked()) {
                    if (spinnerTransferDestination.getSelectedItem() == null) {
                        showError("Select where this transfer goes");
                        return;
                    }
                    destination = spinnerTransferDestination.getSelectedItem().toString();
                    if (destination.equals(newEnvelopeName)) {
                        showError("Transfer destination must be a different envelope");
                        return;
                    }
                }

                if (!oldEnvelopeName.equals(newEnvelopeName)) {
                    Envelope oldEnvelope = findEnvelopeByName(oldEnvelopeName);
                    Envelope newEnvelope = findEnvelopeByName(newEnvelopeName);
                    if (oldEnvelope != null) {
                        oldEnvelope.getTransactions().remove(editTransaction);
                        synchronizeEnvelopeMonth(oldEnvelope, resolveTransactionMonth(editTransaction));
                    }
                    if (newEnvelope != null) {
                        newEnvelope.getTransactions().add(editTransaction);
                        synchronizeEnvelopeMonth(newEnvelope, resolveTransactionMonth(editTransaction));
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

                if (cbIsRecurring.isChecked()) {
                    editTransaction.setRecurring(true);
                    editTransaction.setRecurringFrequency(selectedRecurringFrequency[0]);
                    editTransaction.setRecurringDays(selectedRecurringDays);
                    if (editTransaction.getRecurringSeriesId() == null || editTransaction.getRecurringSeriesId().isEmpty()) {
                        editTransaction.setRecurringSeriesId(UUID.randomUUID().toString());
                    }
                    editTransaction.setRecurringTemplate(wasRecurringBefore ? editTransaction.isRecurringTemplate() : true);
                } else {
                    editTransaction.setRecurring(false);
                    editTransaction.setRecurringFrequency(null);
                    editTransaction.setRecurringDays(new ArrayList<>());
                    editTransaction.setRecurringSeriesId(null);
                    editTransaction.setRecurringTemplate(false);
                }

                if (cbIsTransfer.isChecked()) {
                    String sourceEnvelopeNameForTransfer = finalEditingMirrorTransfer ? destination : newEnvelopeName;
                    String destinationEnvelopeNameForTransfer = finalEditingMirrorTransfer ? newEnvelopeName : destination;
                    upsertTransferForTransaction(editTransaction,
                            sourceEnvelopeNameForTransfer,
                            destinationEnvelopeNameForTransfer,
                            Math.abs(newAmount));
                } else {
                    detachTransferFromTransaction(editTransaction);
                }

                Envelope updatedEnvelope = findEnvelopeByName(editTransaction.getEnvelopeName());
                synchronizeEnvelopeMonth(updatedEnvelope, previousMonth);
                synchronizeEnvelopeMonth(updatedEnvelope, resolveTransactionMonth(editTransaction));

                PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                updateDisplay();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                showError("Invalid amount entered!");
            }
        }));

        dialog.show();
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
    private void setRecurringControlsVisibility(boolean visible,
                                                TextView frequencyLabel,
                                                View frequencyOptionsView,
                                                TextView daysLabel,
                                                View weekdayButtonsView,
                                                TextView monthlyDaysValue,
                                                String selectedFrequency) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        frequencyLabel.setVisibility(visibility);
        frequencyOptionsView.setVisibility(visibility);
        daysLabel.setVisibility(visibility);
        if (!visible) {
            weekdayButtonsView.setVisibility(View.GONE);
            monthlyDaysValue.setVisibility(View.GONE);
            return;
        }
        boolean isMonthly = "monthly".equals(selectedFrequency);
        weekdayButtonsView.setVisibility(isMonthly ? View.GONE : View.VISIBLE);
        monthlyDaysValue.setVisibility(isMonthly ? View.VISIBLE : View.GONE);
    }

    private Map<Integer, TextView> createRecurringWeekdayButtonMap(TextView mon,
                                                                    TextView tue,
                                                                    TextView wed,
                                                                    TextView thu,
                                                                    TextView fri,
                                                                    TextView sat) {
        Map<Integer, TextView> buttonMap = new HashMap<>();
        buttonMap.put(Calendar.MONDAY, mon);
        buttonMap.put(Calendar.TUESDAY, tue);
        buttonMap.put(Calendar.WEDNESDAY, wed);
        buttonMap.put(Calendar.THURSDAY, thu);
        buttonMap.put(Calendar.FRIDAY, fri);
        buttonMap.put(Calendar.SATURDAY, sat);
        return buttonMap;
    }

    private void setRecurringWeekdayButtonHandlers(Map<Integer, TextView> dayButtons,
                                                    List<Integer> selectedDays,
                                                    Runnable onSelectionChanged) {
        for (Map.Entry<Integer, TextView> entry : dayButtons.entrySet()) {
            final int dayValue = entry.getKey();
            final TextView button = entry.getValue();
            button.setOnClickListener(v -> {
                if (selectedDays.contains(dayValue)) {
                    selectedDays.remove(Integer.valueOf(dayValue));
                } else {
                    selectedDays.add(dayValue);
                }
                Collections.sort(selectedDays);
                applyRecurringWeekdayButtonSelection(dayButtons, selectedDays);
                if (onSelectionChanged != null) {
                    onSelectionChanged.run();
                }
            });
        }
    }

    private void applyRecurringWeekdayButtonSelection(Map<Integer, TextView> dayButtons,
                                                       List<Integer> selectedDays) {
        int selectedColor = ContextCompat.getColor(this, android.R.color.black);
        int normalColor = ContextCompat.getColor(this, android.R.color.darker_gray);
        for (Map.Entry<Integer, TextView> entry : dayButtons.entrySet()) {
            boolean selected = selectedDays.contains(entry.getKey());
            TextView button = entry.getValue();
            button.setBackgroundResource(selected
                    ? R.drawable.recurring_option_selected
                    : R.drawable.recurring_option_unselected);
            button.setTextColor(selected ? selectedColor : normalColor);
        }
    }

    private void applyRecurringFrequencyButtonSelection(TextView weekly,
                                                        TextView biWeekly,
                                                        TextView monthly,
                                                        String selectedFrequency) {
        weekly.setBackgroundResource("weekly".equals(selectedFrequency)
                ? R.drawable.recurring_option_selected
                : R.drawable.recurring_option_unselected);
        biWeekly.setBackgroundResource("bi-weekly".equals(selectedFrequency)
                ? R.drawable.recurring_option_selected
                : R.drawable.recurring_option_unselected);
        monthly.setBackgroundResource("monthly".equals(selectedFrequency)
                ? R.drawable.recurring_option_selected
                : R.drawable.recurring_option_unselected);

        int selectedColor = ContextCompat.getColor(this, android.R.color.black);
        int normalColor = ContextCompat.getColor(this, android.R.color.darker_gray);
        weekly.setTextColor("weekly".equals(selectedFrequency) ? selectedColor : normalColor);
        biWeekly.setTextColor("bi-weekly".equals(selectedFrequency) ? selectedColor : normalColor);
        monthly.setTextColor("monthly".equals(selectedFrequency) ? selectedColor : normalColor);
    }

    private String normalizeRecurringFrequency(String selectedDisplay) {
        if (selectedDisplay == null) {
            return "weekly";
        }
        String value = selectedDisplay.trim().toLowerCase(Locale.getDefault());
        if (value.contains("bi")) {
            return "bi-weekly";
        }
        if (value.contains("month")) {
            return "monthly";
        }
        return "weekly";
    }

    private String recurringFrequencyDisplay(String normalized) {
        if (normalized == null) {
            return "Weekly";
        }
        if ("bi-weekly".equals(normalized)) {
            return "Bi-weekly";
        }
        if ("monthly".equals(normalized)) {
            return "Monthly";
        }
        return "Weekly";
    }

    private void updateRecurringDaysSummaryView(TextView daysView, String frequency, List<Integer> selectedDays) {
        if (selectedDays == null || selectedDays.isEmpty()) {
            daysView.setText("Select days");
            return;
        }
        List<Integer> sorted = new ArrayList<>(selectedDays);
        Collections.sort(sorted);
        if ("monthly".equals(frequency)) {
            List<String> labels = new ArrayList<>();
            for (Integer day : sorted) {
                labels.add(String.valueOf(day));
            }
            daysView.setText("Days: " + String.join(", ", labels));
            return;
        }

        String[] weekLabels = new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        List<String> labels = new ArrayList<>();
        for (Integer day : sorted) {
            int idx = day - 1;
            if (idx >= 0 && idx < weekLabels.length) {
                labels.add(weekLabels[idx]);
            }
        }
        daysView.setText("Days: " + String.join(", ", labels));
    }

    private void showRecurringDayPickerDialog(String frequency,
                                              List<Integer> selectedDays,
                                              Runnable onSelectionChanged) {
        if ("monthly".equals(frequency)) {
            showMonthlyRecurringCalendarDialog(selectedDays, onSelectionChanged);
            return;
        }

        final List<Integer> values = new ArrayList<>();
        values.add(Calendar.SUNDAY);
        values.add(Calendar.MONDAY);
        values.add(Calendar.TUESDAY);
        values.add(Calendar.WEDNESDAY);
        values.add(Calendar.THURSDAY);
        values.add(Calendar.FRIDAY);
        values.add(Calendar.SATURDAY);

        final String[] labels = new String[]{"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        boolean[] checked = new boolean[values.size()];
        for (int i = 0; i < values.size(); i++) {
            checked[i] = selectedDays.contains(values.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Choose recurring days")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    Integer value = values.get(which);
                    if (isChecked) {
                        if (!selectedDays.contains(value)) {
                            selectedDays.add(value);
                        }
                    } else {
                        selectedDays.remove(value);
                    }
                })
                .setPositiveButton("OK", (dialog, which) -> {
                    Collections.sort(selectedDays);
                    if (onSelectionChanged != null) {
                        onSelectionChanged.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMonthlyRecurringCalendarDialog(List<Integer> selectedDays, Runnable onSelectionChanged) {
        final Calendar displayedMonth = Calendar.getInstance();
        final Set<Integer> workingSelection = new HashSet<>(selectedDays);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(10);
        root.setPadding(padding, padding, padding, padding);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView btnPrev = new TextView(this);
        btnPrev.setText("\u2039");
        btnPrev.setTextSize(22f);
        btnPrev.setTypeface(btnPrev.getTypeface(), android.graphics.Typeface.BOLD);
        btnPrev.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        btnPrev.setGravity(android.view.Gravity.CENTER);
        btnPrev.setMinWidth(dp(40));
        btnPrev.setContentDescription("Previous month");
        btnPrev.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnPrev.setBackgroundResource(R.drawable.recurring_option_unselected);

        TextView tvMonth = new TextView(this);
        tvMonth.setTextSize(16f);
        tvMonth.setTypeface(tvMonth.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams monthLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvMonth.setLayoutParams(monthLp);
        tvMonth.setGravity(android.view.Gravity.CENTER);

        TextView btnNext = new TextView(this);
        btnNext.setText("\u203A");
        btnNext.setTextSize(22f);
        btnNext.setTypeface(btnNext.getTypeface(), android.graphics.Typeface.BOLD);
        btnNext.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        btnNext.setGravity(android.view.Gravity.CENTER);
        btnNext.setMinWidth(dp(40));
        btnNext.setContentDescription("Next month");
        btnNext.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnNext.setBackgroundResource(R.drawable.recurring_option_unselected);

        header.addView(btnPrev);
        header.addView(tvMonth);
        header.addView(btnNext);

        LinearLayout calendarBody = new LinearLayout(this);
        calendarBody.setOrientation(LinearLayout.VERTICAL);
        calendarBody.setPadding(0, dp(10), 0, 0);

        Runnable renderCalendar = () -> {
            calendarBody.removeAllViews();
            tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(displayedMonth.getTime()));

            String[] dow = new String[]{"S", "M", "T", "W", "T", "F", "S"};
            LinearLayout dowRow = new LinearLayout(this);
            dowRow.setOrientation(LinearLayout.HORIZONTAL);
            for (String label : dow) {
                TextView t = new TextView(this);
                t.setText(label);
                t.setGravity(android.view.Gravity.CENTER);
                t.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                t.setLayoutParams(lp);
                dowRow.addView(t);
            }
            calendarBody.addView(dowRow);

            Calendar first = (Calendar) displayedMonth.clone();
            first.set(Calendar.DAY_OF_MONTH, 1);
            int firstColumn = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);

            LinearLayout weekRow = new LinearLayout(this);
            weekRow.setOrientation(LinearLayout.HORIZONTAL);
            int currentColumn = 0;

            for (int i = 0; i < firstColumn; i++) {
                TextView empty = new TextView(this);
                empty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
                weekRow.addView(empty);
                currentColumn++;
            }

            for (int day = 1; day <= daysInMonth; day++) {
                final int dayValue = day;
                TextView dayCell = new TextView(this);
                dayCell.setText(String.valueOf(day));
                dayCell.setGravity(android.view.Gravity.CENTER);
                dayCell.setTextSize(13f);
                dayCell.setPadding(0, dp(4), 0, dp(4));
                dayCell.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                dayCell.setLayoutParams(lp);

                boolean isSelected = workingSelection.contains(dayValue);
                dayCell.setBackgroundResource(isSelected
                        ? R.drawable.recurring_calendar_day_selected
                        : R.drawable.recurring_calendar_day_unselected);
                dayCell.setOnClickListener(v -> {
                    if (workingSelection.contains(dayValue)) {
                        workingSelection.remove(dayValue);
                    } else {
                        workingSelection.add(dayValue);
                    }
                    boolean selected = workingSelection.contains(dayValue);
                    dayCell.setBackgroundResource(selected
                            ? R.drawable.recurring_calendar_day_selected
                            : R.drawable.recurring_calendar_day_unselected);
                    dayCell.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                });

                weekRow.addView(dayCell);
                currentColumn++;

                if (currentColumn == 7) {
                    calendarBody.addView(weekRow);
                    weekRow = new LinearLayout(this);
                    weekRow.setOrientation(LinearLayout.HORIZONTAL);
                    currentColumn = 0;
                }
            }

            if (currentColumn != 0) {
                for (int i = currentColumn; i < 7; i++) {
                    TextView empty = new TextView(this);
                    empty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
                    weekRow.addView(empty);
                }
                calendarBody.addView(weekRow);
            }
        };

        btnPrev.setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, -1);
            renderCalendar.run();
        });
        btnNext.setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, 1);
            renderCalendar.run();
        });

        root.addView(header);
        root.addView(calendarBody);
        renderCalendar.run();

        new AlertDialog.Builder(this)
                .setTitle("Select monthly days")
                .setView(root)
                .setPositiveButton("OK", (dialog, which) -> {
                    selectedDays.clear();
                    selectedDays.addAll(workingSelection);
                    Collections.sort(selectedDays);
                    if (onSelectionChanged != null) {
                        onSelectionChanged.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }
    private void ensureRecurringTransactionsForCurrentMonth() {
        String activeMonth = currentMonth != null ? currentMonth : MonthTracker.getCurrentMonth(this);
        boolean changed = false;
        for (Envelope envelope : envelopes) {
            List<Transaction> snapshot = new ArrayList<>(envelope.getTransactions());
            for (Transaction template : snapshot) {
                if (!template.isRecurring() || !template.isRecurringTemplate()) {
                    continue;
                }
                if (template.getTransferId() != null && !template.getTransferId().isEmpty()) {
                    continue;
                }
                if (template.getRecurringFrequency() == null || template.getRecurringDays().isEmpty()) {
                    continue;
                }
                if (template.getRecurringSeriesId() == null || template.getRecurringSeriesId().isEmpty()) {
                    template.setRecurringSeriesId(UUID.randomUUID().toString());
                    changed = true;
                }

                List<String> dates = getRecurringDatesForMonth(template, activeMonth);
                for (String date : dates) {
                    if (hasRecurringOccurrence(envelope, template.getRecurringSeriesId(), date)) {
                        continue;
                    }
                    Transaction generated = new Transaction(template.getEnvelopeName(), template.getAmount(), date, template.getComment());
                    generated.setRecurring(true);
                    generated.setRecurringFrequency(template.getRecurringFrequency());
                    generated.setRecurringDays(template.getRecurringDays());
                    generated.setRecurringSeriesId(template.getRecurringSeriesId());
                    generated.setRecurringTemplate(false);
                    envelope.addTransaction(generated, activeMonth);
                    changed = true;
                }
            }
        }
        if (changed) {
            PrefManager.saveEnvelopes(this, envelopes);
        }
    }

    private List<String> getRecurringDatesForMonth(Transaction template, String month) {
        List<String> dates = new ArrayList<>();
        Date anchorDate = parseIsoDate(template.getDate());
        Date monthStart = parseIsoDate(month + "-01");
        if (monthStart == null) {
            return dates;
        }

        Calendar cursor = Calendar.getInstance();
        cursor.setTime(monthStart);
        Calendar monthEnd = Calendar.getInstance();
        monthEnd.setTime(monthStart);
        monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));

        String frequency = template.getRecurringFrequency();
        Set<Integer> selectedDays = new HashSet<>(template.getRecurringDays());
        Calendar anchorWeekStart = null;
        if ("bi-weekly".equals(frequency) && anchorDate != null) {
            anchorWeekStart = Calendar.getInstance();
            anchorWeekStart.setTime(anchorDate);
            anchorWeekStart.set(Calendar.HOUR_OF_DAY, 0);
            anchorWeekStart.set(Calendar.MINUTE, 0);
            anchorWeekStart.set(Calendar.SECOND, 0);
            anchorWeekStart.set(Calendar.MILLISECOND, 0);
            anchorWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        }

        while (!cursor.after(monthEnd)) {
            Date candidateDate = cursor.getTime();
            if (anchorDate != null && candidateDate.before(anchorDate)) {
                cursor.add(Calendar.DAY_OF_MONTH, 1);
                continue;
            }

            boolean include = false;
            if ("monthly".equals(frequency)) {
                include = selectedDays.contains(cursor.get(Calendar.DAY_OF_MONTH));
            } else if ("weekly".equals(frequency)) {
                include = selectedDays.contains(cursor.get(Calendar.DAY_OF_WEEK));
            } else if ("bi-weekly".equals(frequency) && anchorWeekStart != null) {
                if (selectedDays.contains(cursor.get(Calendar.DAY_OF_WEEK))) {
                    Calendar candidateWeekStart = Calendar.getInstance();
                    candidateWeekStart.setTime(candidateDate);
                    candidateWeekStart.set(Calendar.HOUR_OF_DAY, 0);
                    candidateWeekStart.set(Calendar.MINUTE, 0);
                    candidateWeekStart.set(Calendar.SECOND, 0);
                    candidateWeekStart.set(Calendar.MILLISECOND, 0);
                    candidateWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                    long diffMs = candidateWeekStart.getTimeInMillis() - anchorWeekStart.getTimeInMillis();
                    long weeks = Math.abs(diffMs / (7L * 24L * 60L * 60L * 1000L));
                    include = weeks % 2L == 0L;
                }
            }

            if (include) {
                dates.add(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(candidateDate));
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        return dates;
    }

    private boolean hasRecurringOccurrence(Envelope envelope, String seriesId, String date) {
        for (Transaction transaction : envelope.getTransactions()) {
            if (Objects.equals(seriesId, transaction.getRecurringSeriesId())
                    && Objects.equals(date, transaction.getDate())) {
                return true;
            }
        }
        return false;
    }

    private Date parseIsoDate(String date) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date);
        } catch (ParseException e) {
            return null;
        }
    }

    private String resolveTransactionMonth(Transaction transaction) {
        if (transaction == null) {
            return currentMonth;
        }
        if (transaction.getMonth() != null && !transaction.getMonth().isEmpty()) {
            return transaction.getMonth();
        }
        Date parsedDate = parseIsoDate(transaction.getDate());
        if (parsedDate != null) {
            return MonthTracker.formatMonth(parsedDate);
        }
        return currentMonth;
    }

    private void synchronizeEnvelopeMonth(Envelope envelope, String month) {
        if (envelope == null || month == null || month.isEmpty()) {
            return;
        }
        envelope.initializeMonth(month, false);
        if (Objects.equals(month, currentMonth)) {
            envelope.calculateRemaining(month);
        }
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

        String sourceTransactionMonth = resolveTransactionMonth(sourceTransaction);
        Envelope sourceHolder = findEnvelopeByName(sourceTransaction.getEnvelopeName());
        if (sourceHolder != null && sourceHolder != sourceEnvelope) {
            sourceHolder.getTransactions().remove(sourceTransaction);
            synchronizeEnvelopeMonth(sourceHolder, sourceTransactionMonth);
            sourceEnvelope.addTransaction(sourceTransaction, sourceTransactionMonth);
        }

        sourceTransaction.setEnvelopeName(sourceEnvelopeName);
        sourceTransaction.setDate(transaction.getDate());
        sourceTransaction.setComment(transaction.getComment());
        String updatedSourceMonth = resolveTransactionMonth(sourceTransaction);
        sourceEnvelope.updateTransaction(sourceTransaction, Math.abs(amount), updatedSourceMonth);
        synchronizeEnvelopeMonth(sourceEnvelope, sourceTransactionMonth);
        synchronizeEnvelopeMonth(sourceEnvelope, updatedSourceMonth);

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

        String mirrorTargetMonth = resolveTransactionMonth(sourceTransaction);
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
                    synchronizeEnvelopeMonth(envelope, resolveTransactionMonth(candidate));
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
            destinationEnvelope.addTransaction(mirror, mirrorTargetMonth);
            synchronizeEnvelopeMonth(destinationEnvelope, mirrorTargetMonth);
            return;
        }

        if (mirrorEnvelope != null && mirrorEnvelope != destinationEnvelope) {
            String previousMirrorMonth = resolveTransactionMonth(mirror);
            mirrorEnvelope.getTransactions().remove(mirror);
            synchronizeEnvelopeMonth(mirrorEnvelope, previousMirrorMonth);
            destinationEnvelope.addTransaction(mirror, previousMirrorMonth);
        }

        String previousMirrorMonth = resolveTransactionMonth(mirror);
        mirror.setEnvelopeName(destinationEnvelopeName);
        mirror.setDate(sourceTransaction.getDate());
        mirror.setComment(mirrorComment);
        String updatedMirrorMonth = resolveTransactionMonth(mirror);
        destinationEnvelope.updateTransaction(mirror, -Math.abs(amount), updatedMirrorMonth);
        synchronizeEnvelopeMonth(destinationEnvelope, previousMirrorMonth);
        synchronizeEnvelopeMonth(destinationEnvelope, updatedMirrorMonth);
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
    private void updateTransferTotalsPanel(List<TransferTotalsOption> options) {
        if (layoutTransferTotals == null || spinnerTransferTotals == null || tvTransferTotalsSummary == null) {
            return;
        }

        if (!showTransfers) {
            layoutTransferTotals.setVisibility(View.GONE);
            spinnerTransferTotals.setOnItemSelectedListener(null);
            return;
        }

        layoutTransferTotals.setVisibility(View.VISIBLE);

        options.sort((a, b) -> {
            int prefixCompare = a.labelPrefix.compareToIgnoreCase(b.labelPrefix);
            if (prefixCompare != 0) {
                return prefixCompare;
            }
            return a.envelopeName.compareToIgnoreCase(b.envelopeName);
        });

        if (options.isEmpty()) {
            spinnerTransferTotals.setOnItemSelectedListener(null);
            spinnerTransferTotals.setVisibility(View.GONE);
            tvTransferTotalsSummary.setText("No transfers in range");
            selectedTransferTotalsIndex = 0;
            PrefManager.clearLastTransferTotalsOptionKey(this);
            return;
        }

        List<String> labels = new ArrayList<>();
        for (TransferTotalsOption option : options) {
            labels.add(option.labelPrefix + " " + option.envelopeName);
        }

        String savedOptionKey = PrefManager.getLastTransferTotalsOptionKey(this);
        int restoredIndex = -1;
        if (savedOptionKey != null) {
            for (int i = 0; i < options.size(); i++) {
                if (Objects.equals(options.get(i).optionKey, savedOptionKey)) {
                    restoredIndex = i;
                    break;
                }
            }
        }
        if (restoredIndex >= 0) {
            selectedTransferTotalsIndex = restoredIndex;
        } else if (selectedTransferTotalsIndex < 0 || selectedTransferTotalsIndex >= options.size()) {
            selectedTransferTotalsIndex = 0;
        }
        PrefManager.setLastTransferTotalsOptionKey(this, options.get(selectedTransferTotalsIndex).optionKey);

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
                    PrefManager.setLastTransferTotalsOptionKey(MainActivity.this, options.get(position).optionKey);
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
            PrefManager.setLastTransferTotalsOptionKey(this, options.get(0).optionKey);
        }
    }

    private String formatTransferTotalsSummary(TransferTotalsOption option) {
        return String.format(Locale.getDefault(), "%s %s: $%.2f", option.labelPrefix, option.envelopeName, option.total);
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

































































