package com.arena.duelist;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Regressions from physical-phone feedback: combat must work, not just render. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class PlayabilityTest {
    private Game boot(int mode, int map) {
        Game g = new Game(RuntimeEnvironment.getApplication(), new Game.Host() {
            public void announce(String text) { }
            public void setBright(boolean on) { }
            public void vibrate(int ms) { }
            public void sfx(int id) { }
        });
        g.resize(1600, 720);
        g.debugSeed(42);
        g.debugStartMatch(mode, map);
        g.debugSuppressPickups();
        return g;
    }

    @Test public void fistsAttackAtCollisionSeparationOnEveryDifficulty() {
        for (int level = 0; level < 4; level++) {
            Cpu cpu = new Cpu();
            cpu.seed(level);
            Fighter me = new Fighter(1, "CPU", 740, 610, true);
            Fighter foe = new Fighter(0, "P1", 700, 610, false);
            me.spawnT = me.cooldown = 0f;
            int attacks = 0;
            for (int i = 0; i < 120; i++) {
                cpu.think(me, foe, Maps.ALL[0], 1f / 60f, level);
                if (cpu.attack()) attacks++;
            }
            assertTrue("CPU never punches at 40px on difficulty " + level, attacks > 0);
            assertFalse("close combat must not become a jump loop", cpu.jump());
        }
    }

    @Test public void rangedCpuFiresWhileOpponentIsClose() {
        Cpu cpu = new Cpu();
        Fighter me = new Fighter(1, "CPU", 800, 610, true);
        Fighter foe = new Fighter(0, "P1", 700, 610, false);
        me.giveWeapon(Weapons.PISTOL);
        me.spawnT = me.cooldown = 0f;
        cpu.think(me, foe, Maps.ALL[0], 1f / 60f, 1);
        assertTrue("close-range gunner must shoot, not only back away", cpu.attack());
        Game g = boot(0, 8);
        g.debugStartFight();
        Fighter target = g.debugFighter(0), gunner = g.debugFighter(1);
        target.reset(620, 590);
        gunner.reset(730, 590);
        gunner.giveWeapon(Weapons.PISTOL);
        gunner.abilityT = 100f;
        gunner.spawnT = target.spawnT = 0f;
        for (int frame = 0; frame < 120 && target.health == target.maxHealth; frame++)
            g.tick(1f / 60f);
        assertTrue("a real close-range bullet must damage the opponent", target.health < target.maxHealth);
        assertEquals("damage must come before running out of bullets", Weapons.PISTOL, gunner.weapon);
    }

    @Test public void everyMeleeCpuActuallyDamagesAnIdleOpponent() {
        for (int weapon = 0; weapon < Weapons.COUNT; weapon++) {
            if (Weapons.DEFS[weapon].type != Weapons.MELEE) continue;
            for (long seed : new long[] { 7, 42, 91 }) {
                Game g = boot(0, 8);
                g.debugSeed(seed);
                g.debugStartFight();
                Fighter player = g.debugFighter(0), cpu = g.debugFighter(1);
                player.reset(620, 590);
                cpu.reset(710, 590);
                cpu.giveWeapon(weapon);
                // Disable skills: the basic weapon itself must connect.
                cpu.abilityT = 100f;
                player.spawnT = cpu.spawnT = 0f;
                float health = player.health;
                for (int frame = 0; frame < 300 && player.health == health; frame++)
                    g.tick(1f / 60f);
                assertTrue(Weapons.name(weapon) + " CPU did no damage with seed " + seed,
                        player.health < health);
            }
        }
    }

    @Test public void cpuMustDealDamageFromRealCountdownOnEveryMap() {
        for (int map = 0; map < Maps.ALL.length; map++) {
            for (long seed : new long[] { 3, 42, 100 }) {
                Game g = boot(0, map);
                g.debugSeed(seed);
                Fighter player = g.debugFighter(0), cpu = g.debugFighter(1);
                cpu.giveWeapon(Weapons.FIST);
                cpu.abilityT = 100f;
                player.maxHealth = player.health = 10000f;
                int jumps = 0;
                boolean wasGround = cpu.onGround;
                for (int frame = 0; frame < 900 && player.health == player.maxHealth; frame++) {
                    g.tick(1f / 60f);
                    if (wasGround && !cpu.onGround && cpu.vy < -500f) jumps++;
                    wasGround = cpu.onGround;
                    assertFalse(Maps.ALL[map].name + " CPU fell to its death, seed " + seed, cpu.dead);
                }
                assertTrue(Maps.ALL[map].name + " CPU approached but never landed a hit, seed " + seed,
                        player.health < player.maxHealth);
                assertTrue("excessive navigation jumps on " + Maps.ALL[map].name, jumps <= 3);
            }
        }
    }

    @Test public void everySpawnPairHasContinuousFloorAndNoCentralHazard() {
        for (Maps.Arena arena : Maps.ALL) {
            float floor = arena.spawnAy + Fighter.FEET;
            for (float x = arena.spawnAx - 40; x <= arena.spawnBx + 40; x += 8f) {
                boolean supported = false;
                for (Maps.Platform platform : arena.platforms) {
                    supported |= x >= platform.r.left && x <= platform.r.right
                            && Math.abs(platform.r.top - floor) < 1f;
                }
                assertTrue(arena.name + " has a hole in its fighting floor at " + x, supported);
                for (Maps.Hazard hazard : arena.hazards) {
                    assertFalse(arena.name + " puts a hazard across the central fighting route",
                            x > hazard.r.left && x < hazard.r.right);
                }
            }
        }
    }

    @Test public void completedCorpsesNeverReappearAtTheirDeathPosition() {
        Game g = boot(1, 0);
        Fighter f = g.debugFighter(1);
        f.kill(0);
        f.justDied = false;
        for (int i = 0; i < Ragdoll.N; i++) f.body.y[i] = 2000f;
        g.debugStepRagdolls(1f / 60f);
        assertNull("out-of-world corpse should be removed", f.body);
        for (int frame = 0; frame < 300; frame++) {
            g.debugStepRagdolls(1f / 60f);
            assertNull("completed corpse was recreated on frame " + frame, f.body);
        }
        assertTrue(f.dead);
    }

    @Test public void koCameraDoesNotChaseFallingCorpsesOrPumpZoom() {
        Game g = boot(1, 0);
        for (int i = 0; i < 190; i++) g.tick(1f / 60f);
        float[] before = g.debugCamera();
        Fighter f = g.debugFighter(1);
        f.kill(0);
        for (int i = 0; i < Ragdoll.N; i++) f.body.y[i] = 1800f;
        for (int frame = 0; frame < 100; frame++) {
            g.tick(1f / 60f);
            assertArrayEquals("K.O. must retain its last combat camera", before,
                    g.debugCamera(), 0.001f);
        }
        assertEquals(1, g.debugRoundWinsA());
        assertEquals(0, g.debugRoundWinsB());
    }

    @Test public void cameraKeepsSeparatedFightersClearOfHudAndThumbAreas() {
        for (int width : new int[] {960, 1280, 1600, 2160}) {
            Game g = boot(1, 0);
            g.resize(width, 720);
            g.debugStartFight();
            g.debugFighter(0).reset(160, 610);
            g.debugFighter(1).reset(1240, 610);
            for (int i = 0; i < 60; i++) g.tick(1f / 60f);
            float[] c = g.debugCamera();
            float vw = width / (720f / 540f);
            for (int i = 0; i < 2; i++) {
                Fighter f = g.debugFighter(i);
                float x = (f.x - c[0]) * c[2];
                float head = (f.y - Fighter.HEAD - c[1]) * c[2];
                float feet = (f.y + Fighter.FEET - c[1]) * c[2];
                assertTrue("fighter clipped horizontally at width " + width, x > 20 && x < vw - 20);
                assertTrue("fighter clipped by HUD", head >= 100);
                assertTrue("floor hidden by controls", feet <= 360);
            }
        }
    }

    @Test public void cpuComesDownFromSideLedgesToFinishTheFight() {
        // Regression: the CPU used to walk out onto a side ledge, then pace
        // above the opponent forever because a drop to the floor did not count
        // as "progress toward the foe". 31 of 120 seeded matches never ended.
        for (int map : new int[] { 0, 2, 6, 9 }) {
            for (long seed : new long[] { 7, 42 }) {
                Game g = boot(0, map);
                g.debugSeed(seed);
                g.debugStartFight();
                Fighter player = g.debugFighter(0);
                Fighter cpu = g.debugFighter(1);
                player.reset(Maps.ALL[map].spawnAx, Maps.ALL[map].spawnAy);
                Maps.Platform ledge = sideLedge(map);
                cpu.reset(ledge.r.centerX(), ledge.r.top - Fighter.FEET);
                // Fists force a real descent: a gunner could damage the player
                // without ever leaving the ledge.
                cpu.giveWeapon(Weapons.FIST);
                cpu.abilityT = 100f;
                player.spawnT = cpu.spawnT = 0f;
                player.maxHealth = player.health = 10000f;
                int frames = 0;
                while (frames < 60 * 12 && player.health == player.maxHealth) {
                    g.tick(1f / 60f);
                    frames++;
                }
                assertTrue(Maps.ALL[map].name + " CPU never descended from the ledge to engage, seed " + seed,
                        player.health < player.maxHealth);
                assertFalse(Maps.ALL[map].name + " CPU fell out of the world, seed " + seed, cpu.dead);
            }
        }
    }

    @Test public void gravityOffJumpReturnsToTheArena() {
        Game g = boot(1, 0);
        g.profile.gravity = false;
        g.debugStartFight();
        Fighter player = g.debugFighter(0);
        player.spawnT = 0f;
        player.onGround = false;
        player.vy = -850f; // a full jump impulse
        float startY = player.y;
        float peak = startY;
        for (int i = 0; i < 60 * 6; i++) {
            g.tick(1f / 60f);
            peak = Math.min(peak, player.y);
        }
        assertTrue("a gravity-off jump must not leave the top of the world",
                peak > Maps.ALL[0].bounds.top);
        assertTrue("a gravity-off jump must come back down", player.onGround);
        assertTrue("the fighter must not be stranded above the floor", player.y > startY - 5f);
    }

    @Test public void lightMeleeKnockbackMovesTheVictim() {
        // The walk controller used to cancel a fist's 250 px/s knockback in
        // about 11 px, so the weapon table's shove values were cosmetic.
        Game g = boot(1, 0);
        g.debugStartFight();
        Fighter player = g.debugFighter(0);
        player.spawnT = 0f;
        float x0 = player.x;
        player.damage(8f, 250f, -90f, false);
        for (int i = 0; i < 60; i++) g.tick(1f / 60f);
        assertTrue("a landed punch should visibly shove the victim",
                Math.abs(player.x - x0) > 18f);
    }

    @Test public void seededQuickMatchesReachAResultOnEveryArena() {
        for (int map : new int[] { 0, 2, 6, 7, 9 }) {
            for (long seed : new long[] { 7, 42 }) {
                Game g = boot(0, map);
                g.debugSeed(seed);
                int frames = 0;
                while (frames < 60 * 60 && g.debugScreen() != 6) {
                    g.tick(1f / 60f);
                    frames++;
                }
                assertEquals("the CPU must resolve the match on " + Maps.ALL[map].name
                        + " (seed " + seed + ")", 6, g.debugScreen());
            }
        }
    }

    /** Highest side ledge on an arena - the platform that used to trap the CPU. */
    private Maps.Platform sideLedge(int map) {
        Maps.Platform best = null;
        float floor = Maps.ALL[map].platforms.get(0).r.top;
        for (Maps.Platform p : Maps.ALL[map].platforms) {
            if (p.r.width() < 140f || p.r.top > floor - 80f) continue;
            if (best == null || p.r.top < best.r.top) best = p;
        }
        assertNotNull("arena must have a side ledge to test", best);
        return best;
    }

    /** Holds the shield through the real stick so the Game input path is covered. */
    @Test public void shieldAbsorbsSixSwordHits() {
        // At 1.8 shield points per point of damage a sword broke a full shield in
        // four hits and a bomb in two, so the only defensive move in the game was
        // a two-second novelty.
        Game g = boot(0, 0);
        g.debugStartFight();
        Fighter p = g.debugFighter(0);
        p.reset(700, 610);
        p.spawnT = 0f;
        p.maxHealth = p.health = 400f;
        p.giveWeapon(Weapons.SWORD);
        int hits = 0;
        for (int i = 0; i < 60 * 10 && !p.shieldBroken; i++) {
            p.input(0f, 1f, 0f, false, false, true);
            p.update(1f / 60f, Maps.ALL[0], true, true);
            if (p.shieldOn && p.hurtT <= 0f) {
                p.damage(17f, 0f, 0f, false);
                hits++;
            }
        }
        assertEquals("a sword should eat six shield hits, not four", 6, hits);
        assertTrue("the shield breaks at zero", p.shieldBroken);
    }

    @Test public void brokenShieldComesBackDuringTheRound() {
        // A broken shield used to be gone for the rest of the round: the
        // defensive option existed for the first five seconds and never again.
        Game g = boot(0, 0);
        g.debugStartFight();
        Fighter p = g.debugFighter(0);
        p.reset(700, 610);
        p.spawnT = 0f;
        p.maxHealth = p.health = 400f;
        p.shield = 0f;
        p.shieldBroken = true;
        p.shieldDelay = Fighter.SHIELD_REGEN_DELAY;
        int framesToRaise = -1;
        for (int i = 0; i < 60 * 10; i++) {
            p.input(0f, 1f, 0f, false, false, true);
            p.update(1f / 60f, Maps.ALL[0], true, true);
            if (p.shieldOn) { framesToRaise = i; break; }
        }
        assertTrue("a broken shield must be raisable again within 10 s", framesToRaise > 0);
        assertTrue("and not instantly", framesToRaise > 60);
        assertTrue("it comes back at the restore floor or better", p.shield >= p.maxShield * Fighter.SHIELD_RESTORE);
    }

    @Test public void shieldRechargesOnlyAfterAPause() {
        Game g = boot(0, 0);
        g.debugStartFight();
        Fighter p = g.debugFighter(0);
        p.reset(700, 610);
        p.spawnT = 0f;
        p.maxHealth = p.health = 400f;
        p.shield = 50f;
        p.shieldDelay = Fighter.SHIELD_REGEN_DELAY;
        // still inside the delay: no regeneration
        for (int i = 0; i < 30; i++) {
            p.input(0f, 1f, 0f, false, false, false);
            p.update(1f / 60f, Maps.ALL[0], true, true);
        }
        assertEquals("no regeneration during the delay", 50f, p.shield, 0.01f);
        for (int i = 0; i < 60 * 3; i++) {
            p.input(0f, 1f, 0f, false, false, false);
            p.update(1f / 60f, Maps.ALL[0], true, true);
        }
        assertTrue("it regenerates once the delay expires", p.shield > 55f);
    }

    @Test public void guardBreakStaggersLongerThanAHit() {
        // hurtT was assigned, not maxed, so the guard-break stun was flattened
        // back to the ordinary 0.22 s hit-stun the moment it was applied.
        Game g = boot(0, 0);
        g.debugStartFight();
        Fighter p = g.debugFighter(0);
        p.reset(700, 610);
        p.spawnT = 0f;
        p.maxHealth = p.health = 400f;
        for (int i = 0; i < 30; i++) {
            p.input(0f, 1f, 0f, false, false, true);
            p.update(1f / 60f, Maps.ALL[0], true, true);
        }
        p.shield = 1f;
        p.damage(17f, 0f, 0f, false);
        assertTrue("a guard break must cost more than a normal hit",
                p.hurtT > 0.30f);
    }

    @Test public void footballCannotRunForever() {
        // Football had no match clock: goals were the only way to end it and the
        // ball only moves when someone kicks it, so a defensive local game ran
        // indefinitely (measured: still running after 300 s).
        for (int mode : new int[] { 4, 5 }) {
            Game g = boot(mode, 0);
            g.debugStartFight();
            g.debugSetMatchClock(1.5f);
            for (int i = 0; i < 240 && g.debugScreen() != 6; i++) {
                g.tick(1f / 60f);
            }
            assertEquals("a football match must end when its clock expires (mode " + mode + ")",
                    6, g.debugScreen());
        }
    }

    @Test public void footballClockExpiryWithNoGoalsIsADraw() {
        Game g = boot(5, 0);
        g.debugStartFight();
        g.debugSetMatchClock(1.5f);
        for (int i = 0; i < 240 && g.debugScreen() != 6; i++) {
            g.tick(1f / 60f);
        }
        assertEquals("0-0 at full time", 0, g.debugRoundWinsA());
        assertEquals("0-0 at full time", 0, g.debugRoundWinsB());
        assertTrue("a scoreless match must be recorded as a draw", g.debugDrawn());
    }

    @Test public void duelRoundClockAwardsTheHealthierFighter() {
        // Rounds are K.O.-based, but a round where neither can finish the other
        // used to run forever. Time-out now awards the round on remaining health.
        Game g = boot(1, 0);
        g.debugStartFight();
        Fighter a = g.debugFighter(0), b = g.debugFighter(1);
        a.spawnT = b.spawnT = 0f;
        b.health = 40f;
        a.health = 90f;
        g.debugSetRoundClock(0.2f);
        for (int i = 0; i < 40; i++) {
            g.tick(1f / 60f);
        }
        assertEquals("the healthier fighter should take a timed-out round", 1, g.debugRoundWinsA());
        assertEquals("the other fighter gets nothing", 0, g.debugRoundWinsB());
    }

    @Test public void allDrawnRoundsCapTheMatch() {
        // A drawn round awards no point and replays. Two evenly matched idle
        // fighters therefore drew every round forever; MAX_ROUNDS ends it.
        Game g = boot(1, 0);
        g.debugStartFight();
        int frames = 0;
        while (g.debugScreen() != 6 && frames < 60 * 600) {
            g.tick(1f / 60f);
            frames++;
        }
        assertEquals("the match must eventually reach the result screen", 6, g.debugScreen());
        assertTrue("an all-draw duel is a drawn match", g.debugDrawn());
        assertEquals("a draw awards no round", 0, g.debugRoundWinsA() + g.debugRoundWinsB());
    }

    @Test public void repeatedRealRoundsReachResultWithoutDeathLoop() {
        Game g = boot(1, 0);
        for (int round = 1; round <= 2; round++) {
            g.debugStartFight();
            g.debugFighter(1).kill(0);
            for (int i = 0; i < 140; i++) g.tick(1f / 60f);
            assertEquals(round, g.debugRoundWinsA());
            if (round == 1) {
                assertFalse(g.debugFighter(1).dead);
                assertEquals(0, g.debugPhase());
            }
        }
        for (int i = 0; i < 100; i++) g.tick(1f / 60f);
        assertEquals(6, g.debugScreen());
        assertEquals(2, g.debugRoundWinsA());
    }
}
