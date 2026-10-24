import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Shield audit: how many hits it survives per weapon, whether it regenerates,
 * what it is worth to a player who holds it, and what it is worth to the CPU
 * (which never uses it).
 */
public final class Shield {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object field(Object o,String n){
        try{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);} }

    static Game boot(int mode,int map,long seed){
        Game g=new Game(new Context(),new Game.Host(){
            public void announce(String t){} public void setBright(boolean on){} public void vibrate(int ms){} public void sfx(int id){}});
        g.resize(1600,720);
        call(g,"debugSeed",new Class<?>[]{long.class},seed);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},mode,map);
        call(g,"debugSuppressPickups",new Class<?>[]{});
        return g;
    }
    static Fighter f(Game g,int i){ return (Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},i); }

    public static void main(String[] a){
        // ---- 1. hits to break, per weapon -------------------------------
        System.out.println("== hits to break a full shield ==");
        for(int w=1; w<Weapons.DEFS.length; w++){
            Weapons.Def d = Weapons.DEFS[w];
            float sh=100f; int hits=0;
            while(sh>0f && hits<99){ sh -= d.damage*Fighter.SHIELD_PER_DAMAGE; hits++; }
            System.out.printf("%-14s dmg %4.0f  -> %d hits  (shield per hit %.1f)%n",
                Weapons.name(w), d.damage, hits, d.damage*Fighter.SHIELD_PER_DAMAGE);
        }

        // ---- 2. does it regenerate during a round? ----------------------
        {
            Game g=boot(0,0,11);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.spawnT=0f; p.maxHealth=p.health=200f;
            p.shield=0f;
            float[] at=new float[]{0,1,2,4,8,15};
            int k=0;
            for(int i=0;i<15*60;i++){
                p.input(0f,1f,0f,false,false,false);          // standing still, not shielding
                p.update(1f/60f,Maps.ALL[0],true,true,true);  // one update per frame
                if(k<at.length && i>=at[k]*60){ System.out.printf("shield after %.0fs idle: %.1f%n",at[k],p.shield); k++; }
            }
            // how long from a real break to a shield you can raise again?
            p.reset(600,610); p.spawnT=0f; p.maxHealth=p.health=200f;
            for(int i=0;i<60*10;i++){
                p.input(0f,1f,0f,false,false,true);
                p.update(1f/60f,Maps.ALL[0],true,true,true);
                if(p.shieldOn && p.hurtT<=0f) p.damage(17f,0f,0f,false);
                if(p.shieldBroken) break;
            }
            int back=-1;
            for(int i=0;i<20*60;i++){
                p.input(0f,1f,0f,false,false,true);
                p.update(1f/60f,Maps.ALL[0],true,true,true);
                if(p.shieldOn){ back=i; break; }
            }
            System.out.printf("broken shield is usable again after %.1fs (%.0f points)%n",
                back/60f, p.shield);
        }

        // ---- 3. what is holding the shield worth? -----------------------
        // Real Game.tick driving a difficulty-3 CPU, with the human stick held
        // DOWN (shield) or centred (no input) through the pointer API.
        for(int hold : new int[]{0,1}){
            int n=8; float sumT=0f; int died=0; float sumDmg=0f;
            for(int s2=0;s2<n;s2++){
                Game g=boot(0,0,200+s2);
                g.profile.difficulty=3;
                call(g,"debugStartFight",new Class<?>[]{});
                Fighter p=f(g,0), c=f(g,1);
                p.reset(700,610); c.reset(950,610);
                p.maxHealth=p.health=100f; c.giveWeapon(Weapons.SWORD);
                p.spawnT=c.spawnT=0f;
                g.pointerDown(1,400f,500f);
                int fr=0;
                for(;fr<60*20;fr++){
                    g.pointerMove(1,400f, hold==1?573f:500f);
                    g.tick(1f/60f);
                    if(p.dead) break;
                }
                if(p.dead){ died++; sumT+=fr/60f; sumDmg+=100f; }
                else sumT+=fr/60f;
            }
            System.out.printf("%-18s died %d/%d, avg round %.1fs, avg damage taken %.0f%n",
                hold==1?"stick held DOWN":"stick centred", died, n, sumT/n,
                died==0?0f:sumDmg/died);
        }

        // ---- 4. does the CPU ever shield? --------------------------------
        {
            Game g=boot(0,0,7);
            call(g,"debugStartFight",new Class<?>[]{});
            int cpuShieldFrames=0, playerShieldFrames=0;
            for(int i=0;i<60*30;i++){
                g.tick(1f/60f);
                if(f(g,1).shieldOn) cpuShieldFrames++;
                if(f(g,0).shieldOn) playerShieldFrames++;
            }
            System.out.printf("shield frames in 30 s: CPU %d, idle player %d%n",
                cpuShieldFrames, playerShieldFrames);
        }

        // ---- 5. post-break punish ---------------------------------------
        {
            Fighter p=new Fighter(0,"P",600,610,false); p.reset(600,610); p.spawnT=0f;
            p.maxHealth=p.health=200f;
            int hits=0; float hp0=p.health; float hurtAtBreak=0f;
            for(int i=0;i<60*8;i++){
                p.input(0f,1f,0f,false,false,true);
                p.update(1f/60f,Maps.ALL[0],true,true,true);
                if(p.shieldOn && p.iframes<=0f && p.hurtT<=0f){
                    p.damage(17f,0f,0f,false); hits++;
                    if(p.shield<=0f){ hurtAtBreak=p.hurtT; break; }
                }
            }
            System.out.printf("break: %d hits, %.1f HP lost, hurtT %.2fs, shield %.0f, shieldOn %b%n",
                hits, hp0-p.health, hurtAtBreak, p.shield, p.shieldOn);
        }
    }
}
