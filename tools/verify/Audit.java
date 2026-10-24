import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/** Broad audit: weapon balance, economy, shield, football, survival. */
public final class Audit {
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
    static int screen(Game g){ return (Integer)invoke(g,"debugScreen",new Class<?>[]{}); }

    public static void main(String[] a){
        // ---------- 1. weapon balance: CPU with each weapon vs idle player ----------
        System.out.println("== TTK, Insane CPU vs idle player (median of 5 seeds, 60 s cap) ==");
        for(int w=0;w<Weapons.COUNT;w++){
            int[] t=new int[5];
            for(int s=0;s<5;s++){
                Game g=boot(0,0,100+s); g.profile.difficulty=3;
                call(g,"debugStartFight",new Class<?>[]{});
                Fighter p=f(g,0), c=f(g,1);
                p.maxHealth=p.health=100f; c.maxHealth=c.health=100f;
                c.giveWeapon(w); p.giveWeapon(Weapons.FIST);
                p.spawnT=c.spawnT=0f;
                int fr=0; while(fr<3600 && !p.dead){ g.tick(1f/60f); fr++; }
                t[s]=fr;
            }
            java.util.Arrays.sort(t);
            System.out.printf("%-13s TTK=%5.1fs   dmg=%-4.0f cd=%.2fs%n",
                Weapons.name(w), t[2]/60f, Weapons.DEFS[w].damage, Weapons.DEFS[w].cooldown);
        }

        // ---------- 2. economy ----------
        int total=0, free=0;
        for(int w=0;w<Weapons.COUNT;w++){ total+=Weapons.DEFS[w].cost; if(Weapons.DEFS[w].cost==0) free++; }
        System.out.printf("%n== Economy ==%nweapons=%d  total cost=%d  (starter free: %d)%n", Weapons.COUNT, total, free);
        System.out.printf("coins per quick-fight win=70 -> %d matches to buy everything%n", (int)Math.ceil(total/70.0));
        System.out.printf("survival: 20/wave + 40 payout; boss: 25/boss + 100%n");

        // ---------- 3. shield ----------
        {
            Game g=boot(0,0,7);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0); p.spawnT=0f; p.health=p.maxHealth=100f;
            p.input(0f,1f,0f,false,false,true);
            p.update(1f/60f,Maps.ALL[0],true,true,Maps.ALL[0]!=null);
            float total_absorbed=0f; int hits=0; float stunAt=-1f;
            for(int i=0;i<40 && !(p.health<100f);i++){
                if(p.shieldOn){
                    float before=p.health;
                    p.hurtT=0f;
                    p.damage(17f,0f,0f,false); // sword-class
                    total_absorbed += before-p.health; hits++;
                    if(!p.shieldOn){ stunAt=i; break; }
                }
                p.input(0f,1f,0f,false,false,true);
                p.update(1f/60f,Maps.ALL[0],true,true,true);
            }
            System.out.printf("%n== Shield ==%n17-dmg hits blocked before break=%d  total damage taken while blocking=%.1f  shield=%.0f/%.0f%n",
                hits, total_absorbed, p.shield, p.maxShield);
        }

        // ---------- 4. football ----------
        {
            for(int mode : new int[]{4,5}){
                Game g=boot(mode,0,3);
                int goals=0; int frames=0;
                for(; frames<60*90; frames++){
                    int a0=(Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{});
                    int b0=(Integer)invoke(g,"debugRoundWinsB",new Class<?>[]{});
                    g.tick(1f/60f);
                    if((Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{})!=a0
                     ||(Integer)invoke(g,"debugRoundWinsB",new Class<?>[]{})!=b0) goals++;
                    if(screen(g)==6) break;
                }
                System.out.printf("football mode=%d goals=%d in %.0fs  screen=%d A=%d B=%d%n",
                    mode, goals, frames/60f, screen(g),
                    (Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{}),
                    (Integer)invoke(g,"debugRoundWinsB",new Class<?>[]{}));
            }
        }

        // ---------- 5. survival ramp ----------
        {
            int[] waves=new int[5];
            for(int s=0;s<5;s++){
                Game g=boot(2,0,50+s); g.profile.difficulty=1;
                call(g,"debugStartFight",new Class<?>[]{});
                Fighter p=f(g,0); p.maxHealth=p.health=100f;
                int fr=0;
                while(fr<60*180 && !p.dead){ g.tick(1f/60f); fr++; }
                waves[s]=(Integer)invoke(g,"debugWave",new Class<?>[]{});
            }
            java.util.Arrays.sort(waves);
            System.out.printf("%n== Survival (passive player, no input) ==%nwaves survived median=%d (min %d max %d)%n",
                waves[2], waves[0], waves[4]);
        }
    }
}
