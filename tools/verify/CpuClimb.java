import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Can the CPU actually reach a player who is standing somewhere specific?
 *
 * For every platform in every arena: park the player in the middle of that
 * platform (invulnerable, so the match does not end early), put the CPU on the
 * floor at the opposite side, and measure how long it takes to get into melee
 * range. A route that takes longer than CLIMB_LIMIT, or never arrives, is a
 * navigation failure -- the match would stall there in real play.
 */
public final class CpuClimb {
    static final float LIMIT = 12f;

    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }

    public static void main(String[] args){
        int checks=0, fails=0;
        for(int m=0;m<Maps.ALL.length;m++){
            Maps.Arena a=Maps.ALL[m];
            StringBuilder line=new StringBuilder();
            for(int k=1;k<a.platforms.size();k++){
                Maps.Platform pl=a.platforms.get(k);
                Game g=new Game(new Context(),new Game.Host(){
                    public void announce(String t){} public void setBright(boolean on){} public void vibrate(int ms){} public void sfx(int id){}});
                g.resize(1600,720);
                call(g,"debugSeed",new Class<?>[]{long.class},11L+m*13L+k);
                call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},0,m);
                call(g,"debugSuppressPickups",new Class<?>[]{});
                call(g,"debugStartFight",new Class<?>[]{});
                Fighter p=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},0);
                Fighter c=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},1);
                p.spawnT=c.spawnT=0f;
                float px=Math.max(pl.r.left+50f, Math.min(pl.r.right-50f, pl.r.centerX()));
                p.reset(px, pl.r.top - Fighter.FEET);
                p.maxHealth=p.health=100000f;
                c.reset(a.platforms.get(0).r.left + 120f, a.platforms.get(0).r.top - Fighter.FEET);
                c.maxHealth=c.health=100000f;
                c.x = px > a.bounds.centerX() ? a.platforms.get(0).r.left + 120f
                                              : a.platforms.get(0).r.right - 120f;
                boolean arrived=false; float t=-1f;
                for(int f=0;f<(int)(LIMIT*60);f++){
                    g.tick(1f/60f);
                    float dy=Math.abs((c.y+Fighter.FEET)-(p.y+Fighter.FEET));
                    if(Math.abs(c.x-p.x)<70f && dy<70f){ arrived=true; t=f/60f; break; }
                }
                checks++;
                if(!arrived){
                    fails++;
                    System.out.printf("FAIL %s platform %d (%.0f..%.0f top %.0f): CPU stuck at (%.0f,%.0f) player at (%.0f,%.0f)%n",
                        a.name,k,pl.r.left,pl.r.right,pl.r.top,c.x,c.y,p.x,p.y);
                }
                line.append(arrived?String.format("%4.1fs ", t):"  X   ");
            }
            System.out.printf("%-14s %s%n", a.name, line);
        }
        System.out.println("checks="+checks+" failures="+fails);
    }
}
