import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

public final class Ammo {
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
    public static void main(String[] a){
        System.out.println("weapon        cost  ammo  seconds of held fire before forced to Fists");
        for(int w=0;w<Weapons.COUNT;w++){
            Weapons.Def d=Weapons.DEFS[w];
            if(d.type==Weapons.MELEE||d.ammo<=0) continue;
            Game g=boot(1,0,2L);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.reset(400,610); p.spawnT=0f;
            p.giveWeapon(w);
            g.pointerDown(1,92f,556f);
            int frames=0;
            while(p.weapon==w && frames<1800){ g.pointerMove(1,166f,556f); g.tick(1f/60f); frames++; }
            System.out.printf("%-13s %5d %5d  %5.1fs%n", Weapons.name(w), d.cost, d.ammo, frames/60f);
        }
    }
}
