package com.arena.duelist;

import android.graphics.RectF;

import java.util.ArrayList;

/** Arena definitions: platforms, hazards and themes. */
public final class Maps {

    public static final int HAZ_WATER = 0;
    public static final int HAZ_LAVA = 1;
    public static final int HAZ_SPIKES = 2;

    public static final class Platform {
        public final RectF r;
        public final int style; // 0 stone, 1 wood, 2 metal, 3 crystal

        public Platform(float x, float y, float w, float h, int style) {
            this.r = new RectF(x, y, x + w, y + h);
            this.style = style;
        }
    }

    public static final class Hazard {
        public final RectF r;
        public final int type;

        public Hazard(float x, float y, float w, float h, int type) {
            this.r = new RectF(x, y, x + w, y + h);
            this.type = type;
        }
    }

    public static final class Arena {
        public final String name;
        public final int skyTop, skyBottom, ground, accent, deco;
        public final RectF bounds;
        public final ArrayList<Platform> platforms = new ArrayList<Platform>();
        public final ArrayList<Hazard> hazards = new ArrayList<Hazard>();
        public final float spawnAy, spawnBy;
        public final float spawnAx, spawnBx;

        Arena(String name, int skyTop, int skyBottom, int ground, int accent, int deco,
              float w, float h, float sax, float sbx, float say) {
            this.name = name;
            this.skyTop = skyTop;
            this.skyBottom = skyBottom;
            this.ground = ground;
            this.accent = accent;
            this.deco = deco;
            this.bounds = new RectF(0, 0, w, h);
            this.spawnAx = sax;
            this.spawnBx = sbx;
            this.spawnAy = say;
            this.spawnBy = say;
        }

        Arena add(float x, float y, float w, float h, int style) {
            platforms.add(new Platform(x, y, w, h, style));
            return this;
        }

        Arena haz(float x, float y, float w, float h, int type) {
            hazards.add(new Hazard(x, y, w, h, type));
            return this;
        }
    }

    public static final Arena[] ALL = build();

    public static final String[] NAMES = collectNames(ALL);

