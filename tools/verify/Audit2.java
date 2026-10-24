import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/** Verifies ammo exhaustion, pickup auto-swap and shield-break behaviour. */
public final class Audit2 {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Game boot(int mode,int map,long seed){
        Context ctx=new Context();
        Game g=new Game(ctx,new Game.Host(){public void announce(String t){}public void setBright(boolean on){}public void vibrate(int ms){}public void sfx(int id){}});
        g.resize(1600,720);
        call(g,"debugSeed",new Class<?>[]{long.class},seed);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},mode,map);
        return g;
    }
    static Fighter f(Game g,int i){ return (Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},i); }
    static Object field(Object o,String n){ try{Field x=o.getClass().getDeclaredField(n);x.setAccessible(true);return x.get(o);}catch(Exception e){throw new RuntimeException(e);} }

    public static void main(String[] a) throws Exception {
        // ---- 1. ammo exhaustion: what is the player left holding? ----
        for(int w : new int[]{Weapons.PISTOL, Weapons.BOW, Weapons.KUNAI}){
            Game g=boot(1,0,2);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.reset(500,610); p.spawnT=0f;
            p.giveWeapon(w);
            int frames=0;
            while(p.weapon==w && frames<600){
                p.input(0f,1f,0f,false,true,false);
                g.pointerDown(1,92f,556f);
                g.pointerMove(1,166f,556f);
                g.tick(1f/60f); frames++;
            }
            System.out.printf("ammo: %-8s still %-6s after %.1fs of held fire (ammo=%d, reload=%.1fs)%n",
                Weapons.name(w), Weapons.name(p.weapon), frames/60f, p.ammo, Weapons.reloadTime(w));
        }

        // ---- 2. pickup auto-swap: does walking past a drop replace your weapon? ----
        {
            Game g=boot(1,0,5);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.reset(500,610); p.spawnT=0f;
            p.giveWeapon(Weapons.SWORD);
            // spawn a pickup directly in the player's path
            Class<?> pk=g.getClass();
            java.util.List<?> list=(java.util.List<?>)field(g,"pickups");
            Constructor<?> ctor = Class.forName("com.arena.duelist.Game$Pickup").getDeclaredConstructor();
            ctor.setAccessible(true);
            Object pick = ctor.newInstance();
            Field wf=pick.getClass().getDeclaredField("weapon"); wf.setAccessible(true); wf.setInt(pick, Weapons.PISTOL);
            Field xf=pick.getClass().getDeclaredField("x"); xf.setAccessible(true); xf.setFloat(pick, 560f);
            Field yf=pick.getClass().getDeclaredField("y"); yf.setAccessible(true); yf.setFloat(pick, 604f);
            ((java.util.List)list).add(pick);
            System.out.println("pickup: player holds "+Weapons.name(p.weapon)+", drop at x=560, player at 500");
            for(int i=0;i<10;i++){ g.tick(1f/60f); }
            p.x=560; // step onto it, no button
            g.tick(1f/60f);
            System.out.println("pickup: after walking onto it (no input) player holds "+Weapons.name(p.weapon)
                +"  -> silent swap = "+(p.weapon!=Weapons.SWORD)+" (a DOWN press is required now)");
        }

        // ---- 3. shield: how much can it absorb, what breaks it ----
        {
            Game g=boot(0,0,7);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.spawnT=0f; p.maxHealth=p.health=200f;
            int hits=0; float taken=0f; boolean broke=false; int stunFramesAfter=-1;
            for(int i=0;i<60;i++){
                p.input(0f,1f,0f,false,false,true);
                p.update(1f/60f,Maps.ALL[0],true,true,true);
                if(p.shieldOn && p.hurtT<=0f){
                    float before=p.health;
                    p.damage(17f,0f,0f,false);
                    taken+=before-p.health; hits++;
                    if(!p.shieldOn && !broke){ broke=true;
                        // count how long the fighter stays stunned
                        int fr=0; while(p.hurtT>0f && fr<120){ p.hurtT-=1f/60f; fr++; }
                        stunFramesAfter=fr; break; }
                }
            }
            System.out.printf("shield: broke after %d sword hits (17 dmg), only %.1f HP lost, stun ~%.2fs, shield=%.0f%n",
                hits, taken, stunFramesAfter/60f, p.shield);
        }

        // ---- 4. does shield regen during a round? ----
        {
            Game g=boot(0,0,9);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.spawnT=0f;
            p.shield=10f;
            for(int i=0;i<300;i++){ p.input(0f,1f,0f,false,false,false); p.update(1f/60f,Maps.ALL[0],true,true,true); }
            System.out.printf("shield regen over 5 s: 10 -> %.0f (max %.0f)%n", p.shield, p.maxShield);
        }
    }
}
