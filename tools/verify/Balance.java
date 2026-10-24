import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Weapon balance: time-to-kill against an opponent that actually moves.
 *
 * The idle-target number flatters homing/projectile weapons (they cannot miss
 * a statue) and punishes melee (a statue never walks into range). Both are
 * measured here so a weapon is only nerfed for damage that is unavoidable.
 */
public final class Balance {
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

    /**
     * An engaged-but-evasive target: holds ~180 px, strafes, and hops. It does
     * not attack. Pure fleeing is useless as a metric - both fighters run at
     * the same speed, so nothing ever lands and every weapon reads as infinite.
     */
    static void dodge(Fighter p, Fighter c, int frame){
        float dist = Math.abs(p.x - c.x);
        float away = p.x < c.x ? -1f : 1f;
        float move;
        if (dist > 300f) {
            move = -away;                 // close the gap so melee can engage
        } else if (dist < 90f) {
            move = away;                  // do not stand inside the hitbox
        } else {
            move = (frame / 30) % 2 == 0 ? away : -away;   // strafe
        }
        if (p.x < 120f) move = 1f;
        if (p.x > 1480f) move = -1f;
        boolean jump = (frame % 52) < 2;
        p.input(move, move, 0f, jump, false, false);
    }

    static int ttk(int weapon, boolean dodge, long seed){
        Game g=boot(0,0,seed);
        g.profile.difficulty=3;
        call(g,"debugStartFight",new Class<?>[]{});
        Fighter p=f(g,0), c=f(g,1);
        p.reset(700,610); c.reset(950,610);   // reset() wipes the weapon to FIST
        p.maxHealth=p.health=100f; c.maxHealth=c.health=100f;
        c.giveWeapon(weapon); p.giveWeapon(Weapons.FIST);
        p.spawnT=c.spawnT=0f;
        int fr=0;
        while(fr<3600 && !p.dead){
            if(dodge) dodge(p,c,fr);
            g.tick(1f/60f); fr++;
        }
        return fr;
    }
    static double med(int[] v){ int[] x=v.clone(); java.util.Arrays.sort(x); return x[x.length/2]/60.0; }

    public static void main(String[] a){
        int runs=9;
        System.out.println("weapon         dmg  cd   idle  dodging(median)  min   max");
        double[] dod=new double[Weapons.COUNT];
        for(int w=0;w<Weapons.COUNT;w++){
            int[] i9=new int[runs], d9=new int[runs];
            for(int s=0;s<runs;s++){ i9[s]=ttk(w,false,300+s); d9[s]=ttk(w,true,300+s); }
            java.util.Arrays.sort(d9);
            dod[w]=med(d9);
            System.out.printf("%-13s %4.0f %.2f %6.1fs %10.1fs %7.1fs %6.1fs%n",
                Weapons.name(w), Weapons.DEFS[w].damage, Weapons.DEFS[w].cooldown,
                med(i9), dod[w], d9[0]/60.0, d9[runs-1]/60.0);
        }
        // rank against the dodging target, which is the fairer number
        double best=9999, worst=0;
        for(double d:dod){ best=Math.min(best,d); worst=Math.max(worst,d); }
        System.out.printf("%nfair-TTK spread: %.1fs (fastest) .. %.1fs (slowest) = %.1fx%n", best, worst, worst/best);
    }
}
