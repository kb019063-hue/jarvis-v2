package com.arena.duelist;

import android.graphics.RectF;

/** A flying weapon: kunai, arrow, bolt, bomb, boomerang. */
public final class Projectile {

    public float x, y, vx, vy, r;
    /** Previous sample position, used for continuous collision checks. */
    public float prevX, prevY;
    public float damage, knock;
    public float life;
    public int color, owner, special;
    public float angle, spin;
    public boolean dead;
    public boolean returning;
    public final int weapon;
    public float timer;
    /** Boomerangs survive a hit: cap them to one hit per pass, two per throw. */
    public int hitCount;
    public boolean hitThisPass;

    public Projectile(int weapon, int owner, float x, float y, float vx, float vy) {
        Weapons.Def d = Weapons.DEFS[weapon];
        this.weapon = weapon;
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = d.damage;
        this.knock = d.knock;
        this.color = d.color;
        this.special = d.special;
        this.r = special == 1 ? 16f : 9f;
        this.life = 4f; // seconds, not frames: behaviour must not change with FPS
        this.angle = (float) Math.atan2(vy, vx);
        this.spin = special == 3 ? 24f : 0f;
    }

    public void update(float dt) {
        timer += dt;
        prevX = x;
        prevY = y;
        x += vx * dt;
        y += vy * dt;
        angle += spin * dt;
        if (special == 1) {
            vy += 900f * dt;
        }
        life -= dt;
        if (life <= 0f) {
            dead = true;
        }
    }

    /** First segment parameter in [0, 1] that enters a circle, or -1 on a miss. */
    static float segmentCircleEntry(float x0, float y0, float x1, float y1,
                                    float cx, float cy, float radius) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float fx = x0 - cx;
        float fy = y0 - cy;
        float c = fx * fx + fy * fy - radius * radius;
        if (c <= 0f) {
            return 0f;
        }
        float a = dx * dx + dy * dy;
        if (a < 0.00001f) {
            return -1f;
        }
        float b = 2f * (fx * dx + fy * dy);
        float discriminant = b * b - 4f * a * c;
        if (discriminant < 0f) {
            return -1f;
        }
        float entry = (-b - (float) Math.sqrt(discriminant)) / (2f * a);
        return entry >= 0f && entry <= 1f ? entry : -1f;
    }

    /** First segment parameter that touches a rectangle expanded by padding. */
    static float segmentRectEntry(RectF rect, float x0, float y0, float x1, float y1,
                                  float padding) {
        float left = rect.left - padding;
        float right = rect.right + padding;
        float top = rect.top - padding;
        float bottom = rect.bottom + padding;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float enter = 0f;
        float exit = 1f;

        if (Math.abs(dx) < 0.00001f) {
            if (x0 < left || x0 > right) {
                return -1f;
            }
        } else {
            float a = (left - x0) / dx;
            float b = (right - x0) / dx;
            if (a > b) {
                float swap = a;
                a = b;
                b = swap;
            }
            enter = Math.max(enter, a);
            exit = Math.min(exit, b);
            if (enter > exit) {
                return -1f;
            }
        }

        if (Math.abs(dy) < 0.00001f) {
            if (y0 < top || y0 > bottom) {
                return -1f;
            }
        } else {
            float a = (top - y0) / dy;
            float b = (bottom - y0) / dy;
            if (a > b) {
                float swap = a;
                a = b;
                b = swap;
            }
            enter = Math.max(enter, a);
            exit = Math.min(exit, b);
            if (enter > exit) {
                return -1f;
            }
        }
        return enter <= 1f && exit >= 0f ? Math.max(0f, enter) : -1f;
    }
}
