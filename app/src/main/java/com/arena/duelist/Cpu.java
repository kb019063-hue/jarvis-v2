package com.arena.duelist;

import android.graphics.RectF;

import java.util.Random;

/** Opponent AI: platform navigation, spacing, punishes and weapon use. */
public final class Cpu {

    private static final float MAX_ROUTE_RISE = 175f;
    private static final float MAX_ROUTE_GAP = 260f;

    private final Random rnd = new Random();
    private float thinkTimer;
    private float move;
    private boolean jump, attack, shield;
    private float aimX = 1f, aimY = 0f;

    /** Committed landing platform while airborne; prevents panic jump spam. */
    private int routePlatform = -1;
    private float routeLandingX;
    private float jumpCooldown;

    public void reset() {
        thinkTimer = 0;
        move = 0;
        jump = false;
        attack = false;
        shield = false;
        aimX = 1f;
        aimY = 0f;
        routePlatform = -1;
        routeLandingX = 0f;
        jumpCooldown = 0f;
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
            jump = false;
            routePlatform = -1;
            return;
        }

        thinkTimer -= dt;
        jumpCooldown = Math.max(0f, jumpCooldown - dt);

        float skill = 0.25f + level * 0.22f;          // 0.25 .. 0.91
        float reaction = 0.34f - level * 0.07f;      // seconds between re-decisions
        float aggro = 0.35f + level * 0.15f;

        float dx = foe.x - me.x;
        float dy = foe.y - me.y;
        float dist = Math.abs(dx);
        float dir = dx >= 0 ? 1f : -1f;

        aimX = dir;
        aimY = dy < -60 ? -0.4f : (dy > 60 ? 0.5f : 0f);

        // Once a route jump starts, commit to its landing. Changing decision
        // every frame over a pit was how the old AI steered itself into water.
        if (routePlatform >= 0 && !me.onGround) {
            followRoute(me, arena);
            return;
        }
        if (me.onGround) {
            routePlatform = -1;
        }

        // Actual danger and look-ahead are different. Most importantly, backing
        // away from a hazard must not request another jump on every landing.
        boolean hazardHere = hazardDanger(me, arena, me.x, Fighter.HALF_W);
        if (hazardHere) {
            attack = false;
            shield = false;
            jump = false;
            move = hazardEscape(me, arena);
            return;
        }

        // Use real platform routes before treating the hazard ahead as a wall.
        if (me.onGround && navigate(me, foe, arena)) {
            return;
        }

