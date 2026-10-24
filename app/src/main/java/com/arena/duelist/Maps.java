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

    public static final Arena FOOTBALL = new Arena("Football Stadium",
            0xFF163B54, 0xFF3C9364, 0xFF24583D, 0xFFB6F59C, 0xFF2A684A,
            1400, 820, 400, 1000, 560).add(-90, 640, 1580, 180, 1);

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
        // Screens are 2.2-2.4x wider than they are tall, so arenas are too:
        // that ratio is what lets the camera fit both fighters without showing
        // a band of empty world above and below the map.
        //
        // Every arena is a three-tier climb. Steps between tiers are <= 140 px
        // against a 177 px jump apex, and neighbouring ledges are always within
        // one jump of each other, so there is no platform a player cannot reach.
        // Hazards only ever flank the fighting floor, never cross it: a route
        // that needs a hazard jump is a route the CPU cannot use.
        return new Arena[] {
            new Arena("Rooftop Dusk", 0xFF2B1B4A, 0xFFF2885C, 0xFF3A2A3A, 0xFFE8A06B, 0xFF6B4A7A,
                    2200, 980, 836, 1364, 770)
                    .add(0, 800, 2200, 180, 0)
                    .add(250, 660, 300, 22, 2).add(940, 660, 320, 22, 2)
                    .add(1650, 660, 300, 22, 2).add(1020, 520, 160, 22, 2),
            new Arena("Neon Alley", 0xFF0A0E26, 0xFF27193F, 0xFF1B1B2E, 0xFF51E5FF, 0xFF33224A,
                    2100, 950, 798, 1302, 750)
                    .add(0, 780, 2100, 170, 2)
                    .add(120, 640, 420, 20, 2).add(860, 640, 380, 20, 2)
                    .add(1560, 640, 420, 20, 2).add(960, 500, 200, 20, 2),
            new Arena("Lava Core", 0xFF200A0A, 0xFF7A2410, 0xFF33181A, 0xFFFF7A3C, 0xFF5A1E18,
                    2400, 1050, 912, 1488, 820)
                    .add(100, 850, 2200, 200, 0)
                    .add(300, 710, 340, 22, 2).add(1030, 710, 340, 22, 2)
                    .add(1760, 710, 340, 22, 2).add(1100, 570, 200, 22, 2)
                    .haz(0, 890, 100, 160, HAZ_LAVA).haz(2300, 890, 100, 160, HAZ_LAVA),
            new Arena("Sky Temple", 0xFF7FC7E8, 0xFFE7F7FF, 0xFF8C6A4A, 0xFFFFFFFF, 0xFFB8D8E8,
                    2200, 1000, 836, 1364, 790)
                    .add(0, 820, 2200, 180, 1)
                    .add(200, 690, 380, 20, 1).add(830, 690, 540, 20, 1)
                    .add(1620, 690, 380, 20, 1).add(900, 560, 400, 20, 1),
            new Arena("Frozen Lake", 0xFF9AD7F0, 0xFFDFF3FF, 0xFF7FB8D0, 0xFFFFFFFF, 0xFFA8DCF0,
                    2300, 1000, 874, 1426, 790)
                    .add(0, 820, 2300, 180, 3)
                    .add(260, 690, 360, 20, 3).add(820, 690, 300, 20, 3)
                    .add(1640, 690, 400, 20, 3).add(980, 560, 300, 20, 3),
            new Arena("Bamboo Grove", 0xFF173A2A, 0xFF4E7A3A, 0xFF2A3A22, 0xFF9BE86B, 0xFF3A5A2A,
                    2000, 980, 760, 1240, 770)
                    .add(0, 800, 2000, 180, 1)
                    .add(180, 660, 260, 20, 1).add(560, 540, 220, 20, 1)
                    .add(900, 660, 240, 20, 1)
                    .add(1600, 660, 260, 20, 1).add(1520, 540, 220, 20, 1),
            new Arena("Orbital Deck", 0xFF05060E, 0xFF151A33, 0xFF1E2440, 0xFF7D8BFF, 0xFF2A3050,
                    2400, 1060, 912, 1488, 830)
                    .add(200, 860, 2000, 200, 2)
                    .add(420, 720, 360, 20, 2).add(1000, 720, 400, 20, 2)
                    .add(1620, 720, 360, 20, 2).add(1080, 580, 240, 20, 2)
                    .haz(0, 900, 200, 160, HAZ_SPIKES).haz(2200, 900, 200, 160, HAZ_SPIKES),
            new Arena("Volcano Rim", 0xFF1A0A0A, 0xFFC24A1A, 0xFF3A1E16, 0xFFFFB04A, 0xFF6A2A18,
                    2400, 1080, 912, 1488, 850)
                    .add(200, 880, 2000, 200, 0)
                    .add(480, 740, 360, 20, 0).add(1020, 740, 360, 20, 0)
                    .add(1560, 740, 360, 20, 0).add(1100, 600, 200, 20, 0)
                    .haz(0, 920, 200, 160, HAZ_LAVA).haz(2200, 920, 200, 160, HAZ_LAVA),
            new Arena("Dojo Night", 0xFF1B1420, 0xFF3A2A32, 0xFF241C24, 0xFFFFC46B, 0xFF4A3540,
                    1900, 940, 722, 1178, 730)
                    .add(0, 760, 1900, 180, 1)
                    .add(180, 620, 340, 20, 1).add(800, 620, 300, 20, 1)
                    .add(1380, 620, 340, 20, 1).add(860, 480, 180, 20, 1),
            new Arena("Chaos Pit", 0xFF14061E, 0xFF3E1650, 0xFF2A1430, 0xFFE86BFF, 0xFF4A2058,
                    2400, 1060, 912, 1488, 830)
                    .add(150, 860, 2100, 200, 3)
                    .add(340, 720, 300, 20, 3).add(1060, 720, 260, 20, 3)
                    .add(1760, 720, 300, 20, 3).add(1140, 580, 160, 20, 3)
                    .haz(0, 900, 150, 160, HAZ_LAVA).haz(2250, 900, 150, 160, HAZ_LAVA)
        };
    }

    private Maps() {
    }
}