    /** Nudges a spawn x onto the nearest platform so nobody starts over a pit. */
    public static float safeSpawnX(Arena arena, float x, float spawnY) {
        float bestX = x;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < arena.platforms.size(); i++) {
            Platform pl = arena.platforms.get(i);
            if (pl.r.top < spawnY - 20f) {
                continue; // platform above the fighter's feet, can't land on it
            }
            float cx = Math.max(pl.r.left + 46f, Math.min(pl.r.right - 46f, x));
            float dist = Math.abs(cx - x);
            if (dist < bestDist && pl.r.width() > 92f) {
                bestDist = dist;
                bestX = cx;
            }
        }
        return bestX;
    }

    private static String[] collectNames(Arena[] arr) {
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i].name;
        }
        return out;
    }

    private static Arena[] build() {
        ArrayList<Arena> out = new ArrayList<Arena>();

        out.add(new Arena("Rooftop Dusk", 0xFF2B1B4A, 0xFFF2885C, 0xFF3A2A3A, 0xFFE8A06B, 0xFF6B4A7A,
                1400, 820, 380, 1020, 520)
                .add(0, 640, 520, 180, 0)
                .add(880, 640, 520, 180, 0)
                .add(300, 470, 240, 26, 2)
                .add(860, 470, 240, 26, 2)
                .add(560, 330, 280, 26, 2)
                .haz(520, 780, 360, 100, HAZ_WATER));

        out.add(new Arena("Neon Alley", 0xFF0A0E26, 0xFF27193F, 0xFF1B1B2E, 0xFF51E5FF, 0xFF33224A,
                1400, 820, 430, 970, 520)
                .add(0, 640, 560, 180, 2)
                .add(840, 640, 560, 180, 2)
                .add(120, 460, 280, 24, 1)
                .add(1000, 460, 280, 24, 1)
                .add(590, 350, 220, 24, 1)
                .add(640, 560, 120, 20, 1)
                .haz(560, 760, 280, 120, HAZ_LAVA));

        out.add(new Arena("Lava Core", 0xFF200A0A, 0xFF7A2410, 0xFF33181A, 0xFFFF7A3C, 0xFF5A1E18,
                1500, 840, 260, 1240, 540)
                .add(0, 660, 420, 200, 0)
                .add(1080, 660, 420, 200, 0)
                .add(520, 620, 460, 30, 2)
                .add(560, 420, 180, 24, 2)
                .add(760, 330, 180, 24, 2)
                .haz(420, 760, 660, 140, HAZ_LAVA));

        out.add(new Arena("Sky Temple", 0xFF7FC7E8, 0xFFE7F7FF, 0xFF8C6A4A, 0xFFFFFFFF, 0xFFB8D8E8,
                1400, 820, 470, 930, 500)
                .add(0, 620, 1400, 240, 1)
                .add(240, 440, 260, 24, 1)
                .add(900, 440, 260, 24, 1)
                .add(600, 300, 200, 24, 1));

        out.add(new Arena("Frozen Lake", 0xFF9AD7F0, 0xFFDFF3FF, 0xFF7FB8D0, 0xFFFFFFFF, 0xFFA8DCF0,
                1500, 820, 450, 1050, 480)
                .add(0, 600, 1500, 260, 3)
                .add(380, 420, 220, 22, 3)
                .add(900, 420, 220, 22, 3)
                .add(640, 280, 220, 22, 3));

        out.add(new Arena("Bamboo Grove", 0xFF173A2A, 0xFF4E7A3A, 0xFF2A3A22, 0xFF9BE86B, 0xFF3A5A2A,
                1400, 820, 500, 900, 520)
                .add(0, 640, 1400, 220, 1)
                .add(180, 500, 200, 22, 1)
                .add(1020, 500, 200, 22, 1)
                .add(560, 420, 280, 22, 1)
                .add(600, 250, 200, 18, 1));

        out.add(new Arena("Orbital Deck", 0xFF05060E, 0xFF151A33, 0xFF1E2440, 0xFF7D8BFF, 0xFF2A3050,
                1500, 840, 250, 1250, 520)
                .add(0, 660, 460, 200, 2)
                .add(1040, 660, 460, 200, 2)
                .add(520, 640, 460, 26, 2)
                .add(560, 440, 170, 22, 2)
                .add(770, 440, 170, 22, 2)
                .haz(460, 760, 580, 120, HAZ_SPIKES));

        out.add(new Arena("Volcano Rim", 0xFF1A0A0A, 0xFFC24A1A, 0xFF3A1E16, 0xFFFFB04A, 0xFF6A2A18,
                1500, 860, 250, 1250, 540)
                .add(0, 680, 480, 200, 0)
                .add(1020, 680, 480, 200, 0)
                .add(540, 600, 420, 28, 0)
                .add(660, 380, 120, 22, 0)
                .add(820, 470, 120, 22, 0)
                .haz(480, 800, 540, 100, HAZ_LAVA));

        out.add(new Arena("Dojo Night", 0xFF1B1420, 0xFF3A2A32, 0xFF241C24, 0xFFFFC46B, 0xFF4A3540,
                1300, 800, 420, 880, 500)
                .add(0, 620, 1300, 220, 1)
                .add(300, 460, 220, 22, 1)
                .add(780, 460, 220, 22, 1)
                .add(560, 320, 180, 22, 1));

        out.add(new Arena("Chaos Pit", 0xFF14061E, 0xFF3E1650, 0xFF2A1430, 0xFFE86BFF, 0xFF4A2058,
                1500, 860, 500, 1000, 520)
                .add(0, 660, 380, 200, 3)
                .add(1120, 660, 380, 200, 3)
                .add(420, 620, 660, 26, 3)
                .add(460, 400, 160, 22, 3)
                .add(880, 400, 160, 22, 3)
                .add(650, 300, 200, 22, 3)
                .haz(380, 780, 740, 100, HAZ_LAVA));

        return out.toArray(new Arena[0]);
    }

    private Maps() {
    }
}
