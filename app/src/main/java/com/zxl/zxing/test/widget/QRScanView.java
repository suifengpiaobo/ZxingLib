package com.zxl.zxing.test.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import com.zxl.zxing.R;

/**
 * Description 扫面二维码控件
 * Created by zxl on 2017/3/2 下午2:31.
 * Email:444288256@qq.com
 */
public class QRScanView extends View{

    private static final int MIN_FRAME_WIDTH = 200;
    private static final int MIN_FRAME_HEIGHT = 200;
    private static final int MAX_FRAME_WIDTH = 220;
    private static final int MAX_FRAME_HEIGHT = 220;

    private static final long ANIMATION_DELAY = 2L;
    private static final int OPAQUE = 0xFF;

    private int ScreenRate;

    private Context mContext;
    private static final int CORNER_WIDTH = 10;

    private static final int MIDDLE_LINE_WIDTH = 6;

    private static final int MIDDLE_LINE_PADDING = 5;

    private static final int SPEEN_DISTANCE = 5;

    private static float density;

    private static final int TEXT_SIZE = 16;

    private static final int TEXT_PADDING_TOP = 30;

    private Paint paint;

    private int slideTop;

    private int slideBottom;

    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;

    public Rect getFramingRect() {
        Rect framingRect = null;
        WindowManager manager = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics screenResolution = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(screenResolution);
        if (framingRect == null) {

            int width = screenResolution.widthPixels * 3 / 4;
            if (width < MIN_FRAME_WIDTH) {
                width = (int) (MIN_FRAME_WIDTH * screenResolution.density);
            } else if (width > MAX_FRAME_WIDTH) {
                width = (int) (MAX_FRAME_WIDTH * screenResolution.density);
            }
            int height = screenResolution.heightPixels * 3 / 4;
            if (height < MIN_FRAME_HEIGHT) {
                height = (int) (MIN_FRAME_HEIGHT * screenResolution.density);
            } else if (height > MAX_FRAME_HEIGHT) {
                height = (int) (MAX_FRAME_HEIGHT * screenResolution.density);
            }

            int leftOffset = (screenResolution.widthPixels - width) / 2;
            int topOffset = (screenResolution.heightPixels - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
                    topOffset + height);

        }
        return framingRect;
    }

    boolean isFirst;

    public QRScanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        density = context.getResources().getDisplayMetrics().density;
        ScreenRate = (int) (20 * density);

        paint = new Paint();
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);

    }

    @Override
    public void onDraw(Canvas canvas) {
        Rect frame = getFramingRect();
        if (frame == null) {
            return;
        }

        if (!isFirst) {
            isFirst = true;
            slideTop = frame.top;
            slideBottom = frame.bottom;
        }

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        paint.setColor(resultBitmap != null ? resultColor : maskColor);

        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1,
                paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(OPAQUE);
            canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
        } else {
            paint.setColor(Color.GREEN);
            canvas.drawRect(frame.left, frame.top, frame.left + ScreenRate,
                    frame.top + CORNER_WIDTH, paint);
            canvas.drawRect(frame.left, frame.top, frame.left + CORNER_WIDTH,
                    frame.top + ScreenRate, paint);
            canvas.drawRect(frame.right - ScreenRate, frame.top, frame.right,
                    frame.top + CORNER_WIDTH, paint);
            canvas.drawRect(frame.right - CORNER_WIDTH, frame.top, frame.right,
                    frame.top + ScreenRate, paint);
            canvas.drawRect(frame.left, frame.bottom - CORNER_WIDTH, frame.left
                    + ScreenRate, frame.bottom, paint);
            canvas.drawRect(frame.left, frame.bottom - ScreenRate, frame.left
                    + CORNER_WIDTH, frame.bottom, paint);
            canvas.drawRect(frame.right - ScreenRate, frame.bottom
                    - CORNER_WIDTH, frame.right, frame.bottom, paint);
            canvas.drawRect(frame.right - CORNER_WIDTH, frame.bottom
                    - ScreenRate, frame.right, frame.bottom, paint);

            slideTop += SPEEN_DISTANCE;
            if (slideTop >= frame.bottom) {
                slideTop = frame.top;
            }
            canvas.drawRect(frame.left + MIDDLE_LINE_PADDING, slideTop
                            - MIDDLE_LINE_WIDTH / 2, frame.right - MIDDLE_LINE_PADDING,
                    slideTop + MIDDLE_LINE_WIDTH / 2, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(TEXT_SIZE * density);
            paint.setAlpha(0x40);
            paint.setTypeface(Typeface.create("System", Typeface.BOLD));

            Rect scanRect = new Rect();
            String scanText = "将二维码放入框内, 即可自动扫描";
            paint.getTextBounds(scanText, 0, scanText.length(), scanRect);
            canvas.drawText(
                    scanText,
                    frame.left - scanRect.width() / 2
                            + (frame.right - frame.left) / 2,
                    (float) (frame.bottom + (float) TEXT_PADDING_TOP * density),
                    paint);

            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top,
                    frame.right, frame.bottom);

        }
    }

    public void drawViewfinder() {
        resultBitmap = null;
        invalidate();
    }
}
