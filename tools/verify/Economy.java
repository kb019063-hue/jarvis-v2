import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Economy model: how long does it really take to unlock the roster?
 *
 * Plays complete matches with a "typical player": closes to weapon range,
 * attacks on a human cadence, retreats when hurt. Measures wall-clock match
 * length and coins earned, then projects the full unlock curve.
 */
public final class Economy {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static int screen(Game g){ return (Integer)invoke(g,"debugScreen",new Class<?>[]{}); }

    /** One simulated match. Returns {frames, won} and leaves coins in g. */
    static int[] playMatch(long seed, int charSkill){
        Context ctx=new Context();
        Game g=new Game(ctx,new Game.Host(){public void announce(String t){}public void setBright(boolean on){}public void vibrate(int ms){}public void sfx(int id){}});
        g.resize(1600,720);
        g.profile.difficulty=1;                 // default difficulty
        call(g,"debugSeed",new Class<?>[]{long.class},seed);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},0,0);
        Fighter p=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},0);
        Fighter c=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},1);
        int fr=0;
        while(fr<60*240 && screen(g)!=6){
            if((Integer)invoke(g,"debugPhase",new Class<?>[]{})==1 && !p.dead && !c.dead){
                float dist=Math.abs(p.x-c.x);
                float toward = p.x<c.x?1f:-1f;
                float move;
                if(p.health < 35f && dist < 150f) move=-toward;      // back off when hurt
                else if(dist>bweaponReach(p)+20f) move=toward;      // close to range
                else move = (fr/40)%2==0?0.15f*toward:-0.15f*toward;
                // attack when roughly in range, on a human-ish cadence
                boolean atk = dist < bweaponReach(p)+30f && (fr%14)<4;
                boolean shield = p.health<35f && dist<150f && (fr%9)<3;
                p.input(move, toward, 0f, false, atk, shield);
                // a real player presses the stick, so drive the ability occasionally
                if(dist<200f && (fr%150)==0) g.pointerDown(9,92f,556f);
                if((fr%150)==6) g.pointerUp(9,166f,556f);
            }
            g.tick(1f/60f); fr++;
        }
        return new int[]{fr, screen(g)==6 ? 1 : 0};
    }
    static float bweaponReach(Fighter f){
        Weapons.Def d=Weapons.DEFS[f.weapon];
        return d.type==Weapons.MELEE ? Math.max(40f, d.reach) : 220f;
    }

    public static void main(String[] a){
        // ---- roster cost ----
        int total=0;
        StringBuilder costs=new StringBuilder();
        for(int w=0;w<Weapons.COUNT;w++){
            total+=Weapons.DEFS[w].cost;
            costs.append(Weapons.name(w)).append('=').append(Weapons.DEFS[w].cost).append(' ');
        }
        System.out.println("costs: "+costs);
        System.out.printf("total roster cost: %d coins, start with 250%n%n", total);

        // ---- match length and win rate ----
        int n=24, frames=0, wins=0; int maxFr=0, minFr=1<<30;
        for(int i=0;i<n;i++){
            int[] r=playMatch(500+i, 1);
            frames+=r[0]; wins+=r[1];
            maxFr=Math.max(maxFr,r[0]); minFr=Math.min(minFr,r[0]);
        }
        double avgSec=frames/60.0/n, winRate=100.0*wins/n;
        System.out.printf("%d simulated matches vs Normal CPU:%n", n);
        System.out.printf("  win rate        %.0f%%%n", winRate);
        System.out.printf("  match length    %.1fs average (min %.1fs, max %.1fs)%n",
            avgSec, minFr/60.0, maxFr/60.0);
        double coinsPerMatch = (winRate/100.0)*70 + (1-winRate/100.0)*15;
        double coinsPerHour = coinsPerMatch * (3600.0/avgSec);
        System.out.printf("  coins per match %.1f%n", coinsPerMatch);
        System.out.printf("  coins per hour  %.0f%n%n", coinsPerHour);

        // ---- unlock curve ----
        System.out.println("unlock curve at that rate (buying cheapest-first):");
        int[] order=new int[Weapons.COUNT];
        for(int i=0;i<Weapons.COUNT;i++) order[i]=i;
        for(int i=0;i<order.length;i++)
            for(int j=i+1;j<order.length;j++)
                if(Weapons.DEFS[order[j]].cost<Weapons.DEFS[order[i]].cost){
                    int t=order[i]; order[i]=order[j]; order[j]=t;
                }
        double coins=250;
        int winCount=0;
        for(int i=0;i<order.length;i++){
            int w=order[i];
            if(Weapons.DEFS[w].cost==0) continue;
            while(coins < Weapons.DEFS[w].cost){ coins += coinsPerMatch; winCount++; }
            coins -= Weapons.DEFS[w].cost;
            System.out.printf("  %-13s after %3d matches  (%.1f hours)%n",
                Weapons.name(w), winCount, winCount*avgSec/3600.0);
        }
        System.out.printf("%nTo own every weapon: %d matches = %.1f hours of play%n",
            winCount, winCount*avgSec/3600.0);
    }
}
