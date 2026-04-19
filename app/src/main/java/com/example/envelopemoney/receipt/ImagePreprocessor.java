package com.example.envelopemoney.receipt;

import android.graphics.Bitmap;

/**
 * Downscales for OCR latency; full-res gallery copy is handled separately.
 */
public final class ImagePreprocessor {

    private static final int MAX_LONG_EDGE_PX = 1600;

    private ImagePreprocessor() {
    }

    public static Bitmap forOcr(Bitmap source) {
        if (source == null) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= MAX_LONG_EDGE_PX) {
            return source.getConfig() == Bitmap.Config.ARGB_8888
                    ? source.copy(Bitmap.Config.ARGB_8888, false)
                    : source.copy(Bitmap.Config.ARGB_8888, true);
        }
        float scale = MAX_LONG_EDGE_PX / (float) longEdge;
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(source, nw, nh, true);
    }
}
