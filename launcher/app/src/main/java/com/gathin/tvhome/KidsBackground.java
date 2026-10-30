package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/** The kids' night sky: a purple gradient with soft bubbles and a few four-point stars. Drawn once per size, nothing animates. */
final class KidsBackground extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private float[] bubbles = new float[0];   // x, y, radius
    private float[] stars = new float[0];     // x, y, size

    KidsBackground(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Random random = new Random(7);   // the same sky every time
        float density = getResources().getDisplayMetrics().density;
        bubbles = new float[3 * 12];
        for (int i = 0; i < 12; i++) {
            bubbles[3 * i] = random.nextFloat() * w;
            bubbles[3 * i + 1] = random.nextFloat() * h;
            bubbles[3 * i + 2] = (30 + random.nextFloat() * 90) * density;
        }
        stars = new float[3 * 36];
        for (int i = 0; i < 36; i++) {
            stars[3 * i] = random.nextFloat() * w;
            stars[3 * i + 1] = random.nextFloat() * h * 0.7f;
            stars[3 * i + 2] = (3 + random.nextFloat() * 7) * density;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        paint.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF1B1464, 0xFF4A2C8A, 0xFF8B4DAA}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        paint.setColor(0x16FFFFFF);
        for (int i = 0; i + 2 < bubbles.length; i += 3) canvas.drawCircle(bubbles[i], bubbles[i + 1], bubbles[i + 2], paint);
        paint.setColor(0xCCFFE9A8);
        for (int i = 0; i + 2 < stars.length; i += 3) {
            float x = stars[i], y = stars[i + 1], s = stars[i + 2];
            star.reset();
            star.moveTo(x, y - s);
            star.quadTo(x, y, x + s, y);
            star.quadTo(x, y, x, y + s);
            star.quadTo(x, y, x - s, y);
            star.quadTo(x, y, x, y - s);
            canvas.drawPath(star, paint);
        }
    }
}
