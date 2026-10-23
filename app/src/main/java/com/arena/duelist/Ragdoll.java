package com.arena.duelist;

import android.graphics.RectF;

/**
 * Verlet stickman ragdoll: eleven points joined by distance constraints, with
 * gravity, damping and platform collision.
 *
 * Spawned when a fighter is knocked out, so the loser tumbles, hits the floor,
 * flops and settles instead of spinning as one rigid body. The skeleton is
 * deliberately the same proportions as the drawn stickman, and the rest lengths
 * are captured from the pose the fighter died in, so the hand-off is seamless.
 */
public final class Ragdoll {

    public static final int HEAD = 0;
    public static final int CHEST = 1;
    public static final int HIPS = 2;
    public static final int ELBOW_L = 3;
    public static final int HAND_L = 4;
    public static final int ELBOW_R = 5;
    public static final int HAND_R = 6;
    public static final int KNEE_L = 7;
    public static final int FOOT_L = 8;
    public static final int KNEE_R = 9;
    public static final int FOOT_R = 10;
    public static final int N = 11;

    /** Drawn bones (the last two entries are hidden braces). */
    private static final int DRAW_BONES = 10;
    private static final int[][] BONES = {
            {HEAD, CHEST}, {CHEST, HIPS},
            {CHEST, ELBOW_L}, {ELBOW_L, HAND_L},
            {CHEST, ELBOW_R}, {ELBOW_R, HAND_R},
            {HIPS, KNEE_L}, {KNEE_L, FOOT_L},
            {HIPS, KNEE_R}, {KNEE_R, FOOT_R},
            // braces keep the spine from folding flat and the legs from splitting
            {HEAD, HIPS}, {KNEE_L, KNEE_R}
    };
    private static final float[] STIFF = {
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.35f, 0.3f
    };

    public final float[] x = new float[N];
    public final float[] y = new float[N];
    private final float[] px = new float[N];
    private final float[] py = new float[N];
    private final float[] rest = new float[BONES.length];
    private final float[] rad = new float[N];

    public final int color;
    public int hair;
    public float age;
    /** 1 = fully opaque, 0 = finished: the corpse fades once it has settled. */
    public float fade = 1f;
    /** True while the body is essentially at rest. */
    public boolean quiet;
    /** 0..1 blend actually used on the last step; 0 = limp, 1 = muscle holding a pose. */
    public float muscle;

    private float quietT;

    public Ragdoll(Fighter f) {
        this(f.x, f.y, f.facing, f.vx, f.vy, f.color, f.hair);
    }

    public Ragdoll(float cx, float cy, int facing, float vx, float vy, int color, int hair) {
        this.color = color;
        this.hair = hair;
        float f = facing >= 0 ? 1f : -1f;

        // same skeleton as Fighter.draw(): hips 12, chest -24, head -37 above origin
        setPoint(HIPS, cx, cy + 10f);
        setPoint(CHEST, cx, cy - 16f);
        setPoint(HEAD, cx, cy - 37f);
        setPoint(ELBOW_L, cx - f * 13f, cy - 5f);
        setPoint(HAND_L, cx - f * 17f, cy + 11f);
        setPoint(ELBOW_R, cx + f * 13f, cy - 5f);
        setPoint(HAND_R, cx + f * 17f, cy + 11f);
        setPoint(KNEE_L, cx - 7f, cy + 21f);
        setPoint(FOOT_L, cx - 9f, cy + 30f);
        setPoint(KNEE_R, cx + 7f, cy + 21f);
        setPoint(FOOT_R, cx + 9f, cy + 30f);

        for (int i = 0; i < N; i++) {
            // small foot radii so a standing foot can rest on the surface line
            rad[i] = i == HEAD ? 9f : ((i == FOOT_L || i == FOOT_R) ? 1f : 3.5f);
            // inherit the killing blow's momentum
            px[i] = x[i] - vx / 60f;
            py[i] = y[i] - vy / 60f;
        }
        for (int b = 0; b < BONES.length; b++) {
            rest[b] = dist(BONES[b][0], BONES[b][1]);
        }
    }

    private void setPoint(int i, float wx, float wy) {
        x[i] = wx;
        y[i] = wy;
        px[i] = wx;
        py[i] = wy;
    }

