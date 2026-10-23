package com.arena.duelist;

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

/**
 * Smoke tests: drive real matches through the engine while rendering every
 * frame to a Bitmap. Catches crashes in combat, round flow, menus and drawing.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, manifest = Config.NONE)
public class SmokeTest {

    private static final int SCREEN_W = 1600;
    private static final int SCREEN_H = 720;

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

    private Game newGame() {
        Context ctx = RuntimeEnvironment.getApplication();
        Game g = new Game(ctx, new FakeHost());
        g.resize(SCREEN_W, SCREEN_H);
        return g;
    }

    private void render(Game g, Canvas c) {
        g.draw(c);
    }

    @Test
    public void playsAQuickMatchToTheEnd() {
        Game game = newGame();
        Canvas canvas = new Canvas(Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888));

        // title screen renders
        render(game, canvas);

        game.debugStartMatch(0, 3);
        // control positions for 1P layout (scale 1.333): stick, attack, jump
        game.pointerDown(1, 130, 600);
        game.pointerMove(1, 260, 600);
        game.pointerDown(2, 1290, 621);

        for (int frame = 0; frame < 3600; frame++) {
            if (frame % 4 == 0) {
                render(game, canvas);
            }
            game.tick(1f / 60f);

            // tap jump now and then
            if (frame % 180 == 90) {
                game.pointerDown(3, 1272, 483);
                game.pointerUp(3, 1272, 483);
            }

            // force decisive hits so rounds actually end
            if (frame == 420 || frame == 1500 || frame == 2400) {
                game.debugFighter(1).kill(0);
            }
            if (game.debugScreen() == 6) {
                render(game, canvas);
                break;
            }
        }
        assertTrue("match should reach the result screen", game.debugScreen() == 6);
    }

    @Test
    public void localTwoPlayerAndSurvivalRun() {
        Game game = newGame();
        Canvas canvas = new Canvas(Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888));

        game.debugStartMatch(1, 0);
        game.pointerDown(1, 130, 600);
        game.pointerDown(2, 1470, 600);
        for (int i = 0; i < 900; i++) {
            if (i % 5 == 0) {
                render(game, canvas);
            }
            game.tick(1f / 60f);
        }

        game.debugStartMatch(2, 6);
        for (int i = 0; i < 2400; i++) {
            if (i % 5 == 0) {
                render(game, canvas);
            }
            game.tick(1f / 60f);
            if (i % 600 == 300) {
                game.debugFighter(1).kill(0);
            }
        }
        render(game, canvas);
    }

    @Test
    public void everyArenaAndWeaponRenders() {
        Game game = newGame();
        Canvas canvas = new Canvas(Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888));
        for (int m = 0; m < Maps.ALL.length; m++) {
            game.debugStartMatch(0, m);
            for (int w = 0; w < Weapons.COUNT; w++) {
                game.debugFighter(0).giveWeapon(w);
                game.debugFighter(1).giveWeapon((w + 3) % Weapons.COUNT);
                for (int i = 0; i < 30; i++) {
                    game.tick(1f / 60f);
                }
                render(game, canvas);
            }
        }
    }
}
