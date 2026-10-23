package com.arena.duelist;

import android.graphics.Path;
import android.graphics.RectF;

/** A stick fighter: arcade movement, melee/ranged attacks, shield, ragdoll death. */
public final class Fighter {

    public static final float HALF_W = 16f;
    /** Poise breaks and the fighter ragdolls. Fists need 4 hits, a hammer 1. */
    public static final float POISE_MAX = 46f;
    private static final float POISE_REGEN = 14f;
    private static final float POISE_HOLD = 1.1f;
    /** How fast a hit reaction wears off (per second). */
    private static final float LIMP_RECOVER = 2.2f;
    public static final float FEET = 30f;
    public static final float HEAD = 30f;
    public static final float HEIGHT = 96f;

    public final int id;
    public String name;
    public boolean cpu;

    public float x, y, vx, vy;
    public boolean onGround;
    public int facing = 1;

    public float health = 100f;
    public float maxHealth = 100f;
    public float shield = 100f;
    public float maxShield = 100f;

    public int weapon = Weapons.FIST;
    public int ammo = -1;
    public float cooldown;
    public float attackT;
    public float attackDur = 0.3f;
    public boolean attacking;
    public boolean firedThisSwing;
    public boolean hitThisSwing;
    public float swingBoost;

    public float aimX = 1f, aimY = 0f;
    public float moveX;
    public boolean jumpQueued, attackQueued, shieldHeld, crouch;
    public boolean shieldOn;

    public float hurtT;
    public float flashT;
    public float spawnT = 1f;
    public float anim;
    public float walkPhase;

    public boolean dead;
    /** True while the physics body, not the arcade pose, is in charge. */
    public boolean ragdolled() {
        return dead || down;
    }

    /** Max distance between a joint and the pose it is meant to hold (px). */
    float poseError() {
        if (body == null) {
            return -1f;
        }
        float worst = 0f;
        for (int i = 0; i < Ragdoll.N; i++) {
            worst = Math.max(worst, Math.abs(body.x[i] - poseX[i]) + Math.abs(body.y[i] - poseY[i]));
        }
        return worst;
    }

    public float deadT;
    /**
     * The jointed body. It exists for the whole life of the fighter: while
     * standing, muscle pulls it onto the animated pose; when limp, hit or dead,
     * physics takes over. This is what makes every hit flop instead of only KOs.
     */
    public Ragdoll body;
    /** 0..1 floppiness, spiked by hits and decaying back to 0. */
    public float limp;
    /** Knocked down but alive: a ragdoll drives the body until it recovers. */
    public boolean down;
    public float downT;
    /** Set for one frame when a hit should knock this fighter down. */
    public boolean justDown;
    /** Short grace period after standing up so the fighter is not re-launched. */
    public float iframes;
    /** Poise: accumulates with hits; at POISE_MAX the fighter goes down. */
    public float poise;
    public float poiseT;
    /** One-shot: set by kill(), consumed by Game to spawn the ragdoll. */
    public boolean justDied;
    /** Head lag on hits: a small spring that makes the neck look jointed. */
    public float wobX, wobY;
    private final float[] poseX = new float[Ragdoll.N];
    private final float[] poseY = new float[Ragdoll.N];
    private float wobVx, wobVy;

    public int color = 0xFFF2F2F2;
    public int accent = 0xFFFF4D6D;
    public int hair;

    public float score;

    private float prevBottom, prevTop;
    private float coyote;

