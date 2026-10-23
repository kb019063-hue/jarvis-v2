package com.arena.duelist;

import java.util.ArrayList;
import java.util.Random;

/** Lightweight particle system: sparks, blood, dust, shockwaves, floating numbers. */
public final class Particles {

    public static final int SPARK = 0;
    public static final int DUST = 1;
    public static final int BLOOD = 2;
    public static final int RING = 3;
    public static final int TEXT = 4;

    private static final class P {
        int kind;
        float x, y, vx, vy, life, max, size, rot, spin, gravity;
        int color;
        String label;
    }

    private final ArrayList<P> list = new ArrayList<P>(256);
    private final Random rnd = new Random();

    public void clear() {
        list.clear();
    }

    private P make(int kind, float x, float y, int color, float size, float life) {
        P p = new P();
        p.kind = kind;
        p.x = x;
        p.y = y;
        p.color = color;
        p.size = size;
        p.life = life;
        p.max = life;
        p.gravity = 900f;
        return p;
    }

    public void sparks(float x, float y, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            P p = make(SPARK, x, y, color, 2.5f + rnd.nextFloat() * 3.5f, 0.35f + rnd.nextFloat() * 0.35f);
            double a = rnd.nextDouble() * Math.PI * 2;
            float s = speed * (0.35f + rnd.nextFloat());
            p.vx = (float) Math.cos(a) * s;
            p.vy = (float) Math.sin(a) * s;
            p.gravity = 700f;
            list.add(p);
        }
    }

    public void blood(float x, float y, int color, int count, float dirX) {
        for (int i = 0; i < count; i++) {
            P p = make(BLOOD, x, y, color, 2f + rnd.nextFloat() * 4f, 0.5f + rnd.nextFloat() * 0.6f);
            p.vx = dirX * (60 + rnd.nextFloat() * 220) + (rnd.nextFloat() - 0.5f) * 90;
            p.vy = -60 - rnd.nextFloat() * 240;
            p.gravity = 1250f;
            list.add(p);
        }
    }

    public void dust(float x, float y, int color, int count, float spread) {
        for (int i = 0; i < count; i++) {
            P p = make(DUST, x + (rnd.nextFloat() - 0.5f) * spread, y, color,
                    3f + rnd.nextFloat() * 6f, 0.35f + rnd.nextFloat() * 0.4f);
            p.vx = (rnd.nextFloat() - 0.5f) * spread * 2.2f;
            p.vy = -20 - rnd.nextFloat() * 60;
            p.gravity = -30f;
            list.add(p);
        }
    }

    public void ring(float x, float y, int color, float size, float life) {
        P p = make(RING, x, y, color, size, life);
        p.gravity = 0;
        list.add(p);
    }

    public void floatText(float x, float y, String label, int color) {
        P p = make(TEXT, x, y, color, 20, 0.9f);
        p.label = label;
        p.vy = -70;
        p.gravity = -10;
        list.add(p);
    }

    public void confetti(float w, float h) {
        for (int i = 0; i < 120; i++) {
            P p = make(SPARK, rnd.nextFloat() * w, -20 - rnd.nextFloat() * h * 0.4f,
                    Painter.hsv(rnd.nextFloat() * 360f, 0.85f, 1f),
                    4 + rnd.nextFloat() * 5, 2.2f + rnd.nextFloat() * 1.4f);
            p.vx = (rnd.nextFloat() - 0.5f) * 120;
            p.vy = 60 + rnd.nextFloat() * 120;
            p.gravity = 120f;
            p.spin = (rnd.nextFloat() - 0.5f) * 720;
            list.add(p);
        }
    }

    public void update(float dt) {
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                list.remove(i);
                continue;
            }
            p.vy += p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.spin * dt;
            if (p.kind == DUST) {
                p.vx *= (float) Math.pow(0.96f, dt * 60f);
            }
        }
    }

    public void draw(Painter g) {
        for (int i = 0; i < list.size(); i++) {
            P p = list.get(i);
            float t = p.life / p.max;
            int a = (int) (255 * Math.min(1f, t * 1.6f));
            if (p.kind == RING) {
                g.line.setColor(Painter.withAlpha(p.color, a));
                g.line.setStrokeWidth(3f + 5f * (1 - t));
                g.c.drawCircle(p.x, p.y, p.size * (1.15f - t), g.line);
            } else if (p.kind == TEXT) {
                g.size(p.size);
                g.textCenter(p.label, p.x, p.y, Painter.withAlpha(p.color, a));
            } else if (p.kind == BLOOD) {
                g.circle(p.x, p.y, p.size * 0.7f, Painter.withAlpha(p.color, a));
            } else {
                g.circle(p.x, p.y, p.size * (p.kind == DUST ? (2f - t) : 1f),
                        Painter.withAlpha(p.color, a));
            }
        }
        g.reset();
    }
}
