import android.content.Context;
import com.arena.duelist.*;
import java.lang.reflect.*;

/**
 * Map + camera audit.
 *
 * 1. Geometry: how big is each arena, how many platforms does it really have,
 *    and how varied are the heights?
 * 2. Reachability: can a fighter standing on the floor actually jump onto each
 *    platform? Measured by simulating the jump, not by eyeballing coordinates.
 * 3. Camera: with the two fighters pinned to opposite edges of the arena, is
 *    anything clipped, how far has it zoomed out, and how much of the visible
 *    frame is empty space outside the arena?
 */
public final class MapAudit {
    static void call(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }
    static Object invoke(Object o,String n,Class<?>[] t,Object... a){
        try{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}catch(Exception e){throw new RuntimeException(e);} }

    static Game boot(int mode,int map){
        Game g=new Game(new Context(),new Game.Host(){
            public void announce(String t){} public void setBright(boolean on){} public void vibrate(int ms){} public void sfx(int id){}});
        g.resize(1600,720);
        call(g,"debugSeed",new Class<?>[]{long.class},3L);
        call(g,"debugStartMatch",new Class<?>[]{int.class,int.class},mode,map);
        call(g,"debugSuppressPickups",new Class<?>[]{});
        return g;
    }
    static Fighter f(Game g,int i){ return (Fighter)invoke(g,"debugFighter",new Class<?>[]{int.class},i); }

    /** Puts a fighter on the floor under x and tries to jump onto the platform. */
    static boolean canJumpOnto(Fighter p, Maps.Arena a, Maps.Platform target, float fromX, float fromY){
        p.reset(fromX, fromY);
        p.spawnT = 0f;
        p.hurtT = 0f;
        p.onGround = true;
        for(int i=0;i<110;i++){
            // steer only while outside the target's span, then stop: holding the
            // direction the whole way carried the fighter off the far edge
            float dir = 0f;
            if (p.x < target.r.left + 20f) dir = 1f;
            else if (p.x > target.r.right - 20f) dir = -1f;
            p.input(dir, dir, 0f, i==2, false, false);   // jump once, steer as needed
            p.update(1f/60f, a, true, true);
            if(p.onGround && i>10){
                if(Math.abs((p.y+Fighter.FEET) - target.r.top) < 3f) return true;
            }
        }
        return false;
    }

