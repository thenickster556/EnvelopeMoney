package com.example.envelopemoney.receipt;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.envelopemoney.Envelope;
import com.example.envelopemoney.MainActivity;
import com.example.envelopemoney.PrefManager;
import com.example.envelopemoney.R;
import com.example.envelopemoney.Transaction;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

/** Runs against actual Android storage and activities. Test-owned pictures are cleaned up afterward. */
@RunWith(AndroidJUnit4.class)
public class ReceiptFolderRecoveryInstrumentedTest {
    @Test public void unidentifiedReferenceShowsAutomaticRetryWithoutPicturePicker() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String permission = AndroidReceiptSource.readPermission();
        if (permission != null) InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), permission);
        List<Envelope> previous = PrefManager.getEnvelopes(context);
        try {
            PrefManager.saveEnvelopes(context, new ArrayList<>());
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                activity.onActivity(screen -> {
                    try {
                        java.lang.reflect.Method open = MainActivity.class.getDeclaredMethod("showReceiptImagePreview", Uri.class);
                        open.setAccessible(true);
                        open.invoke(screen, Uri.parse("content://media/external/images/media/9223372036854770000"));
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                });
                long deadline = SystemClock.elapsedRealtime() + 15000;
                boolean shown = false;
                while (SystemClock.elapsedRealtime() < deadline && !shown) {
                    try { onView(withText(R.string.receipt_recovery_retry)).inRoot(isDialog()).check(matches(isDisplayed())); shown = true; }
                    catch (androidx.test.espresso.NoMatchingViewException unavailable) { SystemClock.sleep(100); }
                }
                assertTrue("Automatic retry must replace manual picture selection", shown);
                onView(withText(R.string.receipt_recovery_missing)).inRoot(isDialog()).check(matches(isDisplayed()));
                Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), "receipt-recovery-error.png"))) {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
                screenshot.recycle();
            }
        } finally { PrefManager.saveEnvelopes(context, previous); }
    }

    @Test public void yesterdayPictureAutomaticallyRepairsAtStartupAndOpensAfterRestart() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<Envelope> previous = PrefManager.getEnvelopes(context);
        Bitmap image = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(); paint.setColor(Color.BLACK); paint.setTextSize(34);
        canvas.drawText("Yesterday's receipt", 35, 100, paint);
        canvas.drawText("Lunch     $12.50", 35, 170, paint);
        Uri saved = MediaStoreReceiptSaver.saveJpeg(context, image); image.recycle();
        String fileName = ReceiptReferenceResolver.fileNameFromReference(saved.toString());
        Calendar yesterday = Calendar.getInstance(); yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(yesterday.getTime());
        String broken = "content://media/external/images/media/9223372036854770000#" + fileName;
        try {
            Envelope envelope = new Envelope("Receipt recovery test", 100);
            Transaction transaction = new Transaction(envelope.getName(), 12.50, date, "Yesterday's receipt");
            transaction.setReceiptImageUri(broken);
            envelope.getTransactions().add(transaction);
            PrefManager.saveEnvelopes(context, Arrays.asList(envelope));
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                long deadline = SystemClock.elapsedRealtime() + 15000;
                while (SystemClock.elapsedRealtime() < deadline && broken.equals(storedReceipt(context))) SystemClock.sleep(100);
                assertNotEquals("Startup must backfill without a picture picker", broken, storedReceipt(context));
            }
            String repaired = storedReceipt(context);
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                assertEquals(repaired, storedReceipt(context));
            }
            Intent preview = new Intent(context, ReceiptPreviewActivity.class)
                    .putExtra(ReceiptPreviewActivity.EXTRA_IMAGE_URI, repaired);
            try (ActivityScenario<ReceiptPreviewActivity> activity = ActivityScenario.launch(preview)) {
                long deadline = SystemClock.elapsedRealtime() + 15000;
                final boolean[] loaded = {false};
                while (SystemClock.elapsedRealtime() < deadline && !loaded[0]) {
                    activity.onActivity(screen -> loaded[0] = screen.findViewById(R.id.btnReceiptRotateLeft).isEnabled());
                    SystemClock.sleep(100);
                }
                assertTrue("Repaired receipt must render", loaded[0]);
                activity.onActivity(screen -> assertEquals(View.GONE, screen.findViewById(R.id.tvReceiptPreviewErrorFull).getVisibility()));
                Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                File output = new File(context.getExternalFilesDir(null), "receipt-recovery-after.png");
                try (FileOutputStream stream = new FileOutputStream(output)) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream); }
                screenshot.recycle();
            }
            assertEquals(12.50, PrefManager.getEnvelopes(context).get(0).getTransactions().get(0).getAmount(), 0);
            assertEquals(ReceiptReferenceResolver.Status.RESOLVED, AndroidReceiptSource.resolve(context, saved.toString(), null).status);
        } finally {
            PrefManager.saveEnvelopes(context, previous);
            context.getContentResolver().delete(AndroidReceiptSource.providerUri(saved.toString()), null, null);
        }
    }

    private String storedReceipt(Context context) {
        return PrefManager.getEnvelopes(context).get(0).getTransactions().get(0).getReceiptImageUri();
    }
}
