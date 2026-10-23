package com.arena.duelist;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class FootballRegressionTest {
    private Game boot(int mode) {
        Game game = new Game(RuntimeEnvironment.getApplication(), new Game.Host() {
            public void announce(String text) { }
            public void setBright(boolean on) { }
            public void vibrate(int ms) { }
            public void sfx(int id) { }
        });
        game.resize(1600, 720);
        game.debugStartMatch(mode, 0);
        return game;
    }

    private Fighter[] players() {
        return new Fighter[] { new Fighter(0, "P1", 400, 560, false),
                new Fighter(1, "P2", 1000, 560, false) };
    }

    @Test public void goalsRequireWholeBallAndOnlyScoreOnce() {
        Football ball = new Football();
        ball.reset();
        ball.y = 560;
        ball.x = Football.WIDTH + Football.RADIUS - 1;
        assertEquals(-1, ball.update(1f / 240f, players()));
        ball.x += 2;
        assertEquals(0, ball.update(1f / 240f, players()));
        assertEquals(-1, ball.update(1f / 60f, players()));
        ball.reset();
        ball.x = -Football.RADIUS - 1;
        ball.y = 560;
        assertEquals(1, ball.update(1f / 240f, players()));
    }

    @Test public void shotsAboveGoalBounceInsteadOfScoring() {
        Football ball = new Football();
        ball.reset();
        ball.x = Football.WIDTH - 10;
        ball.y = Football.GOAL_TOP - 50;
        ball.vx = 1000;
        assertEquals(-1, ball.update(1f / 60f, players()));
        assertTrue(ball.vx < 0);
        assertTrue(ball.x <= Football.WIDTH - Football.RADIUS);
    }

    @Test public void powerKickNeedsProximityAndARealDirection() {
        Football ball = new Football();
        ball.reset();
        Fighter f = players()[0];
        assertFalse(ball.powerKick(f, 1, 0));
        ball.x = f.x + 60;
        ball.y = f.y;
        assertFalse(ball.powerKick(f, 0, 0));
        assertTrue(ball.powerKick(f, 1, -0.3f));
        assertTrue(ball.vx > 1000);
        assertTrue(ball.vy < 0);
    }

    @Test public void footballScoresThreeGoalsAndAwardsOneMatchReward() {
        Game g = boot(5);
        int coins = g.profile.coins;
        for (int goal = 1; goal <= 3; goal++) {
            g.debugStartFight();
            Football ball = g.debugFootball();
            ball.x = Football.WIDTH + Football.RADIUS + 2;
            ball.y = 560;
            ball.vx = ball.vy = 0;
            g.tick(1f / 60f);
            assertEquals(goal, g.debugRoundWinsA());
            assertEquals(2, g.debugPhase());
            // Goal celebration must not award the same goal repeatedly.
            for (int i = 0; i < 105; i++) g.tick(1f / 60f);
            assertEquals(goal, g.debugRoundWinsA());
            if (goal < 3) {
                assertEquals(0, g.debugPhase());
                assertEquals(Football.WIDTH / 2f, ball.x, 0.01f);
            }
        }
        assertEquals(3, g.debugPhase());
        assertEquals(1, g.profile.wins);
        assertEquals(coins + 70, g.profile.coins);
        for (int i = 0; i < 100; i++) g.tick(1f / 60f);
        assertEquals(6, g.debugScreen());
        assertEquals(coins + 70, g.profile.coins);
    }

    @Test public void cpuGoalCountsForOpponentAndThreeGoalsIsLoss() {
        Game g = boot(4);
        for (int i = 0; i < 3; i++) {
            g.debugStartFight();
            Football ball = g.debugFootball();
            ball.x = -Football.RADIUS - 2;
            ball.y = 560;
            ball.vx = ball.vy = 0;
            g.tick(1f / 60f);
            for (int j = 0; j < 105; j++) g.tick(1f / 60f);
        }
        assertEquals(0, g.debugRoundWinsA());
        assertEquals(3, g.debugRoundWinsB());
        assertEquals(1, g.profile.losses);
    }

    @Test public void countdownAndPauseFreezeBallAndRejectPowerKick() {
        Game g = boot(5);
        Football ball = g.debugFootball();
        float y = ball.y;
        g.tick(1f / 60f);
        assertEquals(y, ball.y, 0f);
        g.debugStartFight();
        g.suspend();
        assertTrue(g.debugPaused());
        g.tick(1f / 60f);
        assertEquals(y, ball.y, 0f);
        assertEquals(0f, g.debugFighter(0).moveX, 0f);
    }

    @Test public void footballCannotDamagePlayersOrSpawnWeapons() {
        Game g = boot(4);
        g.debugStartFight();
        for (int i = 0; i < 900; i++) g.tick(1f / 60f);
        assertEquals(100f, g.debugFighter(0).health, 0f);
        assertEquals(100f, g.debugFighter(1).health, 0f);
        assertEquals(0, g.debugShotCount());
        assertFalse(g.debugFighter(0).dead);
        assertFalse(g.debugFighter(1).dead);
    }

    @Test public void ballStaysFiniteAndSimilarAcrossTimesteps() {
        Football a = new Football(), b = new Football();
        a.reset(); b.reset();
        a.vx = b.vx = 240;
        // Players far from the flight path isolate integration from contact events.
        Fighter[] f = players();
        f[0].x = f[1].x = -500;
        for (int i = 0; i < 30; i++) a.update(1f / 30f, f);
        for (int i = 0; i < 60; i++) b.update(1f / 60f, f);
        assertTrue(Float.isFinite(a.x) && Float.isFinite(a.y));
        assertEquals(a.x, b.x, 0.1f);
        assertEquals(a.y, b.y, 0.1f);
    }

    private Game shotScene(float targetX) {
        Game g = boot(1);
        g.debugStartFight();
        g.debugFighter(1).reset(targetX, 500);
        g.debugFighter(1).spawnT = 0;
        g.debugFighter(1).body = null;
        return g;
    }

    @Test public void projectileHitsFighterBeforeLaterWall() {
        Game g = shotScene(120);
        Maps.Arena arena = new Maps.Arena("Test", 0, 0, 0, 0, 0,
                1400, 820, 400, 1000, 500).add(150, 440, 10, 120, 0);
        g.debugSetArena(arena);
        Projectile p = new Projectile(Weapons.PISTOL, 0, 50, 494, 6000, 0);
        g.debugAddShot(p);
        g.debugUpdateShots(1f / 30f);
        assertTrue(g.debugFighter(1).health < 100);
        assertTrue(p.x < 150);
    }

    @Test public void wallBeforeFighterBlocksShotAndNearestWallWins() {
        Game g = shotScene(240);
        Maps.Arena arena = new Maps.Arena("Test", 0, 0, 0, 0, 0,
                1400, 820, 400, 1000, 500)
                .add(180, 440, 10, 120, 0).add(100, 440, 10, 120, 0);
        g.debugSetArena(arena);
        Projectile p = new Projectile(Weapons.PISTOL, 0, 50, 494, 6000, 0);
        g.debugAddShot(p);
        g.debugUpdateShots(1f / 30f);
        assertEquals(100f, g.debugFighter(1).health, 0f);
        assertEquals(100f - p.r, p.x, 0.01f);
    }
}
