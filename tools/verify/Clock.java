import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Can every mode always reach a result?
 *
 * Football has no match clock: goals are the only way to end it, and the ball
 * only moves when someone kicks it. This probe stalls both players and times
 * how long the match takes to end.
 */
public final class Clock {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static int screen(Game g){ return (Integer)invoke(g,"debugScreen",new Class<?>[]{}); }
    static int phase(Game g){ return (Integer)invoke(g,"debugPhase",new Class<?>[]{}); }

    static Game boot(int mode,int map,long seed){
        Context ctx=new Context();
        Game g=new Game(ctx,new Game.Host(){public void announce(String t){}public void setBright(boolean on){}public void vibrate(int ms){}public void sfx(int id){}});
        g.resize(1600,720);
        call(g,"debugSeed",new Class<?>[]{long.class},seed);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},mode,map);
        return g;
    }

    public static void main(String[] a){
        // ---- football local, both players stand still (defensive stalemate) ----
        for(int mode : new int[]{5, 4}){
            Game g=boot(mode,0,3);
            int fr=0;
            while(fr<60*300 && screen(g)!=6){ g.tick(1f/60f); fr++; }
            System.out.printf("football mode=%d, players idle: %s after %.0fs (score %d-%d)%n",
                mode, screen(g)==6 ? "ENDED" : "STILL RUNNING", fr/60.0,
                (Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{}),
                (Integer)invoke(g,"debugRoundWinsB",new Class<?>[]{}));
        }

        // ---- duel local, both players stand still ----
        // Every round times out with equal health, so every round is a draw and
        // replays. The match must still end once MAX_ROUNDS is reached.
        {
            Game g=boot(1,0,3);
            int fr=0;
            while(fr<60*900 && screen(g)!=6){ g.tick(1f/60f); fr++; }
            System.out.printf("duel local, players idle: %s after %.0fs%n",
                screen(g)==6 ? "ENDED" : "STILL RUNNING", fr/60.0);
        }

        // ---- football where players only defend (walk away from the ball) ----
        {
            Game g=boot(5,0,3);
            int fr=0;
            while(fr<60*300 && screen(g)!=6){
                g.pointerDown(1,92f,556f); g.pointerMove(1,60f,556f);   // P1 runs left
                g.pointerDown(2,1500f,556f); g.pointerMove(2,1530f,556f); // P2 runs right
                g.tick(1f/60f); fr++;
            }
            System.out.printf("football local, both retreating: %s after %.0fs%n",
                screen(g)==6 ? "ENDED" : "STILL RUNNING", fr/60.0);
        }
    }
}
