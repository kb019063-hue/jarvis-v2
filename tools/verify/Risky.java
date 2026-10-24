import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

public final class Risky {
    static int checks=0, fails=0;
    static void check(String n, boolean ok, String d){ checks++; if(!ok) fails++;
        System.out.println((ok?"PASS ":"FAIL ")+n+(d.isEmpty()?"":"  ["+d+"]")); }
    static void call(Object o,String n,Class<?>[] t,Object... a){ try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){ try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Game boot(int mode,int map){
        Context ctx=new Context();
        Game g=new Game(ctx,new Game.Host(){public void announce(String t){}public void setBright(boolean on){}public void vibrate(int ms){}public void sfx(int id){}});
        g.resize(1600,720);
        call(g,"debugSeed",new Class<?>[]{long.class},42L);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},mode,map);
        call(g,"debugSuppressPickups",new Class<?>[]{});
        return g;
    }
    static Fighter f(Game g,int i){ return (Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},i); }
    static int phase(Game g){ return (Integer)invoke(g,"debugPhase",new Class<?>[]{}); }
    public static void main(String[] a){
        // 1. cpuMustDealDamageFromRealCountdownOnEveryMap
        for(int map=0;map<Maps.ALL.length;map++){
            for(long seed:new long[]{3,42,100}){
                Game g=boot(0,map); call(g,"debugSeed",new Class<?>[]{long.class},seed);
                Fighter player=f(g,0), cpu=f(g,1);
                cpu.giveWeapon(Weapons.FIST); cpu.abilityT=100f;
                player.maxHealth=player.health=10000f;
                int jumps=0; boolean was=cpu.onGround; boolean died=false; int fr=0;
                for(; fr<900 && player.health==player.maxHealth && !cpu.dead; fr++){
                    g.tick(1f/60f);
                    if(was && !cpu.onGround && cpu.vy<-500f) jumps++;
                    was=cpu.onGround; died=cpu.dead;
                }
                check("damage "+Maps.ALL[map].name+" seed="+seed,
                    player.health<player.maxHealth && !died && jumps<=3,
                    "t="+(fr/60f)+"s jumps="+jumps+" cpuDead="+died+" hp="+Math.round(player.health));
            }
        }
        // 2. cpuDoesNotSuicideInAnyHazardArenaOpening
        for(int m=0;m<Maps.ALL.length;m++){
            if(Maps.ALL[m].hazards.isEmpty()) continue;
            Game g=boot(0,m);
            Fighter cpu=f(g,1), player=f(g,0);
            cpu.giveWeapon(Weapons.FIST); player.giveWeapon(Weapons.FIST);
            player.maxHealth=player.health=10000f;
            for(int i=0;i<300 && phase(g)!=1;i++) g.tick(1f/60f);
            for(int i=0;i<480 && !cpu.dead;i++) g.tick(1f/60f);
            check("hazard opening "+Maps.ALL[m].name, !cpu.dead, "cpu=("+Math.round(cpu.x)+","+Math.round(cpu.y)+")");
        }
        // 3. first-map opening: survival, jump loop, engagement
        {
            Game g=boot(0,0); g.profile.instantKO=true;
            Fighter cpu=f(g,1), player=f(g,0);
            cpu.giveWeapon(Weapons.FIST); player.giveWeapon(Weapons.FIST);
            for(int i=0;i<300 && phase(g)!=1;i++) g.tick(1f/60f);
            int jumps=0; boolean was=cpu.onGround;
            for(int i=0;i<=720;i++){
                if(cpu.dead||player.dead) break;
                g.tick(1f/60f);
                if(was && !cpu.onGround && cpu.vy<-500f) jumps++;
                was=cpu.onGround;
            }
            check("first-map opening", !cpu.dead && jumps<=4 && (player.health<player.maxHealth || Math.abs(player.x-cpu.x)<120f),
                "jumps="+jumps+" cpuDead="+cpu.dead+" dist="+Math.round(Math.abs(player.x-cpu.x))+" php="+Math.round(player.health));
        }
        System.out.println("checks="+checks+" failures="+fails);
    }
}
