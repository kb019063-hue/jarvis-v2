package com.arena.duelist;

import android.content.Context;
import android.content.SharedPreferences;

/** Player profile: coins, unlocks, settings, records. Stored in SharedPreferences. */
public final class Profile {

    private final SharedPreferences sp;

    public int coins;
    public int wins;
    public int losses;
    public int streak;
    public int bestStreak;
    public int skinColor;
    public int hairStyle;
    public int starter;
    public boolean gravity = true;
    public boolean instantKO = true;
    public boolean energyShield = true;
    public boolean sound = true;
    public boolean voice = true;
    public int difficulty = 1; // 0 easy, 1 normal, 2 hard, 3 insane
    public final boolean[] weaponUnlocked = new boolean[Weapons.COUNT];

    public Profile(Context ctx) {
        sp = ctx.getSharedPreferences("duelist", Context.MODE_PRIVATE);
        coins = sp.getInt("coins", 250);
        wins = sp.getInt("wins", 0);
        losses = sp.getInt("losses", 0);
        bestStreak = sp.getInt("streak", 0);
        streak = sp.getInt("curstreak", 0);
        skinColor = sp.getInt("skin", 0xFFF2F2F2);
        hairStyle = sp.getInt("hair", 0);
        starter = sp.getInt("starter", 0);
        gravity = sp.getBoolean("gravity", true);
        instantKO = sp.getBoolean("instantko", true);
        energyShield = sp.getBoolean("shield", true);
        sound = sp.getBoolean("sound", true);
        voice = sp.getBoolean("voice", true);
        difficulty = sp.getInt("difficulty", 1);
        for (int i = 0; i < Weapons.COUNT; i++) {
            weaponUnlocked[i] = sp.getBoolean("w" + i, Weapons.DEFS[i].cost == 0);
        }
        if (starter < 0 || starter >= Weapons.COUNT || !weaponUnlocked[starter]) {
            starter = 0;
        }
    }

    public boolean unlocked(int weapon) {
        return weapon >= 0 && weapon < Weapons.COUNT && weaponUnlocked[weapon];
    }

    public boolean buy(int weapon) {
        if (weapon < 0 || weapon >= Weapons.COUNT || weaponUnlocked[weapon]) {
            return false;
        }
        int cost = Weapons.DEFS[weapon].cost;
        if (coins < cost) {
            return false;
        }
        coins -= cost;
        weaponUnlocked[weapon] = true;
        save();
        return true;
    }

    public int ownedCount() {
        int n = 0;
        for (int i = 0; i < Weapons.COUNT; i++) {
            if (weaponUnlocked[i]) {
                n++;
            }
        }
        return n;
    }

    public void save() {
        SharedPreferences.Editor e = sp.edit();
        e.putInt("coins", coins);
        e.putInt("wins", wins);
        e.putInt("losses", losses);
        e.putInt("streak", bestStreak);
        e.putInt("curstreak", streak);
        e.putInt("skin", skinColor);
        e.putInt("hair", hairStyle);
        e.putInt("starter", starter);
        e.putBoolean("gravity", gravity);
        e.putBoolean("instantko", instantKO);
        e.putBoolean("shield", energyShield);
        e.putBoolean("sound", sound);
        e.putBoolean("voice", voice);
        e.putInt("difficulty", difficulty);
        for (int i = 0; i < Weapons.COUNT; i++) {
            e.putBoolean("w" + i, weaponUnlocked[i]);
        }
        e.apply();
    }

    public void addCoins(int n) {
        coins += n;
        if (coins < 0) {
            coins = 0;
        }
        save();
    }
}
