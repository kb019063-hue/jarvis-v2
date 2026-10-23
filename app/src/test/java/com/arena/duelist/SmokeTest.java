package com.arena.duelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Smoke tests: drive real matches through the engine while rendering every
 * frame to a Bitmap. Catches crashes in combat, round flow, menus and drawing,
 * and exports PNG screenshots for the repo.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, manifest = Config.NONE)
public class SmokeTest {

    private static final int SCREEN_W = 1600;
    private static final int SCREEN_H = 720;
    private static final int RESULT_SCREEN = 6;

    private Bitmap bmp;
    private Canvas canvas;
    private Game game;
    private FakeHost host;

    private static final class FakeHost implements Game.Host {
        int announcements;

        @Override
        public void announce(String text) {
            announcements++;
        }

        @Override
        public void setBright(boolean on) {
        }

        @Override
        public void vibrate(int ms) {
        }

        @Override
        public void sfx(int id) {
        }
    }

    private void boot() {
        Context ctx = RuntimeEnvironment.getApplication();
        host = new FakeHost();
        game = new Game(ctx, host);
        game.resize(SCREEN_W, SCREEN_H);
        bmp = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bmp);
        game.draw(canvas);
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            game.tick(1f / 60f);
            game.draw(canvas);
        }
    }

    private int shotsWritten;

    private void shot(String name) {
        game.draw(canvas);
        File dir = new File(System.getProperty("screenshot.dir", "build/screenshots"));
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                throw new java.io.IOException("cannot create " + dir.getAbsolutePath());
            }
            File target = new File(dir, name + ".png");
            FileOutputStream out = new FileOutputStream(target);
            boolean ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            if (!ok || target.length() == 0L) {
                throw new java.io.IOException("compress produced no data: " + target);
            }
            shotsWritten++;
        } catch (Exception e) {
            throw new RuntimeException("screenshot '" + name + "' failed in "
                    + dir.getAbsolutePath(), e);
        }
    }

    @Test
    public void menuAndQuickMatch() {
        boot();
        shot("01-title");

        game.debugStartMatch(0, 3);
        // 1P layout at 1600x720: one floating movement/weapon stick on the left
        game.pointerDown(1, 140, 600);
        game.pointerMove(1, 280, 600);
        game.pointerDown(2, 1290, 621);

        step(120);
        assertEquals("match should be running", 5, game.debugScreen());
        shot("02-quick-fight");

        for (int frame = 0; frame < 3600; frame++) {
            game.tick(1f / 60f);
            if (frame % 3 == 0) {
                game.draw(canvas);
            }
            if (frame == 200 || frame == 900) {
                game.debugFighter(1).kill(0);
            }
            if (frame == 260) {
                shot("03-hit-and-hud");
            }
            if (game.debugScreen() == RESULT_SCREEN) {
                break;
            }
        }
        assertEquals("quick match should reach the result screen", RESULT_SCREEN, game.debugScreen());
        shot("04-result");
        assertTrue("screenshots should be written", shotsWritten >= 4);
    }

    @Test
    public void localDuelAndSurvival() {
        boot();
        game.debugStartMatch(1, 5);
        game.pointerDown(1, 140, 600);
        game.pointerDown(2, 1460, 600);
        step(150);
        shot("05-local-duel");

        game.debugStartMatch(2, 6);
        step(160);
        shot("06-survival");

        for (int i = 0; i < 2400; i++) {
            game.tick(1f / 60f);
            if (i % 4 == 0) {
                game.draw(canvas);
            }
            if (i % 700 == 350) {
                game.debugFighter(1).kill(0);
            }
        }
        shot("07-survival-wave");
        assertTrue("screenshots should be written", shotsWritten >= 3);
    }

    private void tap(float vx, float vy) {
        float k = SCREEN_H / 540f;
        game.pointerDown(9, vx * k, vy * k);
        game.pointerUp(9, vx * k, vy * k);
        game.tick(1f / 60f);
        game.draw(canvas);
    }

    @Test
    public void menusAreReachable() {
        boot();
        // title -> QUICK FIGHT -> arena picker
        tap(1020, 154);
        shot("10-arena-picker");
        // pick first arena
        tap(210, 166);
        assertEquals("arena pick starts a match", 5, game.debugScreen());

        boot();
        tap(1020, 330);       // ARSENAL
        shot("11-arsenal");
        // Sword costs 120; the player starts with 250 coins. The first tap must
        // only arm the purchase, the second must actually spend.
        int coinsBefore = game.profile.coins;
        tap(458, 166);
        shot("12-arsenal-after-tap");
        assertEquals("one tap must not spend coins", coinsBefore, game.profile.coins);
        assertTrue("one tap must not unlock", !game.profile.unlocked(1));
        tap(458, 166);
        assertEquals("second tap spends the price", coinsBefore - 120, game.profile.coins);
        assertTrue("second tap unlocks the sword", game.profile.unlocked(1));
        Profile reloaded = new Profile(RuntimeEnvironment.getApplication());
        assertEquals("newly bought starter must survive an app restart", 1, reloaded.starter);
        shot("20-arsenal-bought");

        boot();
        tap(1020, 434);       // SETTINGS
        shot("13-settings");
        tap(200, 147);        // flip gravity
        tap(200, 190);        // flip instant KO (row moved with the compact layout)
        tap(320, 399);        // CPU difficulty cycles

        boot();
        tap(1020, 382);       // CUSTOMIZE
        tap(282, 150);        // pick a body colour
        tap(362, 284);        // pick hair
        tap(186, 284);        // spiky hair
        shot("14-customize");

        boot();
        tap(826, 434);        // "?" how to play (was 786: six units outside the button)
        shot("15-how-to-play");
    }

    @Test
    public void androidBackClearsStaleMenuHitboxes() {
        boot();
        tap(1020, 330); // Arsenal
        int coins = game.profile.coins;
        assertTrue("Back should leave the arsenal", game.backPressed());
        assertEquals("Back should return to the title", 0, game.debugScreen());
        // Before the next draw, this used to hit the invisible Sword card and
        // arm a purchase while the player was already back on the title screen.
        tap(458, 166);
        assertEquals("stale arsenal hitboxes must not spend coins", coins, game.profile.coins);
        assertFalse("stale arsenal hitboxes must not unlock weapons", game.profile.unlocked(1));
        assertEquals("the stale tap must not navigate away from title", 0, game.debugScreen());
    }

    @Test
    public void koSpawnsARagdoll() {
        boot();
        game.debugStartMatch(0, 0);
        game.debugStartFight();
        // modest knockback so the body stays in frame for the screenshot
        game.debugFighter(1).kill(120);

        step(14);
        assertEquals("a KO should hand the body over to the ragdoll", 1,
                game.debugRagdollCount());
        shot("16-ragdoll-ko");

        step(66);
        shot("17-ragdoll-settled");
        assertEquals("the corpse should still be on stage mid round-end", 1,
                game.debugRagdollCount());
    }

    @Test
    public void ragdollPhysicsSettlesAndStaysFinite() {
        Maps.Arena arena = Maps.ALL[0];
        Ragdoll body = new Ragdoll(300, 560, 1, 300f, -200f, 0xFFF2F2F2, 0);
        for (int i = 0; i < 300; i++) {
            body.update(1f / 60f, arena);
        }
        assertTrue("ragdoll should come to rest on the floor", body.quiet);
        for (int i = 0; i < Ragdoll.N; i++) {
            assertTrue("joint " + i + " went non-finite",
                    !Float.isNaN(body.x[i]) && !Float.isInfinite(body.x[i])
                            && !Float.isNaN(body.y[i]) && !Float.isInfinite(body.y[i]));
        }
        assertTrue("body should rest on a platform, not fall out of the arena",
                body.y[Ragdoll.FOOT_L] < arena.bounds.bottom
                        && body.y[Ragdoll.HEAD] < arena.bounds.bottom);
    }

    @Test
    public void standingBodyHoldsThePose() {
        boot();
        game.debugStartMatch(0, 0);
        game.debugStartFight();
        step(40); // settle
        Fighter cpu = game.debugFighter(1);
        assertTrue("the live fighter should be drawn from a simulated body",
                cpu.body != null);
        assertTrue("at rest the body must track the animated pose, was "
                + game.debugPoseError(1), game.debugPoseError(1) < 6f);
        shot("21-standing-body");
    }

    @Test
    public void everyHitFloppsThenRecovers() {
        boot();
        game.debugStartMatch(0, 0);
        game.debugStartFight();
        step(40);
        Fighter cpu = game.debugFighter(1);

        // a light jab must visibly disturb the body...
        cpu.damage(8f, 250f, -90f, false);
        float peak = 0f;
        for (int i = 0; i < 8; i++) {
            game.tick(1f / 60f);
            peak = Math.max(peak, game.debugPoseError(1));
        }
        assertTrue("a jab should make the body flop, peak was " + peak, peak > 12f);
        shot("22-hit-flop");

        // ...and the muscle must pull it back onto the pose
        step(40);
        assertTrue("the body should recover to its pose, was " + game.debugPoseError(1),
                game.debugPoseError(1) < 6f);
        assertTrue("limp must decay to zero", game.debugLimp(1) < 0.01f);
    }

    @Test
    public void heavyHitRagdollsThenAutoRecovers() {
        boot();
        game.debugStartMatch(0, 0);
        game.debugStartFight();
        Fighter cpu = game.debugFighter(1);

        // a hammer-class blow: 40 damage, 600 knockback
        cpu.damage(40f, 600f, -120f, false);
        assertTrue("a heavy hit should knock the fighter down", cpu.down);

        step(12);
        assertEquals("the live body should be driven by a ragdoll", 1,
                game.debugRagdollCount());
        shot("18-knockdown");

        int framesToRecover = 0;
        float maxDownT = 0f;
        while (cpu.down && framesToRecover < 300) {
            game.tick(1f / 60f);
            game.draw(canvas);
            framesToRecover++;
            maxDownT = Math.max(maxDownT, cpu.downT);
        }
        assertTrue("the fighter should have got back up (downT " + cpu.downT
                        + ", max " + maxDownT + ", body " + (cpu.body != null)
                        + ", dead " + cpu.dead + ", hp " + cpu.health + ")",
                !cpu.down);
        assertTrue("recovery should take under two seconds", framesToRecover < 120);
        assertEquals("the ragdoll is released on recovery", 0, game.debugRagdollCount());
        assertTrue("recovery must not kill the fighter", !cpu.dead && cpu.health > 0f);
        assertTrue("poise resets after getting up", cpu.poise == 0f);
        assertTrue("recovered fighters get brief invulnerability", cpu.iframes > 0f);

        // and that grace period must actually absorb a follow-up hit
        float healthAfter = cpu.health;
        cpu.damage(20f, 300f, -90f, false);
        assertEquals("i-frames should eat the follow-up hit", healthAfter, cpu.health, 0.001f);

        step(40);
        shot("19-recovered");
    }

    @Test
    public void poiseBreaksAfterFourJabs() {
        boot();
        game.debugStartMatch(0, 0);
        game.debugStartFight();
        Fighter cpu = game.debugFighter(1);
        float perHit = 8f * (1f + 250f / 500f); // fists: 8 damage, 250 knockback
        for (int i = 1; i <= 3; i++) {
            cpu.damage(8f, 250f, -90f, false);
            game.tick(1f / 60f);
            assertTrue("jab " + i + " should not knock down yet", !cpu.down);
            assertTrue("poise should build", Math.abs(cpu.poise - i * perHit) < 0.01f);
        }
        cpu.damage(8f, 250f, -90f, false);
        assertTrue("the fourth jab breaks poise", cpu.down);
    }

    @Test
    public void downedFighterIgnoresInput() {
        // A ragdoll is deterministic, so the same knockdown must land in exactly
        // the same place whether or not the player mashes the controls.
        boot();
        game.debugStartMatch(0, 0);
        game.debugStartFight();
        Fighter cpu = game.debugFighter(1);
        cpu.damage(40f, 600f, -120f, false);
        step(30);
        float controlX = cpu.x;
        assertTrue("control run should still be prone or just recovered", !cpu.dead);

        boot();
        game.debugStartMatch(0, 0);
        game.debugStartFight();
        cpu = game.debugFighter(1);
        cpu.damage(40f, 600f, -120f, false);
        for (int i = 0; i < 30; i++) {
            cpu.input(1f, 1f, -1f, true, true, true); // hold everything
            game.tick(1f / 60f);
            game.draw(canvas);
            assertTrue("a prone fighter must never attack", !cpu.attacking);
            assertTrue("a prone fighter must never shield", !cpu.shieldOn);
        }
        assertEquals("input must not move a downed fighter", controlX, cpu.x, 0.001f);
    }

    @Test
    public void ragdollIsFiniteInEveryArena() {
        boot();
        for (int m = 0; m < Maps.ALL.length; m++) {
            game.debugStartMatch(0, m);
            game.debugStartFight();
            game.debugFighter(1).kill(520);
            for (int i = 0; i < 150; i++) {
                game.tick(1f / 60f);
                game.draw(canvas);
            }
            Ragdoll body = game.debugFighter(1).body;
            if (body == null) {
                continue; // settled, faded out or dropped into a pit: all fine
            }
            for (int j = 0; j < Ragdoll.N; j++) {
                assertTrue("arena " + m + " joint " + j + " is non-finite",
                        !Float.isNaN(body.x[j]) && !Float.isInfinite(body.x[j])
                                && !Float.isNaN(body.y[j]) && !Float.isInfinite(body.y[j]));
            }
            assertTrue("arena " + m + " corpse escaped the arena",
                    body.y[Ragdoll.HIPS] < Maps.ALL[m].bounds.bottom + 400f);
        }
    }

    @Test
    public void ragdollTimingMatchesAcrossFrameRates() {
        Maps.Arena arena = Maps.ALL[0];
        Ragdoll at60 = new Ragdoll(300, 400, 1, 300f, -200f, 0xFFF2F2F2, 0);
        Ragdoll at30 = new Ragdoll(300, 400, 1, 300f, -200f, 0xFFF2F2F2, 0);
        for (int i = 0; i < 30; i++) {
            at60.update(1f / 60f, arena);
        }
        for (int i = 0; i < 15; i++) {
            at30.update(1f / 30f, arena);
        }
        for (int i = 0; i < Ragdoll.N; i++) {
            assertEquals("joint " + i + " x drifted with render rate",
                    at60.x[i], at30.x[i], 0.02f);
            assertEquals("joint " + i + " y drifted with render rate",
                    at60.y[i], at30.y[i], 0.02f);
        }
    }

    @Test
    public void cpuRetreatsFromLavaEvenBetweenDecisions() {
        Cpu cpu = new Cpu();
        Fighter me = new Fighter(1, "CPU", 380f, 630f, true);
        Fighter foe = new Fighter(0, "P1", 1120f, 630f, false);
        me.onGround = true;
        foe.onGround = true;
        // Prime a positive decision timer on a hazard-free map. Hazard avoidance
        // must still run immediately on the next call, not wait for AI thinking.
        cpu.think(me, foe, Maps.ALL[3], 1f / 60f, 3);
        cpu.think(me, foe, Maps.ALL[2], 0.0001f, 3);
        assertTrue("CPU on the safe left lip must retreat, not jump into lava",
                cpu.move() < 0f);
        assertTrue("the avoidance move should include an evasive jump", cpu.jump());
    }

    @Test
    public void projectileLifetimeUsesSecondsNotFrames() {
        Projectile at60 = new Projectile(Weapons.FIST + 4, 0, 0, 0, 0, 0);
        Projectile at30 = new Projectile(Weapons.FIST + 4, 0, 0, 0, 0, 0);
        for (int i = 0; i < 120; i++) {
            at60.update(1f / 60f);
        }
        for (int i = 0; i < 60; i++) {
            at30.update(1f / 30f);
        }
        assertEquals("same two seconds must consume the same lifetime",
                at60.life, at30.life, 0.001f);
        assertFalse(at60.dead);
        assertFalse(at30.dead);
        for (int i = 0; i < 150; i++) {
            at60.update(1f / 60f);
        }
        for (int i = 0; i < 75; i++) {
            at30.update(1f / 30f);
        }
        assertTrue("4.5 seconds at 60 FPS should expire", at60.dead);
        assertTrue("4.5 seconds at 30 FPS should expire", at30.dead);
    }

    @Test
    public void doubleKoReplaysInsteadOfTiedDefeat() {
        boot();
        game.debugStartMatch(0, 3);
        game.debugStartFight();
        game.debugSetRoundWins(1, 1);
        game.debugFighter(0).kill(0);
        game.debugFighter(1).kill(0);
        game.tick(1f / 60f);
        assertEquals("double K.O. enters round end", 2, game.debugPhase());
        assertEquals(1, game.debugRoundWinsA());
        assertEquals(1, game.debugRoundWinsB());
        step(150);
        assertEquals("a drawn round must replay, not end 2-2", 0, game.debugPhase());
        assertEquals("match is still running", 5, game.debugScreen());
        assertEquals(1, game.debugRoundWinsA());
        assertEquals(1, game.debugRoundWinsB());
    }

    @Test
    public void survivalMutualKoDoesNotAwardPhantomWave() {
        boot();
        game.debugStartMatch(2, 3);
        game.debugStartFight();
        int coins = game.profile.coins;
        game.debugFighter(0).kill(0);
        game.debugFighter(1).kill(0);
        game.tick(1f / 60f);
        assertEquals("mutual K.O. ends survival immediately", 3, game.debugPhase());
        assertEquals("wave must not advance after the player died", 1, game.debugWave());
        assertEquals("only the defeat reward is granted", coins + 15, game.profile.coins);
    }

    @Test
    public void countdownLocksInputAndClearsStaleMotion() {
        boot();
        game.debugStartMatch(1, 3);
        Fighter p = game.debugFighter(0);
        p.moveX = 1f;
        p.attackQueued = true;
        p.jumpQueued = true;
        step(70); // spawn protection is over, but the countdown is not
        float x = p.x;
        assertEquals("stale movement is cleared", 0f, p.moveX, 0.001f);
        game.pointerDown(1, 140, 600);
        game.pointerMove(1, 280, 600);
        game.pointerDown(2, 1290, 621); // old ATK location must do nothing too
        step(10);
        assertEquals("countdown cannot move a fighter", x, p.x, 0.001f);
        assertFalse("countdown cannot start an attack", p.attacking);
        assertFalse("countdown cannot queue a jump", p.jumpQueued);
        assertEquals("countdown cannot pre-fire a shot", 0, game.debugShotCount());
    }

    @Test
    public void voiceOffSilencesReadyAndUiAnnouncements() {
        boot();
        game.profile.voice = false;
        host.announcements = 0;
        tap(1020, 330); // Arsenal
        tap(175, 166);  // equip Fists: normally announces the weapon name
        assertEquals("voice-off must suppress UI announcements", 0, host.announcements);
        game.debugStartMatch(0, 0);
        assertEquals("voice-off must suppress Ready", 0, host.announcements);
    }

    @Test
    public void touchReleaseTriggersWeaponSkill() {
        boot();
        game.debugStartMatch(1, 3);
        game.debugStartFight();
        Fighter p = game.debugFighter(0);
        p.spawnT = 0f;
        p.giveWeapon(1);
        p.abilityT = 0f;
        game.pointerDown(1, 140, 600);
        game.pointerMove(1, 280, 600);
        game.pointerUp(1, 280, 600);
        assertTrue("release should start the sword dash", p.attacking);
        assertTrue("release should start the skill cooldown", p.abilityT > 0f);
    }

    @Test
    public void everyWeaponHasARealReleaseSkill() {
        int[] expectedShots = {0, 0, 0, 0, 3, 0, 3, 5, 3, 2, 3, 1};
        boot();
        for (int w = 0; w < Weapons.COUNT; w++) {
            game.debugStartMatch(1, 3);
            game.debugStartFight();
            Fighter p = game.debugFighter(0);
            Fighter foe = game.debugFighter(1);
            p.spawnT = 0f;
            p.giveWeapon(w);
            p.abilityT = 0f;
            p.x = 500f;
            p.y = 590f;
            foe.x = 560f;
            foe.y = 590f;
            float foeHealth = foe.health;
            assertTrue("skill should fire for " + Weapons.name(w),
                    game.debugUseAbility(0, 1f, 0f));
            assertTrue("skill should start cooldown for " + Weapons.name(w), p.abilityT > 0f);
            assertEquals("projectile skill count for " + Weapons.name(w),
                    expectedShots[w], game.debugShotCount());
            if (w == 3 || w == 5) {
                assertTrue("shockwave should damage for " + Weapons.name(w), foe.health < foeHealth);
            }
            if (w == 0 || w == 1 || w == 2 || w == 11) {
                assertTrue("dash skill should swing for " + Weapons.name(w), p.attacking);
            }
        }
    }

    @Test
    public void meleeContactFollowsWeaponDirection() {
        boot();
        game.debugStartMatch(1, 3);
        game.debugStartFight();
        step(40);
        Fighter p = game.debugFighter(0);
        Fighter foe = game.debugFighter(1);
        p.spawnT = 0f;
        foe.spawnT = 0f;
        p.giveWeapon(1);
        game.pointerDown(1, 140, 600);
        game.pointerMove(1, 140, 550); // up, but below the jump threshold

        p.x = 500f;
        p.y = 590f;
        p.vx = 0f;
        p.vy = 0f;
        p.onGround = true;
        p.body = null;
        foe.x = 500f;
        foe.y = 500f;
        foe.vx = 0f;
        foe.vy = 0f;
        foe.onGround = false;
        foe.body = null;
        float health = foe.health;
        for (int i = 0; i < 30 && foe.health == health; i++) {
            game.tick(1f / 60f);
        }
        assertTrue("an upward sword should contact a fighter above", foe.health < health);
    }

    @Test
    public void everyArenaAndWeaponRenders() {
        boot();
        for (int m = 0; m < Maps.ALL.length; m++) {
            game.debugStartMatch(0, m);
            for (int w = 0; w < Weapons.COUNT; w++) {
                game.debugFighter(0).giveWeapon(w);
                game.debugFighter(1).giveWeapon((w + 3) % Weapons.COUNT);
                for (int i = 0; i < 24; i++) {
                    game.tick(1f / 60f);
                    game.draw(canvas);
                }
                if (m == 2 && w == 5) {
                    shot("08-lava-core");
                }
                if (m == 6 && w == 7) {
                    shot("09-orbital-deck");
                }
            }
        }
        // menus too
        game.pointerDown(1, 140, 600);
        game.pointerUp(1, 140, 600);
        game.tick(1f / 60f);
        game.draw(canvas);
        assertTrue("arena screenshots should be written", shotsWritten >= 2);
    }
}
