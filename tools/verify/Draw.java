import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Draw allocation probe.
 *
 * Renders real frames headlessly and counts Path / RectF / Paint objects the
 * draw path creates, using counters compiled into the stub graphics classes.
 * Before 0.3.8 every frame allocated a blade path, hair path, arc RectF and
 * arc paint per fighter, a Painter plus its three Paints, a Btn and RectF per
 * menu button, and a Path per background hill or spike. On a phone that is
 * garbage on every frame and GC pauses in the middle of a fight.
 *
 * Reported per scenario, as objects created per drawn frame.
 */
public final class Draw {
    static int checks = 0, fails = 0;
    static void check(String n, boolean ok, String d) {
        checks++; if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + n + (d.isEmpty() ? "" : "  [" + d + "]"));
    }
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }

    static Game g;
    static Method draw;
    static Object canvas;
    static int[] marks = new int[3];

    static void mark() {
        marks[0] = android.graphics.Path.ALLOCATED;
        marks[1] = android.graphics.RectF.ALLOCATED;
        marks[2] = android.graphics.Paint.ALLOCATED;
    }

    /** Runs `frames` of tick+draw and prints what that cost. */
    static void measure(String label, int frames, boolean simulate) throws Exception {
        for (int i = 0; i < 240; i++) {            // warm up before counting
            if (simulate) g.tick(1f / 60f);
            draw.invoke(g, canvas);
        }
        mark();
        long t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            if (simulate) g.tick(1f / 60f);
            draw.invoke(g, canvas);
        }
        long ms = (System.nanoTime() - t0) / 1000000L;
        double paths = (android.graphics.Path.ALLOCATED - marks[0]) / (double) frames;
        double rects = (android.graphics.RectF.ALLOCATED - marks[1]) / (double) frames;
        double paints = (android.graphics.Paint.ALLOCATED - marks[2]) / (double) frames;
        lastPerFrame = paths + rects + paints;
        System.out.printf("%-26s Path %6.2f  RectF %6.2f  Paint %6.2f  -> %6.2f objects/frame  (%.3f ms/frame)%n",
                label, paths, rects, paints, lastPerFrame, (double) ms / frames);
    }

    static double lastPerFrame;

    public static void main(String[] a) throws Exception {
        g = new Game(new Context(), new Game.Host() {
            public void announce(String t) {} public void setBright(boolean on) {}
            public void vibrate(int ms) {} public void sfx(int id) {} });
        g.resize(1600, 720);
        Class<?> canvasCls = Class.forName("android.graphics.Canvas");
        canvas = canvasCls.getDeclaredConstructor().newInstance();
        draw = g.getClass().getDeclaredMethod("draw", canvasCls);
        draw.setAccessible(true);

        measure("title screen", 2000, false);

        call(g, "debugStartMatch", new Class<?>[]{int.class, int.class}, 1, 2);
        call(g, "debugStartFight", new Class<?>[] {});
        measure("local duel, Dojo Night", 3000, true);
        double duelAlloc = lastPerFrame;

        int spikeMap = -1;
        for (int i = 0; i < Maps.ALL.length; i++) {
            for (Object hz : Maps.ALL[i].hazards) {
                if (((Maps.Hazard) hz).type == Maps.HAZ_SPIKES) { spikeMap = i; break; }
            }
            if (spikeMap >= 0) break;
        }
        if (spikeMap >= 0) {
            call(g, "debugStartMatch", new Class<?>[]{int.class, int.class}, 1, spikeMap);
            call(g, "debugStartFight", new Class<?>[] {});
            measure("duel, spike arena", 2000, true);
        }

        // Menu hitboxes are pooled now: prove a tap still lands after many draws.
        Game menu = new Game(new Context(), new Game.Host() {
            public void announce(String t) {} public void setBright(boolean on) {}
            public void vibrate(int ms) {} public void sfx(int id) {} });
        menu.resize(1600, 720);
        Method mdraw = menu.getClass().getDeclaredMethod("draw", canvasCls);
        mdraw.setAccessible(true);
        for (int i = 0; i < 40; i++) mdraw.invoke(menu, canvas);
        float k = 720f / 540f;
        menu.pointerDown(9, 1020f * k, 154f * k);
        menu.pointerUp(9, 1020f * k, 154f * k);
        menu.tick(1f / 60f);
        mdraw.invoke(menu, canvas);
        int afterTap = (Integer) invoke(menu, "debugScreen", new Class<?>[] {});
        menu.pointerDown(9, 210f * k, 166f * k);
        menu.pointerUp(9, 210f * k, 166f * k);
        menu.tick(1f / 60f);
        mdraw.invoke(menu, canvas);
        int afterArena = (Integer) invoke(menu, "debugScreen", new Class<?>[] {});
        check("menu buttons live after 40 pooled draws", afterTap == 1 && afterArena == 5,
                "screen " + afterTap + " then " + afterArena);

        check("the match draw path allocates nothing", duelAlloc == 0,
                String.format("%.2f objects/frame", duelAlloc));
        System.out.println("checks=" + checks + " failures=" + fails);
    }
}
