package com.arena.duelist;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Thin drawing helper over Canvas. All calls use world/virtual units. */
public final class Painter {

    public final Canvas c;
    public final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Painter(Canvas c) {
        this.c = c;
        p.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        text.setColor(0xFFFFFFFF);
    }

    public int color(int argb) {
        return argb;
    }

    public void fill(int color) {
        p.setShader(null);
        p.setColor(color);
    }

    public void fillShader(Shader s) {
        p.setShader(s);
    }

    public void alpha(int alpha) {
        p.setAlpha(alpha);
        line.setAlpha(alpha);
    }

    public void reset() {
        p.setAlpha(255);
        line.setAlpha(255);
        p.setShader(null);
    }

    public void rect(RectF r, int color) {
        p.setShader(null);
        p.setColor(color);
        c.drawRect(r, p);
    }

    public void rect(float x, float y, float w, float h, int color) {
        p.setShader(null);
        p.setColor(color);
        c.drawRect(x, y, x + w, y + h, p);
    }

    public void rrect(float x, float y, float w, float h, float rad, int color) {
        p.setShader(null);
        p.setColor(color);
        c.drawRoundRect(x, y, x + w, y + h, rad, rad, p);
    }

    public void rrectStroke(float x, float y, float w, float h, float rad, float width, int color) {
        line.setColor(color);
        line.setStrokeWidth(width);
        c.drawRoundRect(x, y, x + w, y + h, rad, rad, line);
    }

    public void circle(float x, float y, float r, int color) {
        p.setShader(null);
        p.setColor(color);
        c.drawCircle(x, y, r, p);
    }

    public void stroke(float x1, float y1, float x2, float y2, float w, int color) {
        line.setColor(color);
        line.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, line);
    }

    public void dot(float x, float y, float r, int color) {
        line.setColor(color);
        line.setStrokeWidth(r * 2);
        line.setStrokeCap(Paint.Cap.ROUND);
        c.drawPoint(x, y, line);
    }

    public void path(Path path, int color) {
        p.setShader(null);
        p.setColor(color);
        c.drawPath(path, p);
    }

    public float textSize;

    public void size(float s) {
        text.setTextSize(s);
        textSize = s;
    }

    public void align(Paint.Align a) {
        text.setTextAlign(a);
    }

    public void text(String s, float x, float y, int color) {
        text.setShader(null);
        text.setColor(color);
        c.drawText(s, x, y, text);
    }

    public void textCenter(String s, float cx, float y, int color) {
        text.setShader(null);
        text.setColor(color);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, cx, y, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    public void textShadowCenter(String s, float cx, float y, int color, float shadow) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0x99000000);
        c.drawText(s, cx + shadow, y + shadow, text);
        text.setColor(color);
        c.drawText(s, cx, y, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    public void textShadow(String s, float x, float y, int color, float shadow) {
        text.setColor(0x99000000);
        c.drawText(s, x + shadow, y + shadow, text);
        text.setColor(color);
        c.drawText(s, x, y, text);
    }

    public void gradient(float x, float y, float w, float h, int top, int bottom) {
        p.setShader(new LinearGradient(x, y, x, y + h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + w, y + h, p);
        p.setShader(null);
    }

    public static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int mix(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return Color.rgb(clamp(r), clamp(g), clamp(bl));
    }

    public static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }
}
