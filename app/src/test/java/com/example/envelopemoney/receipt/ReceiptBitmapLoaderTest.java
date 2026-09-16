package com.example.envelopemoney.receipt;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28, manifest = org.robolectric.annotation.Config.NONE)
public class ReceiptBitmapLoaderTest {

    @Test
    public void withAlbumDisplayName_keepsContentScheme() {
        Uri named = MediaStoreReceiptSaver.withAlbumDisplayName(
                Uri.parse("content://media/external/images/media/9"),
                "MountainMoney_1.jpg");
        assertEquals("content", named.getScheme());
        assertEquals("MountainMoney_1.jpg", named.getFragment());
        assertEquals("MountainMoney_1.jpg", ReceiptPickerUriNormalizer.albumDisplayName(named));
    }

    @Test
    public void decodeSampled_opensStoredContentUriViaContentResolver() throws Exception {
        org.robolectric.shadows.ShadowBitmapFactory.setAllowInvalidImageData(false);
        File file = File.createTempFile("receipt", ".jpg",
                RuntimeEnvironment.getApplication().getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        bitmap.recycle();
        StoredUriProvider provider = new StoredUriProvider();
        provider.file = file;
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "media";
        info.exported = true;
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal("media", provider);
        Uri stored = Uri.parse("content://media/external/images/media/9");
        Bitmap decoded = ReceiptBitmapLoader.decodeSampled(
                RuntimeEnvironment.getApplication(), stored, 64);
        assertNotNull(decoded);
        decoded.recycle();
        assertTrue(file.delete());
    }

    @Test
    public void decodeSampled_readsUriThatAllowsOnlyOneOpen() throws Exception {
        org.robolectric.shadows.ShadowBitmapFactory.setAllowInvalidImageData(false);
        File file = File.createTempFile("once", ".jpg",
                RuntimeEnvironment.getApplication().getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        bitmap.recycle();
        StoredUriProvider provider = new StoredUriProvider();
        provider.file = file;
        provider.oneShot = true;
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "media.once";
        info.exported = true;
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal("media.once", provider);
        Bitmap decoded = ReceiptBitmapLoader.decodeSampled(
                RuntimeEnvironment.getApplication(),
                Uri.parse("content://media.once/images/1"),
                64);
        assertNotNull(decoded);
        decoded.recycle();
        assertEquals(1, provider.opens);
        assertTrue(file.delete());
    }

    @Test
    public void isReadable_falseForMissingUri() {
        assertFalse(ReceiptBitmapLoader.isReadable(
                RuntimeEnvironment.getApplication(),
                Uri.parse("content://media/external/images/media/404")));
    }

    @Test
    public void decodeSampled_opensLeftoverFileUriWithFileInputStream() throws Exception {
        org.robolectric.shadows.ShadowBitmapFactory.setAllowInvalidImageData(false);
        File file = File.createTempFile("MountainMoney_", ".jpg",
                RuntimeEnvironment.getApplication().getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        bitmap.recycle();
        Bitmap decoded = ReceiptBitmapLoader.decodeSampled(
                RuntimeEnvironment.getApplication(), Uri.fromFile(file), 64);
        assertNotNull(decoded);
        decoded.recycle();
        assertTrue(file.delete());
    }

    @Test
    public void decodeSampled_fileUriFitsInsideMaxDim() throws Exception {
        org.robolectric.shadows.ShadowBitmapFactory.setAllowInvalidImageData(false);
        File file = File.createTempFile("MountainMoney_large_", ".jpg",
                RuntimeEnvironment.getApplication().getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        bitmap.recycle();
        Bitmap decoded = ReceiptBitmapLoader.decodeSampled(
                RuntimeEnvironment.getApplication(), Uri.fromFile(file), 64);
        assertNotNull(decoded);
        assertTrue(decoded.getWidth() <= 64);
        assertTrue(decoded.getHeight() <= 64);
        decoded.recycle();
        assertTrue(file.delete());
    }

    @Test
    public void sampleSizeFitsBothSidesInsideMaxDim() {
        assertEquals(16, ReceiptBitmapLoader.sampleSize(512, 512, 64));
        assertEquals(1, ReceiptBitmapLoader.sampleSize(40, 30, 64));
        assertEquals(1, ReceiptBitmapLoader.sampleSize(64, 64, 64));
    }

    public static class StoredUriProvider extends ContentProvider {
        File file;
        boolean oneShot;
        int opens;

        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return "image/jpeg"; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String sort) {
            return null;
        }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] arguments) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { return 0; }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            if (file == null) {
                throw new FileNotFoundException();
            }
            opens++;
            if (oneShot && opens > 1) {
                throw new FileNotFoundException("one-shot");
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }
}