    public static void main(String[] args){
        System.out.printf("%-14s %6s %6s %5s %5s %6s  %s%n",
                "arena","w","h","plats","tiers","haz","floor span");
        for(int i=0;i<Maps.ALL.length;i++){
            Maps.Arena a=Maps.ALL[i];
            java.util.TreeSet<Integer> tiers=new java.util.TreeSet<Integer>();
            for(Maps.Platform pl : a.platforms) tiers.add(Math.round(pl.r.top));
            Maps.Platform floor=a.platforms.get(0);
            System.out.printf("%-14s %6.0f %6.0f %5d %5d %6d  %.0f..%.0f%n",
                a.name, a.bounds.width(), a.bounds.height(), a.platforms.size(),
                tiers.size(), a.hazards.size(), floor.r.left, floor.r.right);
        }

        System.out.println();
        System.out.println("== spawn lane: solid floor between the two spawns, no hazard in it ==");
        for(int i=0;i<Maps.ALL.length;i++){
            Maps.Arena a=Maps.ALL[i];
            float floor=a.spawnAy+Fighter.FEET;
            int holes=0, hz=0;
            for(float x=a.spawnAx-40;x<=a.spawnBx+40;x+=8f){
                boolean sup=false;
                for(Maps.Platform pl : a.platforms)
                    sup |= x>=pl.r.left && x<=pl.r.right && Math.abs(pl.r.top-floor)<1f;
                if(!sup) holes++;
                for(Maps.Hazard h: a.hazards)
                    if(x>h.r.left && x<h.r.right) hz++;
            }
            System.out.printf("%-14s spawns %.0f..%.0f  floor %.0f  holes=%d hazards=%d%n",
                    a.name, a.spawnAx, a.spawnBx, floor, holes, hz);
        }

        System.out.println();
        System.out.println("== reachability (floor first, then from the tier below) ==");
        for(int i=0;i<Maps.ALL.length;i++){
            Maps.Arena a=Maps.ALL[i];
            StringBuilder sb=new StringBuilder();
            int unreachable=0;
            for(int k=1;k<a.platforms.size();k++){
                Maps.Platform pl=a.platforms.get(k);
                boolean ok=false;
                // try from every surface below it, lowest first: the floor and any
                // platform whose top is within one jump and one hop horizontally
                for(int src=0;src<a.platforms.size() && !ok;src++){
                    Maps.Platform base=a.platforms.get(src);
                    if(base==pl) continue;
                    if(base.r.top < pl.r.top + 40f) continue;      // not below it
                    if(base.r.top - pl.r.top > 190f) continue;     // out of jump range
                    float gap = Math.max(0f, Math.max(base.r.left - pl.r.right, pl.r.left - base.r.right));
                    if(gap > 220f) continue;                        // too far to hop
                    float fromX=Math.max(base.r.left+34f, Math.min(base.r.right-34f,
                            pl.r.centerX()));
                    if(gap > 0f){
                        fromX = Math.max(base.r.left+34f, Math.min(base.r.right-34f,
                                base.r.centerX() + (pl.r.centerX()>base.r.centerX()? base.r.width()*0.5f-34f : -(base.r.width()*0.5f-34f))));
                    }
                    float fromY=base.r.top - Fighter.FEET;
                    ok=canJumpOnto(new Fighter(9,"probe",fromX,fromY,false), a, pl, fromX, fromY);
                }
                if(!ok) unreachable++;
                sb.append(ok?".":"X");
            }
            System.out.printf("%-14s %s  unreachable=%d%n", a.name, sb, unreachable);
        }

        System.out.println();
        System.out.println("== camera with the fighters pinned to opposite edges ==");
        System.out.printf("%-14s %6s %6s %8s %9s  %s%n",
                "arena","zoom","minFit","onscreen","emptyArea","visible width");
        for(int i=0;i<Maps.ALL.length;i++){
            Game g=boot(1,i);
            call(g,"debugStartFight",new Class<?>[]{});
            Maps.Arena a=Maps.ALL[i];
            Fighter p=f(g,0), c=f(g,1);
            p.spawnT=c.spawnT=0f;
            p.x=a.bounds.left+70f; p.y=a.platforms.get(0).r.top-Fighter.FEET-1;
            c.x=a.bounds.right-70f; c.y=a.platforms.get(0).r.top-Fighter.FEET-1;
            for(int k=0;k<90;k++){ g.tick(1f/60f); }
            float[] cam=(float[])invoke(g,"debugCamera",new Class<?>[]{});
            float viewW=1600f/(720f/540f), viewH=540f;
            float left=cam[0], top=cam[1], right=left+viewW/cam[2], bottom=top+viewH/cam[2];
            boolean on = p.x>=left && p.x<=right && c.x>=left && c.x<=right;
            // Empty = visible area outside the arena, ignoring the strip below
            // the floor: that band is deliberately reserved for the touch
            // controls (the floor is pinned to 63 % of the screen height), so
            // counting it as wasted framing would be measuring the HUD, not
            // the camera. Sky above the arena and world beside it still count.
            float clipBottom=Math.min(bottom,a.bounds.bottom);
            float vx0=Math.max(left,a.bounds.left), vx1=Math.min(right,a.bounds.right);
            float vy0=Math.max(top,a.bounds.top), vy1=Math.min(clipBottom,a.bounds.bottom);
            float visible=Math.max(0,vx1-vx0)*Math.max(0,vy1-vy0);
            float area=(right-left)*Math.max(0,clipBottom-top);
            float empty=area>0f ? 1f-visible/area : 1f;
            float below=Math.max(0f,bottom-a.bounds.bottom);
            System.out.printf("%-14s %6.2f %8s %9s %8.0f%%  %5.0f px  +%.0f below floor%n",
                a.name, cam[2], "", on?"both":"CLIP", empty*100f, right-left, below);
        }

        // Mirrors PlayabilityTest.cameraKeepsSeparatedFightersClearOfHudAndThumbAreas:
        // fighters 1080 px apart on the floor, view sizes from a narrow phone to
        // a tablet. The zoom floor must not push the floor behind the on-screen
        // controls (feet <= 360 in the 540-tall world) or a fighter off screen.
        System.out.println();
        System.out.println("== HUD-safe framing, fighters 1080 px apart (map 0) ==");
        int camFails=0;
        for(int width : new int[]{960,1280,1600,2160}){
            Game g=boot(1,0);
            g.resize(width,720);
            call(g,"debugStartFight",new Class<?>[]{});
            Fighter p=f(g,0), c=f(g,1);
            p.spawnT=c.spawnT=0f;
            p.reset(160,610); c.reset(1240,610);
            for(int k=0;k<60;k++) g.tick(1f/60f);
            float[] cam=(float[])invoke(g,"debugCamera",new Class<?>[]{});
            float vw=width/(720f/540f);
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<2;i++){
                Fighter f=f(g,i);
                float sx=(f.x-cam[0])*cam[2];
                float head=(f.y-Fighter.HEAD-cam[1])*cam[2];
                float feet=(f.y+Fighter.FEET-cam[1])*cam[2];
                boolean ok=sx>20 && sx<vw-20 && head>=100 && feet<=360;
                if(!ok) camFails++;
                sb.append(String.format(" [%d] x=%.0f head=%.0f feet=%.0f%s", i, sx, head, feet, ok?"":" FAIL"));
            }
            System.out.printf("w=%-5d zoom=%.3f camX=%.0f camY=%.0f%s%n", width, cam[2], cam[0], cam[1], sb);
        }
        System.out.println("framing failures="+camFails);
    }
}
