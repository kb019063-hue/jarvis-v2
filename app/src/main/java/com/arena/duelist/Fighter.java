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
    /**
     * Downward acceleration used when the "Gravity" house rule is off. It is
     * about a quarter of normal gravity: floaty, but jumps still land.
     */
    private static final float ZERO_G_GRAVITY = 450f;
    /** Deceleration (px/s^2) applied to knockback and dash impulses. */
    private static final float IMPULSE_BRAKE = 1200f;
    public static final float FEET = 30f;
    public static final float HEAD = 30f; // collision/AI clearance, not the drawn head radius
    public static final float CROUCH_HEAD = 18f;
    public static final float HEAD_RADIUS = 8.5f;
    public static final float HEIGHT = 64f;

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

    /**
     * Shield points lost per point of blocked damage. At 1.8 a sword broke a
     * full shield in four hits and a bomb in two, which made the one defensive
     * option in the game a two-second novelty. At 1.0 the shield eats six sword
     * hits or three bombs: it is something you can hold a lane with.
     */
    public static final float SHIELD_PER_DAMAGE = 1.0f;
    /** Points per second regained once the shield has been left alone. */
    public static final float SHIELD_REGEN = 22f;
    /** Seconds without a blocked hit before the shield starts coming back. */
    public static final float SHIELD_REGEN_DELAY = 2.5f;
    /**
     * After a break the shield must climb back to this fraction of its maximum
     * before it can be raised again. Without the floor the player could re-raise
     * a one-point shield the instant it came back, and it would shatter on the
     * next hit instead of reading as a real recovery window.
     */
    public static final float SHIELD_RESTORE = 0.35f;
    /** Counts down after every blocked hit; the shield only recharges at zero. */
    float shieldDelay;
    /** True from a guard break until the shield has recharged enough to raise. */
    public boolean shieldBroken;

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
    /** World-space weapon direction, shared by drawing and hit detection. */
    public float weaponAngle;
    /** Previous rendered weapon segment, used to sweep fast melee swings. */
    float prevWeaponHandX, prevWeaponHandY, prevWeaponTipX, prevWeaponTipY;
    boolean prevWeaponPoseValid;
    /** Remaining seconds before the release-triggered weapon skill is ready. */
    public float abilityT;
    public float moveX;
    public boolean jumpQueued, attackQueued, shieldHeld, crouch;
    public boolean shieldOn;

    // Drawing scratch: a frame redrew a blade, hair and up to two arcs, and each
    // of those allocated a fresh Path/RectF/Paint. At 60 Hz that was ~840
    // objects a second of pure garbage. These are reused instead.
    private final Path scratchPath = new Path();
    private final RectF scratchRect = new RectF();
    private final android.graphics.Paint scratchArc = new android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG);

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

    /**
     * Create the simulated body on demand. A standing fighter grows one on its
     * first update, but a hit can land before that tick - without this the
     * fighter would be flagged down with no body to flop and never recover.
     */
    void ensureBody() {
        if (body == null) {
            body = new Ragdoll(this);
        }
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
    /**
     * Remaining seconds in which an external velocity (knockback or a
     * release-skill dash) outranks the walk controller's braking.
     */
    public float impulseT;
    /**
     * Remaining seconds of an in-progress magazine reload. While this is above
     * zero the equipped ranged weapon cannot fire, but it is never taken away.
     */
    public float reloadT;
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

    public int color = 0xFF48E879;
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
        return body != null ? body.x[Ragdoll.HAND_R]
                : x + (float) Math.cos(weaponAngle) * 20f;
    }

    public float handY() {
        return body != null ? body.y[Ragdoll.HAND_R]
                : y - 6f + (float) Math.sin(weaponAngle) * 20f;
    }

    public void giveWeapon(int w) {
        weapon = w;
        ammo = Weapons.DEFS[w].ammo;
        cooldown = 0.15f;
        abilityT = Math.max(abilityT, 0.35f);
        reloadT = 0f;
    }

    /** True when a ranged weapon's magazine is empty and cannot fire yet. */
    public boolean outOfAmmo() {
        return Weapons.DEFS[weapon].type != Weapons.MELEE && ammo == 0;
    }

    public float weaponReach() {
        if (Weapons.DEFS[weapon].type != Weapons.MELEE) {
            return 0f;
        }
        // The fist's reach is already mostly represented by the animated arm;
        // using its catalogue reach as an extra blade length made invisible hits.
        return weapon == Weapons.FIST ? 6f : Weapons.DEFS[weapon].reach;
    }

    /** Weapon follows the forearm when an impact has pushed the body off-pose. */
    public float currentWeaponAngle() {
        if (body == null || (!dead && !down && limp < 0.25f)) {
            return weaponAngle;
        }
        float dx = body.x[Ragdoll.HAND_R] - body.x[Ragdoll.ELBOW_R];
        float dy = body.y[Ragdoll.HAND_R] - body.y[Ragdoll.ELBOW_R];
        if (dx * dx + dy * dy < 4f) {
            return weaponAngle;
        }
        return (float) Math.atan2(dy, dx);
    }

    /** Keep one frame of the drawn blade so combat can test its swept arc. */
    private void captureWeaponPose() {
        prevWeaponPoseValid = body != null;
        if (!prevWeaponPoseValid) {
            return;
        }
        prevWeaponHandX = body.x[Ragdoll.HAND_R];
        prevWeaponHandY = body.y[Ragdoll.HAND_R];
        float angle = currentWeaponAngle();
        prevWeaponTipX = prevWeaponHandX + (float) Math.cos(angle) * weaponReach();
        prevWeaponTipY = prevWeaponHandY + (float) Math.sin(angle) * weaponReach();
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
            // High enough to reach the game's 170 px platform rises. The old
            // -730 impulse topped out below them and made the intended routes
            // unusable, especially on Rooftop Dusk.
            vy = -850f;
            onGround = false;
            crouch = false;
            coyote = 0f;
        }
    }

    public void damage(float amount, float kx, float ky, boolean ignoreShield) {
        if (dead || iframes > 0f) {
            return;
        }
        float dmg = amount;
        // A downed fighter is already on the floor and cannot block, so a
        // knockdown must not be an automatic kill: halve the damage taken while
        // the body is still recovering.
        if (down) {
            dmg *= 0.5f;
        }
        if (!ignoreShield && shieldOn && shield > 0) {
            shield -= dmg * SHIELD_PER_DAMAGE;
            dmg *= 0.15f;
            shieldDelay = SHIELD_REGEN_DELAY;
            if (shield <= 0) {
                shield = 0;
                shieldOn = false;
                shieldBroken = true;
                // A guard break staggers, but it is no longer a death sentence:
                // at 0.7 s the shield broke and you ate the follow-up for free.
                stun(0.45f);
            }
        }
        health -= dmg;
        vx += kx;
        vy += ky;
        // the head lags behind the body on impact, opposite to the knockback
        wobX -= kx * 0.010f;
        wobY -= ky * 0.010f;
        // max, not assignment: a guard break stuns for longer and this line used
        // to flatten that back to the ordinary hit-stun of 0.22 s, so the
        // intended punisher for a shattered shield never actually happened.
        hurtT = Math.max(hurtT, 0.22f);
        // Let the knockback carry for a moment instead of being cancelled by
        // the movement controller on the very next frame.
        impulseT = Math.max(impulseT, 0.20f);
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
        swingBoost = 0f;
        shieldOn = false;
        shieldHeld = false;
        moveX = 0f;
        vx = kx * 0.35f;
        vy = Math.min(ky, -120f);
        ensureBody(); // a fighter hit before its first tick still needs a body
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
        ensureBody();
    }

    public void startAttack() {
        Weapons.Def d = Weapons.DEFS[weapon];
        attacking = true;
        firedThisSwing = false;
        // A swing must be readable. The old 0.18 s floor made the fastest
        // weapons land their hit window three frames after the input, which is
        // why close combat felt instant and unreactable.
        attackDur = Math.max(0.30f, d.cooldown * 0.75f);
        attackT = attackDur;
        cooldown = d.cooldown;
        hitThisSwing = false;
        if (swingBoost > 0f) {
            impulseT = Math.max(impulseT, attackDur);
        }

        // A small, weapon-weighted step makes close combat feel committed instead
        // of like a stationary arm animation. Release skills set swingBoost first
        // and supply their own (larger) dash, so they are not double-accelerated.
        if (d.type == Weapons.MELEE && swingBoost <= 0f) {
            float aimLen = (float) Math.sqrt(aimX * aimX + aimY * aimY);
            float dirX = aimLen < 0.2f ? facing : aimX / aimLen;
            float lunge = weapon == Weapons.SPEAR ? 92f
                    : (weapon == Weapons.SWORD || weapon == Weapons.ENERGY_BLADE ? 72f
                    : (weapon == Weapons.FIST ? 58f
                    : (weapon == Weapons.HAMMER ? 28f : 46f)));
            vx += dirX * lunge;
        }
    }

    public boolean meleeActive() {
        return attacking && Weapons.DEFS[weapon].type == Weapons.MELEE
                && attackT < attackDur * 0.70f && attackT > attackDur * 0.30f;
    }

    /** Current physical weapon tip, used by the melee contact test. */
    public float tipX() {
        return handX() + (float) Math.cos(currentWeaponAngle()) * weaponReach();
    }

    public float tipY() {
        return handY() + (float) Math.sin(currentWeaponAngle()) * weaponReach();
    }

    public void update(float dt, Maps.Arena arena, boolean gravityOn, boolean shieldAllowed) {
        update(dt, arena, gravityOn, shieldAllowed, true);
    }

    /** Updates the fighter, optionally freezing all player/CPU actions. */
    public void update(float dt, Maps.Arena arena, boolean gravityOn, boolean shieldAllowed,
                       boolean inputAllowed) {
        captureWeaponPose();
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
        if (abilityT > 0f) {
            abilityT = Math.max(0f, abilityT - dt);
        }
        if (outOfAmmo()) {
            if (reloadT <= 0f) {
                reloadT = Weapons.reloadTime(weapon);
            }
            reloadT = Math.max(0f, reloadT - dt);
            if (reloadT == 0f) {
                ammo = Weapons.DEFS[weapon].ammo;
            }
        }
        if (hurtT > 0) {
            hurtT -= dt;
        }
        if (iframes > 0f) {
            iframes -= dt;
        }
        if (impulseT > 0f) {
            impulseT = Math.max(0f, impulseT - dt);
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

        if (!inputAllowed) {
            // Countdowns and round-end sequences simulate gravity and bodies, but
            // never accept queued actions or carry movement over from combat.
            attackQueued = false;
            jumpQueued = false;
            shieldHeld = false;
            moveX = 0f;
            attacking = false;
            firedThisSwing = false;
            crouch = false;
        }
        boolean canAct = inputAllowed && hurtT <= 0f && spawnT <= 0f;
        // Being hit slows you down but must not freeze you completely: the old
        // rule blocked movement for the whole 0.22s recoil, so a fast weapon
        // could chain hits and keep a player from ever walking away.
        boolean canMove = inputAllowed && spawnT <= 0f;
        attackQueued = attackQueued && canAct && cooldown <= 0f && !attacking
                && !outOfAmmo();
        if (attackQueued) {
            attackQueued = false;
            startAttack();
        }
        if (attacking) {
            attackT -= dt;
            if (attackT <= 0) {
                attacking = false;
                swingBoost = 0f;
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
        // A spent or chipped shield comes back on its own once it has been left
        // alone for a moment. Without this a shield broken in the first exchange
        // was gone for the whole round, so the defensive option only existed for
        // the first five seconds of a ninety-second round.
        shieldDelay = Math.max(0f, shieldDelay - dt);
        if (!dead && shield < maxShield && shieldDelay <= 0f) {
            shield = Math.min(maxShield, shield + SHIELD_REGEN * dt);
        }
        if (shieldBroken && shield >= maxShield * SHIELD_RESTORE) {
            shieldBroken = false;
        }
        shieldOn = shieldAllowed && shieldHeld && canAct && shield > 0 && !shieldBroken;
        // House rule from the original: with the energy shield switched off, DEF
        // makes the fighter bend down (crouch) instead.
        crouch = !shieldAllowed && shieldHeld && canAct && onGround;

        // Horizontal movement
        float target = canMove ? moveX : 0f;
        if (hurtT > 0f) {
            target *= 0.45f;
        }
        if (crouch && onGround) {
            target *= 0.25f;
        }
        if (shieldOn) {
            target *= 0.35f;
        }
        if (attacking) {
            target *= 0.45f;
        }
        // Trimming the top speed and the ground acceleration takes the
        // twitchiness out of close combat without touching the jump: platform
        // reach depends on the jump impulse, not on walking speed.
        float accel = onGround ? 2700f : 1600f;
        float maxSpeed = 320f;
        float desired = target * maxSpeed;
        // A hit's knockback (and a release-skill dash) sets vx directly. Braking
        // those at the walk acceleration erased a 250 px/s hit in about 11 px,
        // which made every light weapon feel weightless. While an impulse is
        // active - and for anything faster than a walk - the controller bleeds
        // the external velocity slowly instead of stamping it out.
        boolean impulse = impulseT > 0f || Math.abs(vx) > maxSpeed;
        float rate = impulse ? IMPULSE_BRAKE : accel;
        if (vx < desired) {
            vx = Math.min(desired, vx + rate * dt);
        } else if (vx > desired) {
            vx = Math.max(desired, vx - rate * dt);
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
            // Gravity OFF is low-gravity drift, not true zero-G. The one-stick
            // control scheme has no vertical thrust: with no downward force at
            // all, a single jump coasted upward forever, carried the fighter out
            // of the arena and made the match impossible to finish. This keeps
            // floaty, slow falls while guaranteeing that jumps come back down
            // and that the 170 px arena ledges stay reachable.
            vy = vy * 0.95f + ZERO_G_GRAVITY * dt;
            vx *= 0.995f;
        }

        prevBottom = y + FEET;
        prevTop = y - headExtent();
        x += vx * dt;
        y += vy * dt;

        boolean wasGround = onGround;
        onGround = false;
        collide(arena, dt);
        if (!gravityOn && y - headExtent() < arena.bounds.top) {
            // Safety net for low-G: nothing may leave the world through the sky.
            y = arena.bounds.top + headExtent();
            if (vy < 0f) {
                vy = 0f;
            }
        }
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

        updateBody(dt, arena, gravityOn);
    }

    /** Steps the active ragdoll for a standing fighter: physics + pose muscle. */
    private void updateBody(float dt, Maps.Arena arena, boolean gravityOn) {
        if (body == null) {
            body = new Ragdoll(this);
        }
        body.facing = facing;
        body.hair = hair;
        body.accent = accent;
        poseNow();
        // muscle: 1 when composed, near 0 when freshly smashed. Squaring makes
        // light hits flinch and heavy ones genuinely flop.
        float muscle = (1f - limp) * (1f - limp);
        body.update(dt, arena, poseX, poseY, muscle, gravityOn ? 0.4f : 0f);
    }

    /**
     * Fills poseX/poseY with the world-space skeleton the arcade animation wants
     * this frame - the same geometry drawLocal() renders, so the body and the
     * old pose line up when muscle is at full strength.
     */
    private void poseNow() {
        float squash = (crouch && onGround && !dead) ? 0.72f : 1f;
        float stride = Math.min(1f, Math.abs(vx) / 240f);
        float bob = onGround && !dead
                ? Math.abs((float) Math.sin(walkPhase * 2f)) * 2.1f * stride : 0f;
        float neckY = -20f - bob * 0.7f;
        float hipY = 6f - bob * 0.3f;
        float shoulderY = neckY + 7f;
        float armLen = 24f;

        float legSwing = (float) Math.sin(walkPhase) * (onGround ? 15f : 6f) * stride;
        float legSwing2 = (float) Math.sin(walkPhase + (float) Math.PI)
                * (onGround ? 15f : 6f) * stride;
        if (!onGround && !dead) {
            legSwing = -10f;
            legSwing2 = 8f;
        }

        float lean = Math.max(-1f, Math.min(1f, vx / 360f)) * 3.5f;
        float shoulderX = lean + (float) Math.sin(walkPhase) * 1.2f * stride;
        float aimLength = (float) Math.sqrt(aimX * aimX + aimY * aimY);
        float baseAngle = aimLength < 0.2f
                ? (facing >= 0 ? 0f : (float) Math.PI)
                : (float) Math.atan2(aimY, aimX);
        weaponAngle = baseAngle;

        float handXr;
        float handYr;
        float attackLean = 0f;
        if (shieldOn) {
            handXr = shoulderX + (float) Math.cos(baseAngle) * 16f;
            handYr = shoulderY + 4f + (float) Math.sin(baseAngle) * 12f;
        } else if (attacking) {
            float progress = 1f - Math.max(0f, Math.min(1f, attackT / attackDur));
            float windup = 1f - smoothstep(0f, 0.24f, progress);
            float strike = smoothstep(0.18f, 0.50f, progress);
            float follow = 1f - smoothstep(0.58f, 1f, progress);
            float sign = facing >= 0 ? 1f : -1f;
            float offset;

            if (Weapons.DEFS[weapon].type != Weapons.MELEE) {
                // Ranged attacks still show a readable aim with a small recoil.
                offset = -sign * 0.12f * windup;
                armLen += -3f * windup + 2f * strike * follow;
            } else {
                switch (weapon) {
                    case Weapons.FIST:
                        offset = sign * (-0.10f * windup + 0.10f * strike * follow);
                        armLen += -5f * windup + 13f * strike * follow;
                        attackLean = sign * (0.35f * strike * follow - 0.14f * windup);
                        break;
                    case Weapons.SPEAR:
                        offset = sign * (-0.06f * windup + 0.06f * strike * follow);
                        armLen += -8f * windup + 19f * strike * follow;
                        attackLean = sign * (0.72f * strike * follow - 0.24f * windup);
                        break;
                    case Weapons.DUAL_SABERS:
                        offset = sign * (-0.62f * windup + 2.45f * strike * follow);
                        armLen += 2f * strike * follow;
                        attackLean = sign * 0.3f * strike * follow;
                        break;
                    case Weapons.HAMMER:
                        offset = sign * (-1.08f * windup + 1.48f * strike * follow);
                        armLen += -3f * windup + 4f * strike * follow;
                        attackLean = sign * (0.55f * strike * follow - 0.20f * windup);
                        break;
                    case Weapons.ENERGY_BLADE:
                        offset = sign * (-0.92f * windup + 1.38f * strike * follow);
                        armLen += -2f * windup + 5f * strike * follow;
                        attackLean = sign * 0.42f * strike * follow;
                        break;
                    default: // Sword and other melee weapons: wind up, slash, recover
                        offset = sign * (-0.82f * windup + 1.28f * strike * follow);
                        armLen += -2f * windup + 5f * strike * follow;
                        attackLean = sign * 0.36f * strike * follow;
                        break;
                }
            }

            shoulderX += attackLean;
            weaponAngle = baseAngle + offset;
            handXr = shoulderX + armLen * (float) Math.cos(weaponAngle);
            handYr = shoulderY + armLen * (float) Math.sin(weaponAngle);
        } else {
            handXr = shoulderX + armLen * 0.76f * (float) Math.cos(baseAngle);
            handYr = shoulderY + 9f + armLen * 0.76f * (float) Math.sin(baseAngle);
        }

        float nkX = wobX + lean * 1.35f + attackLean * 0.32f;
        float nkY = neckY + wobY;
        pose(Ragdoll.HIPS, 0f, hipY, squash);
        pose(Ragdoll.CHEST, shoulderX, shoulderY, squash);
        pose(Ragdoll.HEAD, nkX, nkY - HEAD_RADIUS + 4f, squash);

        float armSwing = (float) Math.sin(walkPhase + (float) Math.PI) * 6f * stride;
        float armBob = (float) Math.cos(walkPhase + (float) Math.PI) * 1.5f * stride;
        arm(Ragdoll.ELBOW_L, Ragdoll.HAND_L,
                shoulderX, shoulderY,
                shoulderX - facing * 10f + armSwing, shoulderY + 15f + armBob, squash);
        arm(Ragdoll.ELBOW_R, Ragdoll.HAND_R,
                shoulderX, shoulderY, handXr, handYr, squash);

        legPose(Ragdoll.KNEE_L, Ragdoll.FOOT_L, legSwing, 1, hipY, squash);
        legPose(Ragdoll.KNEE_R, Ragdoll.FOOT_R, legSwing2, -1, hipY, squash);
    }

    private static float smoothstep(float a, float b, float value) {
        float t = Math.max(0f, Math.min(1f, (value - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    private void pose(int i, float lx, float ly, float squash) {
        poseX[i] = x + lx;
        // Squash around the feet so crouching never makes the soles float.
        poseY[i] = y + FEET + (ly - FEET) * squash;
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

    private float headExtent() {
        return crouch ? CROUCH_HEAD : HEAD;
    }

    private void collide(Maps.Arena arena, float dt) {
        float bottom = y + FEET;
        float head = headExtent();
        float top = y - head;
        float left = x - HALF_W;
        float right = x + HALF_W;

        for (int i = 0; i < arena.platforms.size(); i++) {
            RectF r = arena.platforms.get(i).r;
            boolean overlapX = right > r.left + 2 && left < r.right - 2;
            // Thin ledges are one-way platforms: land from above, but allow a
            // fighter to jump up through them. Thick ground remains solid.
            boolean oneWay = r.height() <= 32f;

            if (overlapX && vy >= 0 && prevBottom <= r.top + 8 && bottom >= r.top) {
                y = r.top - FEET;
                vy = 0;
                onGround = true;
                prevBottom = y + FEET;
                bottom = y + FEET;
                top = y - head;
                continue;
            }
            if (!oneWay && overlapX && vy < 0 && prevTop >= r.bottom - 8 && top <= r.bottom) {
                y = r.bottom + head;
                vy = 40;
                top = y - head;
                bottom = y + FEET;
                continue;
            }
            // side walls (one-way ledges deliberately have none)
            boolean overlapY = bottom > r.top + 6 && top < r.bottom - 6;
            if (!oneWay && overlapY) {
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
        shieldDelay = 0f;
        shieldBroken = false;
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
        abilityT = 0f;
        weaponAngle = facing >= 0 ? 0f : (float) Math.PI;
        prevWeaponPoseValid = false;
        hurtT = 0;
        impulseT = 0f;
        reloadT = 0f;
        spawnT = 0.9f;
        flashT = 0;
        onGround = true;
        weapon = Weapons.FIST;
        ammo = -1;
        crouch = false;
        shieldOn = false;
        hitThisSwing = false;
        firedThisSwing = false;
        moveX = 0f;
        aimX = facing >= 0 ? 1f : -1f;
        aimY = 0f;
        jumpQueued = false;
        attackQueued = false;
        shieldHeld = false;
        landed = false;
        coyote = 0f;
        walkPhase = 0f;
        swingBoost = 0f;
        prevBottom = y + FEET;
        prevTop = y - headExtent();
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

    /** Draw the shared clean stickman model from either live or pose joints. */
    private void drawBody(Painter g) {
        int bodyColor = flashT > 0f ? 0xFFFFFFFF : color;
        boolean ghost = spawnT > 0f && ((int) (spawnT * 20f) % 2 == 0);
        int outline = outlineColor(bodyColor);
        if (ghost) {
            g.alpha(120);
        }
        drawSkeleton(g, body.x, body.y, bodyColor, outline);
        if (ghost) {
            g.alpha(120);
        }
        drawWeapon(g, body.x[Ragdoll.HAND_R], body.y[Ragdoll.HAND_R]);
        if (ghost) {
            g.alpha(120);
        }
        if (shieldOn && shield > 0f) {
            drawShield(g, (body.x[Ragdoll.CHEST] + body.x[Ragdoll.HIPS]) * 0.5f,
                    (body.y[Ragdoll.CHEST] + body.y[Ragdoll.HIPS]) * 0.5f);
        }
        g.reset();
        drawHealthBar(g, body.x[Ragdoll.HEAD], body.y[Ragdoll.HEAD]);
    }

    /** Same joint renderer for the customize preview; no separate cartoon pose. */
    private void drawLocal(Painter g) {
        poseNow();
        int bodyColor = flashT > 0f ? 0xFFFFFFFF : color;
        boolean ghost = spawnT > 0f && ((int) (spawnT * 20f) % 2 == 0);
        int outline = outlineColor(bodyColor);
        if (ghost) {
            g.alpha(120);
        }
        drawSkeleton(g, poseX, poseY, bodyColor, outline);
        if (ghost) {
            g.alpha(120);
        }
        drawWeapon(g, poseX[Ragdoll.HAND_R], poseY[Ragdoll.HAND_R]);
        if (ghost) {
            g.alpha(120);
        }
        if (shieldOn && shield > 0f) {
            drawShield(g, (poseX[Ragdoll.CHEST] + poseX[Ragdoll.HIPS]) * 0.5f,
                    (poseY[Ragdoll.CHEST] + poseY[Ragdoll.HIPS]) * 0.5f);
        }
        g.reset();
        drawHealthBar(g, poseX[Ragdoll.HEAD], poseY[Ragdoll.HEAD]);
    }

    private void drawSkeleton(Painter g, float[] jx, float[] jy, int bodyColor, int outline) {
        bone(g, jx[Ragdoll.HIPS], jy[Ragdoll.HIPS],
                jx[Ragdoll.KNEE_L], jy[Ragdoll.KNEE_L], 3.2f, bodyColor, outline);
        bone(g, jx[Ragdoll.KNEE_L], jy[Ragdoll.KNEE_L],
                jx[Ragdoll.FOOT_L], jy[Ragdoll.FOOT_L], 2.8f, bodyColor, outline);
        bone(g, jx[Ragdoll.HIPS], jy[Ragdoll.HIPS],
                jx[Ragdoll.KNEE_R], jy[Ragdoll.KNEE_R], 3.2f, bodyColor, outline);
        bone(g, jx[Ragdoll.KNEE_R], jy[Ragdoll.KNEE_R],
                jx[Ragdoll.FOOT_R], jy[Ragdoll.FOOT_R], 2.8f, bodyColor, outline);

        // A single stick-like spine and open joints keep the figure lightweight.
        bone(g, jx[Ragdoll.HEAD], jy[Ragdoll.HEAD],
                jx[Ragdoll.CHEST], jy[Ragdoll.CHEST], 3.4f, bodyColor, outline);
        bone(g, jx[Ragdoll.CHEST], jy[Ragdoll.CHEST],
                jx[Ragdoll.HIPS], jy[Ragdoll.HIPS], 3.4f, bodyColor, outline);
        bone(g, jx[Ragdoll.CHEST], jy[Ragdoll.CHEST],
                jx[Ragdoll.ELBOW_L], jy[Ragdoll.ELBOW_L], 2.8f, bodyColor, outline);
        bone(g, jx[Ragdoll.ELBOW_L], jy[Ragdoll.ELBOW_L],
                jx[Ragdoll.HAND_L], jy[Ragdoll.HAND_L], 2.7f, bodyColor, outline);
        bone(g, jx[Ragdoll.CHEST], jy[Ragdoll.CHEST],
                jx[Ragdoll.ELBOW_R], jy[Ragdoll.ELBOW_R], 2.8f, bodyColor, outline);
        bone(g, jx[Ragdoll.ELBOW_R], jy[Ragdoll.ELBOW_R],
                jx[Ragdoll.HAND_R], jy[Ragdoll.HAND_R], 2.7f, bodyColor, outline);

        float hx = jx[Ragdoll.HEAD];
        float hy = jy[Ragdoll.HEAD];
        g.circle(hx, hy, HEAD_RADIUS + 1.6f, outline);
        g.circle(hx, hy, HEAD_RADIUS, bodyColor);
        drawHairAt(g, hx, hy);
    }

    private void bone(Painter g, float x1, float y1, float x2, float y2, float width,
                      int bodyColor, int outline) {
        g.stroke(x1, y1, x2, y2, width + 2f, outline);
        g.stroke(x1, y1, x2, y2, width, bodyColor);
    }

    private void drawShield(Painter g, float cx, float cy) {
        int a = 70 + (int) (50f * (shield / maxShield));
        g.line.setColor(Painter.withAlpha(0xFF6FE8FF, a));
        g.line.setStrokeWidth(3f);
        g.c.drawCircle(cx, cy, 46f, g.line);
        g.line.setColor(Painter.withAlpha(0xFFAFF6FF, 34));
        g.line.setStrokeWidth(11f);
        g.c.drawCircle(cx, cy, 46f, g.line);
    }

    private void drawHealthBar(Painter g, float hx, float hy) {
        if (!dead && health < maxHealth) {
            float w = 44f;
            float barY = hy - HEAD_RADIUS - 8f;
            g.rrect(hx - w / 2f, barY, w, 5f, 2.5f, 0x88000000);
            g.rrect(hx - w / 2f, barY, w * (health / maxHealth), 5f, 2.5f,
                    health > 50f ? 0xFF7BFF9E
                            : (health > 25f ? 0xFFFFD166 : 0xFFFF5D5D));
        }
    }

    private int outlineColor(int bodyColor) {
        return luminance(bodyColor) > 0.42f ? 0xFF11141B : 0xFFE8EBF2;
    }

    private static float luminance(int c) {
        float r = ((c >> 16) & 0xFF) / 255f;
        float gg = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        return 0.299f * r + 0.587f * gg + 0.114f * b;
    }

    private void drawHairAt(Painter g, float hx, float hy) {
        if (hair == 0) {
            return;
        }
        int c = accent;
        if (hair == 1) {
            Path p = scratchPath;
            p.reset();
            p.moveTo(hx - 8f, hy - 5f);
            p.lineTo(hx - 5f, hy - HEAD_RADIUS - 5f);
            p.lineTo(hx, hy - HEAD_RADIUS - 1f);
            p.lineTo(hx + 4f, hy - HEAD_RADIUS - 6f);
            p.lineTo(hx + 8f, hy - 5f);
            p.close();
            g.path(p, c);
        } else if (hair == 2) {
            g.stroke(hx - 8f, hy - 5f, hx + 8f, hy - 5f, 3.2f, c);
        } else {
            g.rrect(hx - 8f, hy - HEAD_RADIUS - 2f, 16f, 5f, 2f, c);
        }
    }

    /** Draws the held weapon while the jointed body is down or dead. */
    public void drawRagdollWeapon(Painter g) {
        if (body != null) {
            drawWeapon(g, body.x[Ragdoll.HAND_R], body.y[Ragdoll.HAND_R], body.fade);
        }
    }

    private void drawWeapon(Painter g, float hx, float hy) {
        drawWeapon(g, hx, hy, 1f);
    }

    private void drawWeapon(Painter g, float hx, float hy, float opacity) {
        opacity = Math.max(0f, Math.min(1f, opacity));
        if (opacity <= 0f) {
            return;
        }
        int w = weapon;
        if (w == Weapons.FIST) {
            g.circle(hx, hy, 4.5f, fadeColor(color, opacity));
            return;
        }
        Weapons.Def d = Weapons.DEFS[w];
        int rawColor = abilityT <= 0f ? d.color : Painter.mix(d.color, 0xFF4A4A55, 0.55f);
        int c = fadeColor(rawColor, opacity);
        g.c.save();
        g.c.translate(hx, hy);
        float rot = (float) Math.toDegrees(currentWeaponAngle());
        g.c.rotate(rot);
        switch (w) {
            case Weapons.SWORD:
                g.rrect(-10f, -2f, 12f, 4f, 2f, fadeColor(0xFF76552D, opacity));
                g.rrect(-1f, -5f, 2.5f, 10f, 1f, fadeColor(0xFFB38A4A, opacity));
                drawBlade(g, 2f, d.reach - 2f, 3.2f, c);
                break;
            case Weapons.SPEAR:
                g.stroke(-8f, 0f, d.reach - 10f, 0f, 3f, fadeColor(0xFF8A6B3A, opacity));
                drawBlade(g, d.reach - 13f, 13f, 4.5f, c);
                break;
            case Weapons.DUAL_SABERS:
                g.rrect(-5f, -2f, 10f, 4f, 2f, fadeColor(0xFF76552D, opacity));
                drawBlade(g, 3f, 47f, 2.7f, c);
                g.c.save();
                g.c.rotate(-42f);
                drawBlade(g, 1f, 25f, 2.4f, fadeColor(Painter.mix(rawColor, 0xFFFFFFFF, 0.25f), opacity));
                g.c.restore();
                break;
            case Weapons.KUNAI:
                drawBlade(g, -1f, 25f, 4f, c);
                g.rrect(-8f, -2f, 9f, 4f, 1.5f, fadeColor(0xFF76552D, opacity));
                break;
            case Weapons.HAMMER:
                g.stroke(-7f, 0f, d.reach - 8f, 0f, 4f, fadeColor(0xFF8A6B3A, opacity));
                g.rrect(d.reach - 15f, -9f, 17f, 18f, 3f,
                        fadeColor(Painter.mix(rawColor, 0xFF000000, 0.2f), opacity));
                g.rrect(d.reach - 13f, -7f, 12f, 14f, 2f, c);
                break;
            case Weapons.BOW:
                scratchRect.set(-12f, -18f, 13f, 18f);
                g.c.drawArc(scratchRect, -74f, 148f, false, arcPaint(c, 2.6f));
                g.stroke(-5f, 0f, 17f, 0f, 1.4f, fadeColor(0xFFE8E8E8, opacity));
                break;
            case Weapons.WAND:
                g.stroke(-5f, 0f, 29f, 0f, 3.4f, fadeColor(0xFF8A6B3A, opacity));
                g.circle(30f, 0f, 5f, c);
                g.circle(30f, 0f, 2f, fadeColor(Painter.mix(rawColor, 0xFFFFFFFF, 0.65f), opacity));
                break;
            case Weapons.BOMB:
                g.circle(0f, 0f, 6f, fadeColor(0xFF303039, opacity));
                g.rrect(4f, -5f, 9f, 10f, 2f, c);
                g.circle(13f, -4f, 2f, fadeColor(0xFFFFE38A, opacity));
                break;
            case Weapons.BOOMERANG:
                g.stroke(-5f, 0f, 20f, 0f, 3.2f, fadeColor(0xFF8A6B3A, opacity));
                scratchRect.set(12f, -13f, 39f, 13f);
                g.c.drawArc(scratchRect, -62f, 124f, false, arcPaint(c, 3.2f));
                break;
            case Weapons.PISTOL:
                g.rrect(-3f, -4f, 24f, 8f, 2f, fadeColor(0xFF4A4A55, opacity));
                g.rrect(4f, 2f, 8f, 6f, 1.5f, fadeColor(0xFF2A2A32, opacity));
                g.rrect(17f, -2f, 8f, 3f, 1f, c);
                break;
            case Weapons.ENERGY_BLADE:
                g.rrect(-8f, -3f, 10f, 6f, 2f, fadeColor(0xFF384650, opacity));
                drawBlade(g, 2f, d.reach - 2f, 4f, c);
                g.stroke(3f, 0f, d.reach, 0f, 1.1f, fadeColor(0xFFE8FFFF, opacity));
                break;
            default:
                g.circle(0f, 0f, 4f, fadeColor(color, opacity));
                break;
        }
        g.c.restore();
        g.reset();
    }

    private static int fadeColor(int color, float opacity) {
        int originalAlpha = (color >>> 24) & 0xFF;
        return Painter.withAlpha(color, (int) (originalAlpha * opacity));
    }

    private void drawBlade(Painter g, float start, float length, float halfWidth, int color) {
        float end = start + length;
        float shoulder = Math.max(start, end - Math.min(8f, length * 0.22f));
        Path blade = scratchPath;
        blade.reset();
        blade.moveTo(start, -halfWidth * 0.55f);
        blade.lineTo(shoulder, -halfWidth);
        blade.lineTo(end, 0f);
        blade.lineTo(shoulder, halfWidth);
        blade.lineTo(start, halfWidth * 0.55f);
        blade.close();
        g.path(blade, color);
    }

    private android.graphics.Paint arcPaint(int c, float w) {
        scratchArc.setStyle(android.graphics.Paint.Style.STROKE);
        scratchArc.setStrokeWidth(w);
        scratchArc.setColor(c);
        scratchArc.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        return scratchArc;
    }
}
