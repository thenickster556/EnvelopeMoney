package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Pinch-zoom, drag when zoomed, double-tap to reset fit. Host activity may call
 * {@link #setRotation(float)} on this view for 90° preview steps before persisting pixel rotation.
 */
public class ReceiptZoomImageView extends AppCompatImageView {

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private float fitScale = 1f;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private int mode = NONE;
    private final PointF lastTouch = new PointF();
    private int viewW;
    private int viewH;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private boolean layoutReady;

    public ReceiptZoomImageView(Context context) {
        this(context, null);
    }

    public ReceiptZoomImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float cur = currentUniformScale();
                if (cur <= 0f) {
                    return false;
                }
                float factor = detector.getScaleFactor();
                float next = cur * factor;
                float min = fitScale;
                float max = fitScale * 8f;
                next = Math.max(min, Math.min(next, max));
                float ratio = next / cur;
                matrix.postScale(ratio, ratio, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                resetFit();
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
        setOnTouchListener((v, event) -> handleTouch(event));
    }

    private boolean handleTouch(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        PointF curr = new PointF(event.getX(), event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(curr);
                mode = DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1
                        && mode == DRAG
                        && !scaleDetector.isInProgress()) {
                    float cur = currentUniformScale();
                    if (cur > fitScale * 1.02f) {
                        float dx = curr.x - lastTouch.x;
                        float dy = curr.y - lastTouch.y;
                        matrix.postTranslate(dx, dy);
                        setImageMatrix(matrix);
                    }
                }
                lastTouch.set(curr);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = NONE;
                break;
            default:
                break;
        }
        return true;
    }

    private float currentUniformScale() {
        matrix.getValues(matrixValues);
        return Math.abs(matrixValues[Matrix.MSCALE_X]);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
        viewW = w;
        viewH = h;
        if (w > 0 && h > 0) {
            layoutReady = true;
            resetFit();
        }
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        if (layoutReady && viewW > 0 && viewH > 0) {
            post(this::resetFit);
        }
    }

    public void resetFit() {
        Drawable d = getDrawable();
        if (d == null || viewW <= 0 || viewH <= 0) {
            return;
        }
        int bw = d.getIntrinsicWidth();
        int bh = d.getIntrinsicHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        matrix.reset();
        fitScale = Math.min((float) viewW / bw, (float) viewH / bh);
        matrix.postScale(fitScale, fitScale);
        float dx = (viewW - bw * fitScale) * 0.5f;
        float dy = (viewH - bh * fitScale) * 0.5f;
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }
}
