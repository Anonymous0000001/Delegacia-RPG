package com.seuusuario.delegaciarpg;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

public class Button {
    private final RectF rect;
    private final String label;

    public Button(float x, float y, float width, float height, String label) {
        rect = new RectF(x, y, x + width, y + height);
        this.label = label;
    }

    public void draw(Canvas canvas, Paint paint) {
        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();
        float originalStrokeWidth = paint.getStrokeWidth();
        float originalTextSize = paint.getTextSize();
        Paint.Align originalTextAlign = paint.getTextAlign();
        Typeface originalTypeface = paint.getTypeface();

        float cornerRadius = Math.min(20f, rect.height() * 0.22f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(180, 20, 20, 25));
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.min(3f, rect.height() * 0.05f));
        paint.setColor(Color.argb(100, 200, 200, 210));
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        float textSize = Math.min(38f, rect.height() * 0.42f);
        paint.setTextSize(textSize);
        float availableWidth = Math.max(1f, rect.width() - rect.height() * 0.5f);
        if (paint.measureText(label) > availableWidth) {
            paint.setTextSize(textSize * availableWidth / paint.measureText(label));
        }
        float baseline = rect.centerY() - (paint.ascent() + paint.descent()) * 0.5f;
        canvas.drawText(label, rect.centerX(), baseline, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
        paint.setStrokeWidth(originalStrokeWidth);
        paint.setTextSize(originalTextSize);
        paint.setTextAlign(originalTextAlign);
        paint.setTypeface(originalTypeface);
    }

    public boolean contains(float tx, float ty) {
        return rect.contains(tx, ty);
    }
}