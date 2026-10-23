package com.arena.duelist;

import android.graphics.Path;
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
    public int accent = 0xFFFF4D6D;
    public int facing = 1;
    public float age;
    /** 1 = fully opaque, 0 = finished: the corpse fades once it has settled. */
    public float fade = 1f;
    /** True while the body is essentially at rest. */
    public boolean quiet;
    /** 0..1 blend actually used on the last step; 0 = limp, 1 = muscle holding a pose. */
    public float muscle;

    private float quietT;
    /** Previous Verlet step, used to preserve velocity when dt changes. */
    private float lastDt = 1f / 60f;

    public Ragdoll(Fighter f) {
        this(f.x, f.y, f.facing, f.vx, f.vy, f.color, f.hair);
        this.accent = f.accent;
    }

    public Ragdoll(float cx, float cy, int facing, float vx, float vy, int color, int hair) {
        this.color = color;
        this.hair = hair;
        this.facing = facing >= 0 ? 1 : -1;
        float f = this.facing;

        // Same compact, longer-legged silhouette as Fighter.poseNow().
        setPoint(HIPS, cx, cy + 6f);
        setPoint(CHEST, cx, cy - 13f);
        setPoint(HEAD, cx, cy - 20f - Fighter.HEAD_RADIUS + 4f);
        setPoint(ELBOW_L, cx - f * 5.5f, cy - 4.75f);
        setPoint(HAND_L, cx - f * 10f, cy + 2f);
        setPoint(ELBOW_R, cx + f * 10f, cy - 8.5f);
        setPoint(HAND_R, cx + f * 18f, cy - 4f);
        setPoint(KNEE_L, cx + 7.4f, cy + 18.5f);
        setPoint(FOOT_L, cx + 7.4f, cy + 30f);
        setPoint(KNEE_R, cx - 7.4f, cy + 18.5f);
        setPoint(FOOT_R, cx - 7.4f, cy + 30f);

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

    float collisionRadius(int joint) {
        return rad[joint];
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
        if (dt <= 0f) {
            return;
        }
        // Small, deterministic substeps keep constraints stable without making
        // the simulation run in slow motion on a lower-FPS device.
        if (dt > 0.020f) {
            int pieces = (int) Math.ceil(dt / 0.020f);
            float step = dt / pieces;
            for (int i = 0; i < pieces; i++) {
                update(step, arena, tx, ty, muscle, gravityScale);
            }
            return;
        }
        this.muscle = (tx == null) ? 0f : Math.max(0f, Math.min(1f, muscle));
        age += dt;

        // Verlet integration: previous position carries velocity. Scale that
        // displacement by dt/lastDt so changing frame rate does not change the
        // body's physical velocity, and make damping time-based as well.
        float ratio = dt / lastDt;
        float damp = (float) Math.pow(0.993f, dt * 60f);
        // standing bodies get reduced gravity: a 0.3s flinch should not collapse
        // the fighter to the floor, it should just wobble
        float gg = 2000f * gravityScale * dt * dt;
        for (int i = 0; i < N; i++) {
            float nx = x[i] + (x[i] - px[i]) * ratio * damp;
            float ny = y[i] + (y[i] - py[i]) * ratio * damp + gg;
            px[i] = x[i];
            py[i] = y[i];
            x[i] = nx;
            y[i] = ny;
        }
        lastDt = dt;

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
        quiet = speed / dt < 24f;
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
        float bx = vx * lastDt;
        float by = vy * lastDt;
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
        lastDt = 1f / 60f;
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
        int edgeColor = luminance(color) > 0.42f ? 0xFF11141B : 0xFFE8EBF2;
        int outline = Painter.withAlpha(edgeColor, (int) (230f * a));

        for (int b = 0; b < DRAW_BONES; b++) {
            int i = BONES[b][0];
            int j = BONES[b][1];
            g.stroke(x[i], y[i], x[j], y[j], 5.2f, outline);
            g.stroke(x[i], y[i], x[j], y[j], 3.2f, body);
        }
        float hx = x[HEAD];
        float hy = y[HEAD];
        g.circle(hx, hy, Fighter.HEAD_RADIUS + 1.6f, outline);
        g.circle(hx, hy, Fighter.HEAD_RADIUS, body);
        drawHair(g, hx, hy, a);
    }

    private void drawHair(Painter g, float hx, float hy, float alpha) {
        if (hair == 0) {
            return;
        }
        int c = Painter.withAlpha(accent, (int) (255f * alpha));
        float r = Fighter.HEAD_RADIUS;
        if (hair == 1) {
            Path p = new Path();
            p.moveTo(hx - 9f, hy - r + 2f);
            p.lineTo(hx - 4f, hy - r - 7f);
            p.lineTo(hx + 1f, hy - r - 1f);
            p.lineTo(hx + 6f, hy - r - 7f);
            p.lineTo(hx + 9f, hy - r + 2f);
            p.close();
            g.path(p, c);
        } else if (hair == 2) {
            g.stroke(hx - 9f, hy - r + 1f, hx + 9f, hy - r + 1f, 3f, c);
        } else {
            g.rrect(hx - 9f, hy - r - 4f, 18f, 5f, 2f, c);
        }
    }

    private static float luminance(int c) {
        float r = ((c >> 16) & 0xFF) / 255f;
        float gg = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        return 0.299f * r + 0.587f * gg + 0.114f * b;
    }
}
