package com.example.envelopemoney;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.envelopemoney.receipt.OcrAmountWeights;

import java.util.ArrayList;
import java.util.List;

/**
 * Sidecar SQLite store for comment typeahead and OCR amount weights.
 * Envelope Gson prefs are unchanged. A missing or corrupt file falls back to defaults.
 */
public final class LearningDb extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "mountain_money_learning.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TAG = "LearningDb";

    public LearningDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)");
        db.execSQL("CREATE TABLE comments (text TEXT PRIMARY KEY, last_used_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE ocr_weight_vec ("
                + "id INTEGER PRIMARY KEY CHECK (id = 1), n INTEGER, floats BLOB)");
        db.execSQL("INSERT INTO meta(key, value) VALUES ('version', '1')");
        ContentValues weights = new ContentValues();
        weights.put("id", 1);
        weights.put("n", OcrAmountWeights.FEATURE_COUNT);
        weights.put("floats", OcrAmountWeights.toLittleEndianBlob(OcrAmountWeights.defaults()));
        db.insert("ocr_weight_vec", null, weights);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 has no migrations.
    }

    @NonNull
    public List<String> loadComments() {
        List<String> comments = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT text FROM comments ORDER BY last_used_ms DESC", null);
            try {
                while (cursor.moveToNext()) {
                    comments.add(cursor.getString(0));
                }
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadComments", e);
        }
        return comments;
    }

    public void rememberComment(String comment) {
        try {
            List<String> next = CommentHistory.remember(loadComments(), comment);
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                db.execSQL("DELETE FROM comments");
                long now = System.currentTimeMillis();
                for (int i = 0; i < next.size(); i++) {
                    ContentValues row = new ContentValues();
                    row.put("text", next.get(i));
                    row.put("last_used_ms", now - i);
                    db.insert("comments", null, row);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "rememberComment", e);
        }
    }

    @NonNull
    public float[] getWeights() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT n, floats FROM ocr_weight_vec WHERE id = 1", null);
            try {
                if (cursor.moveToFirst()) {
                    int n = cursor.getInt(0);
                    byte[] blob = cursor.getBlob(1);
                    if (n == OcrAmountWeights.FEATURE_COUNT) {
                        return OcrAmountWeights.fromLittleEndianBlob(blob);
                    }
                }
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "getWeights", e);
        }
        return OcrAmountWeights.defaults();
    }

    public void saveWeights(float[] weights) {
        try {
            float[] clamped = OcrAmountWeights.clamp(weights);
            ContentValues row = new ContentValues();
            row.put("id", 1);
            row.put("n", OcrAmountWeights.FEATURE_COUNT);
            row.put("floats", OcrAmountWeights.toLittleEndianBlob(clamped));
            SQLiteDatabase db = getWritableDatabase();
            db.replace("ocr_weight_vec", null, row);
        } catch (Exception e) {
            Log.e(TAG, "saveWeights", e);
        }
    }
}
