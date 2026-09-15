package com.example.envelopemoney.receipt;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.envelopemoney.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

/**
 * Full-screen receipt: pinch-zoom, pan, double-tap refit; 90° rotation is view-only until saved.
 * Save decodes from the URI, applies cumulative rotation to pixels via {@link android.graphics.Matrix}, overwrites the same URI as JPEG.
 * Candidate mode is the read-only check before attaching: transaction header, arrows or a
 * fit-scale swipe between candidates, and a "use this picture" result; rotate/save stay hidden there.
 */
public class ReceiptPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "receipt_image_uri";
    public static final String EXTRA_CANDIDATE_REFERENCES = "receipt_candidate_references";
    public static final String EXTRA_CANDIDATE_INDEX = "receipt_candidate_index";
    public static final String EXTRA_CANDIDATE_TITLE = "receipt_candidate_title";
    public static final String EXTRA_CANDIDATE_DETAIL = "receipt_candidate_detail";
    public static final String EXTRA_CANDIDATE_COMMENT = "receipt_candidate_comment";
    public static final String EXTRA_CANDIDATE_POND = "receipt_candidate_pond";
    public static final String EXTRA_CANDIDATE_AMOUNT = "receipt_candidate_amount";
    public static final String EXTRA_PICKED_REFERENCE = "receipt_picked_reference";
    public static final String EXTRA_REQUEST_SWAP = "receipt_request_swap";
    public static final String EXTRA_SWAP_REFERENCE = "receipt_swap_reference";
    private static final String STATE_CANDIDATE_INDEX = "receipt_candidate_index_state";
    private static final String STATE_IMMERSIVE = "receipt_preview_immersive_state";

    /**
     * URI permission grants follow {@link android.content.Intent#setData}, not extras.
     * Only {@code content://} may be put on Intent data ({@code file://} throws FileUriExposedException).
     */
    @Nullable
    public static Uri intentDataUri(@Nullable Uri uri) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        return uri.getFragment() == null ? uri : uri.buildUpon().fragment(null).build();
    }

    /** True when this launch is the candidate check rather than the attached-receipt viewer. */
    static boolean isCandidateIntent(@Nullable Intent intent) {
        return intent != null && intent.getStringArrayExtra(EXTRA_CANDIDATE_REFERENCES) != null;
    }

    static String[] candidateReferences(@Nullable Intent intent) {
        if (!isCandidateIntent(intent)) return new String[0];
        return intent.getStringArrayExtra(EXTRA_CANDIDATE_REFERENCES);
    }

    /** Start index clamped to the candidate list so a stale intent can never point outside it. */
    static int candidateIndex(@Nullable Intent intent) {
        String[] references = candidateReferences(intent);
        if (references.length == 0) return 0;
        int index = intent.getIntExtra(EXTRA_CANDIDATE_INDEX, 0);
        return Math.max(0, Math.min(index, references.length - 1));
    }

    private static final String TAG = "EnvelopeMoney";

    private ReceiptZoomImageView zoomImage;
    private ImageButton btnRotLeft;
    private ImageButton btnRotRight;
    private ImageButton btnSaveRotation;
    private ImageButton btnChooseDifferent;
    private ImageButton btnFullscreen;
    private ImageButton btnExitFullscreen;
    private View topChrome;
    private View candidateBottomBar;
    private View candidateImmersiveCue;
    private TextView tvImmersiveComment;
    private TextView tvImmersivePond;
    private TextView tvImmersiveAmount;
    private MaterialButton btnCandidateSelect;
    private ImageButton btnCandidatePrevious;
    private ImageButton btnCandidateNext;
    private TextView tvCandidateTitle;
    private TextView tvCandidateDetail;
    private TextView tvCandidateCount;
    private TextView tvGesturesHint;
    private TextView tvError;

    private Uri imageUri;
    @Nullable
    private Bitmap displayBitmap;
    private boolean loadOk;
    private boolean pictureOperationInProgress;
    private final java.util.concurrent.ExecutorService pictureExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Multiple of 90° for {@link ReceiptZoomImageView#setRotation(float)}; 0 when aligned with file. */
    private int rotationQuarters;
    /** Candidate check state; empty array means normal attached-receipt viewing. */
    private String[] candidateReferences = new String[0];
    private int candidateIndex;
    private boolean gesturesHintDismissed;
    private boolean immersive;

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            tryClosePreview();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_preview);
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        zoomImage = findViewById(R.id.receiptZoomImage);
        tvError = findViewById(R.id.tvReceiptPreviewErrorFull);
        ImageButton btnClose = findViewById(R.id.btnReceiptPreviewClose);
        btnRotLeft = findViewById(R.id.btnReceiptRotateLeft);
        btnRotRight = findViewById(R.id.btnReceiptRotateRight);
        btnSaveRotation = findViewById(R.id.btnReceiptSaveRotation);
        btnChooseDifferent = findViewById(R.id.btnReceiptChooseDifferent);
        btnFullscreen = findViewById(R.id.btnReceiptPreviewFullscreen);
        btnExitFullscreen = findViewById(R.id.btnReceiptPreviewExitFullscreen);
        topChrome = findViewById(R.id.receiptPreviewTopChrome);
        candidateBottomBar = findViewById(R.id.candidateBottomBar);
        candidateImmersiveCue = findViewById(R.id.candidateImmersiveCue);
        tvImmersiveComment = findViewById(R.id.tvImmersiveComment);
        tvImmersivePond = findViewById(R.id.tvImmersivePond);
        tvImmersiveAmount = findViewById(R.id.tvImmersiveAmount);
        btnCandidateSelect = findViewById(R.id.btnCandidateSelect);
        btnCandidatePrevious = findViewById(R.id.btnCandidatePrevious);
        btnCandidateNext = findViewById(R.id.btnCandidateNext);
        tvCandidateTitle = findViewById(R.id.tvCandidateTitle);
        tvCandidateDetail = findViewById(R.id.tvCandidateDetail);
        tvCandidateCount = findViewById(R.id.tvCandidateCount);
        tvGesturesHint = findViewById(R.id.tvReceiptPreviewGesturesHint);
        zoomImage.setOnUserTransformListener(this::dismissGesturesHint);

        btnClose.setOnClickListener(v -> tryClosePreview());
        btnRotLeft.setOnClickListener(v -> applyViewRotation(-90f));
        btnRotRight.setOnClickListener(v -> applyViewRotation(90f));
        btnSaveRotation.setOnClickListener(v -> confirmReplaceThenSave());
        btnCandidatePrevious.setOnClickListener(v -> {
            dismissGesturesHint();
            showCandidate(candidateIndex - 1);
        });
        btnCandidateNext.setOnClickListener(v -> {
            dismissGesturesHint();
            showCandidate(candidateIndex + 1);
        });
        btnCandidateSelect.setOnClickListener(v -> returnPickedCandidate());
        btnChooseDifferent.setOnClickListener(v -> returnSwapRequest());
        btnFullscreen.setOnClickListener(v -> setPreviewImmersive(true));
        btnExitFullscreen.setOnClickListener(v -> setPreviewImmersive(false));

        if (isCandidateIntent(getIntent())) {
            enterCandidateMode(savedInstanceState);
            return;
        }
        Uri fromData = getIntent() != null ? getIntent().getData() : null;
        String uriStr = getIntent() != null ? getIntent().getStringExtra(EXTRA_IMAGE_URI) : null;
        if (fromData != null) {
            imageUri = fromData;
        } else if (uriStr != null && !uriStr.isEmpty()) {
            imageUri = Uri.parse(uriStr);
        } else {
            showError();
            return;
        }
        loadPictureInBackground(false);
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CANDIDATE_INDEX, candidateIndex);
        outState.putBoolean(STATE_IMMERSIVE, immersive);
    }

    /** Read-only check chrome: transaction header, arrow bar, no rotate/save; restores index after rotation. */
    private void enterCandidateMode(@Nullable Bundle savedInstanceState) {
        candidateReferences = candidateReferences(getIntent());
        if (candidateReferences.length == 0) {
            showError();
            return;
        }
        candidateIndex = savedInstanceState != null
                ? Math.max(0, Math.min(savedInstanceState.getInt(STATE_CANDIDATE_INDEX, 0),
                        candidateReferences.length - 1))
                : candidateIndex(getIntent());
        btnRotLeft.setVisibility(View.GONE);
        btnRotRight.setVisibility(View.GONE);
        btnSaveRotation.setVisibility(View.GONE);
        btnChooseDifferent.setVisibility(View.GONE);
        btnFullscreen.setVisibility(View.VISIBLE);
        findViewById(R.id.candidateInfo).setVisibility(View.VISIBLE);
        candidateBottomBar.setVisibility(View.VISIBLE);
        tvCandidateTitle.setText(getIntent().getStringExtra(EXTRA_CANDIDATE_TITLE));
        tvCandidateDetail.setText(getIntent().getStringExtra(EXTRA_CANDIDATE_DETAIL));
        bindImmersiveCue();
        tvGesturesHint.setText(R.string.receipt_preview_gestures_hint_candidates);
        zoomImage.setFitSwipeListener(delta -> {
            if (pictureOperationInProgress) {
                return;
            }
            dismissGesturesHint();
            showCandidate(candidateIndex + delta);
        });
        showCandidate(candidateIndex);
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_IMMERSIVE, false)) {
            setPreviewImmersive(true);
        }
    }

    private void setPreviewImmersive(boolean value) {
        immersive = value;
        ReceiptPreviewImmersive.apply(immersive, isCandidateMode(),
                topChrome, candidateBottomBar, btnExitFullscreen, candidateImmersiveCue);
    }

    /** Comment, pond, and amount for the fullscreen glass; empty comment reads "No comment". */
    private void bindImmersiveCue() {
        String comment = getIntent().getStringExtra(EXTRA_CANDIDATE_COMMENT);
        String pond = getIntent().getStringExtra(EXTRA_CANDIDATE_POND);
        String amount = getIntent().getStringExtra(EXTRA_CANDIDATE_AMOUNT);
        if (comment == null || comment.trim().isEmpty()) {
            comment = getString(R.string.receipt_candidate_no_comment);
        }
        if (pond == null) {
            pond = "";
        }
        if (amount == null) {
            amount = "";
        }
        tvImmersiveComment.setText(comment);
        tvImmersivePond.setText(pond);
        tvImmersiveAmount.setText(amount);
        candidateImmersiveCue.setContentDescription(
                getString(R.string.content_desc_receipt_immersive_cue, comment, pond, amount));
    }

    private boolean isCandidateMode() {
        return candidateReferences.length > 0;
    }

    private void dismissGesturesHint() {
        gesturesHintDismissed = true;
        if (tvGesturesHint != null) {
            tvGesturesHint.setVisibility(View.GONE);
        }
    }

    private void showCandidate(int index) {
        if (index < 0 || index >= candidateReferences.length) return;
        candidateIndex = index;
        rotationQuarters = 0;
        zoomImage.setRotation(0f);
        imageUri = Uri.parse(candidateReferences[index]);
        tvCandidateCount.setText(getString(R.string.receipt_candidate_count,
                candidateIndex + 1, candidateReferences.length));
        btnCandidatePrevious.setEnabled(candidateIndex > 0);
        btnCandidateNext.setEnabled(candidateIndex < candidateReferences.length - 1);
        loadOk = false;
        updateCandidateSelectEnabled();
        if (gesturesHintDismissed && tvGesturesHint != null) {
            tvGesturesHint.setVisibility(View.GONE);
        }
        loadPictureInBackground(false);
    }

    private void updateCandidateSelectEnabled() {
        boolean enabled = loadOk && !pictureOperationInProgress;
        if (btnCandidateSelect != null) {
            btnCandidateSelect.setEnabled(enabled);
            btnCandidateSelect.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void returnPickedCandidate() {
        if (candidateIndex >= candidateReferences.length) return;
        Intent picked = new Intent();
        picked.putExtra(EXTRA_PICKED_REFERENCE, candidateReferences[candidateIndex]);
        setResult(RESULT_OK, picked);
        finish();
    }

    /** Asks MainActivity to reopen the picker for this receipt; nothing changes here. */
    private void returnSwapRequest() {
        Intent swap = new Intent();
        swap.putExtra(EXTRA_REQUEST_SWAP, true);
        swap.putExtra(EXTRA_SWAP_REFERENCE, getIntent() != null
                ? getIntent().getStringExtra(EXTRA_IMAGE_URI) : null);
        setResult(RESULT_CANCELED, swap);
        finish();
    }

    @Override
    protected void onDestroy() {
        pictureExecutor.shutdownNow();
        recycleDisplayBitmap();
        super.onDestroy();
    }

    private void tryClosePreview() {
        if (ReceiptPreviewImmersive.consumeBack(immersive)) {
            setPreviewImmersive(false);
            return;
        }
        if (!loadOk) {
            finish();
            return;
        }
        if (!isRotationDirty()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.receipt_preview_discard_rotation_title)
                .setMessage(R.string.receipt_preview_discard_rotation_message)
                .setNegativeButton(R.string.receipt_preview_keep_editing, null)
                .setPositiveButton(R.string.receipt_preview_discard, (d, w) -> finish())
                .show();
    }

    private void confirmReplaceThenSave() {
        if (imageUri == null || !isRotationDirty()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.receipt_preview_replace_title)
                .setMessage(R.string.receipt_preview_replace_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.receipt_preview_replace_confirm, (d, w) -> saveRotationOverwrite())
                .show();
    }

    private void saveRotationOverwrite() {
        if (imageUri == null || pictureOperationInProgress) return;
        float degrees = rotationQuarters * 90f;
        setPictureOperationInProgress(true);
        pictureExecutor.execute(() -> {
            try {
                ReceiptRotatedJpegWriter.writeRotatedJpegOverwrite(this, imageUri, degrees);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    rotationQuarters = 0;
                    zoomImage.setRotation(0f);
                    loadPictureInBackground(true);
                });
            } catch (IOException failure) {
                Log.e(TAG, "receipt rotate save", failure);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setPictureOperationInProgress(false);
                    new MaterialAlertDialogBuilder(this).setMessage(R.string.receipt_preview_save_failed)
                            .setPositiveButton(android.R.string.ok, null).show();
                });
            }
        });
    }

    /** Gallery lookup, EXIF reading and decoding never block gestures or the activity's main thread. */
    private void loadPictureInBackground(boolean afterSave) {
        int maximumDimension = computeDecodeMaxDimension();
        setPictureOperationInProgress(true);
        pictureExecutor.execute(() -> {
            Bitmap decoded;
            try {
                decoded = ReceiptBitmapLoader.decodeSampled(this, imageUri, maximumDimension);
            } catch (IOException failure) {
                Log.e(TAG, "receipt picture decode", failure);
                decoded = null;
            }
            final Bitmap picture = decoded;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (picture != null) picture.recycle();
                    return;
                }
                if (picture == null) {
                    if (afterSave) {
                        recycleDisplayBitmap();
                        showErrorAfterSave();
                    } else {
                        showError();
                    }
                } else {
                    recycleDisplayBitmap();
                    displayBitmap = picture;
                    loadOk = true;
                    tvError.setVisibility(View.GONE);
                    zoomImage.setVisibility(View.VISIBLE);
                    zoomImage.setImageBitmap(picture);
                }
                setPictureOperationInProgress(false);
            });
        });
    }

    private void setPictureOperationInProgress(boolean inProgress) {
        pictureOperationInProgress = inProgress;
        setIconEnabled(btnRotLeft, !inProgress && loadOk);
        setIconEnabled(btnRotRight, !inProgress && loadOk);
        updateCandidateSelectEnabled();
        updateRotationDirtyUi();
    }

    /** Icon buttons dim instead of relying on text-button tinting; never hidden by state. */
    private void setIconEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
    }
    private void showErrorAfterSave() {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(R.string.receipt_preview_load_failed);
        zoomImage.setVisibility(View.GONE);
        hideChrome();
        loadOk = false;
    }

    private void applyViewRotation(float delta) {
        int step = delta > 0f ? 1 : -1;
        rotationQuarters = (rotationQuarters + step + 4) % 4;
        zoomImage.setRotation(rotationQuarters * 90f);
        updateRotationDirtyUi();
    }

    private boolean isRotationDirty() {
        return rotationQuarters != 0;
    }

    private void updateRotationDirtyUi() {
        if (btnSaveRotation != null) {
            setIconEnabled(btnSaveRotation,
                    loadOk && !pictureOperationInProgress && isRotationDirty());
        }
    }

    private void showError() {
        loadOk = false;
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(R.string.receipt_preview_load_failed);
        if (isCandidateMode()) {
            recycleDisplayBitmap();
            zoomImage.setImageDrawable(null);
            zoomImage.setVisibility(View.VISIBLE);
            return;
        }
        zoomImage.setVisibility(View.GONE);
        hideChrome();
    }

    private void hideChrome() {
        if (btnRotLeft != null) {
            btnRotLeft.setVisibility(View.GONE);
        }
        if (btnRotRight != null) {
            btnRotRight.setVisibility(View.GONE);
        }
        if (btnSaveRotation != null) {
            btnSaveRotation.setVisibility(View.GONE);
        }
        if (tvGesturesHint != null) {
            tvGesturesHint.setVisibility(View.GONE);
        }
    }

    private void recycleDisplayBitmap() {
        if (displayBitmap != null && !displayBitmap.isRecycled()) {
            displayBitmap.recycle();
        }
        displayBitmap = null;
    }

    private int computeDecodeMaxDimension() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int longest = Math.max(dm.widthPixels, dm.heightPixels);
        int target = Math.max(longest * 2, 2048);
        return Math.min(target, 4096);
    }
}
