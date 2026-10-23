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
