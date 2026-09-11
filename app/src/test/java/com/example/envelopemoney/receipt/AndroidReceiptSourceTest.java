package com.example.envelopemoney.receipt;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 33}, manifest = Config.NONE)
public class AndroidReceiptSourceTest {
    private Context context;
    private GalleryProvider gallery;
    private static final String NAME = "MountainMoney_yesterday.jpg";
    private static final String OLD = "content://media/external/images/media/1";
    private static final String FRESH = "content://media/external/images/media/2";

    @Before public void setup() {
        org.robolectric.shadows.ShadowBitmapFactory.setAllowInvalidImageData(false);
        context = RuntimeEnvironment.getApplication();
        gallery = new GalleryProvider();
        android.content.pm.ProviderInfo providerInfo = new android.content.pm.ProviderInfo();
        providerInfo.authority = "media";
        providerInfo.exported = true;
        gallery.attachInfo(context, providerInfo);
        ShadowContentResolver.registerProviderInternal("media", gallery);
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES);
    }

    @Test public void repairsYesterdayPictureUsingExactAlbumQueryAndDecodesPreview() throws Exception {
        gallery.file = picture();
        try (InputStream stream = context.getContentResolver().openInputStream(Uri.parse(FRESH))) {
            assertNotNull(stream);
        }
        AndroidReceiptSource source = new AndroidReceiptSource(context);
        ReceiptReferenceResolver.Result result = new ReceiptReferenceResolver(source).resolve(OLD, NAME);
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED, result.status);
        assertEquals(FRESH, result.reference);
        assertTrue(gallery.selection.contains(android.os.Build.VERSION.SDK_INT >= 29 ? "relative_path" : "_data"));
        assertTrue(gallery.selectionArguments[0].replace('\\', '/').contains("Pictures/Mountain Money"));
        Bitmap preview = ReceiptBitmapLoader.decodeSampled(context, Uri.parse(OLD + "#" + NAME), 128);
        assertNotNull(preview); preview.recycle();
        assertEquals(0, gallery.deletes);
        assertEquals(0, gallery.writes);
    }

    @Test public void permissionDenialRetriesAndCorruptionIsDistinct() throws Exception {
        gallery.file = picture(); gallery.denied = true;
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                AndroidReceiptSource.resolve(context, OLD, NAME).status);
        gallery.denied = false;
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED,
                AndroidReceiptSource.resolve(context, OLD, NAME).status);
        try (FileOutputStream output = new FileOutputStream(gallery.file)) { output.write(new byte[]{1, 2, 3}); }
        assertEquals(ReceiptReferenceResolver.Status.CORRUPT,
                new AndroidReceiptSource(context).inspect(FRESH).status);
    }

    @Test public void missingFilesAndRestrictedEmptyLibraryRemainRetryable() {
        AndroidReceiptSource source = new AndroidReceiptSource(context);
        assertEquals(ReceiptReferenceResolver.Status.MISSING, source.inspect(OLD).status);
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES);
        assertTrue(source.needsReadPermission());
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED, source.inspect(OLD).status);
        try { source.readAlbum(); fail("Expected access request"); } catch (SecurityException expected) { }
        assertTrue(source.shouldRequestLibraryAccess(true));
        assertFalse(source.shouldRequestLibraryAccess(false));
    }

    @Test public void ownedPicturesWithoutLibraryPermissionStillRequireAccessToFindLegacyFiles() throws Exception {
        gallery.file = picture();
        gallery.displayName = "MountainMoney_owned.jpg";
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES);
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                AndroidReceiptSource.resolve(context, OLD, NAME).status);
    }

    @Test @Config(sdk = 33) public void exactAlbumLocationRejectsNestedFoldersAndAcceptsOptionalSlash() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mountain Money");
        assertTrue(AndroidReceiptSource.isExactAlbumLocation("Pictures/Mountain Money", true, folder));
        assertTrue(AndroidReceiptSource.isExactAlbumLocation("Pictures/Mountain Money/", true, folder));
        assertFalse(AndroidReceiptSource.isExactAlbumLocation("Pictures/Mountain Money/nested", true, folder));
        assertFalse(AndroidReceiptSource.isExactAlbumLocation(null, true, folder));
    }

    @Test @Config(sdk = 33) public void albumRowsWithoutTrailingSlashStillMatchExactFolder() throws Exception {
        gallery.file = picture();
        gallery.relativePath = "Pictures/Mountain Money";
        ReceiptReferenceResolver.Result result = AndroidReceiptSource.resolve(context, OLD, NAME);
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED, result.status);
        assertEquals(FRESH, result.reference);
    }

    @Test public void plainAndFilePathsRemainReadableAndFragmentsNeverReachProvider() throws Exception {
        File file = picture();
        AndroidReceiptSource source = new AndroidReceiptSource(context);
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED, source.inspect(file.getAbsolutePath()).status);
        // File.toURI supplies portable separators when the host test JVM runs on Windows.
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED, source.inspect(file.toURI().toString()).status);
        assertNull(AndroidReceiptSource.providerUri(FRESH + "#" + NAME).getFragment());
        gallery.file = file;
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED, source.inspect(FRESH + "#" + NAME).status);
        assertEquals(NAME, source.inspect(FRESH).fileName);
    }

    @Test public void duplicateAlbumPicturesAreAmbiguous() throws Exception {
        gallery.file = picture(); gallery.duplicate = true;
        assertEquals(ReceiptReferenceResolver.Status.AMBIGUOUS,
                AndroidReceiptSource.resolve(context, OLD, NAME).status);
    }

    @Test public void unavailableMetadataDoesNotBlockReadableOriginal() throws Exception {
        gallery.file = picture(); gallery.metadataUnavailable = true;
        ReceiptReferenceResolver.Result result = AndroidReceiptSource.resolve(context, FRESH, null);
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED, result.status);
        assertNull(result.fileName);
    }

    @Test public void unsupportedDocumentIdsAreNotGuessed() {
        String video = "content://com.android.providers.media.documents/document/video%3A2";
        assertEquals(video, AndroidReceiptSource.providerUri(video).toString());
        String oversized = "content://com.android.providers.media.documents/document/image%3A999999999999999999999999";
        assertEquals(oversized, AndroidReceiptSource.providerUri(oversized).toString());
    }

    @Test @Config(sdk = 22) public void oldAndroidDoesNotRequestRuntimePhotoPermission() {
        assertNull(AndroidReceiptSource.readPermission());
        assertFalse(new AndroidReceiptSource(context).needsReadPermission());
        assertFalse(new AndroidReceiptSource(context).shouldRequestLibraryAccess(true));
    }

    @Test public void failedOriginalStreamRetainsMetadataForAutomaticFolderMatch() throws Exception {
        gallery.file = picture();
        assertEquals(FRESH, AndroidReceiptSource.resolve(context, OLD, null).reference);
    }

    @Test public void mediaDocumentIdentifierOpensExistingMediaRowWithoutPicker() throws Exception {
        gallery.file = picture();
        assertEquals(FRESH, AndroidReceiptSource.resolve(context,
                "content://com.android.providers.media.documents/document/image%3A2", null).reference);
    }

    @Test public void unindexedFolderPictureIsDiscoveredAutomatically() throws Exception {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mountain Money");
        folder.mkdirs();
        File receipt = new File(folder, NAME);
        java.nio.file.Files.copy(picture().toPath(), receipt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ReceiptReferenceResolver.Result result = AndroidReceiptSource.resolve(context, OLD, NAME);
        assertEquals(ReceiptReferenceResolver.Status.RESOLVED, result.status);
        assertEquals(NAME, result.fileName);
        assertEquals("file", Uri.parse(result.reference).getScheme());
        assertTrue(receipt.exists());
    }

    @Test public void rotationWritesResolvedPictureAndLeavesOriginalReferenceAlone() throws Exception {
        gallery.file = picture();
        ReceiptRotatedJpegWriter.writeRotatedJpegOverwrite(context, Uri.parse(OLD + "#" + NAME), 90);
        assertEquals(1, gallery.writes);
        assertEquals(FRESH, gallery.lastWrite);
        assertEquals(0, gallery.deletes);
    }

    private File picture() throws Exception {
        File file = File.createTempFile("yesterday", ".jpg", context.getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
        try (OutputStream output = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output); }
        bitmap.recycle(); return file;
    }

    public static class GalleryProvider extends ContentProvider {
        File file;
        boolean denied;
        boolean duplicate;
        boolean metadataUnavailable;
        String displayName = NAME;
        String relativePath;
        String selection;
        String[] selectionArguments;
        int writes;
        int deletes;
        String lastWrite;
        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return "image/jpeg"; }
        @Override public Uri insert(Uri uri, ContentValues values) { throw new AssertionError("Unexpected import"); }
        @Override public int delete(Uri uri, String selection, String[] arguments) { deletes++; return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { return 0; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String sort) {
            if (denied) throw new SecurityException();
            assertNull(uri.getFragment());
            MatrixCursor cursor = new MatrixCursor(projection);
            if (file == null) return cursor;
            if (projection.length == 1) {
                if (metadataUnavailable) throw new UnsupportedOperationException();
                cursor.addRow(new Object[]{displayName}); return cursor;
            }
            this.selection = selection; selectionArguments = arguments;
            String location = android.os.Build.VERSION.SDK_INT >= 29
                    ? (relativePath != null ? relativePath : "Pictures/Mountain Money/")
                    : new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mountain Money/" + displayName).getAbsolutePath();
            cursor.addRow(new Object[]{2L, displayName, location});
            if (duplicate) cursor.addRow(new Object[]{3L, NAME, location});
            return cursor;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            if (denied) throw new SecurityException();
            assertNull(uri.getFragment());
            if (file == null || !uri.toString().equals(FRESH)) throw new FileNotFoundException();
            if (mode.contains("w")) { writes++; lastWrite = uri.toString(); }
            return ParcelFileDescriptor.open(file, mode.contains("w")
                    ? ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE
                    : ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }
}
