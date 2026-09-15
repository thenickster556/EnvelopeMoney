package com.example.envelopemoney.receipt;

import android.view.View;

import androidx.annotation.Nullable;

/**
 * Candidate-preview chrome visibilities for immersive (JPEG fills the screen) vs restored bars.
 */
final class ReceiptPreviewImmersive {
    private ReceiptPreviewImmersive() {}

    static int topChromeVisibility(boolean immersive) {
        return immersive ? View.GONE : View.VISIBLE;
    }

    static int candidateBottomVisibility(boolean immersive, boolean candidateMode) {
        if (!candidateMode) {
            return View.GONE;
        }
        return immersive ? View.GONE : View.VISIBLE;
    }

    static int exitButtonVisibility(boolean immersive) {
        return immersive ? View.VISIBLE : View.GONE;
    }

    /** Translucent comment/pond/amount strip: only candidate fullscreen, never attached preview. */
    static int cueVisibility(boolean immersive, boolean candidateMode) {
        return immersive && candidateMode ? View.VISIBLE : View.GONE;
    }

    static boolean consumeBack(boolean immersive) {
        return immersive;
    }

    static void apply(boolean immersive, boolean candidateMode,
            @Nullable View topChrome, @Nullable View candidateBottom, @Nullable View exitButton,
            @Nullable View cue) {
        if (topChrome != null) {
            topChrome.setVisibility(topChromeVisibility(immersive));
        }
        if (candidateBottom != null) {
            candidateBottom.setVisibility(candidateBottomVisibility(immersive, candidateMode));
        }
        if (exitButton != null) {
            exitButton.setVisibility(exitButtonVisibility(immersive));
        }
        if (cue != null) {
            cue.setVisibility(cueVisibility(immersive, candidateMode));
        }
    }
}
