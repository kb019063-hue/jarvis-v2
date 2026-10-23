package com.arena.duelist;

/** A flying weapon: kunai, arrow, bolt, bomb, boomerang. */
public final class Projectile {

    public float x, y, vx, vy, r;
    public float damage, knock;
    public int color, owner, life, special;
    public float angle, spin;
    public boolean dead;
    public boolean returning;
    public final int weapon;
    public float timer;

    public Projectile(int weapon, int owner, float x, float y, float vx, float vy) {
        Weapons.Def d = Weapons.DEFS[weapon];
        this.weapon = weapon;
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = d.damage;
        this.knock = d.knock;
        this.color = d.color;
        this.special = d.special;
        this.r = special == 1 ? 16f : 9f;
        this.life = 240;
        this.angle = (float) Math.atan2(vy, vx);
        this.spin = special == 3 ? 24f : 0f;
    }

    public void update(float dt) {
        timer += dt;
        x += vx * dt;
        y += vy * dt;
        angle += spin * dt;
        if (special == 1) {
            vy += 900f * dt;
        }
        life--;
        if (life <= 0) {
            dead = true;
        }
    }
}
