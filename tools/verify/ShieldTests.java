import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/** Headless mirror of the 0.3.7 shield tests in PlayabilityTest. */
public final class ShieldTests {
    static int checks=0, fails=0;
    static void check(String n, boolean ok, String d){ checks++; if(!ok) fails++;
        System.out.println((ok?"PASS ":"FAIL ")+n+(d.isEmpty()?"":"  ["+d+"]")); }
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Game boot(){ Game g=new Game(new Context(),new Game.Host(){
            public void announce(String t){} public void setBright(boolean on){} public void vibrate(int ms){} public void sfx(int id){}});
        g.resize(1600,720); call(g,"debugSeed",new Class<?>[]{long.class},42L);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},0,0);
        call(g,"debugSuppressPickups",new Class<?>[]{}); call(g,"debugStartFight",new Class<?>[]{}); return g; }
    static Fighter f(Game g){ return (Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},0); }
    static void setF(Object o,String n,Object v){
        try{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}catch(Exception e){throw new RuntimeException(e);} }

    public static void main(String[] a){
        // 1. six sword hits
        {
            Game g=boot(); Fighter p=f(g); p.reset(700,610); p.spawnT=0f;
            p.maxHealth=p.health=400f; p.giveWeapon(Weapons.SWORD);
            int hits=0;
            for(int i=0;i<60*10 && !p.shieldBroken;i++){
                p.input(0f,1f,0f,false,false,true);
                p.update(1f/60f,Maps.ALL[0],true,true);
                if(p.shieldOn && p.hurtT<=0f){ p.damage(17f,0f,0f,false); hits++; }
            }
            check("sword takes 6 shield hits", hits==6 && p.shieldBroken, "hits="+hits);
        }
        // 2. broken shield returns
        {
            Game g=boot(); Fighter p=f(g); p.reset(700,610); p.spawnT=0f;
            p.maxHealth=p.health=400f; p.shield=0f; p.shieldBroken=true;
            setF(p,"shieldDelay",Fighter.SHIELD_REGEN_DELAY);
            int raise=-1;
            for(int i=0;i<60*10;i++){
                p.input(0f,1f,0f,false,false,true);
                p.update(1f/60f,Maps.ALL[0],true,true);
                if(p.shieldOn){ raise=i; break; }
            }
            check("broken shield raisable again in 3-6 s", raise>60 && raise<60*7
                && p.shield>=p.maxShield*Fighter.SHIELD_RESTORE,
                "raise="+(raise/60.0)+"s shield="+p.shield);
        }
        // 3. regen only after the delay
        {
            Game g=boot(); Fighter p=f(g); p.reset(700,610); p.spawnT=0f;
            p.maxHealth=p.health=400f; p.shield=50f; setF(p,"shieldDelay",Fighter.SHIELD_REGEN_DELAY);
            for(int i=0;i<30;i++){ p.input(0f,1f,0f,false,false,false); p.update(1f/60f,Maps.ALL[0],true,true); }
            float mid=p.shield;
            for(int i=0;i<60*3;i++){ p.input(0f,1f,0f,false,false,false); p.update(1f/60f,Maps.ALL[0],true,true); }
            check("no regen inside the delay, regen after", Math.abs(mid-50f)<0.01f && p.shield>55f,
                "mid="+mid+" after="+p.shield);
        }
        // 4. guard break staggers longer than a hit
        {
            Game g=boot(); Fighter p=f(g); p.reset(700,610); p.spawnT=0f;
            p.maxHealth=p.health=400f;
            for(int i=0;i<30;i++){ p.input(0f,1f,0f,false,false,true); p.update(1f/60f,Maps.ALL[0],true,true); }
            p.shield=1f; p.damage(17f,0f,0f,false);
            check("guard break stuns > 0.30 s", p.hurtT>0.30f, "hurtT="+p.hurtT);
        }
        System.out.println("checks="+checks+" failures="+fails);
    }
}
