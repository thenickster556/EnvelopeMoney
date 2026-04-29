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

        String uriStr = getIntent() != null ? getIntent().getStringExtra(EXTRA_IMAGE_URI) : null;
        if (uriStr == null || uriStr.isEmpty()) {
            showError();
            return;
        }
        imageUri = Uri.parse(uriStr);
        int maxDim = computeDecodeMaxDimension();
        Bitmap bmp;
        try {
            bmp = ReceiptBitmapLoader.decodeSampled(this, imageUri, maxDim);
        } catch (IOException e) {
            Log.e(TAG, "receipt fullscreen decode", e);
            bmp = null;
        }
        if (bmp == null) {
            showError();
            return;
        }
        loadOk = true;
        displayBitmap = bmp;
        tvError.setVisibility(View.GONE);
        zoomImage.setImageBitmap(bmp);
        rotationQuarters = 0;
        zoomImage.setRotation(0f);
        updateRotationDirtyUi();
    }

    @Override
    protected void onDestroy() {
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
        if (imageUri == null) {
            return;
        }
        float degrees = rotationQuarters * 90f;
        try {
            ReceiptRotatedJpegWriter.writeRotatedJpegOverwrite(this, imageUri, degrees);
        } catch (IOException e) {
            Log.e(TAG, "receipt rotate save", e);
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.receipt_preview_save_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        rotationQuarters = 0;
        zoomImage.setRotation(0f);
        reloadBitmapAfterSave();
        updateRotationDirtyUi();
    }

    private void reloadBitmapAfterSave() {
        recycleDisplayBitmap();
        if (imageUri == null) {
            return;
        }
        int maxDim = computeDecodeMaxDimension();
        try {
            Bitmap bmp = ReceiptBitmapLoader.decodeSampled(this, imageUri, maxDim);
            if (bmp == null) {
                showErrorAfterSave();
                return;
            }
            displayBitmap = bmp;
            loadOk = true;
            tvError.setVisibility(View.GONE);
            zoomImage.setVisibility(View.VISIBLE);
            zoomImage.setImageBitmap(bmp);
        } catch (IOException e) {
            Log.e(TAG, "receipt reload after save", e);
            showErrorAfterSave();
        }
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
            btnSaveRotation.setEnabled(loadOk && isRotationDirty());
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
