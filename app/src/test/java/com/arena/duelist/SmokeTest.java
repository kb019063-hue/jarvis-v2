package com.arena.duelist;

import static org.junit.Assert.assertEquals;
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
        game = new Game(ctx, new FakeHost());
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
        // 1P layout at 1600x720: stick left, buttons right
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
        tap(1020, 330);       // equip/buy a weapon
        shot("12-arsenal-after-tap");

        boot();
        tap(1020, 434);       // SETTINGS
        shot("13-settings");
        tap(200, 147);        // flip gravity
        tap(200, 209);        // flip instant KO
        tap(320, 399);        // CPU difficulty cycles

        boot();
        tap(1020, 382);       // CUSTOMIZE
        tap(282, 150);        // pick a body colour
        tap(362, 284);        // pick hair
        tap(186, 284);        // spiky hair
        shot("14-customize");

        boot();
        tap(786, 434);        // "?" how to play
        shot("15-how-to-play");
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
            Ragdoll body = game.debugFighter(1).ragdoll;
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
