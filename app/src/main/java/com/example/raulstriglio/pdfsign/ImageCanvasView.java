package com.example.raulstriglio.pdfsign;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.v7.widget.TintContextWrapper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

/**
 * Created by raul.striglio on 25/01/18.
 */

public class ImageCanvasView extends android.support.v7.widget.AppCompatImageView {
    private static final float TOLERANCE = 5.0F;
    public int width;
    public int height;
    Context context;
    public Bitmap mBitmap;
    public Canvas mCanvas;
    private Path mPath;
    private Paint mPaint;
    private float mX;
    private float mY;

    public ImageCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        this.context = context;
        onImageCanvasView(context);
    }

    public ImageCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        onImageCanvasView(context);
    }

    public ImageCanvasView(Context context) {
        super(context);
        this.context = context;
        onImageCanvasView(context);
    }

    public void onImageCanvasView(Context c) {
        this.context = c;
        this.mPath = new Path();
        this.mPaint = new Paint();
        this.mPaint.setAntiAlias(true);
        this.mPaint.setColor(-16777216);
        this.mPaint.setStyle(Paint.Style.STROKE);
        this.mPaint.setStrokeJoin(Paint.Join.ROUND);
        this.mPaint.setStrokeWidth(4.0F);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //this.mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        //this.mCanvas = new Canvas(this.mBitmap);
    }

    protected void onDraw(Canvas canvas) {

        if(canvas != null) {
            super.onDraw(canvas);
            canvas.drawPath(this.mPath, this.mPaint);
        }
    }

    private void startTouch(float x, float y) {
        this.mPath.moveTo(x, y);
        this.mX = x;
        this.mY = y;
    }

    private void moveTouch(float x, float y) {
        float dx = Math.abs(x - this.mX);
        float dy = Math.abs(y - this.mY);
        if(dx >= 5.0F || dy >= 5.0F) {
            this.mPath.quadTo(this.mX, this.mY, (x + this.mX) / 2.0F, (y + this.mY) / 2.0F);
            this.mX = x;
            this.mY = y;
        }

    }

    public void clearCanvas() {
        this.mPath.reset();
        this.invalidate();
    }

    private void upTouch() {
        this.mPath.lineTo(this.mX, this.mY);
    }

    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch(event.getAction()) {
            case 0:
                this.startTouch(x, y);
                this.invalidate();
                break;
            case 1:
                this.upTouch();
                this.invalidate();
                break;
            case 2:
                this.moveTouch(x, y);
                this.invalidate();
        }

        return true;
    }
}
