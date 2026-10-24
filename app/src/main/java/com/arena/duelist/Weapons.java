package com.arena.duelist;

/** Weapon catalogue. Melee, thrown and projectile weapons. */
public final class Weapons {

    public static final int MELEE = 0;
    public static final int RANGED = 1;
    public static final int THROW = 2;

    public static final class Def {
        public final String name;
        public final int type;
        public final float damage;
        public final float reach;
        public final float cooldown;
        public final float knock;
        public final int color;
        public final int cost;
        public final float speed;
        public final int ammo;
        public final int special; // 0 none, 1 explosive, 2 homing, 3 boomerang
        public final String blurb;

        Def(String name, int type, float damage, float reach, float cooldown, float knock,
            int color, int cost, float speed, int ammo, int special, String blurb) {
            this.name = name;
            this.type = type;
            this.damage = damage;
            this.reach = reach;
            this.cooldown = cooldown;
            this.knock = knock;
            this.color = color;
            this.cost = cost;
            this.speed = speed;
            this.ammo = ammo;
            this.special = special;
            this.blurb = blurb;
        }
    }

    public static final int FIST = 0;
    public static final int SWORD = 1;
    public static final int SPEAR = 2;
    public static final int DUAL_SABERS = 3;
    public static final int KUNAI = 4;
    public static final int HAMMER = 5;
    public static final int BOW = 6;
    public static final int WAND = 7;
    public static final int BOMB = 8;
    public static final int BOOMERANG = 9;
    public static final int PISTOL = 10;
    public static final int ENERGY_BLADE = 11;

    /**
     * Attack cadence, in seconds between the starts of two swings.
     *
     * The 0.3.1 table fired the light weapons 3.5-4.6 times per second, which
     * on a phone reads as "everything is too fast": holding the one stick
     * auto-attacks, so the player machine-guns without ever committing. These
     * values cap the fastest weapon at ~2.5 swings/s and give slow weapons a
     * readable wind-up. Balance of damage/reach is unchanged.
     */
    public static final Def[] DEFS = {
            new Def("Fists", MELEE, 8f, 36f, 0.45f, 250f, 0xFFE8E8E8, 0, 0, -1, 0,
                    "Release: rushing power jab."),
            new Def("Sword", MELEE, 17f, 56f, 0.58f, 360f, 0xFFD8E4FF, 120, 0, -1, 0,
                    "Release: directional dash slash."),
            new Def("Spear", MELEE, 14f, 88f, 0.70f, 320f, 0xFFC0E0D0, 320, 0, -1, 0,
                    "Release: long spear lunge."),
            new Def("Dual Sabers", MELEE, 11f, 50f, 0.40f, 200f, 0xFFFFD9A0, 520, 0, -1, 0,
                    "Release: all-around saber spin."),
            new Def("Kunai", THROW, 15f, 0f, 0.54f, 280f, 0xFFBFD8FF, 260, 900f, 3, 0,
                    "Release: three-blade fan. 3 ammo."),
            new Def("Hammer", MELEE, 27f, 50f, 1.05f, 760f, 0xFFE0B080, 700, 0, -1, 0,
                    "Release: heavy shockwave."),
            new Def("Bow", RANGED, 19f, 0f, 0.78f, 300f, 0xFFB8E0A0, 460, 1250f, 4, 0,
                    "Release: triple-arrow volley. 4 ammo."),
            new Def("Wand", RANGED, 10f, 0f, 0.50f, 180f, 0xFFD9A0FF, 760, 560f, 6, 2,
                    "Release: five seeking bolts. 6 ammo."),
            new Def("Bomb", THROW, 34f, 0f, 1.25f, 820f, 0xFFE86B6B, 880, 720f, 2, 1,
                    "Release: bomb cluster. 2 ammo."),
            new Def("Boomerang", THROW, 13f, 0f, 1.15f, 240f, 0xFFFFC46B, 960, 820f, 1, 3,
                    "Release: twin returning blades."),
            new Def("Pistol", RANGED, 9f, 0f, 0.36f, 150f, 0xFFCFCFCF, 1100, 1650f, 8, 0,
                    "Release: three-bullet spread. 8 ammo."),
            new Def("Energy Blade", MELEE, 21f, 66f, 0.52f, 440f, 0xFF7DF9FF, 1500, 0, -1, 0,
                    "Release: dash plus plasma wave."),
    };

    public static final int COUNT = DEFS.length;

    public static String name(int i) {
        if (i < 0 || i >= COUNT) {
            return "Fists";
        }
        return DEFS[i].name;
    }

    public static int color(int i) {
        if (i < 0 || i >= COUNT) {
            return 0xFFFFFFFF;
        }
        return DEFS[i].color;
    }

    private Weapons() {
    }
}
