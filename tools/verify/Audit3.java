import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/** Pickup placement vs hazards, and football stalemate risk. */
public final class Audit3 {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object field(Object o,String n){ try{Field x=o.getClass().getDeclaredField(n);x.setAccessible(true);return x.get(o);}catch(Exception e){throw new RuntimeException(e);} }
    static Game boot(int mode,int map,long seed){
        Context ctx=new Context();
        Game g=new Game(ctx,new Game.Host(){public void announce(String t){}public void setBright(boolean on){}public void vibrate(int ms){}public void sfx(int id){}});
        g.resize(1600,720);
        call(g,"debugSeed",new Class<?>[]{long.class},seed);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},mode,map);
        return g;
    }
    public static void main(String[] a) throws Exception {
        // where do weapon drops land?
        int total=0, inHazard=0, offPlatform=0;
        for(int map=0;map<Maps.ALL.length;map++){
            Game g=boot(1,map,3);
            call(g,"debugStartFight",new Class<?>[]{});
            java.util.List<?> list=(java.util.List<?>)field(g,"pickups");
            for(int i=0;i<60;i++){
                list.clear();
                call(g,"spawnPickup",new Class<?>[]{});
                if(list.isEmpty()) continue;
                Object pk=list.get(0);
                Field xf=pk.getClass().getDeclaredField("x"); xf.setAccessible(true);
                Field yf=pk.getClass().getDeclaredField("y"); yf.setAccessible(true);
                float x=xf.getFloat(pk), y=yf.getFloat(pk);
                total++;
                for(Maps.Hazard hz: Maps.ALL[map].hazards){
                    if(x>hz.r.left&&x<hz.r.right&&y>hz.r.top&&y<hz.r.bottom) { inHazard++; break; }
                }
            }
        }
        System.out.printf("pickups: %d placed, %d inside a hazard cell (%.0f%%)%n", total, inHazard, 100f*inHazard/total);

        // football: is a local 2P match guaranteed to end?
        Game g=boot(5,0,1);
        int frames=0;
        while(frames<60*300 && (Integer)invoke(g,"debugScreen",new Class<?>[]{})!=6){ g.tick(1f/60f); frames++; }
        System.out.printf("football local, nobody touches anything: screen=%d after %.0fs (6=result)%n",
            (Integer)invoke(g,"debugScreen",new Class<?>[]{}), frames/60f);
        Object fb=invoke(g,"debugFootball",new Class<?>[]{});
        Field bx=fb.getClass().getDeclaredField("x"); bx.setAccessible(true);
        Field by=fb.getClass().getDeclaredField("y"); by.setAccessible(true);
        System.out.printf("  ball rest position (%.0f, %.0f) - it never moves without a kick%n", bx.getFloat(fb), by.getFloat(fb));
    }
}
