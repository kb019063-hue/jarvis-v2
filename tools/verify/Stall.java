import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;
import java.util.Random;

public final class Stall {
    static Object field(Object o, String n) {
        try { Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static void call(Object o, String n, Class<?>[] t, Object... a) {
        try { Method m = o.getClass().getDeclaredMethod(n, t); m.setAccessible(true); m.invoke(o, a); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static Game game;
    static int screen() { return (Integer) field(game, "screen"); }
    static Game newGame() {
        Context ctx = new Context();
        Game.Host host = new Game.Host() {
            public void announce(String t) {} public void setBright(boolean on) {}
            public void vibrate(int ms) {} public void sfx(int id) {}
        };
        Game g = new Game(ctx, host);
        g.resize(1560, 720);
        return g;
    }
    public static void main(String[] a) {
        int seedFrom = 1, seedTo = 30;
        int total = 0, stalls = 0;
        for (int m = 0; m < Maps.ALL.length; m++) {
            int ms = 0, mt = 0;
            StringBuilder stalled = new StringBuilder();
            for (int s = seedFrom; s <= seedTo; s++) {
                game = newGame();
                ((Random) field(game, "rnd")).setSeed(s * 7919L + m);
                call(field(game, "cpu"), "seed", new Class<?>[]{long.class}, s * 104729L + m);
                call(game, "resetMatch", new Class<?>[]{int.class, int.class, boolean.class}, 0, m, true);
                Fighter[] fs = (Fighter[]) field(game, "fighters");
                int frames = 0;
                while (frames < 90 * 60 && screen() != 6) { game.tick(1f / 60f); frames++; }
                mt++; total++;
                if (screen() != 6) {
                    ms++; stalls++;
                    stalled.append(" seed=").append(s)
                           .append(" weapon=").append(Weapons.name(fs[1].weapon))
                           .append(" P(").append(Math.round(fs[0].x)).append(",").append(Math.round(fs[0].y)).append(" hp=").append(Math.round(fs[0].health)).append(")")
                           .append(" C(").append(Math.round(fs[1].x)).append(",").append(Math.round(fs[1].y)).append(" hp=").append(Math.round(fs[1].health)).append(" g=").append(fs[1].onGround).append(")");
                }
            }
            System.out.println((ms == 0 ? "OK   " : "STALL") + " " + pad(Maps.ALL[m].name) + " stalls=" + ms + "/" + mt + stalled);
        }
        System.out.println("TOTAL stalls=" + stalls + "/" + total);
    }
    static String pad(String s) { while (s.length() < 14) s += " "; return s; }
}
