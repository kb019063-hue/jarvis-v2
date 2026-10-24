import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/** Headless mirror of the 0.3.6 clock/streak tests added to the Robolectric suite. */
public final class ClockTests {
    static int checks=0, fails=0;
    static void check(String n, boolean ok, String d){ checks++; if(!ok) fails++;
        System.out.println((ok?"PASS ":"FAIL ")+n+(d.isEmpty()?"":"  ["+d+"]")); }
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }

    static Game boot(int mode,int map){
        Context ctx=new Context();
        Game g=new Game(ctx,new Game.Host(){public void announce(String t){}public void setBright(boolean on){}public void vibrate(int ms){}public void sfx(int id){}});
        g.resize(1600,720);
        call(g,"debugSeed",new Class<?>[]{long.class},7L);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},mode,map);
        call(g,"debugSuppressPickups",new Class<?>[]{});
        return g;
    }
    static Fighter f(Game g,int i){ return (Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},i); }
    static int screen(Game g){ return (Integer)invoke(g,"debugScreen",new Class<?>[]{}); }

    public static void main(String[] a){
        // 1. footballCannotRunForever
        for(int mode : new int[]{4,5}){
            Game g=boot(mode,0);
            call(g,"debugStartFight",new Class<?>[]{});
            call(g,"debugSetMatchClock",new Class<?>[]{float.class},1.5f);
            int i=0; for(; i<240 && screen(g)!=6; i++) g.tick(1f/60f);
            check("football ends on clock (mode "+mode+")", screen(g)==6, "screen="+screen(g)+" t="+(i/60.0));
        }
        // 2. footballClockExpiryWithNoGoalsIsADraw
        {
            Game g=boot(5,0);
            call(g,"debugStartFight",new Class<?>[]{});
            call(g,"debugSetMatchClock",new Class<?>[]{float.class},1.5f);
            for(int i=0;i<240 && screen(g)!=6;i++) g.tick(1f/60f);
            check("0-0 full time is a draw",
                (Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{})==0
                && (Integer)invoke(g,"debugRoundWinsB",new Class<?>[]{})==0
                && (Boolean)invoke(g,"debugDrawn",new Class<?>[]{}), "");
        }
        // 3. duelRoundClockAwardsTheHealthierFighter
        {
            Game g=boot(1,0);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p1=f(g,0), p2=f(g,1);
            p1.spawnT=p2.spawnT=0f;
            p2.health=40f; p1.health=90f;
            call(g,"debugSetRoundClock",new Class<?>[]{float.class},0.2f);
            for(int i=0;i<40;i++) g.tick(1f/60f);
            check("timeout awards the healthier fighter",
                (Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{})==1
                && (Integer)invoke(g,"debugRoundWinsB",new Class<?>[]{})==0,
                "A="+invoke(g,"debugRoundWinsA",new Class<?>[]{})
                +" B="+invoke(g,"debugRoundWinsB",new Class<?>[]{}));
        }
        // 4. winStreakPaysBonusCoins
        {
            Game g=boot(0,0);
            g.profile.streak=3;
            int before=g.profile.coins;
            call(g,"debugSetRoundWins",new Class<?>[]{int.class,int.class},1,0);
            call(g,"debugStartFight",new Class<?>[]{});
            f(g,1).kill(0);
            for(int i=0;i<240;i++) g.tick(1f/60f);
            check("win streak pays 70 + 30", g.profile.streak==4 && g.profile.coins==before+100,
                "streak="+g.profile.streak+" coins+"+(g.profile.coins-before));
        }
        // 5. winStreakBonusIsCapped
        {
            Game g=boot(0,0);
            g.profile.streak=20;
            int before=g.profile.coins;
            call(g,"debugSetRoundWins",new Class<?>[]{int.class,int.class},1,0);
            call(g,"debugStartFight",new Class<?>[]{});
            f(g,1).kill(0);
            for(int i=0;i<240;i++) g.tick(1f/60f);
            check("streak bonus caps at 50", g.profile.coins==before+120,
                "coins+"+(g.profile.coins-before));
        }
        // 6. allDrawnRoundsCapTheMatch
        {
            Game g=boot(1,0);
            call(g,"debugStartFight",new Class<?>[]{});
            int frames=0; long t0=System.currentTimeMillis();
            while(screen(g)!=6 && frames<60*600){ g.tick(1f/60f); frames++; }
            check("all-draw duel ends as a draw", screen(g)==6
                && (Boolean)invoke(g,"debugDrawn",new Class<?>[]{})
                && (Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{})==0,
                "t="+(frames/60)+"s wall="+(System.currentTimeMillis()-t0)+"ms");
        }
        System.out.println("checks="+checks+" failures="+fails);
    }
}
