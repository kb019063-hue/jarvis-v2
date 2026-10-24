import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;
import java.util.Random;

public final class Sweep {
    static Object field(Object o, String n) { try { Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o);} catch(Exception e){throw new RuntimeException(e);} }
    static void call(Object o, String n, Class<?>[] t, Object... a) { try { Method m=o.getClass().getDeclaredMethod(n,t); m.setAccessible(true); m.invoke(o,a);} catch(Exception e){throw new RuntimeException(e);} }
    static Game newGame() {
        Context ctx = new Context();
        Game.Host host = new Game.Host() {
            public void announce(String t) {} public void setBright(boolean on) {}
            public void vibrate(int ms) {} public void sfx(int id) {}
        };
        Game g = new Game(ctx, host); g.resize(1560,720); return g;
    }
    public static void main(String[] a) {
        int seeds = Integer.parseInt(System.getProperty("seeds", "30"));
        int stalls = 0, cpuDeaths = 0, total = 0, maxJumps = 0, sumJumps = 0;
        for (int m = 0; m < Maps.ALL.length; m++) {
            for (int s = 1; s <= seeds; s++) {
                Game g = newGame();
                ((Random) field(g,"rnd")).setSeed(s * 7919L + m);
                call(field(g,"cpu"), "seed", new Class<?>[]{long.class}, s * 104729L + m);
                call(g, "resetMatch", new Class<?>[]{int.class,int.class,boolean.class}, 0, m, true);
                Fighter[] fs = (Fighter[]) field(g,"fighters");
                int frames = 0, jumps = 0; boolean wasGround = true;
                while (frames < 90*60 && (Integer) field(g,"screen") != 6) {
                    g.tick(1f/60f); frames++;
                    boolean now = fs[1].onGround;
                    if (wasGround && !now && fs[1].vy < -500f) jumps++;
                    wasGround = now;
                }
                total++;
                if ((Integer) field(g,"screen") != 6) stalls++;
                if (fs[1].dead && !fs[0].dead) cpuDeaths++;
                maxJumps = Math.max(maxJumps, jumps); sumJumps += jumps;
            }
        }
        System.out.println("matches=" + total + " stalls=" + stalls + " cpuSelfDeaths=" + cpuDeaths
            + " jumpsMax=" + maxJumps + " jumpsAvg=" + (sumJumps / (float) total));
    }
}
