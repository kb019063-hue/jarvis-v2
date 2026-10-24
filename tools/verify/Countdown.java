import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Mirrors PlayabilityTest.cpuMustDealDamageFromRealCountdownOnEveryMap.
 *
 * Every map, three seeds: the CPU starts from the real countdown spawns and has
 * 15 s to land a hit on an idle player, without falling out of the world and
 * without turning navigation into a pogo stick (the harness limit is 3 jumps).
 * Run this after changing map geometry or CPU routing, because the Robolectric
 * suite only runs in CI.
 */
public final class Countdown {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }

    public static void main(String[] args){
        int checks=0, fails=0;
        long[] seeds={3,42,100};
        for(int map=0;map<Maps.ALL.length;map++){
            StringBuilder line=new StringBuilder();
            for(long seed:seeds){
                Game g=new Game(new Context(),new Game.Host(){
                    public void announce(String t){} public void setBright(boolean on){} public void vibrate(int ms){} public void sfx(int id){}});
                g.resize(1600,720);
                call(g,"debugSeed",new Class<?>[]{long.class},seed);
                call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},0,map);
                call(g,"debugSuppressPickups",new Class<?>[]{});
                Fighter p=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},0);
                Fighter c=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},1);
                c.giveWeapon(Weapons.FIST);
                c.abilityT=100f;
                p.maxHealth=p.health=10000f;
                int jumps=0; boolean was=c.onGround; boolean died=false;
                int frames=0;
                for(;frames<900 && p.health==p.maxHealth;frames++){
                    g.tick(1f/60f);
                    if(was && !c.onGround && c.vy < -500f) jumps++;
                    was=c.onGround;
                    if(c.dead) died=true;
                }
                checks++;
                boolean hit=p.health<p.maxHealth;
                if(!hit || died || jumps>3){
                    fails++;
                    System.out.printf("FAIL %s seed %d: hit=%s dead=%s jumps=%d at (%.0f,%.0f)%n",
                            Maps.ALL[map].name, seed, hit, died, jumps, c.x, c.y);
                }
                line.append(String.format("%s(%d jumps,%.1fs) ", hit&&!died&&jumps<=3?"ok":"X", jumps, frames/60f));
            }
            System.out.printf("%-14s %s%n", Maps.ALL[map].name, line);
        }
        // Section 2 mirrors everyMeleeCpuActuallyDamagesAnIdleOpponent, which
        // drops both fighters from y=590 -- mid-air on the taller arenas. It is
        // here because a map change moves the floor under that number.
        for(int weapon=0;weapon<Weapons.COUNT;weapon++){
            if(Weapons.DEFS[weapon].type!=Weapons.MELEE) continue;
            StringBuilder line=new StringBuilder();
            for(long seed:new long[]{7,42,91}){
                Game g=new Game(new Context(),new Game.Host(){
                    public void announce(String t){} public void setBright(boolean on){} public void vibrate(int ms){} public void sfx(int id){}});
                g.resize(1600,720);
                call(g,"debugSeed",new Class<?>[]{long.class},seed);
                call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},0,8);
                call(g,"debugSuppressPickups",new Class<?>[]{});
                call(g,"debugStartFight",new Class<?>[]{});
                Fighter p=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},0);
                Fighter c=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},1);
                p.reset(620,590); c.reset(710,590);
                c.reset(710,590); c.giveWeapon(weapon);
                c.abilityT=100f; p.spawnT=c.spawnT=0f;
                float hp=p.health;
                int frames=0;
                for(;frames<300 && p.health==hp;frames++) g.tick(1f/60f);
                checks++;
                boolean hit=p.health<hp;
                if(!hit){
                    fails++;
                    System.out.printf("FAIL melee %s seed %d did no damage in 5 s (P at %.0f,%.0f C at %.0f,%.0f)%n",
                            Weapons.name(weapon), seed, p.x, p.y, c.x, c.y);
                }
                line.append(hit?String.format("%.1fs ", frames/60f):"X ");
            }
            System.out.printf("%-14s %s%n", Weapons.name(weapon), line);
        }
        System.out.println("checks="+checks+" failures="+fails);
    }
}
