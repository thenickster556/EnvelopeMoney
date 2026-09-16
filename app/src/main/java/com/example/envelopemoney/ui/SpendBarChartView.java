package com.example.envelopemoney.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.envelopemoney.R;
import com.example.envelopemoney.SpendAnalysisHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple Canvas bars for Analysis (no chart library). Vertical month bars or horizontal pond bars.
 */
public final class SpendBarChartView extends View {

    public static final int MODE_VERTICAL = 0;
    public static final int MODE_HORIZONTAL = 1;

    private int mode = MODE_VERTICAL;
    private final List<String> labels = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();
    private double max = SpendAnalysisHelper.MIN_BAR_SCALE;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private final int itemHeightPx;
    private final int chartHeightPx;
    private final int horizontalLabelWidthPx;

    public SpendBarChartView(Context context) {
        this(context, null);
    }

    public SpendBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(ContextCompat.getColor(context, R.color.mountain_primary));
        textPaint.setColor(resolveTextColor(context));
        textPaint.setTextSize(sp(12));
        itemHeightPx = dp(36);
        chartHeightPx = context.getResources().getDimensionPixelSize(R.dimen.analysis_month_chart_height);
        horizontalLabelWidthPx = dp(88);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setVerticalBars(List<String> nextLabels, List<Double> nextValues, String contentDescription) {
        setBars(MODE_VERTICAL, nextLabels, nextValues, contentDescription);
    }

    public void setHorizontalBars(List<String> nextLabels, List<Double> nextValues, String contentDescription) {
        setBars(MODE_HORIZONTAL, nextLabels, nextValues, contentDescription);
    }

    private void setBars(int nextMode, List<String> nextLabels, List<Double> nextValues, String contentDescription) {
        mode = nextMode;
        labels.clear();
        values.clear();
        if (nextLabels != null) {
            labels.addAll(nextLabels);
        }
        if (nextValues != null) {
            values.addAll(nextValues);
        }
        List<Double> scaleValues = new ArrayList<>();
        for (Double value : values) {
            scaleValues.add(value == null ? 0d : Math.max(0d, value));
        }
        max = SpendAnalysisHelper.barScaleMax(scaleValues);
        setContentDescription(contentDescription == null ? "" : contentDescription);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (mode == MODE_HORIZONTAL) {
            int rows = Math.max(1, labels.size());
            int height = getPaddingTop() + getPaddingBottom() + rows * itemHeightPx;
            setMeasuredDimension(width, height);
        } else {
            int height = getPaddingTop() + getPaddingBottom() + chartHeightPx;
            setMeasuredDimension(width, height);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels.isEmpty()) {
            return;
        }
        if (mode == MODE_HORIZONTAL) {
            drawHorizontal(canvas);
        } else {
            drawVertical(canvas);
        }
    }

    private void drawVertical(Canvas canvas) {
        int count = labels.size();
        float labelSpace = textPaint.getTextSize() + dp(8);
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom() - labelSpace;
        float chartHeight = Math.max(1f, bottom - top);
        float innerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float slot = innerWidth / count;
        float barWidth = slot * 0.55f;
        for (int i = 0; i < count; i++) {
            double value = i < values.size() && values.get(i) != null ? Math.max(0d, values.get(i)) : 0d;
            float barHeight = (float) ((value / max) * chartHeight);
            float left = getPaddingLeft() + slot * i + (slot - barWidth) / 2f;
            float barTop = bottom - barHeight;
            canvas.drawRect(left, barTop, left + barWidth, bottom, barPaint);
            String label = labels.get(i);
            textPaint.getTextBounds(label, 0, label.length(), textBounds);
            float textX = left + barWidth / 2f - textBounds.width() / 2f;
            canvas.drawText(label, textX, bottom + textPaint.getTextSize() + dp(2), textPaint);
        }
    }

    private void drawHorizontal(Canvas canvas) {
        int count = labels.size();
        float amountSpace = dp(72);
        float left = getPaddingLeft() + horizontalLabelWidthPx;
        float right = getWidth() - getPaddingRight() - amountSpace;
        float trackWidth = Math.max(1f, right - left);
        for (int i = 0; i < count; i++) {
            float rowTop = getPaddingTop() + i * itemHeightPx;
            float centerY = rowTop + itemHeightPx / 2f;
            String label = labels.get(i);
            canvas.drawText(label, getPaddingLeft(), centerY + textPaint.getTextSize() / 3f, textPaint);
            double value = i < values.size() && values.get(i) != null ? values.get(i) : 0d;
            float barWidth = (float) ((Math.max(0d, value) / max) * trackWidth);
            float barTop = centerY - dp(6);
            canvas.drawRect(left, barTop, left + barWidth, barTop + dp(12), barPaint);
            String amount = String.format(java.util.Locale.getDefault(), "$%.2f", value);
            canvas.drawText(amount, right + dp(8), centerY + textPaint.getTextSize() / 3f, textPaint);
        }
    }

    private int resolveTextColor(Context context) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(context, typedValue.resourceId);
            }
            return typedValue.data;
        }
        return ContextCompat.getColor(context, R.color.mountain_primary);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
