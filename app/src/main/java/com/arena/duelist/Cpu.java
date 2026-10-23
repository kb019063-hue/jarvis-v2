package com.arena.duelist;

import java.util.Random;

/** Simple but lively opponent AI: spaces, punishes, dodges hazards, uses weapons. */
public final class Cpu {

    private final Random rnd = new Random();
    private float thinkTimer;
    private float move;
    private boolean jump, attack, shield;
    private float aimX = 1f, aimY = 0f;

    public void reset() {
        thinkTimer = 0;
        move = 0;
        jump = false;
        attack = false;
        shield = false;
        aimX = 1f;
        aimY = 0f;
    }

    public float move() {
        return move;
    }

    public float aimX() {
        return aimX;
    }

    public float aimY() {
        return aimY;
    }

    public boolean jump() {
        boolean j = jump;
        jump = false;
        return j;
    }

    public boolean attack() {
        boolean a = attack;
        attack = false;
        return a;
    }

    public boolean shield() {
        return shield;
    }

    public void think(Fighter me, Fighter foe, Maps.Arena arena, float dt, int level) {
        if (me.dead || foe.dead || me.down) {
            move = 0;
            attack = false;
            shield = false;
            return;
        }
        // punish a knocked-down opponent: close in and swing
        if (foe.down) {
            float gap = Math.abs(foe.x - me.x);
            move = gap > 70f ? (foe.x > me.x ? 1f : -1f) : 0f;
            attack = gap < 150f;
            shield = false;
            aimX = foe.x >= me.x ? 1f : -1f;
            aimY = 0f;
            jump = false;
            return;
        }

        float skill = 0.25f + level * 0.22f;          // 0.25 .. 0.91
        float reaction = 0.34f - level * 0.07f;      // seconds between re-decisions
        float aggro = 0.35f + level * 0.15f;

        float dx = foe.x - me.x;
        float dy = foe.y - me.y;
        float dist = Math.abs(dx);
        float dir = dx >= 0 ? 1f : -1f;

        aimX = dir;
        aimY = dy < -60 ? -0.4f : (dy > 60 ? 0.5f : 0f);

        thinkTimer -= dt;

        // Look ahead instead of noticing a hazard only after it has already
        // killed us. This check runs every frame, independently of thinkTimer.
        boolean hazardHere = hazardDanger(me, arena, me.x);
        boolean hazardAhead = hazardDanger(me, arena, me.x + dir * 85f);
        if (hazardHere || hazardAhead) {
            attack = false;
            shield = false;
            if (hazardAhead && !hazardHere && me.onGround && dist > 100f && dist < 270f) {
                // A nearby opponent can be reached with a committed gap jump.
                move = dir;
                jump = true;
            } else {
                move = hazardHere ? (arena.bounds.centerX() > me.x ? 1f : -1f) : -dir;
                jump = me.onGround;
            }
            return;
        }

        if (thinkTimer > 0f) {
            return;
        }
        thinkTimer = reaction * (0.6f + rnd.nextFloat() * 0.9f);

        boolean foeAttacking = foe.attacking && foe.hurtT <= 0f;
        boolean myRanged = Weapons.DEFS[me.weapon].type != Weapons.MELEE;
        float reach = Weapons.DEFS[me.weapon].type == Weapons.MELEE
                ? Weapons.DEFS[me.weapon].reach : 340f;

        shield = false;
        move = 0;
        attack = false;

        if (me.health < 25 && rnd.nextFloat() < 0.5f * (1f - skill * 0.5f)) {
            // stay away and recover
            move = dist < 280 ? -dir : 0f;
            shield = dist < 190 && level >= 1;
            if (dist > 420 && myRanged && me.ammo != 0) {
                attack = true;
            }
            return;
        }

        if (foeAttacking && dist < Weapons.DEFS[foe.weapon].reach + 70) {
            float r = rnd.nextFloat();
            if (r < skill * 0.7f) {
                if (rnd.nextFloat() < 0.55f) {
                    shield = true;
                    move = -dir * 0.4f;
                } else {
                    jump = true;
                    move = -dir;
                }
            } else {
                move = -dir;
            }
            return;
        }

        if (myRanged) {
            float ideal = 320f;
            if (dist > ideal + 40) {
                move = dir;
            } else if (dist < ideal - 60) {
                move = -dir;
            } else {
                move = 0;
                if (rnd.nextFloat() < 0.85f && me.ammo != 0) {
                    attack = true;
                }
                if (rnd.nextFloat() < 0.25f) {
                    jump = true;
                }
            }
            if (me.ammo == 0) {
                move = dir;
            }
            return;
        }

        // melee
        if (dist > reach * 0.85f) {
            move = dir;
            if (dy < -50 && rnd.nextFloat() < 0.55f) {
                jump = true;
            }
            if (dist > 420 && rnd.nextFloat() < 0.15f) {
                jump = true;
            }
        } else if (dist < reach * 0.45f) {
            // too close, back up a bit sometimes
            move = rnd.nextFloat() < 0.35f ? -dir : 0f;
            if (rnd.nextFloat() < aggro) {
                attack = true;
            }
            if (rnd.nextFloat() < 0.06f) {
                shield = true;
            }
        } else {
            if (rnd.nextFloat() < 0.82f) {
                attack = true;
            }
            move = rnd.nextFloat() < 0.3f ? dir : 0f;
        }

        // occasional jump-in
        if (rnd.nextFloat() < 0.03f + level * 0.01f) {
            jump = true;
        }
    }

    /** True when a horizontal position leads into a hazard on this level. */
    private boolean hazardDanger(Fighter me, Maps.Arena arena, float x) {
        for (int i = 0; i < arena.hazards.size(); i++) {
            Maps.Hazard h = arena.hazards.get(i);
            boolean nearX = x > h.r.left - 48f && x < h.r.right + 48f;
            // Ignore hazards far below an upper platform, but see the pit below
            // the floor the CPU is currently walking on.
            boolean relevantY = me.y + Fighter.FEET > h.r.top - 175f
                    && me.y - Fighter.HEAD < h.r.bottom + 20f;
            if (nearX && relevantY) {
                return true;
            }
        }
        return false;
    }
}
