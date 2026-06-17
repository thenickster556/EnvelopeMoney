package com.example.envelopemoney.receipt;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.envelopemoney.R;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Camera capture for receipts; saves upright JPEG to {@link MediaStoreReceiptSaver}.
 */
public class ReceiptCaptureActivity extends AppCompatActivity {

    public static final String EXTRA_CAPTURE_MODE = "capture_mode";
    public static final String EXTRA_SAVED_IMAGE_URI = "saved_image_uri";

    private static final int REQ_CAM = 4001;
    private static final int REQ_WRITE = 4002;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ReceiptCaptureMode selectedMode = ReceiptCaptureMode.AUTO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_capture);

        previewView = findViewById(R.id.previewView);
        MaterialButton shutter = findViewById(R.id.btnShutter);
        Toolbar toolbar = findViewById(R.id.receiptToolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back_white_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        RadioGroup rg = findViewById(R.id.rgCaptureMode);
        rg.check(R.id.rbModeAuto);
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbModeAuto) {
                selectedMode = ReceiptCaptureMode.AUTO;
            } else if (checkedId == R.id.rbModeReceipt) {
                selectedMode = ReceiptCaptureMode.RECEIPT;
            } else if (checkedId == R.id.rbModeRestaurant) {
                selectedMode = ReceiptCaptureMode.RESTAURANT;
            } else if (checkedId == R.id.rbModeGas) {
                selectedMode = ReceiptCaptureMode.GAS;
            }
        });

        shutter.setOnClickListener(v -> capturePhoto());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAM);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.receipt_permission_camera, Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQ_WRITE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                capturePhoto();
            } else {
                Toast.makeText(this, R.string.receipt_permission_storage, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                int rotation = previewView.getDisplay() != null
                        ? previewView.getDisplay().getRotation()
                        : Surface.ROTATION_0;
                imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(rotation)
                        .build();
                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, R.string.receipt_ocr_failed, Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (imageCapture == null) {
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
                return;
            }
        }

        File out = new File(getCacheDir(), "mm_cap_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(out).build();
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Bitmap bmp;
                        try {
                            bmp = ReceiptExifBitmapLoader.decodeUprightFromFile(out.getAbsolutePath());
                        } catch (IOException e) {
                            Toast.makeText(ReceiptCaptureActivity.this, R.string.receipt_ocr_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (bmp == null) {
                            Toast.makeText(ReceiptCaptureActivity.this, R.string.receipt_ocr_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            Uri saved = MediaStoreReceiptSaver.saveJpeg(ReceiptCaptureActivity.this, bmp);
                            Intent data = new Intent();
                            data.putExtra(EXTRA_CAPTURE_MODE, selectedMode.name());
                            data.putExtra(EXTRA_SAVED_IMAGE_URI, saved.toString());
                            setResult(RESULT_OK, data);
                            finish();
                        } catch (IOException e) {
                            Toast.makeText(ReceiptCaptureActivity.this, R.string.receipt_ocr_failed, Toast.LENGTH_LONG).show();
                        } finally {
                            bmp.recycle();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(ReceiptCaptureActivity.this, R.string.receipt_ocr_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
