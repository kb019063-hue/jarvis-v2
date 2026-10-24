import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

public final class Harness {
    static int checks = 0, fails = 0;
    static void check(String name, boolean ok, String detail) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
    }

    static Object field(Object o, String n) {
        try {
            Field f = o.getClass().getDeclaredField(n);
            f.setAccessible(true);
            return f.get(o);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void set(Object o, String n, Object v) {
        try {
            Field f = o.getClass().getDeclaredField(n);
            f.setAccessible(true);
            f.set(o, v);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void call(Object o, String n, Class<?>[] types, Object... args) {
        try {
            Method m = o.getClass().getDeclaredMethod(n, types);
            m.setAccessible(true);
            m.invoke(o, args);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static Fighter[] fighters(Game g) { return (Fighter[]) field(g, "fighters"); }
    static int screen(Game g) { return (Integer) field(g, "screen"); }
    static int phase(Game g) { return (Integer) field(g, "phase"); }
    static int mode(Game g) { return (Integer) field(g, "mode"); }
    static Profile profile(Game g) { return g.profile; }

    static Game newGame() {
        Context ctx = new Context();
        Game.Host host = new Game.Host() {
            public void announce(String t) {}
            public void setBright(boolean on) {}
            public void vibrate(int ms) {}
            public void sfx(int id) {}
        };
        Game g = new Game(ctx, host);
        g.resize(1560, 720); // 16:9-ish landscape
        return g;
    }

    static final float DT = 1f / 60f;

    public static void main(String[] args) {
        System.out.println("=== Stick Duelist headless audit harness ===");

        // 1. Every arena resolves against an idle player (CPU must engage).
        for (int m = 0; m < Maps.ALL.length; m++) {
            Game g = newGame();
            profile(g).gravity = true;
            call(g, "resetMatch", new Class<?>[]{int.class, int.class, boolean.class}, 0, m, true);
            fighters(g)[0].name = "idle";
            int frames = 0;
            int limit = 90 * 60;
            while (frames < limit && screen(g) != 6) { g.tick(DT); frames++; }
            float px = fighters(g)[0].health;
            boolean resolved = screen(g) == 6;
            check("arena[" + Maps.ALL[m].name + "] resolves vs idle player",
                    resolved, "t=" + (frames / 60f) + "s playerHP=" + Math.round(px));
        }

        // 2. Zero-G: player jumps once, does the fighter leave the arena and does
        //    the match still resolve?
        {
            Game g = newGame();
            profile(g).gravity = false;
            call(g, "resetMatch", new Class<?>[]{int.class, int.class, boolean.class}, 0, 0, true);
            Fighter p = fighters(g)[0];
            float y0 = p.y;
            // press stick up to jump, then release
            g.pointerDown(1, 300f, 560f);   // stick A base area
            g.pointerMove(1, 300f, 430f);
            g.tick(DT);
            g.tick(DT);
            g.pointerUp(1, 300f, 430f);
            float maxUp = y0;
            for (int i = 0; i < 180; i++) { g.tick(DT); maxUp = Math.min(maxUp, p.y); }
            check("zero-G jump stays inside arena", p.y > -200f,
                    "y0=" + Math.round(y0) + " y_after3s=" + Math.round(p.y) + " minY=" + Math.round(maxUp));
            int frames = 0;
            while (frames < 90 * 60 && screen(g) != 6) { g.tick(DT); frames++; }
            check("zero-G match resolves", screen(g) == 6, "t=" + (frames / 60f) + "s screen=" + screen(g));
        }

        // 3. Knockback: how far does a standard 250 px/s hit actually move a
        //    grounded fighter? (The controller damps velocity every tick.)
        {
            Game g = newGame();
            call(g, "resetMatch", new Class<?>[]{int.class, int.class, boolean.class}, 0, 0, true);
            Fighter p = fighters(g)[0];
            p.damage(8f, 250f, -120f, false);
            float x0 = p.x;
            for (int i = 0; i < 60; i++) g.tick(DT);
            float moved = Math.abs(p.x - x0);
            check("knockback produces visible displacement", moved > 40f,
                    "moved=" + Math.round(moved) + "px from a 250px/s hit");
        }

        // 4. Hitstun lock: can a player holding a direction ever move while the
        //    CPU keeps attacking?
        {
            Game g = newGame();
            profile(g).difficulty = 3;
            call(g, "resetMatch", new Class<?>[]{int.class, int.class, boolean.class}, 0, 0, true);
            Fighter p = fighters(g)[0], c = fighters(g)[1];
            // place them adjacent on the main floor
            c.x = p.x + 46f;
            c.y = p.y;
            // survive the intro
            int frames = 0;
            while (phase(g) == 0 && frames < 400) { g.tick(DT); frames++; }
            g.pointerDown(7, 300f, 560f);
            g.pointerMove(7, 420f, 560f); // hold right, toward the CPU
            int lockedFrames = 0, movingFrames = 0, hits = 0;
            float lastHealth = p.health;
            for (int i = 0; i < 60 * 8; i++) {
                g.tick(DT);
                if (p.health < lastHealth) { hits++; lastHealth = p.health; }
                if (p.dead) break;
                if (p.hurtT > 0f) lockedFrames++; else {
                    if (Math.abs(p.vx) > 30f) movingFrames++;
                }
            }
            check("player is not permanently hitlocked",
                    (p.dead ? lockedFrames : lockedFrames) < 60 * 6,
                    "hits=" + hits + " stunFrames=" + lockedFrames + " moveFrames=" + movingFrames
                            + " hp=" + Math.round(p.health) + " dead=" + p.dead);
        }

        // 5. Ability dash distance: release-skill should move the fighter.
        {
            Game g = newGame();
            call(g, "resetMatch", new Class<?>[]{int.class, int.class, boolean.class}, 0, 0, true);
            int frames = 0;
            while (phase(g) == 0 && frames < 400) { g.tick(DT); frames++; }
            Fighter p = fighters(g)[0];
            p.giveWeapon(Weapons.SWORD);
            p.abilityT = 0f;
            p.spawnT = 0f;
            p.hurtT = 0f;
            float x0 = p.x;
            // simulate the stick release that triggers the ability
            boolean used = (Boolean) invoke(g, "useAbility",
                    new Class<?>[]{Fighter.class, float.class, float.class}, p, 1f, 0f);
            for (int i = 0; i < 30; i++) g.tick(DT);
            float moved = Math.abs(p.x - x0);
            check("sword dash covers ground", used && moved > 80f, "used=" + used + " moved=" + Math.round(moved) + "px");
        }

        System.out.println("checks=" + checks + " failures=" + fails);
    }

    static Object invoke(Object o, String n, Class<?>[] t, Object... a) {
        try {
            Method m = o.getClass().getDeclaredMethod(n, t);
            m.setAccessible(true);
            return m.invoke(o, a);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