    private float dist(int a, int b) {
        float dx = x[a] - x[b];
        float dy = y[a] - y[b];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Pure physics (corpse / knocked down). */
    public void update(float dt, Maps.Arena arena) {
        update(dt, arena, null, null, 0f);
    }

    /**
     * Active ragdoll: physics plus a "muscle" that pulls every joint toward a
     * target pose. At muscle 1 the body tracks the pose exactly; at 0 it is limp.
     * Bone rest lengths are blended toward the target lengths too, so an extended
     * sword swing is reachable at high muscle but the limbs keep their physical
     * length when limp.
     */
    public void update(float dt, Maps.Arena arena, float[] tx, float[] ty, float muscle) {
        update(dt, arena, tx, ty, muscle, 1f);
    }

    public void update(float dt, Maps.Arena arena, float[] tx, float[] ty, float muscle,
                       float gravityScale) {
        this.muscle = (tx == null) ? 0f : Math.max(0f, Math.min(1f, muscle));
        if (dt <= 0f) {
            return;
        }
        if (dt > 0.034f) {
            dt = 0.034f;
        }
        age += dt;

        // Verlet integration: previous position carries the velocity
        float damp = 0.993f;
        // standing bodies get reduced gravity: a 0.3s flinch should not collapse
        // the fighter to the floor, it should just wobble
        float gg = 2000f * gravityScale * dt * dt;
        for (int i = 0; i < N; i++) {
            float nx = x[i] + (x[i] - px[i]) * damp;
            float ny = y[i] + (y[i] - py[i]) * damp + gg;
            px[i] = x[i];
            py[i] = y[i];
            x[i] = nx;
            y[i] = ny;
        }

        // relax the skeleton, resolving collisions every pass
        float m = this.muscle;
        for (int it = 0; it < 5; it++) {
            for (int b = 0; b < BONES.length; b++) {
                int a = BONES[b][0];
                int c = BONES[b][1];
                float len = rest[b];
                if (m > 0f) {
                    float tdx = tx[a] - tx[c];
                    float tdy = ty[a] - ty[c];
                    float target = (float) Math.sqrt(tdx * tdx + tdy * tdy);
                    if (target > 0.01f) {
                        len = rest[b] + (target - rest[b]) * m;
                    }
                }
                solve(a, c, len, STIFF[b]);
            }
            collide(arena);
        }

        if (m > 0f) {
            // Muscle is applied once per frame, not per iteration: doing it five
            // times made the body five times stiffer, which killed the flop.
            // Positional only - the previous position moves with the joint so the
            // muscle does not inject velocity and make the body buzz.
            if (m > 0.85f) {
                // Fully composed: the animation is kinematic. Snapping avoids the
                // few pixels of drift the constraint solver leaves in the
                // over-constrained torso triangle, and costs nothing perceptually
                // because the eased pull above has already closed most of the gap.
                for (int i = 0; i < N; i++) {
                    x[i] = tx[i];
                    y[i] = ty[i];
                    px[i] = tx[i];
                    py[i] = ty[i];
                }
            } else {
                float pull = m * 0.85f;
                for (int i = 0; i < N; i++) {
                    float dx = (tx[i] - x[i]) * pull;
                    float dy = (ty[i] - y[i]) * pull;
                    x[i] += dx;
                    y[i] += dy;
                    px[i] += dx;
                    py[i] += dy;
                }
            }
            collide(arena);
        }

        float left = arena.bounds.left + 4f;
        float right = arena.bounds.right - 4f;
        for (int i = 0; i < N; i++) {
            if (x[i] < left) {
                x[i] = left;
                px[i] = x[i];
            } else if (x[i] > right) {
                x[i] = right;
                px[i] = x[i];
            }
        }

        float speed = 0f;
        for (int i = 0; i < N; i++) {
            speed = Math.max(speed, Math.abs(x[i] - px[i]) + Math.abs(y[i] - py[i]));
        }
        quiet = speed < 0.4f;
        quietT = quiet ? quietT + dt : 0f;

        if (m > 0f) {
            // a standing body never ages out or fades
            fade = 1f;
            age = 0f;
            quietT = 0f;
            return;
        }
        if (y[HIPS] > arena.bounds.bottom + 400f) {
            fade = 0f; // fell out of the world
        } else if (age > 3.4f || quietT > 1.8f) {
            fade = Math.max(0f, fade - dt / 1.1f);
        }
    }

    private void solve(int a, int b, float len, float stiff) {
        float dx = x[b] - x[a];
        float dy = y[b] - y[a];
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.0001f) {
            return;
        }
        float diff = ((d - len) / d) * 0.5f * stiff;
        float ox = dx * diff;
        float oy = dy * diff;
        x[a] += ox;
        y[a] += oy;
        x[b] -= ox;
        y[b] -= oy;
    }