    public Fighter(int id, String name, float x, float y, boolean cpu) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.cpu = cpu;
        this.accent = id == 0 ? 0xFF4D9BFF : 0xFFFF4D6D;
    }

    public boolean alive() {
        return !dead;
    }

    public float handX() {
        return x + facing * 20f;
    }

    public float handY() {
        return y - 6f;
    }

    public void giveWeapon(int w) {
        weapon = w;
        ammo = Weapons.DEFS[w].ammo;
        cooldown = 0.15f;
    }

    public float weaponReach() {
        return Weapons.DEFS[weapon].type == Weapons.MELEE
                ? Weapons.DEFS[weapon].reach : 0f;
    }

    /** Queue input for this frame (called by human controls or CPU). */
    public void input(float mx, float ax, float ay, boolean jump, boolean atk, boolean shield) {
        this.moveX = mx;
        this.aimX = ax;
        this.aimY = ay;
        this.shieldHeld = shield;
        if (jump) {
            this.jumpQueued = true;
        }
        if (atk) {
            this.attackQueued = true;
        }
    }

    public void jump() {
        if (onGround || coyote > 0f) {
            vy = -730f;
            onGround = false;
            coyote = 0f;
        }
    }

    public void damage(float amount, float kx, float ky, boolean ignoreShield) {
        if (dead || iframes > 0f) {
            return;
        }
        float dmg = amount;
        if (!ignoreShield && shieldOn && shield > 0) {
            shield -= dmg * 1.8f;
            dmg *= 0.15f;
            if (shield <= 0) {
                shield = 0;
                shieldOn = false;
                stun(0.7f);
            }
        }
        health -= dmg;
        vx += kx;
        vy += ky;
        // the head lags behind the body on impact, opposite to the knockback
        wobX -= kx * 0.010f;
        wobY -= ky * 0.010f;
        hurtT = 0.22f;
        flashT = 0.16f;

        // every hit knocks the body out of its pose for a moment
        limp = Math.min(1f, limp + 0.55f + dmg * 0.02f);
        if (body != null) {
            body.kick(kx * 1.2f, ky * 1.2f);
        }

        // Poise: heavy weapons put you on the floor at once, light ones after a
        // couple of clean hits. Already-down fighters just take the damage.
        if (!down) {
            poise += dmg * (1f + Math.abs(kx) / 500f);
            poiseT = POISE_HOLD;
            if (poise >= POISE_MAX) {
                poise = 0f;
                knockDown(kx, ky);
            }
        }

        if (health <= 0f) {
            health = 0f;
            kill(kx * 0.6f);
        }
    }

    /** Go limp: the live body is handed to the ragdoll, then recovers by itself. */
    public void knockDown(float kx, float ky) {
        if (dead || down) {
            return;
        }
        down = true;
        downT = 0f;
        justDown = true;
        limp = 1f;
        attacking = false;
        attackQueued = false;
        jumpQueued = false;
        shieldOn = false;
        shieldHeld = false;
        moveX = 0f;
        vx = kx * 0.35f;
        vy = Math.min(ky, -120f);
    }

    /** Stand back up at a spot chosen by the caller. */
    public void recoverAt(float nx, float ny) {
        down = false;
        downT = 0f;
        x = nx;
        y = ny;
        vx = 0f;
        vy = 0f;
        onGround = true;
        health = Math.max(1f, health);
        hurtT = 0.12f;
        iframes = 0.6f;
        poise = 0f;
        poiseT = 0f;
        attacking = false;
        attackQueued = false;
        jumpQueued = false;
        limp = 0f;
        poseNow();
        if (body != null) {
            body.teleport(poseX, poseY);
        }
    }

    public void stun(float t) {
        hurtT = Math.max(hurtT, t);
    }

    public void kill(float kx) {
        if (dead) {
            return;
        }
        dead = true;
        health = 0f;
        attacking = false;
        down = false; // no recovery once dead; the body is now a corpse
        limp = 1f;
        vy = -280f;
        vx = kx + facing * -90f;
        justDied = true;
    }

    public void startAttack() {
        Weapons.Def d = Weapons.DEFS[weapon];
        attacking = true;
        firedThisSwing = false;
        attackDur = Math.max(0.18f, d.cooldown * 0.75f);
        attackT = attackDur;
        cooldown = d.cooldown;
        hitThisSwing = false;
    }

    public boolean meleeActive() {
        return attacking && Weapons.DEFS[weapon].type == Weapons.MELEE
                && attackT < attackDur * 0.75f && attackT > attackDur * 0.25f;
    }

    /** Current sword/hand tip position used for the melee hit arc. */
    public float tipX() {
        float reach = weaponReach();
        return x + facing * reach;
    }

    public float tipY() {
        return y - 10f;
    }

    public void update(float dt, Maps.Arena arena, boolean gravityOn, boolean shieldAllowed) {
        anim += dt;
        if (spawnT > 0) {
            spawnT -= dt;
        }
        if (flashT > 0) {
            flashT -= dt;
        }
        if (cooldown > 0) {
            cooldown -= dt;
        }
        if (hurtT > 0) {
            hurtT -= dt;
        }
        if (iframes > 0f) {
            iframes -= dt;
        }
        if (limp > 0f) {
            limp = Math.max(0f, limp - LIMP_RECOVER * dt);
        }
        if (!down && poise > 0f) {
            if (poiseT > 0f) {
                poiseT -= dt;
            } else {
                poise = Math.max(0f, poise - POISE_REGEN * dt);
            }
        }
        if (down) {
            // the ragdoll drives the body; Game steps it and calls recoverAt()
            downT += dt;
            attackQueued = false;
            jumpQueued = false;
            attacking = false;
            shieldOn = false;
            return;
        }

        if (dead) {
            // the corpse is simulated by Ragdoll now; keep the fighter frozen so
            // the camera does not chase an invisible rigid body into a pit
            deadT += dt;
            return;
        }

        wobVx += (-wobX * 150f - wobVx * 13f) * dt;
        wobVy += (-wobY * 150f - wobVy * 13f) * dt;
        wobX += wobVx * dt;
        wobY += wobVy * dt;

        boolean canAct = hurtT <= 0f && spawnT <= 0f;
        attackQueued = attackQueued && canAct && cooldown <= 0f && !attacking;
        if (attackQueued) {
            attackQueued = false;
            startAttack();
        }
        if (attacking) {
            attackT -= dt;
            if (attackT <= 0) {
                attacking = false;
                if (Weapons.DEFS[weapon].type == Weapons.MELEE) {
                    // fists never run dry, other melee weapons do
                    if (weapon != Weapons.FIST && ammo > 0) {
                        ammo--;
                        if (ammo <= 0) {
                            weapon = Weapons.FIST;
                            ammo = -1;
                        }
                    }
                }
            }
        }

        // DEF blocks with any weapon. This used to be melee-only, so the shield
        // silently did nothing while you were holding a gun, bow or wand.
        shieldOn = shieldAllowed && shieldHeld && canAct && shield > 0;
        // House rule from the original: with the energy shield switched off, DEF
        // makes the fighter bend down (crouch) instead.
        crouch = !shieldAllowed && shieldHeld && canAct && onGround;

        // Horizontal movement
        float target = canAct ? moveX : 0f;
        if (crouch && onGround) {
            target *= 0.25f;
        }
        if (shieldOn) {
            target *= 0.35f;
        }
        if (attacking) {
            target *= 0.45f;
        }
        float accel = onGround ? 3400f : 1900f;
        float maxSpeed = 360f;
        float desired = target * maxSpeed;
        if (vx < desired) {
            vx = Math.min(desired, vx + accel * dt);
        } else if (vx > desired) {
            vx = Math.max(desired, vx - accel * dt);
        }
        if (Math.abs(moveX) > 0.25f && Math.abs(vx) > 20f) {
            facing = moveX > 0 ? 1 : -1;
        }
        if (attacking && Weapons.DEFS[weapon].type != Weapons.MELEE) {
            facing = aimX >= 0 ? 1 : -1;
        }

        if (jumpQueued) {
            jumpQueued = false;
            if (canAct) {
                jump();
            }
        }

        // Gravity (toggleable "space" rule)
        if (gravityOn) {
            vy += 1900f * dt;
            if (vy > 1500f) {
                vy = 1500f;
            }
        } else {
            vy += 120f * dt;
            vy *= 0.985f;
            vx *= 0.985f;
            if (vy > 220f) {
                vy = 220f;
            }
        }

        prevBottom = y + FEET;
        prevTop = y - HEAD;
        x += vx * dt;
        y += vy * dt;

        boolean wasGround = onGround;
        onGround = false;
        collide(arena, dt);
        if (onGround) {
            coyote = 0.1f;
            walkPhase += Math.abs(vx) * dt * 0.035f;
        } else {
            coyote -= dt;
            if (vy > 60) {
                walkPhase += dt * 2f;
            }
        }
        if (onGround && !wasGround) {
            landed = true; // Game turns this into a dust puff
        }

        if (x < arena.bounds.left + HALF_W) {
            x = arena.bounds.left + HALF_W;
            vx = Math.max(0, vx);
        }
        if (x > arena.bounds.right - HALF_W) {
            x = arena.bounds.right - HALF_W;
            vx = Math.min(0, vx);
        }

        updateBody(dt, arena);
    }

    /** Steps the active ragdoll for a standing fighter: physics + pose muscle. */
    private void updateBody(float dt, Maps.Arena arena) {
        if (body == null) {
            body = new Ragdoll(this);
        }
        poseNow();
        // muscle: 1 when composed, near 0 when freshly smashed. Squaring makes
        // light hits flinch and heavy ones genuinely flop.
        float muscle = (1f - limp) * (1f - limp);
        body.update(dt, arena, poseX, poseY, muscle, 0.4f);
    }

    /**
     * Fills poseX/poseY with the world-space skeleton the arcade animation wants
     * this frame - the same geometry drawLocal() renders, so the body and the
     * old pose line up when muscle is at full strength.
     */
    private void poseNow() {
        float squash = (crouch && onGround && !dead) ? 0.72f : 1f;
        float headR = 13f;
        float neckY = -24f;
        float hipY = 12f;
        float shoulderY = neckY + 8f;
        float armLen = 22f;

        float legSwing = (float) Math.sin(walkPhase) * (onGround ? 12f : 6f);
        float legSwing2 = (float) Math.sin(walkPhase + 3.14f) * (onGround ? 12f : 6f);
        if (!onGround && !dead) {
            legSwing = -10f;
            legSwing2 = 8f;
        }

        float handXr;
        float handYr;
        if (shieldOn) {
            handXr = facing * 16f;
            handYr = shoulderY + 4f;
        } else if (attacking) {
            float t = 1f - Math.max(0f, attackT) / attackDur;
            float swing = (float) Math.sin(t * Math.PI) * (float) Math.PI * 0.9f;
            handXr = facing * (armLen * (float) Math.cos(swing * 0.9f));
            handYr = shoulderY + armLen * (float) Math.sin(swing * 0.9f) * 0.9f;
        } else {
            handXr = facing * 10f;
            handYr = shoulderY + 20f;
        }

        float nkX = wobX;
        float nkY = neckY + wobY;

        pose(Ragdoll.HIPS, 0f, hipY, squash);
        pose(Ragdoll.CHEST, 0f, shoulderY, squash);
        pose(Ragdoll.HEAD, nkX, nkY - headR + 4f, squash);

        // back arm and weapon arm, with elbows held half way out
        arm(Ragdoll.ELBOW_L, Ragdoll.HAND_L, 0f, shoulderY, -facing * 8f, shoulderY + 18f, squash);
        arm(Ragdoll.ELBOW_R, Ragdoll.HAND_R, 0f, shoulderY, handXr, handYr, squash);

        legPose(Ragdoll.KNEE_L, Ragdoll.FOOT_L, legSwing, 1, hipY, squash);
        legPose(Ragdoll.KNEE_R, Ragdoll.FOOT_R, legSwing2, -1, hipY, squash);
    }

    private void pose(int i, float lx, float ly, float squash) {
        poseX[i] = x + lx;
        poseY[i] = y + ly * squash;
    }

    private void arm(int elbow, int hand, float sx, float sy, float hx, float hy, float squash) {
        float ex = sx + (hx - sx) * 0.55f;
        float ey = sy + (hy - sy) * 0.55f;
        pose(hand, hx, hy, squash);
        pose(elbow, ex, ey, squash);
    }

    private void legPose(int knee, int foot, float swing, int side, float hipY, float squash) {
        float hipX = side * HALF_W * 0.34f;
        float kneeX = hipX + swing * 0.55f + side * 2f;
        float footX = kneeX + swing * 0.5f;
        float kneeY = hipY + (FEET - hipY) * 0.52f;
        pose(knee, kneeX, kneeY, squash);
        pose(foot, footX, FEET, squash);
    }

    /** Set for one frame when the fighter touches down; consumed by Game. */
    public boolean landed;

    private void collide(Maps.Arena arena, float dt) {
        float bottom = y + FEET;
        float top = y - HEAD;
        float left = x - HALF_W;
        float right = x + HALF_W;

        for (int i = 0; i < arena.platforms.size(); i++) {
            RectF r = arena.platforms.get(i).r;
            boolean overlapX = right > r.left + 2 && left < r.right - 2;

            if (overlapX && vy >= 0 && prevBottom <= r.top + 8 && bottom >= r.top) {
                y = r.top - FEET;
                vy = 0;
                onGround = true;
                prevBottom = y + FEET;
                bottom = y + FEET;
                top = y - HEAD;
                continue;
            }
            if (overlapX && vy < 0 && prevTop >= r.bottom - 8 && top <= r.bottom) {
                y = r.bottom + HEAD;
                vy = 40;
                top = y - HEAD;
                bottom = y + FEET;
                continue;
            }
            // side walls
            boolean overlapY = bottom > r.top + 6 && top < r.bottom - 6;
            if (overlapY) {
                if (x < r.centerX() && right > r.left && x > r.left) {
                    x = r.left - HALF_W;
                    vx = Math.min(0, vx) - 30;
                } else if (x >= r.centerX() && left < r.right && x < r.right) {
                    x = r.right + HALF_W;
                    vx = Math.max(0, vx) + 30;
                }
            }
        }
    }

    public void reset(float nx, float ny) {
        x = nx;
        y = ny;
        vx = 0;
        vy = 0;
        health = maxHealth;
        shield = maxShield;
        dead = false;
        deadT = 0;
        body = null;
        limp = 0f;
        justDied = false;
        down = false;
        downT = 0f;
        justDown = false;
        iframes = 0f;
        poise = 0f;
        poiseT = 0f;
        wobX = 0;
        wobY = 0;
        wobVx = 0;
        wobVy = 0;
        attacking = false;
        attackT = 0;
        cooldown = 0;
        hurtT = 0;
        spawnT = 0.9f;
        flashT = 0;
        onGround = true;
        weapon = Weapons.FIST;
        ammo = -1;
        crouch = false;
        shieldOn = false;
        hitThisSwing = false;
        firedThisSwing = false;
        prevBottom = y + FEET;
        prevTop = y - HEAD;
    }

    // ---------------------------------------------------------------- drawing

    public void draw(Painter g) {
        if (body == null) {
            // menus (e.g. the Customize preview) draw a fighter that never ticks,
            // so it has no simulated body to draw from
            drawLocal(g);
            return;
        }
        drawBody(g);
    }

    /**
     * Draws the stickman straight from the simulated joints, so limbs trail and
     * flop on a hit instead of following a canned pose.
     */
    private void drawBody(Painter g) {
        float squash = (crouch && onGround && !dead) ? 0.72f : 1f;

        int bodyColor = color;
        if (flashT > 0) {
            bodyColor = 0xFFFFFFFF;
        }
        boolean ghost = spawnT > 0 && ((int) (spawnT * 20) % 2 == 0);
        if (ghost) {
            g.alpha(120);
        }
        int outline = luminance(bodyColor) > 0.55f ? 0x66000000 : 0x66FFFFFF;

        float cx = body.x[Ragdoll.CHEST];
        float cy = body.y[Ragdoll.CHEST];
        float hx = body.x[Ragdoll.HIPS];
        float hy = body.y[Ragdoll.HIPS];
        float nx = body.x[Ragdoll.HEAD];
        float ny = body.y[Ragdoll.HEAD];
        boolean composed = limp < 0.45f;

        // legs first, then torso, then arms, then head (same stacking as before)
        bone(g, hx, hy, body.x[Ragdoll.KNEE_L], body.y[Ragdoll.KNEE_L], 7f, bodyColor, outline);
        bone(g, body.x[Ragdoll.KNEE_L], body.y[Ragdoll.KNEE_L],
                body.x[Ragdoll.FOOT_L], body.y[Ragdoll.FOOT_L], 6.4f, bodyColor, outline);
        bone(g, hx, hy, body.x[Ragdoll.KNEE_R], body.y[Ragdoll.KNEE_R], 7f, bodyColor, outline);
        bone(g, body.x[Ragdoll.KNEE_R], body.y[Ragdoll.KNEE_R],
                body.x[Ragdoll.FOOT_R], body.y[Ragdoll.FOOT_R], 6.4f, bodyColor, outline);
        if (composed) {
            foot(g, body.x[Ragdoll.FOOT_L], body.y[Ragdoll.FOOT_L], bodyColor, outline);
            foot(g, body.x[Ragdoll.FOOT_R], body.y[Ragdoll.FOOT_R], bodyColor, outline);
        }

        bone(g, cx, cy, hx, hy, 7f, bodyColor, outline);
        bone(g, cx, cy, body.x[Ragdoll.ELBOW_L], body.y[Ragdoll.ELBOW_L], 6f, bodyColor, outline);
        bone(g, body.x[Ragdoll.ELBOW_L], body.y[Ragdoll.ELBOW_L],
                body.x[Ragdoll.HAND_L], body.y[Ragdoll.HAND_L], 6f, bodyColor, outline);
        bone(g, cx, cy, body.x[Ragdoll.ELBOW_R], body.y[Ragdoll.ELBOW_R], 6f, bodyColor, outline);
        bone(g, body.x[Ragdoll.ELBOW_R], body.y[Ragdoll.ELBOW_R],
                body.x[Ragdoll.HAND_R], body.y[Ragdoll.HAND_R], 6f, bodyColor, outline);

        // head: cheeky eye offset by facing, hair re-anchored on the head joint
        float headR = 13f;
        g.circle(nx, ny, headR + 1.8f, outline);
        g.circle(nx, ny, headR, bodyColor);
        g.circle(nx + facing * 5f, ny - 2f, 2.4f, 0xFF101018);
        g.c.save();
        g.c.translate(nx, ny - (-24f + wobY - headR + 4f) * squash);
        drawHair(g, bodyColor);
        g.c.restore();

        drawWeapon(g, body.x[Ragdoll.HAND_R], body.y[Ragdoll.HAND_R]);

        if (shieldOn && shield > 0) {
            float sx = (cx + hx) * 0.5f;
            float sy = (cy + hy) * 0.5f;
            int a = 70 + (int) (50f * (shield / maxShield));
            g.line.setColor(Painter.withAlpha(0xFF6FE8FF, a));
            g.line.setStrokeWidth(3f);
            g.c.drawCircle(sx, sy, 46f, g.line);
            g.line.setColor(Painter.withAlpha(0xFFAFF6FF, 34));
            g.line.setStrokeWidth(11f);
            g.c.drawCircle(sx, sy, 46f, g.line);
        }

        if (ghost) {
            g.alpha(255);
        }
        g.reset();

        if (!dead && health < maxHealth) {
            float w = 44f;
            float barY = ny - 14f;
            g.rrect(nx - w / 2, barY, w, 5f, 2.5f, 0x88000000);
            g.rrect(nx - w / 2, barY, w * (health / maxHealth), 5f, 2.5f,
                    health > 50 ? 0xFF7BFF9E : (health > 25 ? 0xFFFFD166 : 0xFFFF5D5D));
        }
    }

    private void bone(Painter g, float x1, float y1, float x2, float y2, float w,
                      int body, int outline) {
        g.stroke(x1, y1, x2, y2, w + 3.4f, outline);
        g.stroke(x1, y1, x2, y2, w, body);
    }

    private void foot(Painter g, float fx, float fy, int body, int outline) {
        g.stroke(fx - 2f, fy, fx + 7f, fy, 8.4f, outline);
        g.stroke(fx - 2f, fy, fx + 7f, fy, 5f, body);
    }

    /** The old transform-based pose, kept for fighters that never simulate. */
    private void drawLocal(Painter g) {
        g.c.save();
        g.c.translate(x, y);

        float squash = 1f;
        if (crouch && onGround && !dead) {
            squash = 0.72f;
        }
        g.c.scale(1f, squash);

        int bodyColor = color;
        if (flashT > 0) {
            bodyColor = 0xFFFFFFFF;
        }
        if (spawnT > 0 && ((int) (spawnT * 20) % 2 == 0)) {
            g.alpha(120);
        }
        int outline = luminance(bodyColor) > 0.55f ? 0x66000000 : 0x66FFFFFF;

        float headR = 13f;
        float neckY = -24f;
        float hipY = 12f;

        float legSwing = (float) Math.sin(walkPhase) * (onGround ? 12f : 6f);
        float legSwing2 = (float) Math.sin(walkPhase + 3.14f) * (onGround ? 12f : 6f);
        if (!onGround && !dead) {
            legSwing = -10f;
            legSwing2 = 8f;
        }

        float shoulderY = neckY + 8f;
        float armLen = 22f;
        float handXr;
        float handYr;
        if (shieldOn) {
            handXr = facing * 16f;
            handYr = shoulderY + 4f;
        } else if (attacking) {
            float t = 1f - Math.max(0f, attackT) / attackDur;
            float swing = (float) Math.sin(t * Math.PI) * (float) Math.PI * 0.9f;
            handXr = facing * (armLen * (float) Math.cos(swing * 0.9f));
            handYr = shoulderY + armLen * (float) Math.sin(swing * 0.9f) * 0.9f;
        } else {
            handXr = facing * 10f;
            handYr = shoulderY + 20f;
        }

        float kneeY = hipY + (FEET - hipY) * 0.52f;
        leg(g, hipY, kneeY, legSwing, 1, bodyColor, outline);
        leg(g, hipY, kneeY, legSwing2, -1, bodyColor, outline);
        float nkX = wobX;
        float nkY = neckY + wobY;
        limb(g, nkX, nkY, 0, hipY, 7f, bodyColor, outline);
        limb(g, 0, shoulderY, -facing * 8f, shoulderY + 18f, 6f, bodyColor, outline);
        limb(g, 0, shoulderY, handXr, handYr, 6f, bodyColor, outline);

        g.circle(nkX, nkY - headR + 4f, headR + 1.8f, outline);
        g.circle(nkX, nkY - headR + 4f, headR, bodyColor);
        g.circle(nkX + facing * 5f, nkY - headR + 2f, 2.4f, 0xFF101018);
        g.c.save();
        g.c.translate(wobX, wobY);
        drawHair(g, bodyColor);
        g.c.restore();

        drawWeapon(g, handXr, handYr);

        if (shieldOn && shield > 0) {
            int a = 70 + (int) (50f * (shield / maxShield));
            g.line.setColor(Painter.withAlpha(0xFF6FE8FF, a));
            g.line.setStrokeWidth(3f);
            g.c.drawCircle(0, -6f, 46f, g.line);
            g.line.setColor(Painter.withAlpha(0xFFAFF6FF, 34));
            g.line.setStrokeWidth(11f);
            g.c.drawCircle(0, -6f, 46f, g.line);
        }

        g.c.restore();
        g.reset();

        if (!dead && health < maxHealth) {
            float w = 44f;
            g.rrect(x - w / 2, y - HEAD - 34f, w, 5f, 2.5f, 0x88000000);
            g.rrect(x - w / 2, y - HEAD - 34f, w * (health / maxHealth), 5f, 2.5f,
                    health > 50 ? 0xFF7BFF9E : (health > 25 ? 0xFFFFD166 : 0xFFFF5D5D));
        }
    }

    private void leg(Painter g, float hipY, float kneeY, float swing, int side,
                     int body, int outline) {
        float hipX = side * HALF_W * 0.34f;
        float kneeX = hipX + swing * 0.55f + side * 2f;
        float footX = kneeX + swing * 0.5f;
        limb(g, hipX, hipY, kneeX, kneeY, 7f, body, outline);
        limb(g, kneeX, kneeY, footX, FEET, 6.4f, body, outline);
        limb(g, footX - 2f, FEET, footX + 7f, FEET, 5f, body, outline);
    }

    private void limb(Painter g, float x1, float y1, float x2, float y2, float w,
                      int body, int outline) {
        g.stroke(x1, y1, x2, y2, w + 3.4f, outline);
        g.stroke(x1, y1, x2, y2, w, body);
    }

    private static float luminance(int c) {
        float r = ((c >> 16) & 0xFF) / 255f;
        float gg = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        return 0.299f * r + 0.587f * gg + 0.114f * b;
    }

    private void drawHair(Painter g, int body) {
        if (hair == 0) {
            return;
        }
        int c = accent;
        if (hair == 1) {
            Path p = new Path();
            float hx = 0f, hy = -HEAD + 4f - 13f;
            p.moveTo(hx - 11, hy + 3);
            p.lineTo(hx - 4, hy - 12);
            p.lineTo(hx + 1, hy - 1);
            p.lineTo(hx + 6, hy - 11);
            p.lineTo(hx + 11, hy + 3);
            p.close();
            g.path(p, c);
        } else if (hair == 2) {
            g.stroke(-13, -13f - 12f + 4f, 13, -13f - 12f + 4f, 4f, c);
        } else {
            g.rrect(-13, -46f, 26, 7, 3, c);
        }
    }

    private void drawWeapon(Painter g, float hx, float hy) {
        if (dead) {
            return;
        }
        int w = weapon;
        if (w == Weapons.FIST) {
            g.circle(hx, hy, 4.5f, color);
            return;
        }
        Weapons.Def d = Weapons.DEFS[w];
        int c = d.color;
        g.c.save();
        g.c.translate(hx, hy);
        float rot = 0f;
        if (attacking) {
            float t = 1f - Math.max(0f, attackT) / attackDur;
            rot = (t - 0.5f) * 110f * facing;
        } else {
            rot = 25f * facing;
        }
        g.c.rotate(rot);
        switch (w) {
            case 1:
                g.rrect(-2, -4, 30, 6, 2, c);
                g.rrect(-6, -6, 6, 10, 2, 0xFF8A6B3A);
                break;
            case 2:
                g.rrect(-2, -3, 46, 5, 2, c);
                g.circle(46, 0, 4, Painter.mix(c, 0xFFFFFFFF, 0.4f));
                break;
            case 3:
                g.rrect(-2, -3, 26, 6, 2, c);
                g.rrect(22, -4, 22, 8, 3, Painter.mix(c, 0xFFFFFFFF, 0.35f));
                break;
            case 4:
                g.rrect(-2, -3, 24, 6, 2, c);
                g.rrect(-2, -9, 20, 5, 2, Painter.mix(c, 0xFFFFFFFF, 0.3f));
                break;
            case 5:
                g.rrect(-2, -4, 24, 8, 3, c);
                g.rrect(20, -8, 12, 16, 3, Painter.mix(c, 0xFF000000, 0.25f));
                break;
            case 6:
                g.c.drawArc(new RectF(-12, -16, 12, 16), -70, 140, false, arcPaint(c, 3f));
                g.stroke(-4, 0, 14, 0, 1.5f, 0xFFE8E8E8);
                break;
            case 7:
                g.stroke(-2, 0, 26, 0, 5f, 0xFF8A6B3A);
                g.circle(26, 0, 4.5f, c);
                break;
            case 8:
                g.circle(0, 0, 6, 0xFF3A3A44);
                g.rrect(5, -4, 10, 8, 2, c);
                break;
            case 9:
                g.stroke(-2, 0, 20, 0, 5f, 0xFF8A6B3A);
                g.c.drawArc(new RectF(14, -12, 38, 12), -60, 120, false, arcPaint(c, 3f));
                break;
            case 10:
                g.rrect(-2, -4, 22, 8, 2, 0xFF4A4A55);
                g.rrect(4, 0, 10, 6, 2, 0xFF2A2A32);
                break;
            case 11:
                g.rrect(-2, -4, 34, 8, 4, c);
                g.stroke(-2, 0, 34, 0, 2f, 0xFFFFFFFF);
                break;
            default:
                g.circle(0, 0, 5, c);
                break;
        }
        g.c.restore();
        g.reset();
    }

    private android.graphics.Paint arcPaint(int c, float w) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(c);
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        return p;
    }
}
