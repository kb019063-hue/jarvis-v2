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
    public float deadT;
    /** Jointed body: the corpse after a KO, or the knocked-down body while alive. */
    public Ragdoll ragdoll;
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
        ragdoll = null;
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
        down = false; // no recovery once dead; the ragdoll is now a corpse
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
        ragdoll = null;
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

        // dark figures get a light outline and vice versa, so the stickman reads
        // on both bright and dark arenas
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

        // legs: hip -> knee -> foot, with a knee that bends while moving
        float kneeY = hipY + (FEET - hipY) * 0.52f;
        leg(g, hipY, kneeY, legSwing, 1, bodyColor, outline);
        leg(g, hipY, kneeY, legSwing2, -1, bodyColor, outline);
        float nkX = wobX;
        float nkY = neckY + wobY;
        limb(g, nkX, nkY, 0, hipY, 7f, bodyColor, outline);
        limb(g, 0, shoulderY, -facing * 8f, shoulderY + 18f, 6f, bodyColor, outline);
        limb(g, 0, shoulderY, handXr, handYr, 6f, bodyColor, outline);

        // head with outline (offset by the neck spring so hits make it snap)
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
