package com.arena.duelist;

import android.graphics.RectF;

import java.util.Random;

/** Opponent AI: platform navigation, spacing, punishes and weapon use. */
public final class Cpu {

    private static final float MAX_ROUTE_RISE = 175f;
    private static final float MAX_ROUTE_GAP = 260f;

    private final Random rnd = new Random();
    private float thinkTimer;
    private float attackTimer;
    private float move;
    private boolean jump, attack, shield;
    private float aimX = 1f, aimY = 0f;

    /** Committed landing platform while airborne; prevents panic jump spam. */
    private int routePlatform = -1;
    private float routeLandingX;
    private float jumpCooldown;

    public void reset() {
        thinkTimer = 0;
        attackTimer = 0;
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

    void seed(long seed) { rnd.setSeed(seed); }

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
        attackTimer = Math.max(0f, attackTimer - dt);
        jumpCooldown = Math.max(0f, jumpCooldown - dt);

        float reaction = 0.34f - level * 0.07f;      // seconds between re-decisions

        float dx = foe.x - me.x;
        float dy = foe.y - me.y;
        float dist = Math.abs(dx);
        float dir = dx >= 0 ? 1f : -1f;

        float aimLength = (float) Math.sqrt(dx * dx + dy * dy);
        aimX = aimLength > 1f ? dx / aimLength : dir;
        aimY = aimLength > 1f ? dy / aimLength : 0f;

        // Once a route jump starts, commit to its landing. Changing decision
        // every frame over a pit was how the old AI steered itself into water.
        if (routePlatform >= 0 && !me.onGround
                && (dist > me.weaponReach() + 44f || Math.abs(dy) > 65f)) {
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

        boolean ranged = Weapons.DEFS[me.weapon].type != Weapons.MELEE;
        // Reach is measured from the physical hand, not the catalogue's blade
        // length alone. Fists used to stop attacking at 30.6px while body push
        // kept fighters >=32px apart: an unreachable attack condition.
        float strikeRange = me.weaponReach() + 44f;
        boolean sameHeight = Math.abs(dy) < (ranged ? 100f : 65f);
        boolean close = sameHeight && dist <= strikeRange;
        boolean ready = !me.attacking && me.cooldown <= 0f
                && me.hurtT <= 0f && me.spawnT <= 0f;
        attack = false;
        shield = false;

        // Close combat takes precedence over routing. Even on different ledges,
        // an opponent already in reach must not be ignored to plan another jump.
        if (!ranged && close) {
            routePlatform = -1;
            jump = false;
            float ideal = Math.max(34f, me.weaponReach() * 0.65f + 28f);
            move = dist > ideal + 5f ? dir * 0.45f : 0f;
            attack = requestAttack(ready, level);
            return;
        }

        if (me.onGround && navigate(me, foe, arena)) return;

        boolean hazardAhead = hazardDanger(me, arena, me.x + dir * 85f, 48f);
        if (hazardAhead) {
            jump = false;
            move = -dir;
            // A ranged fighter can still fire while retreating from an edge.
            attack = requestAttack(ranged && sameHeight && ready && clearShot(me, foe, arena), level);
            return;
        }

        if (ranged) {
            // Fire at close range AND while changing distance, not just in a
            // narrow 260–360px band. Reaction delay governs travel, not cooldown.
            attack = requestAttack(sameHeight && dist < 700f && ready && clearShot(me, foe, arena), level);
            if (thinkTimer <= 0f) {
                thinkTimer = reaction;
                move = dist > 360f ? dir : (dist < 200f ? -dir * 0.65f : 0f);
                if (!safeStep(me, arena, move)) move = 0f;
            }
            return;
        }

        if (thinkTimer <= 0f) {
            thinkTimer = reaction;
            move = dir;
            if (dy < -70f && me.onGround) requestJump(me);
        }
    }

    private boolean requestAttack(boolean ready, int level) {
        if (!ready || attackTimer > 0f) return false;
        // Difficulty changes cadence. The floor is raised so even at Insane
        // the CPU cannot spam faster than the player's own attack cooldown -
        // that used to create an unavoidable stunlock.
        attackTimer = 0.22f + (3 - Math.max(0, Math.min(3, level))) * 0.12f
                + rnd.nextFloat() * 0.08f;
        return true;
    }

    private boolean clearShot(Fighter me, Fighter foe, Maps.Arena arena) {
        for (Maps.Platform platform : arena.platforms) {
            if (Projectile.segmentRectEntry(platform.r, me.handX(), me.handY(),
                    foe.x, foe.y - 8f, 3f) >= 0f) return false;
        }
        return true;
    }

    /** No blind retreat/dash off a ledge. A supported floor must be ahead. */
    private boolean safeStep(Fighter me, Maps.Arena arena, float direction) {
        if (Math.abs(direction) < 0.01f) return true;
        float x = me.x + Math.signum(direction) * 90f;
        for (Maps.Platform p : arena.platforms) {
            if (x >= p.r.left + 20f && x <= p.r.right - 20f
                    && Math.abs(p.r.top - (me.y + Fighter.FEET)) < 40f) return true;
        }
        return false;
    }

    boolean skillSafe(Fighter me, Maps.Arena arena) {
        boolean dash = me.weapon == Weapons.FIST || me.weapon == Weapons.SWORD
                || me.weapon == Weapons.SPEAR || me.weapon == Weapons.ENERGY_BLADE;
        return !dash || (Math.abs(aimY) < 0.25f && safeStep(me, arena, aimX));
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
        // Walk off the platform edge on the foe's side so the CPU drops down to
        // engage instead of orbiting the ledge above the opponent.
        if (to.top > from.top + 24f) {
            routePlatform = next;
            routeLandingX = clamp(foe.x, to.left + 24f, to.right - 24f);
            float platformCx = (from.left + from.right) * 0.5f;
            float exitX = foe.x >= platformCx ? (from.right + 40f) : (from.left - 40f);
            // exitX is clearly off-platform, so move never flips while the CPU
            // is still standing on the platform; once airborne, followRoute
            // takes over and steers toward routeLandingX.
            move = exitX > me.x ? 1f : -1f;
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
            float rise = from.top - to.top;
            if (rise > MAX_ROUTE_RISE) {
                continue; // target too high to reach
            }
            // Dropping to a lower platform doesn't require horizontal progress
            // toward the foe on the current platform - gravity carries the
            // fighter down. Enforce direction only for level or upward jumps so
            // the CPU never jumps away from the opponent.
            boolean dropping = rise < -24f;
            if (!dropping && Math.abs(centerDelta) > 1f
                    && (centerDelta > 0f ? 1f : -1f) != dir) {
                continue; // every step must make horizontal progress toward the foe
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
