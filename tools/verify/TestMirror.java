import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

public final class TestMirror {
    static int checks=0, fails=0;
    static void check(String n, boolean ok, String d) { checks++; if(!ok) fails++;
        System.out.println((ok?"PASS ":"FAIL ")+n+(d.isEmpty()?"":"  ["+d+"]")); }
    static Object field(Object o, String n) { try { Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o);} catch(Exception e){throw new RuntimeException(e);} }
    static void call(Object o, String n, Class<?>[] t, Object... a) { try { Method m=o.getClass().getDeclaredMethod(n,t); m.setAccessible(true); m.invoke(o,a);} catch(Exception e){throw new RuntimeException(e);} }
    static Game boot(int mode, int map) {
        Context ctx = new Context();
        Game g = new Game(ctx, new Game.Host() {
            public void announce(String t) {} public void setBright(boolean on) {}
            public void vibrate(int ms) {} public void sfx(int id) {}
        });
        g.resize(1600, 720);
        call(g, "debugSeed", new Class<?>[]{long.class}, 42L);
        call(g, "debugStartMatch", new Class<?>[]{int.class,int.class}, mode, map);
        call(g, "debugSuppressPickups", new Class<?>[]{});
        return g;
    }
    static Fighter f(Game g, int i) { return (Fighter) invoke(g,"debugFighter",new Class<?>[]{int.class}, i); }
    static Maps.Platform sideLedge(int map) {
        Maps.Platform best=null; float floor=Maps.ALL[map].platforms.get(0).r.top;
        for (Maps.Platform p : Maps.ALL[map].platforms) {
            if (p.r.width() < 140f || p.r.top > floor-80f) continue;
            if (best==null || p.r.top < best.r.top) best=p;
        }
        return best;
    }
    static public void main(String[] a) {
        // 1. CPU descends from a side ledge
        for (int map : new int[]{0,2,6,9}) for (long seed : new long[]{7,42}) {
            Game g = boot(0,map); call(g,"debugSeed",new Class<?>[]{long.class},seed);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0), c=f(g,1);
            p.reset(Maps.ALL[map].spawnAx, Maps.ALL[map].spawnAy);
            Maps.Platform ledge = sideLedge(map);
            c.reset(ledge.r.centerX(), ledge.r.top - Fighter.FEET);
            c.giveWeapon(Weapons.FIST); c.abilityT=100f;
            p.spawnT=c.spawnT=0f; p.maxHealth=p.health=10000f;
            int fr=0; while(fr<60*12 && p.health==p.maxHealth){ g.tick(1f/60f); fr++; }
            check("ledge descent "+Maps.ALL[map].name+" seed="+seed, p.health<p.maxHealth && !c.dead,
                  "damaged="+(p.health<p.maxHealth)+" t="+(fr/60f)+"s cpuDead="+c.dead+" cpuY="+Math.round(c.y)+" weapon="+Weapons.name(c.weapon));
        }
        // 2. gravity-off jump returns
        {
            Game g=boot(1,0); g.profile.gravity=false; call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.spawnT=0f; p.onGround=false; p.vy=-850f;
            float start=p.y, peak=p.y;
            for(int i=0;i<360;i++){ g.tick(1f/60f); peak=Math.min(peak,p.y); }
            check("gravity-off jump returns", peak > Maps.ALL[0].bounds.top && p.onGround && p.y>start-5,
                "peak="+Math.round(peak)+" onGround="+p.onGround+" y="+Math.round(p.y));
        }
        // 3. light knockback
        {
            Game g=boot(1,0); call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.spawnT=0f; float x0=p.x;
            p.damage(8f,250f,-90f,false);
            for(int i=0;i<60;i++) g.tick(1f/60f);
            check("light knockback moves victim", Math.abs(p.x-x0)>18f, "moved="+Math.round(Math.abs(p.x-x0)));
        }
        // 4. seeded matches resolve on the previously stalling arenas
        for (int map : new int[]{0,2,6,7,9}) for (long seed : new long[]{7,42}) {
            Game g=boot(0,map); call(g,"debugSeed",new Class<?>[]{long.class},seed);
            int fr=0; while(fr<3600 && (Integer) invoke(g,"debugScreen",new Class<?>[]{}) != 6){ g.tick(1f/60f); fr++; }
            check("seeded resolve "+Maps.ALL[map].name+" seed="+seed,
                (Integer) invoke(g,"debugScreen",new Class<?>[]{})==6, "t="+(fr/60f)+"s");
        }
        // 5. changed gravity-off unit expectation
        {
            Fighter f = new Fighter(0,"TEST",300f,220f,false);
            f.spawnT=0f; f.onGround=false;
            f.update(1f/60f, Maps.ALL[0], false, false);
            check("gravity-off frame is gentle", f.vy>0f && f.vy<12f, "vy="+f.vy);
        }
        System.out.println("checks="+checks+" failures="+fails);
    }
    static Object invoke(Object o, String n, Class<?>[] t, Object... a) {
        try { Method m=o.getClass().getDeclaredMethod(n,t); m.setAccessible(true); return m.invoke(o,a); }
        catch(Exception e){ throw new RuntimeException(e); }
    }
}