    /**
     * Pushes every joint out of the arena's platforms.
     *
     * Which side to eject from is decided by where the joint came from, not by
     * the smallest penetration: thin ledges are only 20-26 units tall and joints
     * are 3.5-9, so "smallest penetration" could pick the wrong face and leave a
     * corpse impaled through a ledge, hanging in mid-air.
     */
    private void collide(Maps.Arena arena) {
        for (int i = 0; i < N; i++) {
            float r = rad[i];
            for (int k = 0; k < arena.platforms.size(); k++) {
                RectF q = arena.platforms.get(k).r;
                if (x[i] < q.left - r || x[i] > q.right + r
                        || y[i] < q.top - r || y[i] > q.bottom + r) {
                    continue;
                }
                // "resting on the surface" beats the previous-position test: a
                // foot that sinks a pixel below the top would otherwise be
                // ejected sideways to the platform edge and teleport metres away
                boolean nearTop = y[i] <= q.top + 3f;
                boolean fromAbove = py[i] + r <= q.top + 6f || nearTop;
                boolean fromBelow = py[i] - r >= q.bottom - 2f;
                if (fromAbove) {
                    y[i] = q.top - r;
                    py[i] = y[i];                                  // no bounce
                    px[i] = x[i] - (x[i] - px[i]) * 0.55f;         // ground friction
                } else if (fromBelow) {
                    y[i] = q.bottom + r;
                    py[i] = y[i];
                } else {
                    // came in from a side (or started inside): eject horizontally
                    if (px[i] < q.centerX()) {
                        x[i] = q.left - r;
                    } else {
                        x[i] = q.right + r;
                    }
                    px[i] = x[i];
                    py[i] = y[i] - (y[i] - py[i]) * 0.7f;
                }
            }
        }
    }

    /**
     * A knocked-down body that is then killed: keep the pose and physics but give
     * it a fresh lifetime so the corpse stays on stage before fading.
     */
    /** Adds an impulse to every joint without teleporting the body. */
    public void kick(float vx, float vy) {
        float bx = vx / 60f;
        float by = vy / 60f;
        for (int i = 0; i < N; i++) {
            px[i] -= bx;
            py[i] -= by;
        }
    }

    /** Snaps the skeleton onto a pose, clearing all momentum. */
    public void teleport(float[] tx, float[] ty) {
        for (int i = 0; i < N; i++) {
            x[i] = tx[i];
            y[i] = ty[i];
            px[i] = tx[i];
            py[i] = ty[i];
        }
        fade = 1f;
        age = 0f;
        quietT = 0f;
    }

    public void reborn() {
        age = 0f;
        quietT = 0f;
        fade = 1f;
    }

    public boolean gone() {
        return fade <= 0f;
    }

    public void draw(Painter g) {
        float a = Math.max(0f, Math.min(1f, fade));
        int body = Painter.withAlpha(color, (int) (255f * a));
        int outline = Painter.withAlpha(
                luminance(color) > 0.55f ? 0x000000 : 0xFFFFFF, (int) (102f * a));

        for (int b = 0; b < DRAW_BONES; b++) {
            int i = BONES[b][0];
            int j = BONES[b][1];
            g.stroke(x[i], y[i], x[j], y[j], 10f, outline);
            g.stroke(x[i], y[i], x[j], y[j], 6.6f, body);
        }
        g.circle(x[HEAD], y[HEAD], 14.8f, outline);
        g.circle(x[HEAD], y[HEAD], 13f, body);
    }

    private static float luminance(int c) {
        float r = ((c >> 16) & 0xFF) / 255f;
        float gg = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        return 0.299f * r + 0.587f * gg + 0.114f * b;
    }
}
