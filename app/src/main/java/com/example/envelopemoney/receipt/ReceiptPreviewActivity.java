package com.example.envelopemoney.receipt;

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
 */
public class ReceiptPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "receipt_image_uri";

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

    private static final String TAG = "EnvelopeMoney";

    private ReceiptZoomImageView zoomImage;
    private MaterialButton btnRotLeft;
    private MaterialButton btnRotRight;
    private MaterialButton btnSaveRotation;
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
        tvGesturesHint = findViewById(R.id.tvReceiptPreviewGesturesHint);

        btnClose.setOnClickListener(v -> tryClosePreview());
        btnRotLeft.setOnClickListener(v -> applyViewRotation(-90f));
        btnRotRight.setOnClickListener(v -> applyViewRotation(90f));
        btnSaveRotation.setOnClickListener(v -> confirmReplaceThenSave());

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
    protected void onDestroy() {
        pictureExecutor.shutdownNow();
        recycleDisplayBitmap();
        super.onDestroy();
    }

    private void tryClosePreview() {
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
                recycleDisplayBitmap();
                if (picture == null) {
                    if (afterSave) showErrorAfterSave(); else showError();
                } else {
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
        btnRotLeft.setEnabled(!inProgress && loadOk);
        btnRotRight.setEnabled(!inProgress && loadOk);
        updateRotationDirtyUi();
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
            btnSaveRotation.setEnabled(loadOk && !pictureOperationInProgress && isRotationDirty());
        }
    }

    private void showError() {
        loadOk = false;
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(R.string.receipt_preview_load_failed);
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
