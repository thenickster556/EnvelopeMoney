package com.example.envelopemoney;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.envelopemoney.receipt.PaddleOcrAdapter;
import com.example.envelopemoney.receipt.ReceiptCaptureActivity;
import com.example.envelopemoney.receipt.ReceiptPickerUriNormalizer;
import com.example.envelopemoney.receipt.ReceiptPreviewActivity;
import com.example.envelopemoney.receipt.ReceiptCaptureMode;
import com.example.envelopemoney.receipt.ReceiptDraft;
import com.example.envelopemoney.receipt.ReceiptOcrPipeline;
import com.example.envelopemoney.receipt.ReceiptRowUi;
import com.example.envelopemoney.ui.BoundedNestedScrollView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.envelopemoney.BillsDayAnchor;
import com.example.envelopemoney.Envelope;
import com.example.envelopemoney.MonthRolloverHelper;
import com.example.envelopemoney.MonthTracker;
import com.example.envelopemoney.PrefManager;
import com.example.envelopemoney.R;
import com.example.envelopemoney.Transaction;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.io.IOException;
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
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final double TRANSFER_STEP_AMOUNT = 0.50d;
    private static final int TAB_TYPE_SPENDING = 0;
    private static final int TAB_TYPE_TRANSFER = 1;
    private static final int TAB_TYPE_SPLIT = 2;
    private static final int TAB_TIME_ONE_TIME = 0;
    private static final int TAB_TIME_RECURRING = 1;

    private final Set<String> expandedSplitGroupIds = new HashSet<>();
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
    private boolean billsPeriodFilterActive = false;
    private TextView tvPondTotalsFooter;

    /** Non-null while add or edit transaction dialog is open and owns receipt capture state. */
    @Nullable
    private View receiptDialogHostView;
    private ActivityResultLauncher<Intent> receiptCaptureLauncher;
    private ActivityResultLauncher<String> galleryPickLauncher;

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

    private static final class TransferDialogViews {
        final View section;
        final BoundedNestedScrollView scrollView;
        final MaterialAutoCompleteTextView sourceDropdown;
        final TextView allocatedSummary;
        final TextView spentHereSummary;
        final TextView remainingSummary;
        final TextView validationMessage;
        final LinearLayout bucketsContainer;
        final View addBucketButton;
        final List<TransferBucketRowController> bucketControllers = new ArrayList<>();
        boolean hasMeaningfulInteraction = false;
        boolean saveAttempted = false;
        @Nullable Button positiveButton;

        TransferDialogViews(View section,
                            BoundedNestedScrollView scrollView,
                            MaterialAutoCompleteTextView sourceDropdown,
                            TextView allocatedSummary,
                            TextView spentHereSummary,
                            TextView remainingSummary,
                            TextView validationMessage,
                            LinearLayout bucketsContainer,
                            View addBucketButton) {
            this.section = section;
            this.scrollView = scrollView;
            this.sourceDropdown = sourceDropdown;
            this.allocatedSummary = allocatedSummary;
            this.spentHereSummary = spentHereSummary;
            this.remainingSummary = remainingSummary;
            this.validationMessage = validationMessage;
            this.bucketsContainer = bucketsContainer;
            this.addBucketButton = addBucketButton;
        }
    }

    private final class TransferBucketRowController {
        private final View rootView;
        private final TextView titleView;
        private final MaterialAutoCompleteTextView destinationDropdown;
        private final View removeButton;
        private final View decreaseButton;
        private final View increaseButton;
        private final TextView amountView;
        private final Slider amountSlider;
        private final EditText manualAmountView;
        private final List<TextView> scaleLabelViews = new ArrayList<>();
        private final TransferBucketAllocation allocation;
        private boolean suppressCallbacks = false;
        private List<String> availableDestinations = new ArrayList<>();

        TransferBucketRowController(View rootView, TransferBucketAllocation allocation) {
            this.rootView = rootView;
            this.allocation = allocation;
            this.titleView = rootView.findViewById(R.id.tvTransferBucketTitle);
            this.destinationDropdown = rootView.findViewById(R.id.spinnerTransferBucketDestination);
            this.removeButton = rootView.findViewById(R.id.btnRemoveTransferBucket);
            this.decreaseButton = rootView.findViewById(R.id.btnTransferBucketDecrease);
            this.increaseButton = rootView.findViewById(R.id.btnTransferBucketIncrease);
            this.amountView = rootView.findViewById(R.id.tvTransferBucketAmount);
            this.amountSlider = rootView.findViewById(R.id.sliderTransferBucketAmount);
            this.manualAmountView = rootView.findViewById(R.id.etTransferBucketManualAmount);
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelStart));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelQuarter));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelHalf));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelThreeQuarter));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelEnd));
            configureDialogDropdown(destinationDropdown, () -> {
                if (activeTransferDialogViews != null) {
                    prepareDialogDropdownForOpen(activeTransferDialogViews, destinationDropdown, rootView);
                }
            });
            manualAmountView.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus && activeTransferDialogViews != null) {
                    scrollTransferDialogToView(activeTransferDialogViews, rootView, false);
                }
            });
            bindCallbacks();
            setAmountInternal(allocation.getAmount(), false);
        }

        TransferBucketAllocation getAllocation() {
            return allocation;
        }

        View getRootView() {
            return rootView;
        }

        void dismissDropdown() {
            destinationDropdown.dismissDropDown();
        }

        void setIndex(int index, boolean canRemove) {
            titleView.setText(String.format(Locale.getDefault(), "Bucket %d", index + 1));
            removeButton.setVisibility(canRemove ? View.VISIBLE : View.GONE);
        }

        void bindDestinations(String sourceEnvelopeName, @Nullable String selectedDestination) {
            availableDestinations = TransferDestinationList.excludingSource(envelopes, sourceEnvelopeName);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_list_item_1, availableDestinations);
            destinationDropdown.setAdapter(adapter);
            destinationDropdown.setEnabled(!availableDestinations.isEmpty());

            String targetDestination = selectedDestination;
            if (targetDestination == null || !availableDestinations.contains(targetDestination)) {
                targetDestination = availableDestinations.isEmpty() ? null : availableDestinations.get(0);
            }
            suppressCallbacks = true;
            if (targetDestination != null) {
                destinationDropdown.setText(targetDestination, false);
                allocation.setToEnvelope(targetDestination);
            } else {
                destinationDropdown.setText("", false);
                allocation.setToEnvelope(null);
            }
            suppressCallbacks = false;
        }

        void refreshSliderBounds(double totalAmount) {
            double maxAmountForBucket = Math.max(0d, totalAmount - allocatedExcluding(this));
            double sliderMaximum = Math.floor((maxAmountForBucket + 0.0001d) / TRANSFER_STEP_AMOUNT) * TRANSFER_STEP_AMOUNT;
            boolean sliderEnabled = sliderMaximum >= TRANSFER_STEP_AMOUNT;
            suppressCallbacks = true;
            amountSlider.setValueFrom(0f);
            amountSlider.setStepSize((float) TRANSFER_STEP_AMOUNT);
            amountSlider.setEnabled(sliderEnabled);
            amountSlider.setValueTo((float) Math.max(TRANSFER_STEP_AMOUNT, sliderMaximum));
            amountSlider.setValue(sliderEnabled
                    ? (float) TransferBucketUiHelper.snapToStep(allocation.getAmount(), TRANSFER_STEP_AMOUNT, sliderMaximum)
                    : 0f);
            suppressCallbacks = false;
            updateScaleLabels(maxAmountForBucket);
        }

        private void updateScaleLabels(double maxAmountForBucket) {
            int labelCount = TransferBucketUiHelper.recommendedScaleLabelCount(
                    rootView.getWidth(),
                    rootView.getResources().getDisplayMetrics().density);
            if (labelCount <= 3) {
                List<String> compactLabels = TransferBucketUiHelper.buildScaleLabels(maxAmountForBucket, 3);
                scaleLabelViews.get(0).setVisibility(View.VISIBLE);
                scaleLabelViews.get(0).setText(compactLabels.get(0));
                scaleLabelViews.get(1).setVisibility(View.GONE);
                scaleLabelViews.get(2).setVisibility(View.VISIBLE);
                scaleLabelViews.get(2).setText(compactLabels.get(1));
                scaleLabelViews.get(3).setVisibility(View.GONE);
                scaleLabelViews.get(4).setVisibility(View.VISIBLE);
                scaleLabelViews.get(4).setText(compactLabels.get(2));
                return;
            }

            List<String> labels = TransferBucketUiHelper.buildScaleLabels(maxAmountForBucket, scaleLabelViews.size());
            for (int i = 0; i < scaleLabelViews.size() && i < labels.size(); i++) {
                scaleLabelViews.get(i).setVisibility(View.VISIBLE);
                scaleLabelViews.get(i).setText(labels.get(i));
            }
        }

        private void bindCallbacks() {
            destinationDropdown.setOnItemClickListener((parent, view, position, id) -> {
                if (suppressCallbacks) {
                    return;
                }
                markTransferInteraction(activeTransferDialogViews);
                allocation.setToEnvelope(availableDestinations.get(position));
                updateTransferSectionSummary(activeTransferDialogViews);
            });
            removeButton.setOnClickListener(v -> {
                if (activeTransferDialogViews == null) {
                    return;
                }
                markTransferInteraction(activeTransferDialogViews);
                activeTransferDialogViews.bucketControllers.remove(this);
                activeTransferDialogViews.bucketsContainer.removeView(rootView);
                refreshTransferBucketLabels(activeTransferDialogViews);
                updateTransferSectionSummary(activeTransferDialogViews);
            });
            decreaseButton.setOnClickListener(v -> {
                markTransferInteraction(activeTransferDialogViews);
                double maxAllowed = Math.max(0d, parseAmountOrZero(activeTransferAmountInput) - allocatedExcluding(this));
                double next = TransferBucketUiHelper.snapToStep(
                        allocation.getAmount() - TRANSFER_STEP_AMOUNT,
                        TRANSFER_STEP_AMOUNT,
                        maxAllowed);
                setAmountInternal(next, true);
                updateTransferSectionSummary(activeTransferDialogViews);
            });
            increaseButton.setOnClickListener(v -> {
                markTransferInteraction(activeTransferDialogViews);
                double maxAllowed = Math.max(0d, parseAmountOrZero(activeTransferAmountInput) - allocatedExcluding(this));
                double next = TransferBucketUiHelper.snapToStep(
                        allocation.getAmount() + TRANSFER_STEP_AMOUNT,
                        TRANSFER_STEP_AMOUNT,
                        maxAllowed);
                setAmountInternal(next, true);
                updateTransferSectionSummary(activeTransferDialogViews);
            });
            amountSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(Slider slider) {
                    markTransferInteraction(activeTransferDialogViews);
                    if (activeTransferDialogViews != null) {
                        scrollTransferDialogToView(activeTransferDialogViews, rootView, false);
                    }
                }

                @Override
                public void onStopTrackingTouch(Slider slider) {
                }
            });
            amountSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (suppressCallbacks || !fromUser) {
                    return;
                }
                markTransferInteraction(activeTransferDialogViews);
                setAmountInternal(value, true);
                updateTransferSectionSummary(activeTransferDialogViews);
            });
            manualAmountView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (suppressCallbacks) {
                        return;
                    }
                    markTransferInteraction(activeTransferDialogViews);
                    allocation.setAmount(parseAmountOrZero(s == null ? null : s.toString()));
                    amountView.setText(formatCurrency(allocation.getAmount()));
                    updateTransferSectionSummary(activeTransferDialogViews);
                }
            });
        }

        private void setAmountInternal(double amount, boolean snapManualField) {
            allocation.setAmount(Math.max(0d, amount));
            amountView.setText(formatCurrency(allocation.getAmount()));
            if (snapManualField) {
                suppressCallbacks = true;
                manualAmountView.setText(String.format(Locale.getDefault(), "%.2f", allocation.getAmount()));
                manualAmountView.setSelection(manualAmountView.getText().length());
                suppressCallbacks = false;
            } else {
                suppressCallbacks = true;
                if (manualAmountView.getText().length() == 0 && allocation.getAmount() == 0d) {
                    manualAmountView.setText("");
                } else {
                    manualAmountView.setText(String.format(Locale.getDefault(), "%.2f", allocation.getAmount()));
                    manualAmountView.setSelection(manualAmountView.getText().length());
                }
                suppressCallbacks = false;
            }
        }
    }


    private static final class SplitDialogViews {
        final View section;
        final BoundedNestedScrollView scrollView;
        final TextView allocatedSummary;
        final TextView validationMessage;
        final LinearLayout bucketsContainer;
        final View addBucketButton;
        final List<SplitPurchaseBucketRowController> bucketControllers = new ArrayList<>();
        boolean hasMeaningfulInteraction = false;
        boolean saveAttempted = false;
        @Nullable Button positiveButton;

        SplitDialogViews(View section,
                         BoundedNestedScrollView scrollView,
                         TextView allocatedSummary,
                         TextView validationMessage,
                         LinearLayout bucketsContainer,
                         View addBucketButton) {
            this.section = section;
            this.scrollView = scrollView;
            this.allocatedSummary = allocatedSummary;
            this.validationMessage = validationMessage;
            this.bucketsContainer = bucketsContainer;
            this.addBucketButton = addBucketButton;
        }
    }

    private final class SplitPurchaseBucketRowController {
        private final View rootView;
        private final TextView titleView;
        private final MaterialAutoCompleteTextView pondDropdown;
        private final View removeButton;
        private final View decreaseButton;
        private final View increaseButton;
        private final TextView amountView;
        private final Slider amountSlider;
        private final EditText manualAmountView;
        private final List<TextView> scaleLabelViews = new ArrayList<>();
        private final SplitPurchaseSliceAllocation allocation;
        private boolean suppressCallbacks = false;
        private List<String> availablePonds = new ArrayList<>();

        SplitPurchaseBucketRowController(View rootView, SplitPurchaseSliceAllocation allocation) {
            this.rootView = rootView;
            this.allocation = allocation;
            this.titleView = rootView.findViewById(R.id.tvTransferBucketTitle);
            this.pondDropdown = rootView.findViewById(R.id.spinnerTransferBucketDestination);
            this.removeButton = rootView.findViewById(R.id.btnRemoveTransferBucket);
            this.decreaseButton = rootView.findViewById(R.id.btnTransferBucketDecrease);
            this.increaseButton = rootView.findViewById(R.id.btnTransferBucketIncrease);
            this.amountView = rootView.findViewById(R.id.tvTransferBucketAmount);
            this.amountSlider = rootView.findViewById(R.id.sliderTransferBucketAmount);
            this.manualAmountView = rootView.findViewById(R.id.etTransferBucketManualAmount);
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelStart));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelQuarter));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelHalf));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelThreeQuarter));
            scaleLabelViews.add(rootView.findViewById(R.id.tvTransferScaleLabelEnd));
            configureDialogDropdown(pondDropdown, () -> {
                if (activeSplitDialogViews != null) {
                    prepareSplitDialogDropdownForOpen(activeSplitDialogViews, pondDropdown, rootView);
                }
            });
            manualAmountView.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus && activeSplitDialogViews != null) {
                    scrollSplitDialogToView(activeSplitDialogViews, rootView, false);
                }
            });
            bindCallbacks();
            setAmountInternal(allocation.getAmount(), false);
        }

        SplitPurchaseSliceAllocation getAllocation() {
            return allocation;
        }

        View getRootView() {
            return rootView;
        }

        MaterialAutoCompleteTextView getPondDropdown() {
            return pondDropdown;
        }

        void dismissDropdown() {
            pondDropdown.dismissDropDown();
        }

        void setIndex(int index, boolean canRemove) {
            titleView.setText(String.format(Locale.getDefault(), "Slice %d", index + 1));
            removeButton.setVisibility(canRemove ? View.VISIBLE : View.GONE);
        }

        void bindPonds(List<String> allPondNames, @Nullable String selectedPond) {
            availablePonds = allPondNames == null ? new ArrayList<>() : new ArrayList<>(allPondNames);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_list_item_1, availablePonds);
            pondDropdown.setAdapter(adapter);
            pondDropdown.setEnabled(!availablePonds.isEmpty());

            String target = selectedPond;
            if (target == null || !availablePonds.contains(target)) {
                target = availablePonds.isEmpty() ? null : availablePonds.get(0);
            }
            suppressCallbacks = true;
            if (target != null) {
                pondDropdown.setText(target, false);
                allocation.setPondName(target);
            } else {
                pondDropdown.setText("", false);
                allocation.setPondName(null);
            }
            suppressCallbacks = false;
        }

        void refreshSliderBounds(double totalAmount) {
            double maxAmountForBucket = Math.max(0d, totalAmount - splitAllocatedExcluding(this));
            double sliderMaximum = Math.floor((maxAmountForBucket + 0.0001d) / TRANSFER_STEP_AMOUNT) * TRANSFER_STEP_AMOUNT;
            boolean sliderEnabled = sliderMaximum >= TRANSFER_STEP_AMOUNT;
            suppressCallbacks = true;
            amountSlider.setValueFrom(0f);
            amountSlider.setStepSize((float) TRANSFER_STEP_AMOUNT);
            amountSlider.setEnabled(sliderEnabled);
            amountSlider.setValueTo((float) Math.max(TRANSFER_STEP_AMOUNT, sliderMaximum));
            amountSlider.setValue(sliderEnabled
                    ? (float) TransferBucketUiHelper.snapToStep(allocation.getAmount(), TRANSFER_STEP_AMOUNT, sliderMaximum)
                    : 0f);
            suppressCallbacks = false;
            updateScaleLabels(maxAmountForBucket);
        }

        private void updateScaleLabels(double maxAmountForBucket) {
            int labelCount = TransferBucketUiHelper.recommendedScaleLabelCount(
                    rootView.getWidth(),
                    rootView.getResources().getDisplayMetrics().density);
            if (labelCount <= 3) {
                List<String> compactLabels = TransferBucketUiHelper.buildScaleLabels(maxAmountForBucket, 3);
                scaleLabelViews.get(0).setVisibility(View.VISIBLE);
                scaleLabelViews.get(0).setText(compactLabels.get(0));
                scaleLabelViews.get(1).setVisibility(View.GONE);
                scaleLabelViews.get(2).setVisibility(View.VISIBLE);
                scaleLabelViews.get(2).setText(compactLabels.get(1));
                scaleLabelViews.get(3).setVisibility(View.GONE);
                scaleLabelViews.get(4).setVisibility(View.VISIBLE);
                scaleLabelViews.get(4).setText(compactLabels.get(2));
                return;
            }

            List<String> labels = TransferBucketUiHelper.buildScaleLabels(maxAmountForBucket, scaleLabelViews.size());
            for (int i = 0; i < scaleLabelViews.size() && i < labels.size(); i++) {
                scaleLabelViews.get(i).setVisibility(View.VISIBLE);
                scaleLabelViews.get(i).setText(labels.get(i));
            }
        }

        private void bindCallbacks() {
            pondDropdown.setOnItemClickListener((parent, view, position, id) -> {
                if (suppressCallbacks) {
                    return;
                }
                markSplitInteraction(activeSplitDialogViews);
                allocation.setPondName(availablePonds.get(position));
                updateSplitSectionSummary(activeSplitDialogViews);
            });
            removeButton.setOnClickListener(v -> {
                if (activeSplitDialogViews == null) {
                    return;
                }
                markSplitInteraction(activeSplitDialogViews);
                activeSplitDialogViews.bucketControllers.remove(this);
                activeSplitDialogViews.bucketsContainer.removeView(rootView);
                refreshSplitBucketLabels(activeSplitDialogViews);
                updateSplitSectionSummary(activeSplitDialogViews);
            });
            decreaseButton.setOnClickListener(v -> {
                markSplitInteraction(activeSplitDialogViews);
                double maxAllowed = Math.max(0d, parseAmountOrZero(activeSplitTotalInput) - splitAllocatedExcluding(this));
                double next = TransferBucketUiHelper.snapToStep(
                        allocation.getAmount() - TRANSFER_STEP_AMOUNT,
                        TRANSFER_STEP_AMOUNT,
                        maxAllowed);
                setAmountInternal(next, true);
                updateSplitSectionSummary(activeSplitDialogViews);
            });
            increaseButton.setOnClickListener(v -> {
                markSplitInteraction(activeSplitDialogViews);
                double maxAllowed = Math.max(0d, parseAmountOrZero(activeSplitTotalInput) - splitAllocatedExcluding(this));
                double next = TransferBucketUiHelper.snapToStep(
                        allocation.getAmount() + TRANSFER_STEP_AMOUNT,
                        TRANSFER_STEP_AMOUNT,
                        maxAllowed);
                setAmountInternal(next, true);
                updateSplitSectionSummary(activeSplitDialogViews);
            });
            amountSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(Slider slider) {
                    markSplitInteraction(activeSplitDialogViews);
                    if (activeSplitDialogViews != null) {
                        scrollSplitDialogToView(activeSplitDialogViews, rootView, false);
                    }
                }

                @Override
                public void onStopTrackingTouch(Slider slider) {
                }
            });
            amountSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (suppressCallbacks || !fromUser) {
                    return;
                }
                markSplitInteraction(activeSplitDialogViews);
                setAmountInternal(value, true);
                updateSplitSectionSummary(activeSplitDialogViews);
            });
            manualAmountView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (suppressCallbacks) {
                        return;
                    }
                    markSplitInteraction(activeSplitDialogViews);
                    allocation.setAmount(parseAmountOrZero(s == null ? null : s.toString()));
                    amountView.setText(formatCurrency(allocation.getAmount()));
                    updateSplitSectionSummary(activeSplitDialogViews);
                }
            });
        }

        private void setAmountInternal(double amount, boolean snapManualField) {
            allocation.setAmount(Math.max(0d, amount));
            amountView.setText(formatCurrency(allocation.getAmount()));
            if (snapManualField) {
                suppressCallbacks = true;
                manualAmountView.setText(String.format(Locale.getDefault(), "%.2f", allocation.getAmount()));
                manualAmountView.setSelection(manualAmountView.getText().length());
                suppressCallbacks = false;
            } else {
                suppressCallbacks = true;
                if (manualAmountView.getText().length() == 0 && allocation.getAmount() == 0d) {
                    manualAmountView.setText("");
                } else {
                    manualAmountView.setText(String.format(Locale.getDefault(), "%.2f", allocation.getAmount()));
                    manualAmountView.setSelection(manualAmountView.getText().length());
                }
                suppressCallbacks = false;
            }
        }
    }

    @Nullable
    private SplitDialogViews activeSplitDialogViews;
    @Nullable
    private EditText activeSplitTotalInput;
    @Nullable
    private TransferDialogViews activeTransferDialogViews;
    @Nullable
    private EditText activeTransferAmountInput;
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        receiptCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    Intent data = result.getData();
                    String uriStr = data.getStringExtra(ReceiptCaptureActivity.EXTRA_SAVED_IMAGE_URI);
                    String modeName = data.getStringExtra(ReceiptCaptureActivity.EXTRA_CAPTURE_MODE);
                    ReceiptCaptureMode mode = ReceiptCaptureMode.AUTO;
                    if (modeName != null) {
                        try {
                            mode = ReceiptCaptureMode.valueOf(modeName);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (uriStr != null && receiptDialogHostView != null) {
                        runReceiptOcr(Uri.parse(uriStr), mode);
                    }
                });
        galleryPickLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && receiptDialogHostView != null) {
                        runReceiptOcr(uri, ReceiptCaptureMode.AUTO);
                    }
                });
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

        ImageButton btnBillsPeriodFilter = findViewById(R.id.btnBillsPeriodFilter);
        billsPeriodFilterActive = PrefManager.isBillsFilterActive(this);
        btnBillsPeriodFilter.setOnClickListener(v -> toggleBillsPeriodFilter());
        updateBillsPeriodFilterButton(btnBillsPeriodFilter);

        ImageButton btnBillsDaysSetup = findViewById(R.id.btnBillsDaysSetup);
        btnBillsDaysSetup.setOnClickListener(v -> showBillsDaysPickerDialog());
        expandTouchTarget(btnBillsDaysSetup, 8);

        ImageButton btnRecalculateBalances = findViewById(R.id.btnRecalculateBalances);
        btnRecalculateBalances.setOnClickListener(v -> showResetConfirmationDialog());
        expandTouchTarget(btnRecalculateBalances, 8);

        ImageButton btnAddEnvelope = findViewById(R.id.btnAddEnvelope);
        btnAddEnvelope.setOnClickListener(v -> showEnvelopeDialog(null));

        btnToggleEnvelopes = findViewById(R.id.btnToggleEnvelopes);
        listViewEnvelopes = findViewById(R.id.listViewEnvelopes);
        layoutEnvelopesSection = findViewById(R.id.layoutEnvelopesSection);
        envelopesCollapsed = PrefManager.isEnvelopesCollapsed(this);
        applyEnvelopesCollapsedState();
        expandTouchTarget(btnToggleEnvelopes, 8);
        btnToggleEnvelopes.setOnClickListener(v -> {
            envelopesCollapsed = !envelopesCollapsed;
            PrefManager.setEnvelopesCollapsed(MainActivity.this, envelopesCollapsed);
            applyEnvelopesCollapsedState();
        });
        listViewTransactions = findViewById(R.id.listViewTransactions);
        layoutTransferTotals = findViewById(R.id.layoutTransferTotals);
        spinnerTransferTotals = findViewById(R.id.spinnerTransferTotals);
        tvTransferTotalsSummary = findViewById(R.id.tvTransferTotalsSummary);
        tvPondTotalsFooter = findViewById(R.id.tvPondTotalsFooter);

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
        applyPersistedBillsFilterState();
        updatePondTotalsFooter();

        // Initialize adapters
        transactionAdapter = new TransactionAdapter(this, allTransactions);
        listViewTransactions.setAdapter(transactionAdapter);
        envelopeAdapter = new EnvelopeAdapter(this, envelopes);
        listViewEnvelopes.setAdapter(envelopeAdapter);



        updateTransactionHistory();
        updatePondTotalsFooter();
    }

    private void applyEnvelopesCollapsedState() {
        if (layoutEnvelopesSection == null || btnToggleEnvelopes == null) {
            return;
        }
        layoutEnvelopesSection.setVisibility(envelopesCollapsed ? View.GONE : View.VISIBLE);
        btnToggleEnvelopes.setImageResource(envelopesCollapsed ? android.R.drawable.arrow_down_float : android.R.drawable.arrow_up_float);
    }

    /**
     * Expands the effective hit box without changing the visual icon size, which makes taps register
     * reliably on smaller screens and when the user is pressing near the icon edge.
     */
    private void expandTouchTarget(View target, int extraPaddingDp) {
        if (target == null) {
            return;
        }
        View parent = (View) target.getParent();
        if (parent == null) {
            return;
        }
        final int extraPaddingPx = Math.round(extraPaddingDp * getResources().getDisplayMetrics().density);
        parent.post(() -> {
            Rect hitRect = new Rect();
            target.getHitRect(hitRect);
            hitRect.top -= extraPaddingPx;
            hitRect.bottom += extraPaddingPx;
            hitRect.left -= extraPaddingPx;
            hitRect.right += extraPaddingPx;
            parent.setTouchDelegate(new TouchDelegate(hitRect, target));
        });
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
        clearBillsPeriodFilterState();
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

    private void refreshDataForMonth() {
        for (Envelope env : envelopes) {
            env.getMonthlyData(currentMonth);
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

    private static double safe(Double v) {
        if (v == null) return 0d;
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0d;
        return v;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void showNewTransactionDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_transaction, null);
        receiptDialogHostView = dialogView;

        MaterialAutoCompleteTextView spinnerEnvelope = dialogView.findViewById(R.id.spinnerEditEnvelope);
        EditText etDate = dialogView.findViewById(R.id.etEditTransactionDate);
        EditText etAmount = dialogView.findViewById(R.id.etEditTransactionAmount);
        EditText etSplitTotal = dialogView.findViewById(R.id.etSplitPurchaseTotal);
        EditText etComment = dialogView.findViewById(R.id.etEditTransactionComment);
        TransferDialogViews transferViews = createTransferDialogViews(dialogView);
        SplitDialogViews splitViews = createSplitDialogViews(dialogView);
        TabLayout tabTransactionType = dialogView.findViewById(R.id.tabTransactionType);
        TabLayout tabTransactionTime = dialogView.findViewById(R.id.tabTransactionTime);
        LinearLayout panelSpending = dialogView.findViewById(R.id.panelSpending);
        LinearLayout panelTransfer = dialogView.findViewById(R.id.panelTransfer);
        View panelSplit = dialogView.findViewById(R.id.panelSplitPurchase);
        LinearLayout layoutRowPond = dialogView.findViewById(R.id.layoutRowPond);
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

        tabTransactionType.addTab(tabTransactionType.newTab().setText(R.string.tab_transaction_type_spending));
        tabTransactionType.addTab(tabTransactionType.newTab().setText(R.string.tab_transaction_type_transfer));
        tabTransactionType.addTab(tabTransactionType.newTab().setText(R.string.tab_transaction_type_split_purchase));
        tabTransactionTime.addTab(tabTransactionTime.newTab().setText(R.string.tab_transaction_time_one_time));
        tabTransactionTime.addTab(tabTransactionTime.newTab().setText(R.string.tab_transaction_time_recurring));

        bindDialogDropdownOptions(spinnerEnvelope, getEnvelopeNames());
        String savedSourceEnvelope = PrefManager.getLastAddTransactionEnvelope(this);
        List<String> envelopeNames = getEnvelopeNames();
        if (savedSourceEnvelope != null && envelopeNames.contains(savedSourceEnvelope)) {
            spinnerEnvelope.setText(savedSourceEnvelope, false);
        } else if (!envelopeNames.isEmpty()) {
            spinnerEnvelope.setText(envelopeNames.get(0), false);
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

        String initialSource = getSelectedDropdownValue(spinnerEnvelope);
        String savedDestination = PrefManager.getLastAddTransferDestination(this, initialSource);
        initializeTransferDialogSection(transferViews, etAmount, spinnerEnvelope, savedDestination, null, false);
        initializeSplitDialogSection(splitViews, etSplitTotal, null, false);
        attachTransactionDialogScrollDismiss(transferViews, splitViews);

        cbIsRecurring.setOnCheckedChangeListener((buttonView, checked) -> {
            if (tabTransactionTime.getSelectedTabPosition() != (checked ? TAB_TIME_RECURRING : TAB_TIME_ONE_TIME)) {
                Objects.requireNonNull(tabTransactionTime.getTabAt(checked ? TAB_TIME_RECURRING : TAB_TIME_ONE_TIME)).select();
            }
            setRecurringControlsVisibility(checked,
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });
        tabTransactionTime.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean recurring = tab.getPosition() == TAB_TIME_RECURRING;
                if (cbIsRecurring.isChecked() != recurring) {
                    cbIsRecurring.setChecked(recurring);
                } else {
                    setRecurringControlsVisibility(recurring,
                            tvRecurringFrequencyLabel,
                            layoutRecurringFrequencyOptions,
                            tvRecurringDaysLabel,
                            layoutRecurringWeekdayButtons,
                            tvRecurringDaysValue,
                            selectedRecurringFrequency[0]);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        tabTransactionType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                applyTransactionDialogTypeState(tab.getPosition(),
                        tabTransactionTime,
                        panelSpending,
                        panelTransfer,
                        panelSplit,
                        layoutRowPond,
                        etAmount,
                        etSplitTotal,
                        cbIsRecurring,
                        tvRecurringFrequencyLabel,
                        layoutRecurringFrequencyOptions,
                        tvRecurringDaysLabel,
                        layoutRecurringWeekdayButtons,
                        tvRecurringDaysValue,
                        selectedRecurringFrequency[0],
                        transferViews,
                        splitViews);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        Objects.requireNonNull(tabTransactionTime.getTabAt(TAB_TIME_ONE_TIME)).select();
        Objects.requireNonNull(tabTransactionType.getTabAt(TAB_TYPE_SPENDING)).select();

        wireReceiptRow(dialogView, null);

        builder.setView(dialogView)
                .setTitle("New Transaction")
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            if (receiptDialogHostView == dialogView) {
                receiptDialogHostView = null;
            }
            if (activeTransferDialogViews == transferViews) {
                activeTransferDialogViews = null;
                activeTransferAmountInput = null;
            }
            if (activeSplitDialogViews == splitViews) {
                activeSplitDialogViews = null;
                activeSplitTotalInput = null;
            }
        });
        dialog.setOnShowListener(ignored -> {
            applyIconMaterialDialogActions(dialog);
            configureTransactionDialogWindow(dialog);
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            transferViews.positiveButton = positive;
            splitViews.positiveButton = positive;
            updateTransferSectionSummary(transferViews);
            updateSplitSectionSummary(splitViews);
            positive.setOnClickListener(v -> {
            try {
                int typeTab = tabTransactionType.getSelectedTabPosition();
                String comment = etComment.getText().toString();
                String date = etDate.getText().toString();
                Object uriTag = dialogView.getTag(R.id.tag_receipt_image_uri);
                String receiptUri = uriTag instanceof String ? (String) uriTag : null;

                if (typeTab == TAB_TYPE_SPLIT) {
                    double total = parseAmountOrZero(etSplitTotal);
                    List<SplitPurchaseSliceAllocation> slices = snapshotSplitAllocations(splitViews);
                    TransferGroupValidationResult validation = SplitPurchaseGroupDraft.validate(total, slices);
                    if (!validation.isValid()) {
                        splitViews.saveAttempted = true;
                        updateSplitSectionSummary(splitViews);
                        return;
                    }
                    Set<String> months = SplitPurchaseSyncHelper.applyGroup(
                            envelopes, null, date, comment, receiptUri, slices, currentMonth);
                    for (String m : months) {
                        synchronizeAllEnvelopesForMonth(m);
                    }
                    PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                    updateDisplay();
                    dialog.dismiss();
                    return;
                }

                if (typeTab == TAB_TYPE_TRANSFER) {
                    String envelopeName = getSelectedDropdownValue(spinnerEnvelope);
                    if (envelopeName == null || envelopeName.isEmpty()) {
                        showError("Select a pond");
                        return;
                    }
                    double amount = Double.parseDouble(etAmount.getText().toString());
                    Transaction newTransaction = new Transaction(envelopeName, amount, date, comment);
                    if (receiptUri != null && !receiptUri.isEmpty()) {
                        newTransaction.setReceiptImageUri(receiptUri);
                    }
                    List<TransferBucketAllocation> allocations = snapshotTransferAllocations(transferViews);
                    TransferGroupValidationResult validation = TransferGroupDraft.validate(amount, envelopeName, allocations);
                    if (!validation.isValid()) {
                        transferViews.saveAttempted = true;
                        updateTransferSectionSummary(transferViews);
                        return;
                    }
                    Envelope env = findEnvelopeByName(envelopeName);
                    if (env == null) {
                        showError("Envelope not found");
                        return;
                    }
                    PrefManager.setLastAddTransactionEnvelope(MainActivity.this, envelopeName);
                    PrefManager.setLastAddTransferDestination(MainActivity.this, envelopeName, allocations.get(0).getToEnvelope());
                    env.addTransaction(newTransaction, currentMonth);
                    TransferSyncHelper.applyTransferGroup(envelopes, newTransaction, envelopeName, allocations);
                    synchronizeAllEnvelopesForMonth(resolveTransactionMonth(newTransaction));
                    PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                    updateDisplay();
                    dialog.dismiss();
                    return;
                }

                String envelopeName = getSelectedDropdownValue(spinnerEnvelope);
                if (envelopeName == null || envelopeName.isEmpty()) {
                    showError("Select a pond");
                    return;
                }
                double amount = Double.parseDouble(etAmount.getText().toString());

                Transaction newTransaction = new Transaction(envelopeName, amount, date, comment);
                if (receiptUri != null && !receiptUri.isEmpty()) {
                    newTransaction.setReceiptImageUri(receiptUri);
                }
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

                PrefManager.setLastAddTransactionEnvelope(MainActivity.this, envelopeName);
                env.addTransaction(newTransaction, currentMonth);
                PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                updateDisplay();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                showError("Invalid amount entered!");
            }
        });
        });
        dialog.show();
    }

    private void runReceiptOcr(Uri imageUri, ReceiptCaptureMode mode) {
        if (receiptDialogHostView == null || imageUri == null) {
            return;
        }
        final TextView status = receiptDialogHostView.findViewById(R.id.tvReceiptOcrStatus);
        if (status != null) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.receipt_ocr_reading);
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            Uri uri = imageUri;
            try {
                uri = ReceiptPickerUriNormalizer.normalize(MainActivity.this, uri);
            } catch (IOException e) {
                Log.e("EnvelopeMoney", "receipt persist picker uri", e);
                runOnUiThread(() -> {
                    if (status != null) {
                        status.setText(R.string.receipt_ocr_failed);
                    }
                    Toast.makeText(MainActivity.this, R.string.receipt_ocr_failed, Toast.LENGTH_LONG).show();
                });
                return;
            }
            final Uri finalUri = uri;
            runOnUiThread(() -> {
                receiptDialogHostView.setTag(R.id.tag_receipt_image_uri, finalUri.toString());
                syncReceiptActionUi(receiptDialogHostView);
            });
            Bitmap bmp;
            try (java.io.InputStream is = getContentResolver().openInputStream(finalUri)) {
                bmp = is != null ? BitmapFactory.decodeStream(is) : null;
            } catch (java.io.IOException | SecurityException e) {
                Log.e("EnvelopeMoney", "receipt decode", e);
                bmp = null;
            }
            if (bmp == null) {
                runOnUiThread(() -> {
                    if (status != null) {
                        status.setText(R.string.receipt_ocr_failed);
                    }
                    Toast.makeText(MainActivity.this, R.string.receipt_ocr_failed, Toast.LENGTH_LONG).show();
                });
                return;
            }
            final Bitmap bitmap = bmp;
            ReceiptOcrPipeline pipeline = new ReceiptOcrPipeline(PaddleOcrAdapter.createDefaultEngine());
            pipeline.runAsync(this, bitmap, mode, new ReceiptOcrPipeline.PipelineCallback() {
                @Override
                public void onResult(ReceiptDraft draft) {
                    bitmap.recycle();
                    runOnUiThread(() -> applyReceiptDraft(draft, status));
                }

                @Override
                public void onError(Throwable t) {
                    bitmap.recycle();
                    runOnUiThread(() -> {
                        if (status != null) {
                            status.setText(R.string.receipt_ocr_failed);
                        }
                        Toast.makeText(MainActivity.this, R.string.receipt_ocr_failed, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void applyReceiptDraft(ReceiptDraft draft, TextView status) {
        if (receiptDialogHostView == null || draft == null) {
            return;
        }
        EditText etAmount = receiptDialogHostView.findViewById(R.id.etEditTransactionAmount);
        EditText etSplitTotal = receiptDialogHostView.findViewById(R.id.etSplitPurchaseTotal);
        EditText etComment = receiptDialogHostView.findViewById(R.id.etEditTransactionComment);
        EditText etDate = receiptDialogHostView.findViewById(R.id.etEditTransactionDate);
        if (draft.totalAmount != null) {
            if (etSplitTotal != null && etSplitTotal.getVisibility() == View.VISIBLE) {
                etSplitTotal.setText(String.format(Locale.getDefault(), "%.2f", draft.totalAmount));
            } else if (etAmount != null) {
                etAmount.setText(String.format(Locale.getDefault(), "%.2f", draft.totalAmount));
            }
        }
        if (draft.merchantForComment != null && etComment != null) {
            etComment.setText(draft.merchantForComment);
        }
        if (draft.dateYyyyMmDd != null && etDate != null) {
            etDate.setText(draft.dateYyyyMmDd);
        }
        if (status != null) {
            if (draft.amountConfidence < 0.45f) {
                status.setVisibility(View.VISIBLE);
                status.setText("Check amount — low confidence");
            } else {
                status.setVisibility(View.GONE);
            }
        }
        syncReceiptActionUi(receiptDialogHostView);
    }

    private void syncReceiptActionUi(@Nullable View host) {
        if (host == null) {
            return;
        }
        Object tag = host.getTag(R.id.tag_receipt_image_uri);
        boolean has = tag instanceof String && !((String) tag).isEmpty();
        View preview = host.findViewById(R.id.btnReceiptPreview);
        View remove = host.findViewById(R.id.btnReceiptRemove);
        if (preview != null) {
            preview.setEnabled(has);
        }
        if (remove != null) {
            remove.setEnabled(has);
        }
    }

    private void wireReceiptRow(View dialogView, @Nullable String initialUri) {
        if (initialUri != null && !initialUri.isEmpty()) {
            dialogView.setTag(R.id.tag_receipt_image_uri, initialUri);
        } else {
            dialogView.setTag(R.id.tag_receipt_image_uri, null);
        }
        View btnReceiptCamera = dialogView.findViewById(R.id.btnReceiptCamera);
        View btnReceiptGallery = dialogView.findViewById(R.id.btnReceiptGallery);
        if (btnReceiptCamera != null) {
            btnReceiptCamera.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ReceiptCaptureActivity.class);
                receiptCaptureLauncher.launch(intent);
            });
        }
        if (btnReceiptGallery != null) {
            btnReceiptGallery.setOnClickListener(v -> galleryPickLauncher.launch("image/*"));
        }
        View btnPreview = dialogView.findViewById(R.id.btnReceiptPreview);
        if (btnPreview != null) {
            btnPreview.setOnClickListener(v -> {
                Object u = dialogView.getTag(R.id.tag_receipt_image_uri);
                if (u instanceof String && !((String) u).isEmpty()) {
                    showReceiptImagePreview(Uri.parse((String) u));
                }
            });
        }
        View btnRemove = dialogView.findViewById(R.id.btnReceiptRemove);
        if (btnRemove != null) {
            btnRemove.setOnClickListener(v -> {
                dialogView.setTag(R.id.tag_receipt_image_uri, null);
                syncReceiptActionUi(dialogView);
                TextView st = dialogView.findViewById(R.id.tvReceiptOcrStatus);
                if (st != null) {
                    st.setVisibility(View.GONE);
                }
            });
        }
        syncReceiptActionUi(dialogView);
    }

    private void showReceiptImagePreview(Uri uri) {
        if (uri == null) {
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            Uri resolved = uri;
            try {
                resolved = ReceiptPickerUriNormalizer.normalize(MainActivity.this, resolved);
            } catch (IOException e) {
                Log.e("EnvelopeMoney", "receipt preview uri", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        R.string.receipt_preview_load_failed, Toast.LENGTH_LONG).show());
                return;
            }
            final Uri out = resolved;
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, ReceiptPreviewActivity.class)
                    .putExtra(ReceiptPreviewActivity.EXTRA_IMAGE_URI, out.toString())));
        });
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
        Map<String, List<TransferBucketAllocation>> transferAllocationsById = new HashMap<>();
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
                    Envelope ownerEnvelope = null;
                    boolean isTransfer = transferId != null && !transferId.isEmpty();
                    boolean isSourceSide = false;
                    boolean ownerSelected = false;
                    boolean includeTransaction = envelopeSelected;
                    List<TransferBucketAllocation> allocations = new ArrayList<>();

                    if (isTransfer) {
                        allocations = transferAllocationsById.computeIfAbsent(
                                transferId,
                                id -> TransferSyncHelper.getAllocations(envelopes, id));
                        ownerEnvelope = findTransferOwner(transferId);
                        isSourceSide = ownerEnvelope != null
                                && Objects.equals(ownerEnvelope.getName(), transaction.getEnvelopeName())
                                && (transaction.getTransferBucketId() == null || transaction.getTransferBucketId().isEmpty());
                        ownerSelected = ownerEnvelope != null && ownerEnvelope.isSelected();
                        boolean anyDestinationSelected = false;
                        for (TransferBucketAllocation allocation : allocations) {
                            Envelope destinationEnvelope = findEnvelopeByName(allocation.getToEnvelope());
                            if (destinationEnvelope != null && destinationEnvelope.isSelected()) {
                                anyDestinationSelected = true;
                                break;
                            }
                        }
                        if (!includeTransaction && showTransfers && (ownerSelected || anyDestinationSelected)) {
                            includeTransaction = true;
                        }
                    }

                    if (!includeTransaction) {
                        continue;
                    }

                    filteredTransactions.add(transaction);
                    grossTotal += transaction.getAmount();

                    if (isTransfer && !allocations.isEmpty()) {
                        if (isSourceSide) {
                            double allocatedTotal = TransferGroupDraft.allocatedTotal(allocations);
                            outgoingTransferTotal += allocatedTotal;
                            for (TransferBucketAllocation allocation : allocations) {
                                Envelope destinationEnvelope = findEnvelopeByName(allocation.getToEnvelope());
                                boolean destinationSelected = destinationEnvelope != null && destinationEnvelope.isSelected();
                                String summaryKey = "to:" + allocation.getToEnvelope();
                                String relatedEnvelopeName = allocation.getToEnvelope();
                                TransferTotalsOption existing = transferTotalsByEnvelope.get(summaryKey);
                                double running = existing != null ? existing.total : 0d;
                                running += Math.abs(allocation.getAmount());
                                if (destinationSelected) {
                                    running -= Math.abs(allocation.getAmount());
                                }
                                transferTotalsByEnvelope.put(summaryKey,
                                        new TransferTotalsOption(summaryKey, "To", relatedEnvelopeName, running));
                            }
                        } else {
                            incomingTransferTotal += Math.abs(transaction.getAmount());
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
        updatePondTotalsFooter();
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
            ImageButton btnReceipt = convertView.findViewById(R.id.btnTransactionReceipt);
            ImageButton btnSplitExpand = convertView.findViewById(R.id.btnSplitExpand);
            TextView tvSplitBreakdown = convertView.findViewById(R.id.tvSplitBreakdown);
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
            if (transaction.getTransferId() != null && !transaction.getTransferId().isEmpty()
                    && (transaction.getTransferBucketId() == null || transaction.getTransferBucketId().isEmpty())) {
                double allocated = TransferSyncHelper.allocatedTotal(envelopes, transaction.getTransferId());
                double spentHere = transaction.getAmount() - allocated;
                details += String.format(Locale.getDefault(),
                        " | Transfer allocated $%.2f | Spent here $%.2f",
                        allocated,
                        spentHere);
            }
            if (transaction.isRecurring() && transaction.getRecurringFrequency() != null && !transaction.getRecurringFrequency().isEmpty()) {
                details += " | " + recurringFrequencyDisplay(normalizeRecurringFrequency(transaction.getRecurringFrequency()));
            }
            tvDetails.setText(details);

            boolean splitRow = SplitPurchaseSyncHelper.isSplitPurchase(transaction);
            if (splitRow) {
                btnSplitExpand.setVisibility(View.VISIBLE);
                String gid = transaction.getSplitPurchaseGroupId();
                boolean expanded = expandedSplitGroupIds.contains(gid);
                tvSplitBreakdown.setVisibility(expanded ? View.VISIBLE : View.GONE);
                btnSplitExpand.setRotation(expanded ? 180f : 0f);
                btnSplitExpand.setContentDescription(expanded
                        ? getString(R.string.content_desc_split_collapse)
                        : getString(R.string.content_desc_split_expand));
                List<Transaction> peers = SplitPurchaseSyncHelper.findTransactionsInGroup(envelopes, gid);
                double groupTotal = SplitPurchaseSyncHelper.groupTotal(peers);
                String header = getString(R.string.split_breakdown_total_line, groupTotal);
                tvSplitBreakdown.setText(header + "\n" + SplitPurchaseSyncHelper.formatBreakdownLine(peers));
                btnSplitExpand.setOnClickListener(v -> {
                    if (expandedSplitGroupIds.contains(gid)) {
                        expandedSplitGroupIds.remove(gid);
                    } else {
                        expandedSplitGroupIds.add(gid);
                    }
                    notifyDataSetChanged();
                });
            } else {
                btnSplitExpand.setVisibility(View.GONE);
                tvSplitBreakdown.setVisibility(View.GONE);
                btnSplitExpand.setOnClickListener(null);
            }

            if (ReceiptRowUi.showReceiptThumbnail(transaction)) {
                btnReceipt.setVisibility(View.VISIBLE);
                btnReceipt.setOnClickListener(v -> {
                    String uriStr = transaction.getReceiptImageUri();
                    if (uriStr != null && !uriStr.isEmpty()) {
                        showReceiptImagePreview(Uri.parse(uriStr));
                    }
                });
            } else {
                btnReceipt.setVisibility(View.GONE);
                btnReceipt.setOnClickListener(null);
            }

            // Handle Options button click
            btnOptions.setOnClickListener(v -> showTransactionOptionsDialog(transaction));

            return convertView;
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void showTransactionOptionsDialog(Transaction transaction) {
        // We'll show an AlertDialog with "Edit" and "Delete" options
        new MaterialAlertDialogBuilder(this)
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
            String amounts = String.format(Locale.getDefault(),
                    "Limit: $%.2f | Remaining: $%.2f",
                    envelope.getLimit(),
                    envelope.getRemaining());
            if (envelope.getAccountBalance() != null) {
                amounts += String.format(Locale.getDefault(), " | Account: $%.2f", envelope.getAccountBalance());
            }
            tvAmounts.setText(amounts);


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

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void showResetConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_recalculate_title)
                .setMessage(R.string.dialog_recalculate_message)
                .setPositiveButton(R.string.dialog_recalculate_positive, (dialog, which) -> {
                    performMonthlyReset();
                })
                .setNegativeButton(android.R.string.cancel, null)
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
                        onManualDateRangeChanged();
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
                        onManualDateRangeChanged();
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
            // Option A: reset(false) syncs limit/remaining to originalLimit and clears manual override;
            // then calculateRemaining aligns remaining with transactions for the active month.
            envelope.reset(false);
            envelope.calculateRemaining(currentMonth);
        }
        PrefManager.saveEnvelopes(this, envelopes);
        updateDisplay();
        Toast.makeText(this, R.string.toast_balances_recalculated, Toast.LENGTH_SHORT).show();
    }

    private void showEnvelopeOptionsDialog(int position) {
        Envelope envelope = envelopes.get(position);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_pond_options_title)
                .setItems(new CharSequence[]{
                        getString(R.string.action_edit),
                        getString(R.string.action_delete)
                }, (dialog, which) -> {
                    if (which == 0) {
                        showEnvelopeDialog(envelope);
                    } else {
                        new MaterialAlertDialogBuilder(MainActivity.this)
                                .setMessage(R.string.dialog_delete_pond_message)
                                .setPositiveButton(R.string.action_delete, (d, w) -> {
                                    removeTransferReferencesToEnvelope(envelope.getName());
                                    envelopes.remove(position);
                                    PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                                    updateDisplay();
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                })
                .show();
    }

    private void showEnvelopeDialog(@Nullable Envelope envelopeToEdit) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_envelope, null);
        EditText etName = dialogView.findViewById(R.id.etEnvelopeName);
        EditText etLimit = dialogView.findViewById(R.id.etEnvelopeLimit);
        EditText etRemainder = dialogView.findViewById(R.id.etEnvelopeRemainder);
        TextView etReminderLabel = dialogView.findViewById(R.id.etEnvelopeRemainderLabel);
        EditText etAccount = dialogView.findViewById(R.id.etEnvelopeAccount);
        TextView tvAccountLabel = dialogView.findViewById(R.id.tvEnvelopeAccountLabel);
        if (envelopeToEdit == null) {
            etReminderLabel.setVisibility(View.GONE);
            etRemainder.setVisibility(View.GONE);
            tvAccountLabel.setVisibility(View.GONE);
            etAccount.setVisibility(View.GONE);
        }
        if (envelopeToEdit != null) {
            etName.setText(envelopeToEdit.getName());
            etLimit.setText(String.valueOf(envelopeToEdit.getLimit()));
            etRemainder.setText(String.valueOf(envelopeToEdit.getRemaining()));
            Double acct = envelopeToEdit.getAccountBalance();
            if (acct != null) {
                etAccount.setText(String.valueOf(acct));
            }
        }

        builder.setView(dialogView)
                .setTitle(envelopeToEdit == null ? R.string.dialog_new_pond_title : R.string.dialog_edit_pond_title)
                .setPositiveButton(R.string.save, (dialog, which) -> {
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
                        String acctStr = etAccount.getText().toString().trim();
                        if (acctStr.isEmpty()) {
                            envelopeToEdit.setAccountBalance(null);
                        } else {
                            envelopeToEdit.setAccountBalance(Double.parseDouble(acctStr));
                        }
                    }

                    PrefManager.saveEnvelopes(this, envelopes);
                    updateDisplay();
                })
                .setNegativeButton(android.R.string.cancel, null);

        builder.create().show();
    }

    private List<String> getEnvelopeNames() {
        List<String> names = new ArrayList<>();
        for (Envelope e : envelopes) {
            names.add(e.getName());
        }
        return names;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void showTransactionDialog(Transaction transactionToEdit) {
        final boolean isSplitPurchase = SplitPurchaseSyncHelper.isSplitPurchase(transactionToEdit);
        final Transaction editTransaction = isSplitPurchase
                ? SplitPurchaseSyncHelper.resolveForEdit(envelopes, transactionToEdit)
                : TransferSyncHelper.resolveAnchorTransaction(envelopes, transactionToEdit);
        final boolean wasRecurringBefore = editTransaction.isRecurring();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction, null);

        MaterialAutoCompleteTextView spinnerEnvelope = dialogView.findViewById(R.id.spinnerEditEnvelope);
        EditText etDate = dialogView.findViewById(R.id.etEditTransactionDate);
        EditText etAmount = dialogView.findViewById(R.id.etEditTransactionAmount);
        EditText etSplitTotal = dialogView.findViewById(R.id.etSplitPurchaseTotal);
        EditText etComment = dialogView.findViewById(R.id.etEditTransactionComment);
        TransferDialogViews transferViews = createTransferDialogViews(dialogView);
        SplitDialogViews splitViews = createSplitDialogViews(dialogView);
        TabLayout tabTransactionType = dialogView.findViewById(R.id.tabTransactionType);
        TabLayout tabTransactionTime = dialogView.findViewById(R.id.tabTransactionTime);
        LinearLayout panelSpending = dialogView.findViewById(R.id.panelSpending);
        LinearLayout panelTransfer = dialogView.findViewById(R.id.panelTransfer);
        View panelSplit = dialogView.findViewById(R.id.panelSplitPurchase);
        LinearLayout layoutRowPond = dialogView.findViewById(R.id.layoutRowPond);
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

        tabTransactionType.addTab(tabTransactionType.newTab().setText(R.string.tab_transaction_type_spending));
        tabTransactionType.addTab(tabTransactionType.newTab().setText(R.string.tab_transaction_type_transfer));
        tabTransactionType.addTab(tabTransactionType.newTab().setText(R.string.tab_transaction_type_split_purchase));
        tabTransactionTime.addTab(tabTransactionTime.newTab().setText(R.string.tab_transaction_time_one_time));
        tabTransactionTime.addTab(tabTransactionTime.newTab().setText(R.string.tab_transaction_time_recurring));

        bindDialogDropdownOptions(spinnerEnvelope, getEnvelopeNames());

        int envelopeIndex = getEnvelopeNames().indexOf(editTransaction.getEnvelopeName());
        if (envelopeIndex >= 0) {
            spinnerEnvelope.setText(getEnvelopeNames().get(envelopeIndex), false);
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

        List<TransferBucketAllocation> existingAllocations = editTransaction.getTransferId() == null
                ? new ArrayList<>()
                : TransferSyncHelper.getAllocations(envelopes, editTransaction.getTransferId());

        List<SplitPurchaseSliceAllocation> initialSplitSlices = null;
        if (isSplitPurchase) {
            List<Transaction> peers = SplitPurchaseSyncHelper.findTransactionsInGroup(
                    envelopes, editTransaction.getSplitPurchaseGroupId());
            initialSplitSlices = SplitPurchaseSyncHelper.toAllocations(peers);
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

        if (isSplitPurchase) {
            List<Transaction> peers = SplitPurchaseSyncHelper.findTransactionsInGroup(
                    envelopes, editTransaction.getSplitPurchaseGroupId());
            etSplitTotal.setText(String.format(Locale.getDefault(), "%.2f", SplitPurchaseSyncHelper.groupTotal(peers)));
            etAmount.setText("0");
        } else {
            etAmount.setText(String.valueOf(editTransaction.getAmount()));
            etSplitTotal.setText("");
        }
        etComment.setText(editTransaction.getComment());

        boolean isTransfer = !isSplitPurchase
                && editTransaction.getTransferId() != null
                && !editTransaction.getTransferId().isEmpty();

        initializeTransferDialogSection(transferViews,
                etAmount,
                spinnerEnvelope,
                existingAllocations.isEmpty() ? null : existingAllocations.get(0).getToEnvelope(),
                existingAllocations,
                isTransfer);
        initializeSplitDialogSection(splitViews, etSplitTotal, initialSplitSlices, isSplitPurchase);
        attachTransactionDialogScrollDismiss(transferViews, splitViews);

        cbIsRecurring.setChecked(editTransaction.isRecurring());
        cbIsRecurring.setOnCheckedChangeListener((buttonView, checked) -> {
            if (tabTransactionTime.getSelectedTabPosition() != (checked ? TAB_TIME_RECURRING : TAB_TIME_ONE_TIME)) {
                Objects.requireNonNull(tabTransactionTime.getTabAt(checked ? TAB_TIME_RECURRING : TAB_TIME_ONE_TIME)).select();
            }
            setRecurringControlsVisibility(checked,
                    tvRecurringFrequencyLabel,
                    layoutRecurringFrequencyOptions,
                    tvRecurringDaysLabel,
                    layoutRecurringWeekdayButtons,
                    tvRecurringDaysValue,
                    selectedRecurringFrequency[0]);
        });
        tabTransactionTime.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean recurring = tab.getPosition() == TAB_TIME_RECURRING;
                if (cbIsRecurring.isChecked() != recurring) {
                    cbIsRecurring.setChecked(recurring);
                } else {
                    setRecurringControlsVisibility(recurring,
                            tvRecurringFrequencyLabel,
                            layoutRecurringFrequencyOptions,
                            tvRecurringDaysLabel,
                            layoutRecurringWeekdayButtons,
                            tvRecurringDaysValue,
                            selectedRecurringFrequency[0]);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        tabTransactionType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                applyTransactionDialogTypeState(tab.getPosition(),
                        tabTransactionTime,
                        panelSpending,
                        panelTransfer,
                        panelSplit,
                        layoutRowPond,
                        etAmount,
                        etSplitTotal,
                        cbIsRecurring,
                        tvRecurringFrequencyLabel,
                        layoutRecurringFrequencyOptions,
                        tvRecurringDaysLabel,
                        layoutRecurringWeekdayButtons,
                        tvRecurringDaysValue,
                        selectedRecurringFrequency[0],
                        transferViews,
                        splitViews);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        int initialTypeTab = isSplitPurchase ? TAB_TYPE_SPLIT
                : isTransfer ? TAB_TYPE_TRANSFER
                : TAB_TYPE_SPENDING;
        if (editTransaction.isRecurring()) {
            Objects.requireNonNull(tabTransactionTime.getTabAt(TAB_TIME_RECURRING)).select();
        } else {
            Objects.requireNonNull(tabTransactionTime.getTabAt(TAB_TIME_ONE_TIME)).select();
        }
        Objects.requireNonNull(tabTransactionType.getTabAt(initialTypeTab)).select();

        receiptDialogHostView = dialogView;
        wireReceiptRow(dialogView, editTransaction.getReceiptImageUri());

        builder.setView(dialogView)
                .setTitle("Edit Transaction")
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            if (receiptDialogHostView == dialogView) {
                receiptDialogHostView = null;
            }
            if (activeTransferDialogViews == transferViews) {
                activeTransferDialogViews = null;
                activeTransferAmountInput = null;
            }
            if (activeSplitDialogViews == splitViews) {
                activeSplitDialogViews = null;
                activeSplitTotalInput = null;
            }
        });
        dialog.setOnShowListener(ignored -> {
            applyIconMaterialDialogActions(dialog);
            configureTransactionDialogWindow(dialog);
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            transferViews.positiveButton = positive;
            splitViews.positiveButton = positive;
            updateTransferSectionSummary(transferViews);
            updateSplitSectionSummary(splitViews);
            positive.setOnClickListener(v -> {
            try {
                int typeTab = tabTransactionType.getSelectedTabPosition();
                if (!isSplitPurchase && typeTab == TAB_TYPE_SPLIT) {
                    showError("Converting an existing transaction to a split purchase is not supported.");
                    return;
                }
                if (isSplitPurchase && typeTab != TAB_TYPE_SPLIT) {
                    showError("Split purchases cannot be changed to another transaction type.");
                    return;
                }
                String newComment = etComment.getText().toString();
                String newDate = etDate.getText().toString();
                String previousMonth = resolveTransactionMonth(editTransaction);
                Object receiptUriTag = dialogView.getTag(R.id.tag_receipt_image_uri);
                String receiptUri = receiptUriTag instanceof String ? (String) receiptUriTag : null;

                if (typeTab == TAB_TYPE_SPLIT) {
                    double total = parseAmountOrZero(etSplitTotal);
                    List<SplitPurchaseSliceAllocation> slices = snapshotSplitAllocations(splitViews);
                    TransferGroupValidationResult validation = SplitPurchaseGroupDraft.validate(total, slices);
                    if (!validation.isValid()) {
                        splitViews.saveAttempted = true;
                        updateSplitSectionSummary(splitViews);
                        return;
                    }
                    Set<String> months = SplitPurchaseSyncHelper.applyGroup(
                            envelopes,
                            editTransaction.getSplitPurchaseGroupId(),
                            newDate,
                            newComment,
                            receiptUri,
                            slices,
                            currentMonth);
                    for (String m : months) {
                        synchronizeAllEnvelopesForMonth(m);
                    }
                    PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                    updateDisplay();
                    dialog.dismiss();
                    return;
                }

                String oldEnvelopeName = editTransaction.getEnvelopeName();
                String newEnvelopeName = getSelectedDropdownValue(spinnerEnvelope);
                if (newEnvelopeName == null || newEnvelopeName.isEmpty()) {
                    showError("Select a pond");
                    return;
                }
                double newAmount = Double.parseDouble(etAmount.getText().toString());

                if (cbIsRecurring.isChecked() && selectedRecurringDays.isEmpty()) {
                    showError("Recurring requires at least one selected day");
                    return;
                }

                if (typeTab == TAB_TYPE_TRANSFER) {
                    TransferGroupValidationResult validation = TransferGroupDraft.validate(
                            newAmount,
                            newEnvelopeName,
                            snapshotTransferAllocations(transferViews));
                    if (!validation.isValid()) {
                        transferViews.saveAttempted = true;
                        updateTransferSectionSummary(transferViews);
                        return;
                    }
                }

                if (typeTab != TAB_TYPE_SPLIT) {
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

                    if (receiptUriTag instanceof String) {
                        editTransaction.setReceiptImageUri((String) receiptUriTag);
                    } else {
                        editTransaction.setReceiptImageUri(null);
                    }

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

                    if (typeTab == TAB_TYPE_TRANSFER) {
                        List<TransferBucketAllocation> allocations = snapshotTransferAllocations(transferViews);
                        PrefManager.setLastAddTransferDestination(MainActivity.this, newEnvelopeName, allocations.get(0).getToEnvelope());
                        TransferSyncHelper.applyTransferGroup(envelopes, editTransaction, newEnvelopeName, allocations);
                    } else {
                        TransferSyncHelper.detachTransferGroup(envelopes, editTransaction);
                    }
                }

                synchronizeAllEnvelopesForMonth(previousMonth);
                synchronizeAllEnvelopesForMonth(resolveTransactionMonth(editTransaction));

                PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                updateDisplay();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                showError("Invalid amount entered!");
            }
        });
        });

        dialog.show();
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void deleteTransaction(Transaction transaction) {
        if (SplitPurchaseSyncHelper.isSplitPurchase(transaction)) {
            final Transaction ref = SplitPurchaseSyncHelper.resolveForEdit(envelopes, transaction);
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setMessage(R.string.dialog_delete_split_purchase_message)
                    .setPositiveButton(R.string.action_delete, (d, w) -> {
                        String groupId = ref.getSplitPurchaseGroupId();
                        Set<String> months = SplitPurchaseSyncHelper.removeGroup(envelopes, groupId);
                        for (String m : months) {
                            synchronizeAllEnvelopesForMonth(m);
                        }
                        PrefManager.saveEnvelopes(MainActivity.this, envelopes);
                        updateDisplay();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        final Transaction targetTransaction = TransferSyncHelper.resolveAnchorTransaction(envelopes, transaction);
        new MaterialAlertDialogBuilder(MainActivity.this)
                .setMessage("Delete this transaction?")
                .setPositiveButton("Delete", (d, w) -> {
                    Envelope envelope = findEnvelopeByName(targetTransaction.getEnvelopeName());
                    if(envelope != null){
                        String targetMonth = resolveTransactionMonth(targetTransaction);
                        TransferSyncHelper.detachTransferGroup(envelopes, targetTransaction);
                        envelope.removeTransaction(targetTransaction, currentMonth);
                        synchronizeAllEnvelopesForMonth(targetMonth);
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
        return TransferSyncHelper.findTransferOwner(envelopes, transferId);
    }

    private Envelope.TransferData findTransferById(String transferId) {
        List<TransferBucketAllocation> allocations = TransferSyncHelper.getAllocations(envelopes, transferId);
        if (allocations.isEmpty()) {
            return null;
        }
        TransferBucketAllocation first = allocations.get(0);
        return new Envelope.TransferData(transferId, first.getBucketId(), first.getToEnvelope(), first.getAmount());
    }

    private void removeTransferById(String transferId) {
        for (Envelope envelope : envelopes) {
            envelope.removeTransfer(transferId);
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

    private TransferDialogViews createTransferDialogViews(View dialogView) {
        return new TransferDialogViews(
                dialogView.findViewById(R.id.layoutTransferBucketsSection),
                dialogView.findViewById(R.id.scrollTransactionDialogBody),
                dialogView.findViewById(R.id.spinnerEditEnvelope),
                dialogView.findViewById(R.id.tvTransferAllocatedSummary),
                dialogView.findViewById(R.id.tvTransferSpentHereSummary),
                dialogView.findViewById(R.id.tvTransferRemainingSummary),
                dialogView.findViewById(R.id.tvTransferValidationMessage),
                dialogView.findViewById(R.id.layoutTransferBucketsContainer),
                dialogView.findViewById(R.id.btnAddTransferBucket)
        );
    }

    private SplitDialogViews createSplitDialogViews(View dialogView) {
        return new SplitDialogViews(
                dialogView.findViewById(R.id.panelSplitPurchase),
                dialogView.findViewById(R.id.scrollTransactionDialogBody),
                dialogView.findViewById(R.id.tvSplitAllocatedSummary),
                dialogView.findViewById(R.id.tvSplitValidationMessage),
                dialogView.findViewById(R.id.layoutSplitBucketsContainer),
                dialogView.findViewById(R.id.btnAddSplitBucket)
        );
    }

    private void attachTransactionDialogScrollDismiss(TransferDialogViews transferViews, SplitDialogViews splitViews) {
        transferViews.scrollView.setOnScrollChangeListener((View.OnScrollChangeListener) (scrollView, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            dismissTransferDropdowns(transferViews);
            dismissSplitDropdowns(splitViews);
        });
    }

    private void markSplitInteraction(@Nullable SplitDialogViews splitViews) {
        if (splitViews == null) {
            return;
        }
        splitViews.hasMeaningfulInteraction = true;
    }

    private double splitAllocatedExcluding(SplitPurchaseBucketRowController excludedController) {
        if (activeSplitDialogViews == null) {
            return 0d;
        }
        double total = 0d;
        for (SplitPurchaseBucketRowController controller : activeSplitDialogViews.bucketControllers) {
            if (controller == excludedController) {
                continue;
            }
            total += Math.max(0d, controller.getAllocation().getAmount());
        }
        return total;
    }

    private void dismissSplitDropdowns(SplitDialogViews splitViews) {
        dismissSplitDropdownsExcept(splitViews, null);
    }

    private void dismissSplitDropdownsExcept(SplitDialogViews splitViews,
                                           @Nullable MaterialAutoCompleteTextView keepOpen) {
        if (splitViews == null) {
            return;
        }
        for (SplitPurchaseBucketRowController controller : splitViews.bucketControllers) {
            if (keepOpen == null || controller.getPondDropdown() != keepOpen) {
                controller.dismissDropdown();
            }
        }
    }

    private void prepareSplitDialogDropdownForOpen(SplitDialogViews splitViews,
                                                   MaterialAutoCompleteTextView targetDropdown,
                                                   View anchorView) {
        dismissSplitDropdownsExcept(splitViews, targetDropdown);
        scrollSplitDialogToView(splitViews, anchorView, false);
    }

    private void scrollSplitDialogToView(SplitDialogViews splitViews,
                                         @Nullable View targetView,
                                         boolean smooth) {
        if (splitViews == null || targetView == null) {
            return;
        }
        splitViews.scrollView.post(() -> {
            Rect targetRect = new Rect();
            targetView.getDrawingRect(targetRect);
            splitViews.scrollView.offsetDescendantRectToMyCoords(targetView, targetRect);
            int targetTop = Math.max(0, targetRect.top - dp(12));
            if (smooth) {
                splitViews.scrollView.smoothScrollTo(0, targetTop);
            } else {
                splitViews.scrollView.scrollTo(0, targetTop);
            }
        });
    }

    private List<SplitPurchaseSliceAllocation> snapshotSplitAllocations(SplitDialogViews splitViews) {
        List<SplitPurchaseSliceAllocation> snapshot = new ArrayList<>();
        for (SplitPurchaseBucketRowController controller : splitViews.bucketControllers) {
            SplitPurchaseSliceAllocation a = controller.getAllocation();
            snapshot.add(new SplitPurchaseSliceAllocation(a.getBucketId(), a.getPondName(), a.getAmount()));
        }
        return snapshot;
    }

    private void refreshSplitBucketLabels(SplitDialogViews splitViews) {
        boolean canRemove = splitViews.bucketControllers.size() > 2;
        for (int i = 0; i < splitViews.bucketControllers.size(); i++) {
            splitViews.bucketControllers.get(i).setIndex(i, canRemove);
        }
    }

    private void addSplitBucketRow(SplitDialogViews splitViews,
                                   @Nullable SplitPurchaseSliceAllocation initialAllocation) {
        View rowView = getLayoutInflater().inflate(R.layout.item_transfer_bucket, splitViews.bucketsContainer, false);
        SplitPurchaseSliceAllocation allocation = initialAllocation == null
                ? new SplitPurchaseSliceAllocation(null, null, 0d)
                : new SplitPurchaseSliceAllocation(initialAllocation.getBucketId(), initialAllocation.getPondName(), initialAllocation.getAmount());
        SplitPurchaseBucketRowController controller = new SplitPurchaseBucketRowController(rowView, allocation);
        splitViews.bucketControllers.add(controller);
        splitViews.bucketsContainer.addView(rowView);
        controller.bindPonds(getEnvelopeNames(), allocation.getPondName());
        refreshSplitBucketLabels(splitViews);
    }

    private void clearSplitBucketRows(SplitDialogViews splitViews) {
        while (!splitViews.bucketControllers.isEmpty()) {
            SplitPurchaseBucketRowController c = splitViews.bucketControllers.remove(splitViews.bucketControllers.size() - 1);
            splitViews.bucketsContainer.removeView(c.getRootView());
        }
    }

    private void setSplitControlsVisibility(boolean visible, SplitDialogViews splitViews) {
        dismissSplitDropdowns(splitViews);
        if (!visible) {
            clearSplitBucketRows(splitViews);
            splitViews.hasMeaningfulInteraction = false;
            splitViews.saveAttempted = false;
        }
        splitViews.section.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && splitViews.bucketControllers.isEmpty()) {
            addSplitBucketRow(splitViews, null);
            addSplitBucketRow(splitViews, null);
        }
        if (visible) {
            scrollSplitDialogToView(splitViews, splitViews.section, true);
        }
        updateSplitSectionSummary(splitViews);
    }

    private void updateSplitSectionSummary(@Nullable SplitDialogViews splitViews) {
        if (splitViews == null || activeSplitTotalInput == null) {
            return;
        }
        double purchaseTotal = parseAmountOrZero(activeSplitTotalInput);
        for (SplitPurchaseBucketRowController controller : splitViews.bucketControllers) {
            controller.refreshSliderBounds(purchaseTotal);
        }
        List<SplitPurchaseSliceAllocation> snapshot = snapshotSplitAllocations(splitViews);
        double allocated = SplitPurchaseGroupDraft.allocatedTotal(snapshot);
        splitViews.allocatedSummary.setText(String.format(Locale.getDefault(),
                "Allocated: $%.2f / $%.2f", allocated, purchaseTotal));

        boolean sectionVisible = splitViews.section.getVisibility() == View.VISIBLE;
        TransferGroupValidationResult validation = sectionVisible
                ? SplitPurchaseGroupDraft.validate(purchaseTotal, snapshot)
                : TransferGroupValidationResult.valid();
        boolean showValidation = TransferBucketUiHelper.shouldShowValidationMessage(
                sectionVisible,
                splitViews.hasMeaningfulInteraction,
                splitViews.saveAttempted,
                validation);
        if (showValidation) {
            splitViews.validationMessage.setVisibility(View.VISIBLE);
            splitViews.validationMessage.setText(validation.getMessage());
        } else {
            splitViews.validationMessage.setText("");
            splitViews.validationMessage.setVisibility(View.GONE);
        }
        if (splitViews.positiveButton != null) {
            splitViews.positiveButton.setEnabled(!sectionVisible || validation.isValid());
        }
    }

    private void initializeSplitDialogSection(SplitDialogViews splitViews,
                                              EditText totalInput,
                                              @Nullable List<SplitPurchaseSliceAllocation> initialAllocations,
                                              boolean showSectionInitially) {
        activeSplitDialogViews = splitViews;
        activeSplitTotalInput = totalInput;
        clearSplitBucketRows(splitViews);
        splitViews.addBucketButton.setOnClickListener(v -> {
            markSplitInteraction(splitViews);
            addSplitBucketRow(splitViews, null);
            updateSplitSectionSummary(splitViews);
            if (!splitViews.bucketControllers.isEmpty()) {
                scrollSplitDialogToView(splitViews,
                        splitViews.bucketControllers.get(splitViews.bucketControllers.size() - 1).getRootView(),
                        true);
            }
        });
        totalInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (splitViews.section.getVisibility() == View.VISIBLE) {
                    markSplitInteraction(splitViews);
                }
                updateSplitSectionSummary(splitViews);
            }
        });

        if (initialAllocations != null && !initialAllocations.isEmpty()) {
            for (SplitPurchaseSliceAllocation slice : initialAllocations) {
                addSplitBucketRow(splitViews, slice);
            }
        }
        if (showSectionInitially) {
            setSplitControlsVisibility(true, splitViews);
        } else {
            splitViews.section.setVisibility(View.GONE);
            updateSplitSectionSummary(splitViews);
        }
    }

    private void applyTransactionDialogTypeState(int typeTab,
                                               TabLayout tabTime,
                                               View panelSpending,
                                               View panelTransfer,
                                               View panelSplit,
                                               View layoutRowPond,
                                               EditText etAmount,
                                               EditText etSplit,
                                               CheckBox cbIsRecurring,
                                               TextView tvRecurringFrequencyLabel,
                                               LinearLayout layoutRecurringFrequencyOptions,
                                               TextView tvRecurringDaysLabel,
                                               LinearLayout layoutRecurringWeekdayButtons,
                                               TextView tvRecurringDaysValue,
                                               String recurringFrequency,
                                               TransferDialogViews transferViews,
                                               SplitDialogViews splitViews) {
        boolean spending = typeTab == TAB_TYPE_SPENDING;
        boolean transfer = typeTab == TAB_TYPE_TRANSFER;
        boolean split = typeTab == TAB_TYPE_SPLIT;

        tabTime.setVisibility(spending ? View.VISIBLE : View.GONE);
        panelSpending.setVisibility(spending ? View.VISIBLE : View.GONE);
        panelTransfer.setVisibility(transfer ? View.VISIBLE : View.GONE);
        panelSplit.setVisibility(split ? View.VISIBLE : View.GONE);
        layoutRowPond.setVisibility(split ? View.GONE : View.VISIBLE);
        etAmount.setVisibility(split ? View.GONE : View.VISIBLE);
        etSplit.setVisibility(split ? View.VISIBLE : View.GONE);

        if (transfer || split) {
            cbIsRecurring.setChecked(false);
        }

        if (transfer) {
            setTransferControlsVisibility(true, transferViews);
            setSplitControlsVisibility(false, splitViews);
        } else if (split) {
            setTransferControlsVisibility(false, transferViews);
            setSplitControlsVisibility(true, splitViews);
        } else {
            setTransferControlsVisibility(false, transferViews);
            setSplitControlsVisibility(false, splitViews);
        }
        setRecurringControlsVisibility(cbIsRecurring.isChecked(),
                tvRecurringFrequencyLabel,
                layoutRecurringFrequencyOptions,
                tvRecurringDaysLabel,
                layoutRecurringWeekdayButtons,
                tvRecurringDaysValue,
                recurringFrequency);
        updateTransferSectionSummary(transferViews);
        updateSplitSectionSummary(splitViews);
    }

    private void initializeTransferDialogSection(TransferDialogViews transferViews,
                                                 EditText amountInput,
                                                 MaterialAutoCompleteTextView sourceSpinner,
                                                 @Nullable String firstDestination,
                                                 @Nullable List<TransferBucketAllocation> initialAllocations,
                                                 boolean enabledInitially) {
        activeTransferDialogViews = transferViews;
        activeTransferAmountInput = amountInput;
        configureDialogDropdown(sourceSpinner, () -> prepareDialogDropdownForOpen(transferViews, sourceSpinner, sourceSpinner));
        transferViews.addBucketButton.setOnClickListener(v -> {
            markTransferInteraction(transferViews);
            String sourceEnvelope = getSelectedDropdownValue(sourceSpinner);
            addTransferBucketRow(transferViews, sourceEnvelope, null);
            updateTransferSectionSummary(transferViews);
            if (!transferViews.bucketControllers.isEmpty()) {
                scrollTransferDialogToView(transferViews,
                        transferViews.bucketControllers.get(transferViews.bucketControllers.size() - 1).getRootView(),
                        true);
            }
        });
        amountInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (transferViews.section.getVisibility() == View.VISIBLE) {
                    markTransferInteraction(transferViews);
                }
                updateTransferSectionSummary(transferViews);
            }
        });
        sourceSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String selectedSource = (String) parent.getItemAtPosition(position);
            if (transferViews.section.getVisibility() == View.VISIBLE) {
                markTransferInteraction(transferViews);
            }
            rebindTransferBucketDestinations(transferViews, selectedSource);
            updateTransferSectionSummary(transferViews);
        });

        if (initialAllocations != null && !initialAllocations.isEmpty()) {
            for (TransferBucketAllocation allocation : initialAllocations) {
                addTransferBucketRow(transferViews,
                        getSelectedDropdownValue(sourceSpinner),
                        allocation);
            }
        } else if (enabledInitially) {
            addTransferBucketRow(transferViews,
                    getSelectedDropdownValue(sourceSpinner),
                    new TransferBucketAllocation(null, firstDestination, 0d));
        }
        setTransferControlsVisibility(enabledInitially, transferViews);
        updateTransferSectionSummary(transferViews);
    }

    private void addTransferBucketRow(TransferDialogViews transferViews,
                                      @Nullable String sourceEnvelopeName,
                                      @Nullable TransferBucketAllocation initialAllocation) {
        View rowView = getLayoutInflater().inflate(R.layout.item_transfer_bucket, transferViews.bucketsContainer, false);
        TransferBucketAllocation allocation = initialAllocation == null
                ? new TransferBucketAllocation(null, null, 0d)
                : initialAllocation;
        TransferBucketRowController controller = new TransferBucketRowController(rowView, allocation);
        transferViews.bucketControllers.add(controller);
        transferViews.bucketsContainer.addView(rowView);
        controller.bindDestinations(sourceEnvelopeName, allocation.getToEnvelope());
        refreshTransferBucketLabels(transferViews);
    }

    private void rebindTransferBucketDestinations(TransferDialogViews transferViews, @Nullable String sourceEnvelopeName) {
        for (TransferBucketRowController controller : transferViews.bucketControllers) {
            controller.bindDestinations(sourceEnvelopeName, controller.getAllocation().getToEnvelope());
        }
        refreshTransferBucketLabels(transferViews);
    }

    private void refreshTransferBucketLabels(TransferDialogViews transferViews) {
        boolean canRemove = transferViews.bucketControllers.size() > 1;
        for (int i = 0; i < transferViews.bucketControllers.size(); i++) {
            transferViews.bucketControllers.get(i).setIndex(i, canRemove);
        }
    }

    private void setTransferControlsVisibility(boolean visible, TransferDialogViews transferViews) {
        dismissTransferDropdowns(transferViews);
        transferViews.section.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && transferViews.bucketControllers.isEmpty()) {
            addTransferBucketRow(transferViews, getSelectedDropdownValue(transferViews.sourceDropdown), null);
        }
        if (!visible) {
            transferViews.hasMeaningfulInteraction = false;
            transferViews.saveAttempted = false;
        } else {
            scrollTransferDialogToView(transferViews, transferViews.section, true);
        }
        updateTransferSectionSummary(transferViews);
    }

    private void updateTransferSectionSummary(@Nullable TransferDialogViews transferViews) {
        if (transferViews == null || activeTransferAmountInput == null) {
            return;
        }
        double totalAmount = parseAmountOrZero(activeTransferAmountInput);
        for (TransferBucketRowController controller : transferViews.bucketControllers) {
            controller.refreshSliderBounds(totalAmount);
        }

        List<TransferBucketAllocation> allocations = new ArrayList<>();
        for (TransferBucketRowController controller : transferViews.bucketControllers) {
            allocations.add(controller.getAllocation());
        }
        double allocated = TransferGroupDraft.allocatedTotal(allocations);
        double spentHere = Math.max(0d, totalAmount - allocated);
        double remaining = Math.max(0d, totalAmount - allocated);

        transferViews.allocatedSummary.setText(String.format(Locale.getDefault(),
                "Allocated: $%.2f", allocated));
        transferViews.spentHereSummary.setText(String.format(Locale.getDefault(),
                "Spent in this pond: $%.2f", spentHere));
        transferViews.remainingSummary.setText(String.format(Locale.getDefault(),
                "Left to allocate: $%.2f", remaining));

        String sourceEnvelopeName = getSelectedDropdownValue(transferViews.sourceDropdown);

        boolean visible = transferViews.section.getVisibility() == View.VISIBLE;
        TransferGroupValidationResult validation = visible
                ? TransferGroupDraft.validate(totalAmount, sourceEnvelopeName, allocations)
                : TransferGroupValidationResult.valid();
        boolean showValidation = TransferBucketUiHelper.shouldShowValidationMessage(
                visible,
                transferViews.hasMeaningfulInteraction,
                transferViews.saveAttempted,
                validation);
        if (showValidation) {
            transferViews.validationMessage.setVisibility(View.VISIBLE);
            transferViews.validationMessage.setText(validation.getMessage());
        } else {
            transferViews.validationMessage.setText("");
            transferViews.validationMessage.setVisibility(View.GONE);
        }
        if (transferViews.positiveButton != null) {
            transferViews.positiveButton.setEnabled(!visible || validation.isValid());
        }
    }

    private void markTransferInteraction(@Nullable TransferDialogViews transferViews) {
        if (transferViews == null) {
            return;
        }
        transferViews.hasMeaningfulInteraction = true;
    }

    private void bindDialogDropdownOptions(MaterialAutoCompleteTextView dropdown, List<String> options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        dropdown.setAdapter(adapter);
    }

    private void configureDialogDropdown(MaterialAutoCompleteTextView dropdown, Runnable onOpen) {
        dropdown.setKeyListener(null);
        dropdown.setCursorVisible(false);
        dropdown.setOnClickListener(view -> {
            onOpen.run();
            dropdown.showDropDown();
        });
    }

    @Nullable
    private String getSelectedDropdownValue(@Nullable MaterialAutoCompleteTextView dropdown) {
        if (dropdown == null || dropdown.getText() == null) {
            return null;
        }
        String selected = dropdown.getText().toString().trim();
        return selected.isEmpty() ? null : selected;
    }

    private void prepareDialogDropdownForOpen(TransferDialogViews transferViews,
                                              MaterialAutoCompleteTextView targetDropdown,
                                              View anchorView) {
        dismissTransferDropdownsExcept(transferViews, targetDropdown);
        scrollTransferDialogToView(transferViews, anchorView, false);
    }

    private void dismissTransferDropdowns(TransferDialogViews transferViews) {
        dismissTransferDropdownsExcept(transferViews, null);
    }

    private void dismissTransferDropdownsExcept(TransferDialogViews transferViews,
                                                @Nullable MaterialAutoCompleteTextView keepOpen) {
        if (transferViews == null) {
            return;
        }
        if (transferViews.sourceDropdown != keepOpen) {
            transferViews.sourceDropdown.dismissDropDown();
        }
        for (TransferBucketRowController controller : transferViews.bucketControllers) {
            if (controller.destinationDropdown != keepOpen) {
                controller.dismissDropdown();
            }
        }
    }

    private void scrollTransferDialogToView(TransferDialogViews transferViews,
                                            @Nullable View targetView,
                                            boolean smooth) {
        if (transferViews == null || targetView == null) {
            return;
        }
        transferViews.scrollView.post(() -> {
            Rect targetRect = new Rect();
            targetView.getDrawingRect(targetRect);
            transferViews.scrollView.offsetDescendantRectToMyCoords(targetView, targetRect);
            int targetTop = Math.max(0, targetRect.top - dp(12));
            if (smooth) {
                transferViews.scrollView.smoothScrollTo(0, targetTop);
            } else {
                transferViews.scrollView.scrollTo(0, targetTop);
            }
        });
    }

    private void configureTransactionDialogWindow(AlertDialog dialog) {
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.82f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private List<TransferBucketAllocation> snapshotTransferAllocations(TransferDialogViews transferViews) {
        List<TransferBucketAllocation> snapshot = new ArrayList<>();
        for (TransferBucketRowController controller : transferViews.bucketControllers) {
            TransferBucketAllocation allocation = controller.getAllocation();
            snapshot.add(new TransferBucketAllocation(
                    allocation.getBucketId(),
                    allocation.getToEnvelope(),
                    allocation.getAmount()
            ));
        }
        return snapshot;
    }

    private double allocatedExcluding(TransferBucketRowController excludedController) {
        if (activeTransferDialogViews == null) {
            return 0d;
        }
        double total = 0d;
        for (TransferBucketRowController controller : activeTransferDialogViews.bucketControllers) {
            if (controller == excludedController) {
                continue;
            }
            total += Math.max(0d, controller.getAllocation().getAmount());
        }
        return total;
    }

    private double parseAmountOrZero(@Nullable EditText input) {
        return input == null ? 0d : parseAmountOrZero(input.getText().toString());
    }

    private double parseAmountOrZero(@Nullable String value) {
        if (value == null) {
            return 0d;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return 0d;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return 0d;
        }
    }

    private String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "$%.2f", amount);
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
        int selectedColor = ContextCompat.getColor(this, R.color.mountain_primary);
        int unselectedColor = resolveThemeColor(android.R.attr.textColorPrimary);
        for (Map.Entry<Integer, TextView> entry : dayButtons.entrySet()) {
            boolean selected = selectedDays.contains(entry.getKey());
            TextView button = entry.getValue();
            button.setBackgroundResource(selected
                    ? R.drawable.recurring_option_selected_ripple
                    : R.drawable.recurring_option_unselected_ripple);
            button.setTextColor(selected ? selectedColor : unselectedColor);
        }
    }

    private void applyRecurringFrequencyButtonSelection(TextView weekly,
                                                        TextView biWeekly,
                                                        TextView monthly,
                                                        String selectedFrequency) {
        weekly.setBackgroundResource("weekly".equals(selectedFrequency)
                ? R.drawable.recurring_option_selected_ripple
                : R.drawable.recurring_option_unselected_ripple);
        biWeekly.setBackgroundResource("bi-weekly".equals(selectedFrequency)
                ? R.drawable.recurring_option_selected_ripple
                : R.drawable.recurring_option_unselected_ripple);
        monthly.setBackgroundResource("monthly".equals(selectedFrequency)
                ? R.drawable.recurring_option_selected_ripple
                : R.drawable.recurring_option_unselected_ripple);

        int selectedColor = ContextCompat.getColor(this, R.color.mountain_primary);
        int unselectedColor = resolveThemeColor(android.R.attr.textColorPrimary);
        weekly.setTextColor("weekly".equals(selectedFrequency) ? selectedColor : unselectedColor);
        biWeekly.setTextColor("bi-weekly".equals(selectedFrequency) ? selectedColor : unselectedColor);
        monthly.setTextColor("monthly".equals(selectedFrequency) ? selectedColor : unselectedColor);
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

        MaterialAlertDialogBuilder dayBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Choose recurring days")
                .setMultiChoiceItems(labels, checked, (dlg, which, isChecked) -> {
                    Integer value = values.get(which);
                    if (isChecked) {
                        if (!selectedDays.contains(value)) {
                            selectedDays.add(value);
                        }
                    } else {
                        selectedDays.remove(value);
                    }
                })
                .setPositiveButton(android.R.string.ok, (dlg, which) -> {
                    Collections.sort(selectedDays);
                    if (onSelectionChanged != null) {
                        onSelectionChanged.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        AlertDialog dayDialog = dayBuilder.create();
        dayDialog.setOnShowListener(ignored -> applyIconMaterialDialogActions(dayDialog));
        dayDialog.show();
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
        btnPrev.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
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
        btnNext.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
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
            tvMonth.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));

            int onSurface = resolveThemeColor(android.R.attr.textColorPrimary);
            int onChip = ContextCompat.getColor(MainActivity.this, R.color.mountain_primary);

            String[] dow = new String[]{"S", "M", "T", "W", "T", "F", "S"};
            LinearLayout dowRow = new LinearLayout(this);
            dowRow.setOrientation(LinearLayout.HORIZONTAL);
            for (String label : dow) {
                TextView t = new TextView(this);
                t.setText(label);
                t.setGravity(android.view.Gravity.CENTER);
                t.setTextColor(ContextCompat.getColor(this, R.color.mountain_primary));
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
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                dayCell.setLayoutParams(lp);

                boolean isSelected = workingSelection.contains(dayValue);
                dayCell.setBackgroundResource(isSelected
                        ? R.drawable.recurring_calendar_day_selected
                        : R.drawable.recurring_calendar_day_unselected);
                dayCell.setTextColor(isSelected ? onChip : onSurface);
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
                    dayCell.setTextColor(selected ? onChip : onSurface);
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

        MaterialAlertDialogBuilder monthBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Select monthly days")
                .setView(root)
                .setPositiveButton(android.R.string.ok, (dlg, which) -> {
                    selectedDays.clear();
                    selectedDays.addAll(workingSelection);
                    Collections.sort(selectedDays);
                    if (onSelectionChanged != null) {
                        onSelectionChanged.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        AlertDialog monthDialog = monthBuilder.create();
        monthDialog.setOnShowListener(ignored -> applyIconMaterialDialogActions(monthDialog));
        monthDialog.show();
    }

    /** Check (primary) and close (neutral) icons on Material alert actions; clears button labels. */
    private void applyIconMaterialDialogActions(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        int pad = dp(8);
        Drawable check = ContextCompat.getDrawable(this, R.drawable.ic_dialog_check);
        Drawable close = ContextCompat.getDrawable(this, R.drawable.ic_dialog_close);
        if (positive != null && check != null) {
            Drawable wrap = DrawableCompat.wrap(check.mutate());
            DrawableCompat.setTint(wrap, ContextCompat.getColor(this, R.color.mountain_primary));
            positive.setText("");
            positive.setContentDescription(getString(R.string.content_desc_dialog_save));
            positive.setCompoundDrawablesRelativeWithIntrinsicBounds(wrap, null, null, null);
            positive.setCompoundDrawablePadding(0);
            positive.setPadding(pad, positive.getPaddingTop(), pad, positive.getPaddingBottom());
        }
        if (negative != null && close != null) {
            Drawable wrap = DrawableCompat.wrap(close.mutate());
            DrawableCompat.setTint(wrap, resolveThemeColor(androidx.appcompat.R.attr.colorControlNormal));
            negative.setText("");
            negative.setContentDescription(getString(R.string.content_desc_dialog_cancel));
            negative.setCompoundDrawablesRelativeWithIntrinsicBounds(wrap, null, null, null);
            negative.setCompoundDrawablePadding(0);
            negative.setPadding(pad, negative.getPaddingTop(), pad, negative.getPaddingBottom());
        }
    }

    private int resolveThemeColor(int attrId) {
        TypedValue tv = new TypedValue();
        if (!getTheme().resolveAttribute(attrId, tv, true)) {
            return ContextCompat.getColor(this, R.color.mountain_primary);
        }
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        if (tv.resourceId != 0) {
            try {
                return ContextCompat.getColor(this, tv.resourceId);
            } catch (Resources.NotFoundException e) {
                return ContextCompat.getColor(this, R.color.mountain_primary);
            }
        }
        return ContextCompat.getColor(this, R.color.mountain_primary);
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

    private void synchronizeAllEnvelopesForMonth(@Nullable String month) {
        if (month == null || month.isEmpty()) {
            return;
        }
        for (Envelope envelope : envelopes) {
            synchronizeEnvelopeMonth(envelope, month);
        }
    }

    private void ensureMirrorTransactionsForExistingTransfers() {
        // Snapshot transfer groups first because grouped repair rewrites the owner's transfer list.
        Map<String, Envelope> transferOwnersById = new HashMap<>();
        for (Envelope owner : envelopes) {
            for (Envelope.TransferData transfer : owner.getTransfers()) {
                if (transfer.getId() == null || transfer.getId().isEmpty()) {
                    continue;
                }
                transferOwnersById.putIfAbsent(transfer.getId(), owner);
            }
        }

        for (Map.Entry<String, Envelope> entry : transferOwnersById.entrySet()) {
            String transferId = entry.getKey();
            Envelope owner = entry.getValue();
            if (owner == null) {
                continue;
            }

            Transaction sourceTransaction = null;
            for (Transaction candidate : owner.getTransactions()) {
                if (Objects.equals(candidate.getTransferId(), transferId)
                        && (candidate.getTransferBucketId() == null || candidate.getTransferBucketId().isEmpty())) {
                    sourceTransaction = candidate;
                    break;
                }
            }
            if (sourceTransaction == null) {
                continue;
            }

            TransferSyncHelper.applyTransferGroup(
                    envelopes,
                    sourceTransaction,
                    owner.getName(),
                    TransferSyncHelper.getAllocations(envelopes, transferId));
            synchronizeAllEnvelopesForMonth(resolveTransactionMonth(sourceTransaction));
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

        options.sort((a, b) -> a.envelopeName.compareToIgnoreCase(b.envelopeName));

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

    /** Start of local today (00:00) for consistent range end when the bills-period filter is on. */
    private static Date startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private void clearBillsPeriodFilterState() {
        billsPeriodFilterActive = false;
        PrefManager.setBillsFilterActive(this, false);
        PrefManager.clearBillsFilterSavedRange(this);
        updateBillsPeriodFilterButton(findViewById(R.id.btnBillsPeriodFilter));
    }

    private void applyPersistedBillsFilterState() {
        if (!PrefManager.isBillsFilterActive(this)) {
            return;
        }
        List<Integer> days = PrefManager.getBillsDays(this);
        if (days.isEmpty()) {
            PrefManager.setBillsFilterActive(this, false);
            return;
        }
        TextView tvStart = findViewById(R.id.tvStartDate);
        TextView tvEnd = findViewById(R.id.tvEndDate);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        Date anchor = BillsDayAnchor.computeAnchorDate(Calendar.getInstance(), days);
        if (anchor == null) {
            PrefManager.setBillsFilterActive(this, false);
            return;
        }
        tvStart.setText(sdf.format(anchor));
        tvEnd.setText(sdf.format(startOfToday()));
        billsPeriodFilterActive = true;
        updateBillsPeriodFilterButton(findViewById(R.id.btnBillsPeriodFilter));
    }

    private void toggleBillsPeriodFilter() {
        List<Integer> days = PrefManager.getBillsDays(this);
        if (days.isEmpty()) {
            Toast.makeText(this, R.string.toast_configure_bills_days_first, Toast.LENGTH_SHORT).show();
            return;
        }
        TextView tvStart = findViewById(R.id.tvStartDate);
        TextView tvEnd = findViewById(R.id.tvEndDate);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        if (!billsPeriodFilterActive) {
            PrefManager.saveBillsFilterSavedRange(this, tvStart.getText().toString(), tvEnd.getText().toString());
            Date anchor = BillsDayAnchor.computeAnchorDate(Calendar.getInstance(), days);
            if (anchor == null) {
                Toast.makeText(this, R.string.toast_no_bills_anchor, Toast.LENGTH_SHORT).show();
                return;
            }
            tvStart.setText(sdf.format(anchor));
            tvEnd.setText(sdf.format(startOfToday()));
            billsPeriodFilterActive = true;
            PrefManager.setBillsFilterActive(this, true);
        } else {
            String rs = PrefManager.getBillsFilterSavedStartDisplay(this);
            String re = PrefManager.getBillsFilterSavedEndDisplay(this);
            if (rs != null) {
                tvStart.setText(rs);
            }
            if (re != null) {
                tvEnd.setText(re);
            }
            billsPeriodFilterActive = false;
            PrefManager.setBillsFilterActive(this, false);
            PrefManager.clearBillsFilterSavedRange(this);
        }
        updateBillsPeriodFilterButton(findViewById(R.id.btnBillsPeriodFilter));
        updateTransactionHistory();
    }

    private void updateBillsPeriodFilterButton(ImageButton button) {
        if (button == null) {
            return;
        }
        if (billsPeriodFilterActive) {
            button.setColorFilter(ContextCompat.getColor(this, R.color.mountain_primary), PorterDuff.Mode.SRC_IN);
            button.setAlpha(1f);
        } else {
            button.setColorFilter(resolveThemeColor(androidx.appcompat.R.attr.colorControlNormal), PorterDuff.Mode.SRC_IN);
            button.setAlpha(0.65f);
        }
    }

    private void onManualDateRangeChanged() {
        if (billsPeriodFilterActive) {
            billsPeriodFilterActive = false;
            PrefManager.setBillsFilterActive(this, false);
            PrefManager.clearBillsFilterSavedRange(this);
            updateBillsPeriodFilterButton(findViewById(R.id.btnBillsPeriodFilter));
        }
        updateTransactionHistory();
    }

    private void updatePondTotalsFooter() {
        if (tvPondTotalsFooter == null) {
            return;
        }
        double sumRem = 0d;
        for (Envelope e : envelopes) {
            sumRem += e.getRemaining();
        }
        double sumAcct = 0d;
        int acctCount = 0;
        for (Envelope e : envelopes) {
            if (e.getAccountBalance() != null) {
                sumAcct += e.getAccountBalance();
                acctCount++;
            }
        }
        if (acctCount == 0) {
            tvPondTotalsFooter.setText(String.format(Locale.getDefault(), getString(R.string.pond_footer_partial), sumRem));
            return;
        }
        double diff = sumAcct - sumRem;
        tvPondTotalsFooter.setText(String.format(Locale.getDefault(), getString(R.string.pond_footer_full), sumAcct, sumRem, diff));
    }

    private void showBillsDaysPickerDialog() {
        HashSet<Integer> selected = new HashSet<>(PrefManager.getBillsDays(this));
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        TableLayout table = new TableLayout(this);
        table.setPadding(pad, pad, pad, pad);
        int day = 1;
        while (day <= 31) {
            TableRow row = new TableRow(this);
            for (int c = 0; c < 7 && day <= 31; c++) {
                final int d = day;
                day++;
                TextView cell = new TextView(this);
                cell.setText(String.valueOf(d));
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(pad, pad, pad, pad);
                billsDayRefreshCell(cell, selected.contains(d));
                cell.setOnClickListener(v -> {
                    if (selected.contains(d)) {
                        selected.remove(d);
                    } else {
                        selected.add(d);
                    }
                    billsDayRefreshCell(cell, selected.contains(d));
                });
                row.addView(cell);
            }
            table.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(table);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_bills_days_title)
                .setMessage(R.string.dialog_bills_days_message)
                .setView(scroll)
                .setPositiveButton(R.string.save, (di, w) -> {
                    ArrayList<Integer> list = new ArrayList<>(selected);
                    Collections.sort(list);
                    PrefManager.saveBillsDays(this, list);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void billsDayRefreshCell(TextView cell, boolean on) {
        if (on) {
            cell.setBackgroundResource(R.drawable.recurring_option_selected);
            cell.setTextColor(ContextCompat.getColor(this, R.color.mountain_primary));
        } else {
            cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            cell.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
        }
    }

    private void updateTransferToggleButton(ImageButton button) {
        int color = showTransfers
                ? ContextCompat.getColor(this, R.color.mountain_primary)
                : resolveThemeColor(androidx.appcompat.R.attr.colorControlNormal);
        button.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        button.setAlpha(showTransfers ? 1.0f : 0.65f);
    }
    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}

































































