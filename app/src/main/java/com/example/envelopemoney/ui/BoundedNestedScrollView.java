package com.example.envelopemoney.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.example.envelopemoney.R;

/**
 * Nested scroll region capped by {@code @dimen/dialog_transaction_scroll_max_height}
 * so tall transaction dialogs scroll instead of clipping under the IME / window height.
 */
public final class BoundedNestedScrollView extends NestedScrollView {

    private final int maxHeightPx;

    public BoundedNestedScrollView(Context context) {
        this(context, null);
    }

    public BoundedNestedScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        maxHeightPx = context.getResources().getDimensionPixelSize(R.dimen.dialog_transaction_scroll_max_height);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        int cappedSize = mode == MeasureSpec.UNSPECIFIED ? maxHeightPx : Math.min(size, maxHeightPx);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cappedSize, MeasureSpec.AT_MOST));
    }
}
