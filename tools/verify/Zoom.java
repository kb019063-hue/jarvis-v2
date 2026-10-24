import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Zoom in real matches, not in a posed screenshot.
 *
 * Runs seeded CPU duels and samples the camera every frame while the fighters
 * are alive: how far in is the camera when they are close, how far out when
 * they are apart, and how long it spends at either end. The point of the maps
 * round was "zoom out so both fighters stay visible"; this is the number that
 * says whether it actually does that during play.
 */
public final class Zoom {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }

    public static void main(String[] args){
        System.out.printf("%-14s %6s %6s %6s %8s %8s %7s%n",
                "arena","min","median","max","<0.7","<0.85","clipped");
        for(int map=0;map<Maps.ALL.length;map++){
            float[] samples=new float[0];
            java.util.ArrayList<Float> all=new java.util.ArrayList<Float>();
            int clipped=0, total=0;
            for(long seed=1;seed<=5;seed++){
                Game g=new Game(new Context(),new Game.Host(){
                    public void announce(String t){} public void setBright(boolean on){} public void vibrate(int ms){} public void sfx(int id){}});
                g.resize(1600,720);
                call(g,"debugSeed",new Class<?>[]{long.class},seed*7919L+map);
                call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},0,map);
                call(g,"debugSuppressPickups",new Class<?>[]{});
                for(int f=0;f<60*40;f++){
                    g.tick(1f/60f);
                    Object cam=invoke(g,"debugCamera",new Class<?>[]{});
                    float zoom=((float[])cam)[2], camX=((float[])cam)[0], camY=((float[])cam)[1];
                    int wins=(Integer)invoke(g,"debugRoundWinsA",new Class<?>[]{})
                            + (Integer)invoke(g,"debugRoundWinsB",new Class<?>[]{});
                    if(wins>0) break;
                    all.add(zoom);
                    float viewW=1200f, viewH=540f;
                    for(int i=0;i<2;i++){
                        Fighter fi=(Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},i);
                        float sx=(fi.x-camX)*zoom, sy=(fi.y-camY)*zoom;
                        total++;
                        if(sx<0||sx>viewW||sy<0||sy>viewH) clipped++;
                    }
                }
            }
            samples=new float[all.size()];
            for(int i=0;i<samples.length;i++) samples[i]=all.get(i);
            java.util.Arrays.sort(samples);
            if(samples.length==0) continue;
            int below07=0, below085=0;
            for(float z:samples){ if(z<0.70f) below07++; if(z<0.85f) below085++; }
            System.out.printf("%-14s %6.2f %6.2f %6.2f %7.0f%% %7.0f%%  %d/%d%n",
                Maps.ALL[map].name, samples[0], samples[samples.length/2], samples[samples.length-1],
                100f*below07/samples.length, 100f*below085/samples.length, clipped, total);
        }
    }
}
