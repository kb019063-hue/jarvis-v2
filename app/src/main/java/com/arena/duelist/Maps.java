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
        // Each arena has ONE continuous fighting floor between the spawns.
        // Optional ledges flank it; hazards belong at the outer edges, never
        // across the route players need just to reach each other.
        return new Arena[] {
            new Arena("Rooftop Dusk", 0xFF2B1B4A, 0xFFF2885C, 0xFF3A2A3A, 0xFFE8A06B, 0xFF6B4A7A,
                    1400, 820, 480, 920, 610)
                    .add(0, 640, 1400, 180, 0)
                    .add(180, 470, 240, 24, 2).add(860, 470, 240, 24, 2),
            new Arena("Neon Alley", 0xFF0A0E26, 0xFF27193F, 0xFF1B1B2E, 0xFF51E5FF, 0xFF33224A,
                    1400, 820, 480, 920, 610)
                    .add(0, 640, 1400, 180, 2)
                    .add(160, 480, 220, 24, 2).add(1020, 480, 220, 24, 2),
            new Arena("Lava Core", 0xFF200A0A, 0xFF7A2410, 0xFF33181A, 0xFFFF7A3C, 0xFF5A1E18,
                    1500, 840, 520, 980, 630)
                    .add(100, 660, 1300, 180, 0)
                    .add(240, 490, 200, 24, 2).add(1060, 490, 200, 24, 2)
                    .haz(0, 740, 100, 100, HAZ_LAVA).haz(1400, 740, 100, 100, HAZ_LAVA),
            new Arena("Sky Temple", 0xFF7FC7E8, 0xFFE7F7FF, 0xFF8C6A4A, 0xFFFFFFFF, 0xFFB8D8E8,
                    1400, 820, 480, 920, 590)
                    .add(0, 620, 1400, 200, 1)
                    .add(180, 450, 200, 24, 1).add(1020, 450, 200, 24, 1),
            new Arena("Frozen Lake", 0xFF9AD7F0, 0xFFDFF3FF, 0xFF7FB8D0, 0xFFFFFFFF, 0xFFA8DCF0,
                    1500, 820, 520, 980, 570)
                    .add(0, 600, 1500, 220, 3)
                    .add(200, 430, 220, 22, 3).add(1080, 430, 220, 22, 3),
            new Arena("Bamboo Grove", 0xFF173A2A, 0xFF4E7A3A, 0xFF2A3A22, 0xFF9BE86B, 0xFF3A5A2A,
                    1400, 820, 480, 920, 610)
                    .add(0, 640, 1400, 180, 1)
                    .add(160, 480, 220, 22, 1).add(1020, 480, 220, 22, 1),
            new Arena("Orbital Deck", 0xFF05060E, 0xFF151A33, 0xFF1E2440, 0xFF7D8BFF, 0xFF2A3050,
                    1500, 840, 520, 980, 630)
                    .add(100, 660, 1300, 180, 2)
                    .add(220, 490, 220, 22, 2).add(1060, 490, 220, 22, 2)
                    .haz(0, 740, 100, 100, HAZ_SPIKES).haz(1400, 740, 100, 100, HAZ_SPIKES),
            new Arena("Volcano Rim", 0xFF1A0A0A, 0xFFC24A1A, 0xFF3A1E16, 0xFFFFB04A, 0xFF6A2A18,
                    1500, 860, 520, 980, 650)
                    .add(100, 680, 1300, 180, 0)
                    .add(240, 510, 220, 22, 0).add(1040, 510, 220, 22, 0)
                    .haz(0, 760, 100, 100, HAZ_LAVA).haz(1400, 760, 100, 100, HAZ_LAVA),
            new Arena("Dojo Night", 0xFF1B1420, 0xFF3A2A32, 0xFF241C24, 0xFFFFC46B, 0xFF4A3540,
                    1300, 800, 450, 850, 590)
                    .add(0, 620, 1300, 180, 1)
                    .add(160, 450, 180, 22, 1).add(960, 450, 180, 22, 1),
            new Arena("Chaos Pit", 0xFF14061E, 0xFF3E1650, 0xFF2A1430, 0xFFE86BFF, 0xFF4A2058,
                    1500, 860, 520, 980, 630)
                    .add(100, 660, 1300, 200, 3)
                    .add(260, 490, 200, 22, 3).add(1040, 490, 200, 22, 3)
                    .haz(0, 760, 100, 100, HAZ_LAVA).haz(1400, 760, 100, 100, HAZ_LAVA)
        };
    }

    private Maps() {
    }
}
