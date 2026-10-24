import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

public final class Measure {
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
    static void startLocal(Game g) {
        call(g, "resetMatch", new Class<?>[]{int.class,int.class,boolean.class}, 1, 0, true);
        int i = 0; while ((Integer) field(g,"phase") == 0 && i++ < 600) g.tick(1f/60f);
        call(g, "debugSuppressPickups", new Class<?>[]{});
    }
    public static void main(String[] a) {
        System.out.println("--- isolated knockback (local duel, idle opponent) ---");
        for (int w : new int[]{0, Weapons.SWORD, Weapons.HAMMER, Weapons.ENERGY_BLADE, Weapons.BOMB}) {
            Game g = newGame(); startLocal(g);
            Fighter p = ((Fighter[]) field(g,"fighters"))[0];
            float x0 = p.x;
            p.damage(Weapons.DEFS[w].damage, Weapons.DEFS[w].knock, -90f, false);
            for (int k=0;k<60;k++) g.tick(1f/60f);
            System.out.println("  " + pad(Weapons.name(w)) + " moved=" + Math.round(Math.abs(p.x-x0)) + "px (knock " + (int)Weapons.DEFS[w].knock + ")");
        }
        System.out.println("--- isolated release-skill dash ---");
        for (int w : new int[]{0, Weapons.SWORD, Weapons.SPEAR, Weapons.ENERGY_BLADE}) {
            Game g = newGame(); startLocal(g);
            Fighter p = ((Fighter[]) field(g,"fighters"))[0];
            p.giveWeapon(w); p.abilityT = 0; p.spawnT = 0; p.hurtT = 0;
            float x0 = p.x;
            call(g, "useAbility", new Class<?>[]{Fighter.class,float.class,float.class}, p, 1f, 0f);
            for (int k=0;k<45;k++) g.tick(1f/60f);
            System.out.println("  " + pad(Weapons.name(w)) + " moved=" + Math.round(Math.abs(p.x-x0)) + "px");
        }
        System.out.println("--- hitstun: longest frozen stretch at Insane ---");
        for (int w : new int[]{Weapons.DUAL_SABERS, Weapons.PISTOL, 0}) {
            Game g = newGame(); g.profile.difficulty = 3;
            call(g, "resetMatch", new Class<?>[]{int.class,int.class,boolean.class}, 0, 0, true);
            int i = 0; while ((Integer) field(g,"phase") == 0 && i++ < 600) g.tick(1f/60f);
            Fighter[] fs = (Fighter[]) field(g,"fighters");
            Fighter p = fs[0], c = fs[1];
            c.giveWeapon(w); c.x = p.x + 40; c.y = p.y; c.cooldown = 0f;
            g.pointerDown(3, 300f, 560f); g.pointerMove(3, 150f, 560f);
            int longest = 0, cur = 0, hits = 0; float last = p.health;
            for (i=0;i<600 && !p.dead;i++) {
                g.tick(1f/60f);
                if (p.health < last) { hits++; last = p.health; }
                if (p.hurtT > 0f) { cur++; longest = Math.max(longest, cur); } else cur = 0;
            }
            System.out.println("  " + pad(Weapons.name(w)) + " hits=" + hits + " longestStun=" + (longest/60f)
                + "s dead=" + p.dead + " x=" + Math.round(p.x));
        }
        System.out.println("--- zero-G hop ---");
        {
            Game g = newGame(); g.profile.gravity = false; startLocal(g);
            Fighter p = ((Fighter[]) field(g,"fighters"))[0];
            p.vy = -850f; p.onGround = false;
            float peak = p.y; boolean landed = false;
            for (int k=0;k<300;k++){ g.tick(1f/60f); peak = Math.min(peak, p.y); if (k>5 && p.onGround) landed = true; }
            System.out.println("  jump height=" + Math.round(610-peak) + "px landed=" + landed + " y=" + Math.round(p.y));
        }
    }
    static String pad(String s){ while(s.length()<13) s+=" "; return s; }
}