        boolean hazardAhead = hazardDanger(me, arena, me.x + dir * 85f, 48f);
        if (hazardAhead) {
            attack = false;
            shield = false;
            jump = false;
            move = -dir;
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
                    requestJump(me);
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
                if (rnd.nextFloat() < 0.08f) {
                    requestJump(me);
                }
            }
            if (me.ammo == 0) {
                move = dir;
            }
            return;
        }

        // Melee spacing. Long-distance travel belongs to navigate(), not random
        // jumps that can repeatedly hit the underside of a nearby ledge.
        if (dist > reach * 0.85f) {
            move = dir;
            if (dy < -50 && rnd.nextFloat() < 0.55f) {
                requestJump(me);
            }
        } else if (dist < reach * 0.45f) {
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
    }

    /** Choose and execute one platform-to-platform step toward the opponent. */
    private boolean navigate(Fighter me, Fighter foe, Maps.Arena arena) {
        int current = supportingPlatform(me, arena);
        if (current < 0) {
            return false;
        }
        int foePlatform = supportingPlatform(foe, arena);
        if (current == foePlatform) {
            return false;
        }

        int next = chooseNextPlatform(me, foe, arena, current, foePlatform);
        if (next < 0) {
            return false;
        }

        RectF from = arena.platforms.get(current).r;
        RectF to = arena.platforms.get(next).r;
        float dir = to.centerX() >= from.centerX() ? 1f : -1f;
        float rise = from.top - to.top;
        float overlapLeft = Math.max(from.left, to.left);
        float overlapRight = Math.min(from.right, to.right);

        // A higher ledge overhead can be reached by jumping through its one-way
        // underside and landing on top. Align first, then commit to the jump.
        if (rise > 24f && overlapRight > overlapLeft) {
            float landing = clamp(me.x, overlapLeft + 24f, overlapRight - 24f);
            if (Math.abs(me.x - landing) > 24f) {
                move = landing > me.x ? 1f : -1f;
                jump = false;
                return true;
            }
            move = 0f;
            attack = false;
            shield = false;
            if (jumpCooldown <= 0f) {
                routePlatform = next;
                routeLandingX = landing;
                requestJump(me);
            } else {
                jump = false;
            }
            return true;
        }

        // Dropping to a lower platform needs a committed walk off the edge, not
        // a jump. The airborne controller then steers to the selected landing.
        if (to.top > from.top + 24f) {
            routePlatform = next;
            routeLandingX = clamp(me.x + dir * 80f, to.left + 24f, to.right - 24f);
            move = dir;
            attack = false;
            shield = false;
            jump = false;
            return true;
        }

        // Separated level/higher ledges need a takeoff near the current edge.
        float takeoff = dir > 0f ? from.right - 14f : from.left + 14f;
        if (Math.abs(me.x - takeoff) > 30f) {
            move = takeoff > me.x ? 1f : -1f;
            jump = false;
        } else if (jumpCooldown <= 0f) {
            routePlatform = next;
            routeLandingX = dir > 0f ? to.left + 24f : to.right - 24f;
            move = dir;
            attack = false;
            shield = false;
            requestJump(me);
        } else {
            // Wait safely for a deliberate jump instead of walking into the pit.
            move = 0f;
            jump = false;
        }
        return true;
    }

    private int chooseNextPlatform(Fighter me, Fighter foe, Maps.Arena arena,
                                   int current, int foePlatform) {
        RectF from = arena.platforms.get(current).r;
        float dir = foe.x >= me.x ? 1f : -1f;
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < arena.platforms.size(); i++) {
            if (i == current) {
                continue;
            }
            RectF to = arena.platforms.get(i).r;
            float centerDelta = to.centerX() - from.centerX();
            if (Math.abs(centerDelta) > 1f && (centerDelta > 0f ? 1f : -1f) != dir) {
                continue; // every step must make horizontal progress toward the foe
            }
            float rise = from.top - to.top;
            if (rise > MAX_ROUTE_RISE) {
                continue;
            }
            float gap = horizontalGap(from, to);
            if (gap > MAX_ROUTE_GAP && to.top <= from.top + 40f) {
                continue; // not a credible level/upward jump; look for a route
            }
            float score = Math.abs(centerDelta) + Math.abs(to.top - from.top) * 0.45f;
            if (i == foePlatform) {
                score -= 80f;
            }
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private void followRoute(Fighter me, Maps.Arena arena) {
        if (routePlatform < 0 || routePlatform >= arena.platforms.size()) {
            routePlatform = -1;
            move = 0f;
            return;
        }
        float dx = routeLandingX - me.x;
        move = Math.abs(dx) < 8f ? 0f : clamp(dx / 45f, -1f, 1f);
        attack = false;
        shield = false;
        jump = false;
    }

    private int supportingPlatform(Fighter f, Maps.Arena arena) {
        if (!f.onGround) {
            return -1;
        }
        float feet = f.y + Fighter.FEET;
        for (int i = 0; i < arena.platforms.size(); i++) {
            RectF r = arena.platforms.get(i).r;
            if (f.x > r.left - Fighter.HALF_W && f.x < r.right + Fighter.HALF_W
                    && Math.abs(feet - r.top) < 16f) {
                return i;
            }
        }
        return -1;
    }

    private float horizontalGap(RectF a, RectF b) {
        if (a.right < b.left) {
            return b.left - a.right;
        }
        if (b.right < a.left) {
            return a.left - b.right;
        }
        return 0f;
    }

    private void requestJump(Fighter me) {
        if (me.onGround && jumpCooldown <= 0f) {
            jump = true;
            jumpCooldown = 0.9f;
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** True when a horizontal position leads into a hazard on this level. */
    private boolean hazardDanger(Fighter me, Maps.Arena arena, float x, float margin) {
        for (int i = 0; i < arena.hazards.size(); i++) {
            Maps.Hazard h = arena.hazards.get(i);
            boolean nearX = x > h.r.left - margin && x < h.r.right + margin;
            boolean relevantY = me.y + Fighter.FEET > h.r.top - 175f
                    && me.y - Fighter.HEAD < h.r.bottom + 20f;
            if (nearX && relevantY) {
                return true;
            }
        }
        return false;
    }

    /** Direction of the nearest horizontal exit from the hazard underfoot. */
    private float hazardEscape(Fighter me, Maps.Arena arena) {
        for (int i = 0; i < arena.hazards.size(); i++) {
            Maps.Hazard h = arena.hazards.get(i);
            boolean insideX = me.x > h.r.left - Fighter.HALF_W
                    && me.x < h.r.right + Fighter.HALF_W;
            boolean relevantY = me.y + Fighter.FEET > h.r.top - 175f
                    && me.y - Fighter.HEAD < h.r.bottom + 20f;
            if (insideX && relevantY) {
                return me.x < h.r.centerX() ? -1f : 1f;
            }
        }
        return arena.bounds.centerX() > me.x ? 1f : -1f;
    }
}
