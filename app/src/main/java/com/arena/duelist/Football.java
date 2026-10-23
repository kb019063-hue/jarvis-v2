package com.arena.duelist;

/** Offline football simulation. Independent from combat damage and round state. */
public final class Football {
    static final float WIDTH = 1400f;
    static final float FLOOR = 640f;
    static final float CEILING = 180f;
    static final float GOAL_TOP = 470f;
    static final float RADIUS = 22f;
    float x, y, vx, vy, angle;
    private final float[] kickTimers = new float[2];
    private boolean scored;

    void reset() {
        x = WIDTH / 2f;
        y = FLOOR - RADIUS - 85f;
        vx = vy = angle = 0f;
        kickTimers[0] = kickTimers[1] = 0f;
        scored = false;
    }

    /** Returns the scoring player index, or -1. A goal can only be emitted once. */
    int update(float dt, Fighter[] players) {
        if (scored || dt <= 0f) return -1;
        int steps = Math.max(1, (int) Math.ceil(dt / (1f / 240f)));
        float h = dt / steps;
        for (int step = 0; step < steps; step++) {
            for (int i = 0; i < 2; i++) kickTimers[i] = Math.max(0f, kickTimers[i] - h);
            vy += 1150f * h;
            vx *= (float) Math.exp(-0.20f * h);
            x += vx * h;
            y += vy * h;
            angle += vx * h / RADIUS;
            if (y + RADIUS > FLOOR) {
                y = FLOOR - RADIUS;
                vy = -Math.abs(vy) * 0.70f;
                if (Math.abs(vy) < 35f) vy = 0f;
                vx *= (float) Math.exp(-1.3f * h);
            }
            if (y - RADIUS < CEILING) {
                y = CEILING + RADIUS;
                vy = Math.abs(vy) * 0.8f;
            }
            // Entire ball must enter below the crossbar. Side walls above it bounce.
            if (y - RADIUS >= GOAL_TOP) {
                if (x + RADIUS < 0f) { scored = true; return 1; }
                if (x - RADIUS > WIDTH) { scored = true; return 0; }
            } else if (x < RADIUS) {
                x = RADIUS;
                vx = Math.abs(vx) * 0.8f;
            } else if (x > WIDTH - RADIUS) {
                x = WIDTH - RADIUS;
                vx = -Math.abs(vx) * 0.8f;
            }
            for (int i = 0; i < players.length; i++) {
                Fighter f = players[i];
                float cx = f.x;
                float cy = Math.max(f.y - 40f, Math.min(f.y + Fighter.FEET - 12f, y));
                float dx = x - cx, dy = y - cy;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float minDistance = RADIUS + 20f;
                if (distance >= minDistance) continue;
                float nx = distance > 0.001f ? dx / distance : (i == 0 ? 1f : -1f);
                float ny = distance > 0.001f ? dy / distance : 0f;
                x = cx + nx * minDistance;
                y = cy + ny * minDistance;
                float normalSpeed = (vx - f.vx) * nx + (vy - f.vy) * ny;
                if (normalSpeed < 0f) {
                    vx -= 1.6f * normalSpeed * nx;
                    vy -= 1.6f * normalSpeed * ny;
                }
                if (kickTimers[i] <= 0f) {
                    float dir = Math.abs(f.aimX) > 0.2f ? Math.signum(f.aimX) : f.facing;
                    vx = dir * (480f + Math.min(200f, Math.abs(f.vx) * 0.5f));
                    vy = -260f + Math.min(0f, f.aimY) * 250f;
                    kickTimers[i] = 0.24f;
                }
            }
            vx = Math.max(-1400f, Math.min(1400f, vx));
            vy = Math.max(-1400f, Math.min(1400f, vy));
        }
        return -1;
    }

    /** Release skill is a directional power kick, only when the ball is in reach. */
    boolean powerKick(Fighter f, float dx, float dy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float bx = x - f.x, by = y - (f.y + 15f);
        if (scored || len < 0.35f || bx * bx + by * by > 150f * 150f) return false;
        vx = dx / len * 1200f;
        vy = dy / len * 850f - 160f;
        kickTimers[f.id] = 0.3f;
        return true;
    }

    void draw(Painter g) {
        g.stroke(WIDTH / 2f, CEILING, WIDTH / 2f, FLOOR, 3f, 0x55FFFFFF);
        g.line.setColor(0x55FFFFFF);
        g.line.setStrokeWidth(3f);
        g.c.drawCircle(WIDTH / 2f, FLOOR, 115f, g.line);
        for (int side = 0; side < 2; side++) {
            float gx = side == 0 ? -65f : WIDTH;
            int color = side == 0 ? 0xFF4D9BFF : 0xFFFF6B8A;
            g.rect(gx, GOAL_TOP, 65f, FLOOR - GOAL_TOP, 0x22FFFFFF);
            for (float yy = GOAL_TOP; yy <= FLOOR; yy += 20f)
                g.stroke(gx, yy, gx + 65f, yy, 1f, 0x66FFFFFF);
            for (float xx = gx; xx <= gx + 65f; xx += 16f)
                g.stroke(xx, GOAL_TOP, xx, FLOOR, 1f, 0x66FFFFFF);
            g.stroke(gx, GOAL_TOP, gx + 65f, GOAL_TOP, 7f, color);
            g.stroke(side == 0 ? gx : gx + 65f, GOAL_TOP,
                    side == 0 ? gx : gx + 65f, FLOOR, 5f, color);
        }
        g.circle(x, FLOOR - 2f, 20f, 0x33000000);
        g.c.save();
        g.c.translate(x, y);
        g.c.rotate((float) Math.toDegrees(angle));
        g.circle(0, 0, RADIUS + 2f, 0xFF182332);
        g.circle(0, 0, RADIUS, 0xFFF8FBFF);
        g.circle(0, 0, 8f, 0xFF182332);
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            float px = (float) Math.cos(a) * 18f, py = (float) Math.sin(a) * 18f;
            g.stroke(0, 0, px, py, 2f, 0xFF182332);
            g.circle(px, py, 5f, 0xFF182332);
        }
        g.c.restore();
        g.reset();
    }
}
