import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

public final class WeaponHandling {
    static int checks=0, fails=0;
    static void check(String n, boolean ok, String d){ checks++; if(!ok) fails++;
        System.out.println((ok?"PASS ":"FAIL ")+n+(d.isEmpty()?"":"  ["+d+"]")); }
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
        call(g,"debugSuppressPickups",new Class<?>[]{});
        return g;
    }
    static Fighter f(Game g,int i){ return (Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},i); }
    static int pickups(Game g){ return (Integer)invoke(g,"debugPickupCount",new Class<?>[]{}); }
    static int pkWeapon(Game g,int i){ return (Integer)invoke(g,"debugPickupWeapon",new Class<?>[]{int.class},i); }
    static void addPk(Game g,int w,float x,float y){ call(g,"debugAddPickup",new Class<?>[]{int.class,float.class,float.class},w,x,y); }

    public static void main(String[] a){
        // ---------- 1. a magazine reloads instead of stealing the weapon ----------
        for(int w : new int[]{Weapons.PISTOL, Weapons.BOOMERANG}){
            Game g=boot(1,0,2L);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0);
            p.reset(400,Maps.ALL[1].platforms.get(0).r.top-Fighter.FEET); p.spawnT=0f; p.giveWeapon(w);
            int total=0;
            while(p.ammo>0 && total<1200){
                p.input(0f,1f,0f,false,true,false);
                g.pointerDown(1,200f,300f); g.pointerMove(1,280f,300f);
                g.tick(1f/60f); total++;
            }
            check("reload keeps "+Weapons.name(w), p.weapon==w,
                "weapon="+Weapons.name(p.weapon)+" ammo="+p.ammo);

            // let the swing that fired the last round finish
            int settle=0;
            while(p.attacking && settle<120){ p.input(0f,1f,0f,false,false,false); g.tick(1f/60f); settle++; }
            boolean everAttacked=false;
            int guard=0;
            while(p.ammo==0 && guard<30){
                p.input(0f,1f,0f,false,true,false);
                g.tick(1f/60f); guard++;
                everAttacked |= p.attacking;
            }
            check("empty magazine cannot swing "+Weapons.name(w), !everAttacked,
                "attacking="+everAttacked+" for "+guard+" frames");

            int wait=0;
            while(p.ammo==0 && wait<600){ g.tick(1f/60f); wait++; }
            double secs=(total+settle+guard+wait)/60.0 - total/60.0;
            check("reload refills "+Weapons.name(w),
                p.ammo==Weapons.DEFS[w].ammo && Math.abs(secs-Weapons.reloadTime(w))<0.2,
                "ammo="+p.ammo+" reload took "+String.format("%.2f",secs)+"s vs table "+Weapons.reloadTime(w)+"s");
        }

        // ---------- 2. sustained rate including reload ----------
        System.out.println("\ncontinuous fire for 20 s (shots fired / sustained per second):");
        for(int w=0;w<Weapons.COUNT;w++){
            if(Weapons.DEFS[w].type==Weapons.MELEE) continue;
            Game g=boot(1,0,3L);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0);
            p.reset(400,Maps.ALL[1].platforms.get(0).r.top-Fighter.FEET); p.spawnT=0f; p.giveWeapon(w);
            boolean was=false; int fired=0;
            for(int i=0;i<60*20;i++){
                p.input(0f,1f,0f,false,true,false);
                g.tick(1f/60f);
                if(p.attacking && !was) fired++;   // one swing == one round for ranged
                was=p.attacking;
            }
            System.out.printf("   %-13s %4d shots in 20s -> %.2f/s%n", Weapons.name(w), fired, fired/20.0);
        }

        // ---------- 3. walking over a drop does nothing ----------
        {
            Game g=boot(0,0,5L);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0), c=f(g,1);
            // Stand on the floor wherever this map actually puts it: the old
            // hard-coded 590 was written for the 620-top maps and silently
            // dangled the fighter in mid-air once the arenas got bigger.
            float floorY=Maps.ALL[0].platforms.get(0).r.top-Fighter.FEET;
            p.reset(620,floorY); p.spawnT=0f; p.giveWeapon(Weapons.SWORD);
            c.reset(120,floorY); c.spawnT=0f;
            c.cpu = false;   // isolate the drop mechanic from the AI hoovering it up
            float top=Maps.ALL[0].platforms.get(0).r.top;
            addPk(g, Weapons.PISTOL, 620f, top-12f);
            for(int i=0;i<10;i++) g.tick(1f/60f);
            check("walking over a drop does not swap", p.weapon==Weapons.SWORD,
                "weapon="+Weapons.name(p.weapon)+" drops="+pickups(g));
            check("the drop is still there", pickups(g)==1, "drops="+pickups(g));

            g.pointerDown(1, 200f, 300f);
            g.pointerMove(1, 200f, 420f);
            g.tick(1f/60f);
            check("pressing DOWN swaps", p.weapon==Weapons.PISTOL, "weapon="+Weapons.name(p.weapon));
            check("the old weapon is left on the floor",
                pickups(g)==1 && pkWeapon(g,0)==Weapons.SWORD,
                "drops="+pickups(g)+(pickups(g)==1?" w="+Weapons.name(pkWeapon(g,0)):""));

            for(int i=0;i<60;i++) g.tick(1f/60f);
            check("holding DOWN does not swap back", p.weapon==Weapons.PISTOL,
                "weapon="+Weapons.name(p.weapon)+" drops="+pickups(g));

            // release and press again: now it may take the sword back
            g.pointerUp(1, 200f, 300f);
            for(int i=0;i<6;i++) g.tick(1f/60f);
            g.pointerDown(1, 200f, 300f); g.pointerMove(1, 200f, 420f);
            for(int i=0;i<6;i++) g.tick(1f/60f);
            check("a fresh DOWN press can swap back", p.weapon==Weapons.SWORD,
                "weapon="+Weapons.name(p.weapon)+" drops="+pickups(g));
        }

        // ---------- 4. CPU keeps the walk-over grab ----------
        {
            Game g=boot(0,0,6L);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0), c=f(g,1);
            float floorY=Maps.ALL[0].platforms.get(0).r.top-Fighter.FEET;
            p.reset(200,floorY); p.spawnT=0f;
            c.reset(620,floorY); c.spawnT=0f; c.giveWeapon(Weapons.FIST);
            float top=Maps.ALL[0].platforms.get(0).r.top;
            addPk(g, Weapons.HAMMER, 620f, top-12f);
            for(int i=0;i<5;i++) g.tick(1f/60f);
            check("CPU still grabs by walking", c.weapon==Weapons.HAMMER,
                "cpu weapon="+Weapons.name(c.weapon)+" drops="+pickups(g));
        }
        System.out.println("checks="+checks+" failures="+fails);
    }
}
