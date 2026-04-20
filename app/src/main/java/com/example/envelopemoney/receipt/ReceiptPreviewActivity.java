package com.example.envelopemoney.receipt;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.envelopemoney.R;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;

/**
 * Full-screen receipt image for fact-checking: pinch-zoom, drag, double-tap reset, 90° rotation.
 */
public class ReceiptPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "receipt_image_uri";

    private ReceiptZoomImageView zoomImage;
    private float rotationDegrees;
    private MaterialButton btnRotLeft;
    private MaterialButton btnRotRight;
    private TextView tvGesturesHint;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_preview);

        zoomImage = findViewById(R.id.receiptZoomImage);
        TextView tvError = findViewById(R.id.tvReceiptPreviewErrorFull);
        ImageButton btnClose = findViewById(R.id.btnReceiptPreviewClose);
        btnRotLeft = findViewById(R.id.btnReceiptRotateLeft);
        btnRotRight = findViewById(R.id.btnReceiptRotateRight);

        btnClose.setOnClickListener(v -> finish());
        btnRotLeft.setOnClickListener(v -> applyRotation(-90f));
        btnRotRight.setOnClickListener(v -> applyRotation(90f));

        String uriStr = getIntent() != null ? getIntent().getStringExtra(EXTRA_IMAGE_URI) : null;
        if (uriStr == null || uriStr.isEmpty()) {
            showError(tvError);
            return;
        }
        Uri uri = Uri.parse(uriStr);
        int maxDim = computeDecodeMaxDimension();
        Bitmap bmp;
        try {
            bmp = ReceiptBitmapLoader.decodeSampled(this, uri, maxDim);
        } catch (IOException e) {
            Log.e("EnvelopeMoney", "receipt fullscreen decode", e);
            bmp = null;
        }
        if (bmp == null) {
            showError(tvError);
            return;
        }
        tvError.setVisibility(View.GONE);
        zoomImage.setImageBitmap(bmp);
    }

    private void applyRotation(float delta) {
        rotationDegrees = (rotationDegrees + delta) % 360f;
        if (rotationDegrees < 0) {
            rotationDegrees += 360f;
        }
        zoomImage.setRotation(rotationDegrees);
    }

    private void showError(TextView tvError) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(R.string.receipt_preview_load_failed);
        zoomImage.setVisibility(View.GONE);
        if (btnRotLeft != null) {
            btnRotLeft.setVisibility(View.GONE);
        }
        if (btnRotRight != null) {
            btnRotRight.setVisibility(View.GONE);
        }
        if (tvGesturesHint != null) {
            tvGesturesHint.setVisibility(View.GONE);
        }
    }

    private int computeDecodeMaxDimension() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int longest = Math.max(dm.widthPixels, dm.heightPixels);
        int target = Math.max(longest * 2, 2048);
        return Math.min(target, 4096);
    }
}
