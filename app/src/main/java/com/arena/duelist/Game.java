package com.arena.duelist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Random;

/** Whole game: screens, match flow, combat resolution, HUD, touch controls. */
public final class Game {

    public interface Host {
        void announce(String text);

        void setBright(boolean on);

        void vibrate(int ms);

        void sfx(int id);
    }

    // screens
    private static final int S_TITLE = 0;
    private static final int S_MAPS = 1;
    private static final int S_ARSENAL = 2;
    private static final int S_SETTINGS = 3;
    private static final int S_STYLE = 4;
    private static final int S_PLAY = 5;
    private static final int S_RESULT = 6;
    private static final int S_HOW = 7;

    // match phases
    private static final int PH_INTRO = 0;
    private static final int PH_FIGHT = 1;
    private static final int PH_ROUND_END = 2;
    private static final int PH_MATCH_END = 3;
    private static final int PH_SURVIVAL_BREAK = 4;

    // modes
    private static final int M_QUICK = 0;
    private static final int M_LOCAL = 1;
    private static final int M_SURVIVAL = 2;

    // actions
    private static final int A_NONE = 0;
    private static final int A_QUICK = 1;
    private static final int A_LOCAL = 2;
    private static final int A_SURVIVAL = 3;
    private static final int A_ARSENAL = 4;
    private static final int A_SETTINGS = 5;
    private static final int A_STYLE = 6;
    private static final int A_BACK = 7;
    private static final int A_RESUME = 8;
    private static final int A_RESTART = 9;
    private static final int A_QUIT = 10;
    private static final int A_REMATCH = 11;
    private static final int A_MENU = 12;
    private static final int A_HOW = 13;
    private static final int A_MAPS_FROM_PAUSE = 14;
    private static final int A_SOUND = 20;
    private static final int A_VOICE = 21;
    private static final int A_GRAVITY = 22;
    private static final int A_INSTANT = 23;
    private static final int A_SHIELD = 24;
    private static final int A_DIFF = 25;
    private static final int A_RESET = 26;
    private static final int A_MAP_BASE = 100;
    private static final int A_WEAPON_BASE = 200;
    private static final int A_COLOR_BASE = 300;
    private static final int A_HAIR_BASE = 400;

    private final Context ctx;
    private final Host host;
    public final Profile profile;

    private final Particles fx = new Particles();
    private final Random rnd = new Random();
    private final ArrayList<Projectile> shots = new ArrayList<Projectile>();
    private final ArrayList<Pickup> pickups = new ArrayList<Pickup>();
    private final ArrayList<Btn> uiBtns = new ArrayList<Btn>();
    private final Cpu cpu = new Cpu();

    private Maps.Arena arena;
    private int arenaIndex;
    private final Fighter[] fighters = new Fighter[2];

    private int screen = S_TITLE;
    private int pendingMode = M_QUICK;
    private int mode = M_QUICK;
    private int phase = PH_INTRO;
    private float phaseT;
    private int roundNumber = 1;
    private int roundWinsA;
    private int roundWinsB;
    private int wave = 1;
    private boolean paused;
    private boolean countAnnounced;
    private int lastCount = -1;
    private String resultTitle = "";
    private String resultSub = "";
    private int resultCoins;
    private boolean playerWon;
    private float resultDelay;

    private float w = 960, h = 540;
    private float scale = 1f;
    private float viewW = 960f;
    private float viewH = 540f;
    private float camX, camY;
    private static final float ZOOM = 1.55f;
    private float shake;
    private float hitStop;
    private float time;
    private float spawnTimer;
    private float introShake;

    // controls
    private final Stick stickA = new Stick();
    private final Stick stickB = new Stick();
    private final TButton btnAtkA = new TButton();
    private final TButton btnJumpA = new TButton();
    private final TButton btnShieldA = new TButton();
    private final TButton btnAtkB = new TButton();
    private final TButton btnJumpB = new TButton();
    private final TButton btnShieldB = new TButton();
    private final RectF pauseRect = new RectF();
    private int pressedAction;

    private int framesDrawn;

    public Game(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        profile = new Profile(ctx);
        arena = Maps.ALL[0];
        arenaIndex = 0;
        fighters[0] = new Fighter(0, "YOU", 480, 520, false);
        fighters[1] = new Fighter(1, "CPU", 920, 520, true);
        fighters[0].color = profile.skinColor;
        fighters[0].hair = profile.hairStyle;
        fighters[1].hair = 1;
        resetMatch(M_QUICK, 0, false);
    }

    public void resize(int pw, int ph) {
        if (pw <= 0 || ph <= 0) {
            return;
        }
        w = pw;
        h = ph;
        scale = h / 540f;
        viewH = 540f;
        viewW = w / scale;
        layoutControls();
    }

    private void layoutControls() {
        float stickY = viewH - 92f;
        float leftX = 92f;
        float rightX = viewW - 92f;
        stickA.setBase(leftX, stickY, 74f);
        stickB.setBase(rightX, stickY, 74f);

        btnAtkA.set(leftX + 122f, viewH - 64f, 48f, 0);
        btnJumpA.set(leftX + 142f, viewH - 168f, 35f, 1);
        btnShieldA.set(leftX + 58f, viewH - 206f, 29f, 2);

        btnAtkB.set(rightX - 122f, viewH - 64f, 48f, 0);
        btnJumpB.set(rightX - 142f, viewH - 168f, 35f, 1);
        btnShieldB.set(rightX - 58f, viewH - 206f, 29f, 2);

        pauseRect.set(viewW - 60f, 88f, viewW - 20f, 128f);
    }

    // ------------------------------------------------------------------ input

    public void pointerDown(int id, float sx, float sy) {
        float x = sx / scale;
        float y = sy / scale;

        if (screen == S_PLAY) {
            if (paused) {
                handleMenuTap(x, y);
                return;
            }
            if (pauseRect.contains(x, y)) {
                paused = true;
                return;
            }
            if (mode == M_LOCAL) {
                if (stickA.grab(id, x, y)) {
                    return;
                }
                if (stickB.grab(id, x, y)) {
                    return;
                }
                if (btnAtkA.press(id, x, y)) {
                    fighters[0].attackQueued = true;
                    return;
                }
                if (btnJumpA.press(id, x, y)) {
                    fighters[0].jumpQueued = true;
                    sfx(SoundFx.JUMP);
                    return;
                }
                if (btnShieldA.press(id, x, y)) {
                    btnShieldA.held = true;
                    return;
                }
                if (btnAtkB.press(id, x, y)) {
                    fighters[1].attackQueued = true;
                    return;
                }
                if (btnJumpB.press(id, x, y)) {
                    fighters[1].jumpQueued = true;
                    sfx(SoundFx.JUMP);
                    return;
                }
                if (btnShieldB.press(id, x, y)) {
                    btnShieldB.held = true;
                    return;
                }
            } else {
                // single player: left stick + right button cluster
                if (x < viewW * 0.45f) {
                    if (stickA.grabAnywhere(id, x, y)) {
                        return;
                    }
                }
                if (x > viewW * 0.45f) {
                    if (btnAtkB.press(id, x, y)) {
                        fighters[0].attackQueued = true;
                        return;
                    }
                    if (btnJumpB.press(id, x, y)) {
                        fighters[0].jumpQueued = true;
                        sfx(SoundFx.JUMP);
                        return;
                    }
                    if (btnShieldB.press(id, x, y)) {
                        btnShieldB.held = true;
                        return;
                    }
                }
            }
            return;
        }

        if (screen == S_RESULT) {
            handleMenuTap(x, y);
            return;
        }
        handleMenuTap(x, y);
    }

    public void pointerMove(int id, float sx, float sy) {
        float x = sx / scale;
        float y = sy / scale;
        stickA.move(id, x, y);
        stickB.move(id, x, y);
    }

    public void pointerUp(int id, float sx, float sy) {
        stickA.release(id);
        stickB.release(id);
        if (btnAtkA.release(id)) {
            btnAtkA.held = false;
        }
        if (btnJumpA.release(id)) {
            btnJumpA.held = false;
        }
        if (btnShieldA.release(id)) {
            btnShieldA.held = false;
        }
        if (btnAtkB.release(id)) {
            btnAtkB.held = false;
        }
        if (btnJumpB.release(id)) {
            btnJumpB.held = false;
        }
        if (btnShieldB.release(id)) {
            btnShieldB.held = false;
        }
    }

    private void handleMenuTap(float x, float y) {
        for (int i = uiBtns.size() - 1; i >= 0; i--) {
            Btn b = uiBtns.get(i);
            if (b.r.contains(x, y)) {
                act(b.action);
                return;
            }
        }
    }

    private void act(int action) {
        sfx(SoundFx.CLICK);
        if (action >= A_MAP_BASE && action < A_MAP_BASE + 100) {
            int idx = action - A_MAP_BASE;
            if (idx >= 0 && idx < Maps.ALL.length) {
                resetMatch(pendingMode, idx, true);
            }
            return;
        }
        if (action >= A_WEAPON_BASE && action < A_WEAPON_BASE + 100) {
            int idx = action - A_WEAPON_BASE;
            if (profile.unlocked(idx)) {
                profile.starter = idx;
                profile.save();
                host.announce(Weapons.name(idx));
            } else if (profile.buy(idx)) {
                profile.starter = idx;
                host.announce("Unlocked " + Weapons.name(idx));
                host.vibrate(20);
            } else {
                host.announce("Not enough coins");
            }
            return;
        }
        if (action >= A_COLOR_BASE && action < A_COLOR_BASE + 100) {
            profile.skinColor = SKINS[action - A_COLOR_BASE];
            fighters[0].color = profile.skinColor;
            profile.save();
            return;
        }
        if (action >= A_HAIR_BASE && action < A_HAIR_BASE + 10) {
            profile.hairStyle = action - A_HAIR_BASE;
            fighters[0].hair = profile.hairStyle;
            profile.save();
            return;
        }

        switch (action) {
            case A_QUICK:
                pendingMode = M_QUICK;
                screen = S_MAPS;
                break;
            case A_LOCAL:
                pendingMode = M_LOCAL;
                screen = S_MAPS;
                break;
            case A_SURVIVAL:
                pendingMode = M_SURVIVAL;
                screen = S_MAPS;
                break;
            case A_ARSENAL:
                screen = S_ARSENAL;
                break;
            case A_SETTINGS:
                screen = S_SETTINGS;
                break;
            case A_STYLE:
                screen = S_STYLE;
                break;
            case A_HOW:
                screen = S_HOW;
                break;
            case A_BACK:
                screen = S_TITLE;
                break;
            case A_RESUME:
                paused = false;
                break;
            case A_RESTART:
                paused = false;
                resetMatch(mode, arenaIndex, true);
                break;
            case A_QUIT:
                paused = false;
                screen = S_TITLE;
                profile.save();
                break;
            case A_REMATCH:
                resetMatch(mode, arenaIndex, true);
                break;
            case A_MENU:
                screen = S_TITLE;
                break;
            case A_MAPS_FROM_PAUSE:
                paused = false;
                screen = S_MAPS;
                break;
            case A_SOUND:
                profile.sound = !profile.sound;
                profile.save();
                break;
            case A_VOICE:
                profile.voice = !profile.voice;
                profile.save();
                break;
            case A_GRAVITY:
                profile.gravity = !profile.gravity;
                profile.save();
                break;
            case A_INSTANT:
                profile.instantKO = !profile.instantKO;
                profile.save();
                break;
            case A_SHIELD:
                profile.energyShield = !profile.energyShield;
                profile.save();
                break;
            case A_DIFF:
                profile.difficulty = (profile.difficulty + 1) % 4;
                profile.save();
                host.announce(DIFF_NAMES[profile.difficulty]);
                break;
            case A_RESET:
                profile.coins = 250;
                profile.wins = 0;
                profile.losses = 0;
                profile.starter = 0;
                for (int i = 0; i < Weapons.COUNT; i++) {
                    profile.weaponUnlocked[i] = Weapons.DEFS[i].cost == 0;
                }
                profile.save();
                break;
            default:
                break;
        }
    }

    public boolean backPressed() {
        if (screen == S_PLAY) {
            if (paused) {
                screen = S_TITLE;
                paused = false;
            } else {
                paused = true;
            }
            return true;
        }
        if (screen != S_TITLE) {
            screen = (screen == S_RESULT) ? S_TITLE : S_TITLE;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------- loop

    public void tick(float dt) {
        time += dt;
        if (dt > 0.05f) {
            dt = 0.05f;
        }
        fx.update(dt);
        if (shake > 0) {
            shake = Math.max(0, shake - dt * 26f);
        }

        if (screen == S_TITLE) {
            titleIdle(dt);
            return;
        }
        if (screen == S_PLAY) {
            if (paused) {
                return;
            }
            stepMatch(dt);
        }
    }

    private void titleIdle(float dt) {
        pickups.clear();
        shots.clear();
    }

    private void resetMatch(int newMode, int map, boolean startNow) {
        mode = newMode;
        arenaIndex = Math.max(0, Math.min(Maps.ALL.length - 1, map));
        arena = Maps.ALL[arenaIndex];
        float ax = Maps.safeSpawnX(arena, arena.spawnAx, arena.spawnAy + Fighter.FEET);
        float bx = Maps.safeSpawnX(arena, arena.spawnBx, arena.spawnBy + Fighter.FEET);
        fighters[0].reset(ax, arena.spawnAy);
        fighters[1].reset(bx, arena.spawnBy);
        fighters[0].maxHealth = 100;
        fighters[0].health = 100;
        fighters[1].maxHealth = 100;
        fighters[1].health = 100;
        fighters[0].cpu = false;
        fighters[1].cpu = true;
        fighters[1].name = mode == M_LOCAL ? "P2" : "CPU";
        fighters[0].name = "P1";
        fighters[0].color = profile.skinColor;
        fighters[0].hair = profile.hairStyle;
        fighters[1].color = 0xFFE9E9F2;
        fighters[1].hair = 1;
        fighters[1].accent = 0xFFFF4D6D;
        fighters[0].accent = 0xFF4D9BFF;
        fighters[0].giveWeapon(profile.starter);
        fighters[1].giveWeapon(rndWeapon());
        cpu.reset();
        shots.clear();
        pickups.clear();
        fx.clear();
        roundWinsA = 0;
        roundWinsB = 0;
        roundNumber = 1;
        wave = 1;
        spawnTimer = 2.5f;
        phase = PH_INTRO;
        phaseT = 2.6f;
        lastCount = -1;
        countAnnounced = false;
        paused = false;
        resultDelay = 0;
        layoutControls();
        if (startNow) {
            screen = S_PLAY;
            host.announce("Ready");
        }
    }

    private int rndWeapon() {
        if (rnd.nextFloat() < 0.45f) {
            return Weapons.FIST;
        }
        return 1 + rnd.nextInt(Weapons.COUNT - 1);
    }

    // ------------------------------------------------------------------ match

    private void stepMatch(float dt) {
        if (hitStop > 0) {
            hitStop -= dt;
            return;
        }

        phaseT -= dt;

        switch (phase) {
            case PH_INTRO:
                stepIntro();
                for (int i = 0; i < 2; i++) {
                    Fighter f = fighters[i];
                    f.update(dt, arena, profile.gravity, profile.energyShield);
                    fireIfNeeded(f);
                }
                updateCamera(dt);
                break;
            case PH_FIGHT:
                stepFight(dt);
                break;
            case PH_ROUND_END:
                updateWorldFx(dt);
                if (phaseT <= 0) {
                    nextRound();
                }
                break;
            case PH_SURVIVAL_BREAK:
                updateWorldFx(dt);
                if (phaseT <= 0) {
                    spawnWave();
                }
                break;
            case PH_MATCH_END:
                updateWorldFx(dt);
                if (phaseT <= 0 && screen != S_RESULT) {
                    finishMatch();
                }
                break;
            default:
                break;
        }
        updateCamera(dt);
    }

    private void stepIntro() {
        int count = (int) Math.ceil(phaseT - 0.6f);
        if (count > 0 && count != lastCount) {
            lastCount = count;
            if (profile.voice) {
                host.announce(count == 3 ? "Three" : count == 2 ? "Two" : "One");
            }
        }
        if (phaseT <= 0.6f && !countAnnounced) {
            countAnnounced = true;
            phase = PH_FIGHT;
            introShake = 6f;
            shake = 5f;
            if (profile.voice) {
                host.announce("Fight!");
            }
            host.vibrate(30);
        }
    }

    private void stepFight(float dt) {
        spawnTimer -= dt;
        if (spawnTimer <= 0 && pickups.size() < 3 && mode != M_SURVIVAL) {
            spawnTimer = 5.5f + rnd.nextFloat() * 3f;
            spawnPickup();
        }

        readControls(dt);

        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            f.landed = false;
            f.update(dt, arena, profile.gravity, profile.energyShield);
            fireIfNeeded(f);
            if (f.landed) {
                fx.dust(f.x, f.y + Fighter.FEET, 0x88FFFFFF, 5, 20f);
                f.landed = false;
            }
        }

        // fighter-vs-fighter soft push
        Fighter a = fighters[0];
        Fighter b = fighters[1];
        if (!a.dead && !b.dead) {
            float dx = b.x - a.x;
            float adx = Math.abs(dx);
            if (adx < Fighter.HALF_W * 2f && Math.abs(b.y - a.y) < 60f) {
                float push = (Fighter.HALF_W * 2f - adx) * 0.5f;
                float s = dx >= 0 ? 1f : -1f;
                a.x -= s * push;
                b.x += s * push;
                a.vx -= s * 40f;
                b.vx += s * 40f;
            }
        }

        resolveMelee(a, b);
        resolveMelee(b, a);
        updateShots(dt);
        updatePickups(dt);
        checkHazards(a, dt);
        checkHazards(b, dt);
        checkBounds(a);
        checkBounds(b);

        if (mode == M_SURVIVAL) {
            if (b.dead && phase == PH_FIGHT) {
                onEnemyDown();
            } else if (a.dead && phase == PH_FIGHT) {
                endMatch(false);
            }
        } else {
            if ((a.dead || b.dead) && phase == PH_FIGHT) {
                onRoundEnd(a.dead);
            }
        }
    }

    private void readControls(float dt) {
        Fighter a = fighters[0];
        if (mode == M_LOCAL) {
            a.input(stickA.dx, stickA.dx, stickA.dy, btnJumpA.held, false, btnShieldA.held);
            if (btnAtkA.held) {
                a.attackQueued = true;
            }
            Fighter b = fighters[1];
            b.input(stickB.dx, stickB.dx, stickB.dy, btnJumpB.held, false, btnShieldB.held);
            if (btnAtkB.held) {
                b.attackQueued = true;
            }
        } else {
            float jx = stickA.dx;
            float jy = stickA.dy;
            float ax = Math.abs(jx) < 0.12f ? a.facing : jx;
            boolean jump = btnJumpA.held || btnJumpB.held;
            boolean shield = btnShieldA.held || btnShieldB.held;
            a.input(jx, ax, jy, jump, false, shield);
            if (btnAtkB.held) {
                a.attackQueued = true;
            }
        }
        if (fighters[1].cpu) {
            Fighter b = fighters[1];
            int lvl = mode == M_SURVIVAL ? Math.min(3, 1 + wave / 3) : profile.difficulty;
            cpu.think(b, a, arena, dt, lvl);
            b.input(cpu.move(), cpu.aimX(), cpu.aimY(), cpu.jump(), cpu.attack(), cpu.shield());
        }
    }

    /** Spawn a projectile at the start of a ranged/throw swing. */
    private void fireIfNeeded(Fighter f) {
        if (f.dead || !f.attacking || f.firedThisSwing) {
            return;
        }
        Weapons.Def d = Weapons.DEFS[f.weapon];
        if (d.type == Weapons.MELEE) {
            return;
        }
        f.firedThisSwing = true;
        float dirX = f.aimX;
        float dirY = f.aimY;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.2f) {
            dirX = f.facing;
            dirY = 0;
            len = 1f;
        }
        dirX /= len;
        dirY /= len;
        float speed = d.speed;
        if (speed <= 0) {
            speed = 900f;
        }
        float ox = f.x + dirX * 30f;
        float oy = f.y - 8f + dirY * 22f;
        Projectile p = new Projectile(f.weapon, f.id == 0 ? 0 : 1, ox, oy,
                dirX * speed, dirY * speed);
        p.damage = d.damage;
        shots.add(p);
        fx.sparks(ox, oy, d.color, 8, 150f);
        sfx(SoundFx.THROW);
        host.vibrate(8);

        if (f.ammo > 0) {
            f.ammo--;
            if (f.ammo <= 0) {
                f.ammo = -1;
                f.weapon = Weapons.FIST;
                fx.floatText(f.x, f.y - 60, "OUT OF AMMO", 0x88FFFFFF);
            }
        }
    }

    private void resolveMelee(Fighter a, Fighter b) {
        if (a.dead || b.dead || !a.meleeActive() || a.hitThisSwing) {
            return;
        }
        float dx = b.x - a.x;
        float fwd = dx * a.facing;
        float reach = Weapons.DEFS[a.weapon].reach;
        if (fwd > -14f && fwd < reach + 40f && Math.abs(b.y - a.y) < 66f) {
            a.hitThisSwing = true;
            Weapons.Def d = Weapons.DEFS[a.weapon];
            boolean shielded = b.shieldOn && b.shield > 0;
            b.facing = -a.facing;
            b.damage(d.damage, a.facing * d.knock, -90f - d.knock * 0.12f, false);
            float hx = (a.x + b.x) * 0.5f;
            float hy = (a.y + b.y) * 0.5f - 4f;
            if (shielded) {
                sfx(SoundFx.SHIELD);
                fx.sparks(hx, hy, 0xFF7DE8FF, 14, 260f);
                fx.ring(hx, hy, 0xFF8FF0FF, 20, 0.3f);
                shake = Math.max(shake, 3f);
            } else {
                sfx(SoundFx.HIT);
                fx.sparks(hx, hy, 0xFFFFE38A, 16, 320f);
                fx.blood(hx, hy, b.accent, 6, a.facing);
                fx.ring(hx, hy, 0xFFFFD166, 16, 0.22f);
                shake = Math.max(shake, 6f);
                hitStop = 0.045f;
                host.vibrate(12);
                fx.floatText(hx, hy - 34f, "-" + (int) d.damage, 0xFFFF8FA0);
            }
            if (b.health <= 0f) {
                fx.ring(b.x, b.y, 0xFFFF5D5D, 40, 0.5f);
                shake = 12f;
            }
        }
    }

    private void updateShots(float dt) {
        for (int i = shots.size() - 1; i >= 0; i--) {
            Projectile p = shots.get(i);
            p.update(dt);

            Fighter foe = fighters[1 - p.owner];
            Fighter self = fighters[p.owner];

            if (p.special == 2 && !foe.dead) {
                float dx = foe.x - p.x;
                float dy = foe.y - 6f - p.y;
                float len = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                float sp = 560f;
                p.vx += (dx / len) * 900f * dt;
                p.vy += (dy / len) * 900f * dt;
                float cur = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
                p.vx = p.vx / cur * sp;
                p.vy = p.vy / cur * sp;
            }
            if (p.special == 3) {
                if (!p.returning && p.timer > 0.45f) {
                    p.returning = true;
                }
                if (p.returning) {
                    float dx = self.x - p.x;
                    float dy = self.y - 6f - p.y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                    p.vx = dx / len * 900f;
                    p.vy = dy / len * 900f;
                    if (len < 30f) {
                        p.dead = true;
                    }
                }
            }

            // platform collision
            boolean hitWall = false;
            for (int k = 0; k < arena.platforms.size(); k++) {
                if (arena.platforms.get(k).r.contains(p.x, p.y)) {
                    hitWall = true;
                    break;
                }
            }
            if (hitWall || p.x < arena.bounds.left - 200 || p.x > arena.bounds.right + 200
                    || p.y > arena.bounds.bottom + 300) {
                if (p.special == 1) {
                    explode(p);
                } else {
                    fx.sparks(p.x, p.y, p.color, 8, 160f);
                }
                p.dead = true;
            }

            if (!p.dead && !foe.dead && p.owner != foe.id) {
                float dx = foe.x - p.x;
                float dy = (foe.y - 6f) - p.y;
                float rr = p.r + 24f;
                if (dx * dx + dy * dy < rr * rr) {
                    if (p.special == 1) {
                        explode(p);
                    } else {
                        float n = 1f;
                        if (p.special == 3) {
                            n = p.returning ? 1f : 0.8f;
                        }
                        foe.facing = p.vx >= 0 ? 1 : -1;
                        foe.damage(p.damage * n,
                                (p.vx > 0 ? 1 : -1) * p.knock, -120f, false);
                        sfx(SoundFx.HIT);
                        fx.sparks(p.x, p.y, p.color, 12, 240f);
                        fx.floatText(p.x, p.y - 26f, "-" + (int) (p.damage * n), 0xFFFF8FA0);
                        shake = Math.max(shake, 4f);
                        hitStop = 0.03f;
                        host.vibrate(10);
                    }
                    if (!(p.special == 3 && !p.returning)) {
                        p.dead = true;
                    }
                }
            }

            if (p.dead) {
                shots.remove(i);
            }
        }
    }

    private void explode(Projectile p) {
        fx.ring(p.x, p.y, 0xFFFFB347, 26, 0.45f);
        fx.ring(p.x, p.y, 0xFFFF7A3C, 50, 0.55f);
        fx.sparks(p.x, p.y, 0xFFFFC46B, 34, 460f);
        fx.sparks(p.x, p.y, 0xFF8A5A2A, 18, 260f);
        shake = Math.max(shake, 14f);
        host.vibrate(30);
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            if (f.dead) {
                continue;
            }
            float dx = f.x - p.x;
            float dy = (f.y - 6f) - p.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 130f) {
                float t = 1f - d / 130f;
                f.damage(30f * t + 8f, (dx >= 0 ? 1 : -1) * (420f * t + 120f), -300f * t - 60f, false);
                fx.floatText(f.x, f.y - 50f, "-" + (int) (30 * t + 8), 0xFFFF8FA0);
            }
        }
    }

    private void updatePickups(float dt) {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup pk = pickups.get(i);
            pk.t += dt;
            pk.vy += 1500f * dt;
            pk.y += pk.vy * dt;
            for (int k = 0; k < arena.platforms.size(); k++) {
                RectF r = arena.platforms.get(k).r;
                if (pk.x > r.left && pk.x < r.right && pk.y + 12 > r.top && pk.y + 12 < r.top + 40
                        && pk.vy > 0) {
                    pk.y = r.top - 12;
                    pk.vy = 0;
                    pk.onGround = true;
                }
            }
            for (int f = 0; f < 2; f++) {
                Fighter ft = fighters[f];
                if (ft.dead) {
                    continue;
                }
                if (Math.abs(ft.x - pk.x) < 38f && Math.abs(ft.y - pk.y) < 60f) {
                    ft.giveWeapon(pk.weapon);
                    sfx(SoundFx.PICKUP);
                    fx.ring(pk.x, pk.y, Weapons.color(pk.weapon), 18, 0.35f);
                    fx.floatText(pk.x, pk.y - 40f, Weapons.name(pk.weapon), Weapons.color(pk.weapon));
                    pickups.remove(i);
                    break;
                }
            }
        }
    }

    private void spawnPickup() {
        if (arena.platforms.isEmpty()) {
            return;
        }
        Maps.Platform pl = arena.platforms.get(rnd.nextInt(arena.platforms.size()));
        float x = pl.r.left + 40 + rnd.nextFloat() * Math.max(20, pl.r.width() - 80);
        Pickup pk = new Pickup();
        pk.weapon = rndWeapon();
        pk.x = x;
        pk.y = pl.r.top - 160;
        pickups.add(pk);
    }

    private void checkHazards(Fighter f, float dt) {
        if (f.dead) {
            return;
        }
        for (int i = 0; i < arena.hazards.size(); i++) {
            Maps.Hazard hz = arena.hazards.get(i);
            RectF r = hz.r;
            boolean inside = f.x + Fighter.HALF_W > r.left && f.x - Fighter.HALF_W < r.right
                    && f.y + Fighter.FEET > r.top && f.y - Fighter.HEAD < r.bottom;
            if (!inside) {
                continue;
            }
            if (profile.instantKO) {
                f.kill(0);
                fx.ring(f.x, f.y, 0xFFFF9F43, 34, 0.5f);
                fx.blood(f.x, f.y, f.accent, 10, 0);
                shake = 9f;
                return;
            }
            float dps = hz.type == Maps.HAZ_LAVA ? 45f : (hz.type == Maps.HAZ_SPIKES ? 35f : 26f);
            f.health -= dps * dt;
            f.flashT = 0.08f;
            if (rnd.nextFloat() < dt * 8f) {
                fx.sparks(f.x, f.y + 20, hz.type == Maps.HAZ_LAVA ? 0xFFFF7A3C : 0xFF6BD8FF,
                        3, 120f);
            }
            if (f.health <= 0) {
                f.health = 0;
                f.kill(0);
                shake = 8f;
            }
        }
    }

    private void checkBounds(Fighter f) {
        if (!f.dead && f.y > arena.bounds.bottom + 140) {
            f.kill(0);
        }
    }

    private void onRoundEnd(boolean aDead) {
        phase = PH_ROUND_END;
        phaseT = 2.2f;
        boolean bDead = fighters[1].dead;
        if (aDead && bDead) {
            roundWinsA++;
            roundWinsB++;
        } else if (bDead) {
            roundWinsA++;
        } else {
            roundWinsB++;
        }
        fighters[0].finalFlip = aDead;
        fighters[1].finalFlip = bDead;
        shake = 10f;
        fx.ring((fighters[0].x + fighters[1].x) * 0.5f,
                (fighters[0].y + fighters[1].y) * 0.5f - 10, 0xFFFFE38A, 60, 0.7f);
        if (profile.voice) {
            host.announce("K O!");
        }
        sfx(SoundFx.KO);
        host.vibrate(60);
    }

    private void nextRound() {
        if (roundWinsA >= 2 || roundWinsB >= 2) {
            endMatch(roundWinsA > roundWinsB);
            return;
        }
        roundNumber++;
        fighters[0].reset(Maps.safeSpawnX(arena, arena.spawnAx, arena.spawnAy + Fighter.FEET),
                arena.spawnAy);
        fighters[1].reset(Maps.safeSpawnX(arena, arena.spawnBx, arena.spawnBy + Fighter.FEET),
                arena.spawnBy);
        fighters[0].giveWeapon(profile.starter);
        fighters[1].giveWeapon(rndWeapon());
        shots.clear();
        pickups.clear();
        phase = PH_INTRO;
        phaseT = 2.2f;
        lastCount = -1;
        countAnnounced = false;
    }

    private void onEnemyDown() {
        wave++;
        fighters[0].score = wave - 1;
        profile.addCoins(20);
        fx.floatText(fighters[1].x, fighters[1].y - 60, "+20", 0xFFFFD166);
        phase = PH_SURVIVAL_BREAK;
        phaseT = 1.4f;
        shake = 8f;
        if (profile.voice) {
            host.announce("Wave " + wave);
        }
    }

    private void spawnWave() {
        Fighter p = fighters[0];
        Fighter e = fighters[1];
        p.health = Math.min(p.maxHealth, p.health + 22f);
        p.shield = p.maxShield;
        float hpm = 70f + wave * 26f;
        e.reset(Maps.safeSpawnX(arena, arena.spawnBx, arena.spawnBy + Fighter.FEET),
                arena.spawnBy);
        e.maxHealth = hpm;
        e.health = hpm;
        e.color = 0xFFE9E9F2;
        e.accent = 0xFFFF4D6D;
        e.giveWeapon(rndWeapon());
        e.name = "WAVE " + wave;
        e.cpu = true;
        cpu.reset();
        phase = PH_FIGHT;
        spawnTimer = 3f;
    }

    private void endMatch(boolean won) {
        playerWon = won;
        phase = PH_MATCH_END;
        phaseT = 1.3f;
        if (won) {
            resultTitle = "VICTORY";
            resultCoins = mode == M_SURVIVAL ? 40 : 70;
            profile.wins++;
        } else {
            resultTitle = "DEFEAT";
            resultCoins = 15;
            profile.losses++;
        }
        if (mode == M_SURVIVAL) {
            resultSub = "Reached wave " + wave + "  ·  " + (wave - 1) + " takedowns";
        } else {
            resultSub = "Rounds " + roundWinsA + " - " + roundWinsB + "  ·  " + arena.name;
        }
        profile.addCoins(resultCoins);
        profile.save();
        host.vibrate(won ? 40 : 20);
        if (profile.voice) {
            host.announce(won ? "Victory" : "Defeat");
        }
        if (won) {
            fx.confetti(viewW, viewH);
        }
    }

    private void finishMatch() {
        screen = S_RESULT;
        paused = false;
    }

    private void updateWorldFx(float dt) {
        for (int i = 0; i < 2; i++) {
            fighters[i].update(dt, arena, profile.gravity, profile.energyShield);
            fireIfNeeded(fighters[i]);
        }
        updateShots(dt);
        updatePickups(dt);
    }

    private void updateCamera(float dt) {
        float vw = viewW / ZOOM;
        float vh = viewH / ZOOM;
        float tx = (fighters[0].x + fighters[1].x) * 0.5f;
        float ty = (fighters[0].y + fighters[1].y) * 0.5f;
        float targetX = tx - vw * 0.5f;
        float targetY = ty - vh * 0.55f;

        float minX = arena.bounds.left - 40f;
        float maxX = arena.bounds.right - vw + 40f;
        if (maxX < minX) {
            maxX = minX = (arena.bounds.left + arena.bounds.right - vw) * 0.5f;
        }
        targetX = Math.max(minX, Math.min(maxX, targetX));

        float minY = arena.bounds.top - 120f;
        float maxY = arena.bounds.bottom - vh + 70f;
        if (maxY < minY) {
            maxY = minY = (arena.bounds.top + arena.bounds.bottom - vh) * 0.5f;
        }
        targetY = Math.max(minY, Math.min(maxY, targetY));

        float k = 1f - (float) Math.pow(0.0016f, dt);
        camX += (targetX - camX) * k;
        camY += (targetY - camY) * k;
    }

    // ------------------------------------------------------------------ draw

    public void draw(Canvas canvas) {
        framesDrawn++;
        Painter g = new Painter(canvas);
        canvas.drawColor(0xFF0B0F17);

        if (screen == S_PLAY || screen == S_RESULT) {
            drawWorld(g);
            if (screen == S_PLAY) {
                drawHud(g);
            }
            if (paused) {
                drawPause(g);
            }
            if (screen == S_RESULT) {
                drawResult(g);
            }
            return;
        }

        drawMenuBackground(g, screen);
        uiBtns.clear();
        if (screen == S_TITLE) {
            drawTitle(g);
        } else if (screen == S_MAPS) {
            drawMaps(g);
        } else if (screen == S_ARSENAL) {
            drawArsenal(g);
        } else if (screen == S_SETTINGS) {
            drawSettings(g);
        } else if (screen == S_STYLE) {
            drawStyle(g);
        } else if (screen == S_HOW) {
            drawHow(g);
        }
    }

    private void beginUi(Canvas c) {
        c.save();
        c.scale(scale, scale);
    }

    private void drawWorld(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);

        float shx = 0, shy = 0;
        if (shake > 0.2f) {
            shx = (rnd.nextFloat() - 0.5f) * shake;
            shy = (rnd.nextFloat() - 0.5f) * shake;
        }

        // sky
        g.gradient(0, 0, viewW, viewH, arena.skyTop, arena.skyBottom);

        c.save();
        c.scale(ZOOM * 0.55f, ZOOM * 0.55f);
        c.translate(-camX * 0.35f + shx * 0.4f, -camY * 0.2f + shy * 0.4f);
        drawBackdrop(g);
        c.restore();

        c.save();
        c.scale(ZOOM, ZOOM);
        c.translate(-camX + shx / ZOOM, -camY + shy / ZOOM);
        drawArena(g);
        for (int i = 0; i < pickups.size(); i++) {
            drawPickup(g, pickups.get(i));
        }
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            g.alpha(40);
            g.oval(f.x, f.y + Fighter.FEET - 1f, 22, 6, 0xFF000000);
            g.reset();
            f.draw(g);
        }
        for (int i = 0; i < shots.size(); i++) {
            drawShot(g, shots.get(i));
        }
        fx.draw(g);
        c.restore();

        if (screen == S_PLAY && !paused) {
            drawControls(g);
        }
        if (screen == S_PLAY) {
            drawIntroOverlay(g);
        }
        c.restore();
        g.reset();
    }

    private void drawBackdrop(Painter g) {
        float baseY = arena.bounds.bottom;
        int deco = arena.deco;
        if (arenaIndex == 1) {
            for (int i = 0; i < 24; i++) {
                float x = i * 92f - 60f;
                float hgt = 130f + ((i * 73) % 210);
                g.rect(x, baseY - hgt - 40, 66, hgt, Painter.withAlpha(deco, 150));
                for (int win = 0; win < 4; win++) {
                    g.rect(x + 12, baseY - hgt - 20 + win * 34, 8, 10, 0x33FFE38A);
                    g.rect(x + 34, baseY - hgt - 20 + win * 34, 8, 10, 0x22FFE38A);
                }
            }
        } else if (arenaIndex == 5) {
            for (int i = 0; i < 20; i++) {
                float x = i * 105f - 40f + (i % 3) * 14;
                g.rect(x, baseY - 360, 15, 360, 0xFF2E5A32);
                g.rect(x + 3, baseY - 360, 5, 360, 0xFF4E8A46);
                g.circle(x + 7, baseY - 366, 40, 0xFF2E5A32);
                g.circle(x + 4, baseY - 372, 26, 0xFF3E7440);
            }
        } else if (arenaIndex == 6) {
            for (int i = 0; i < 40; i++) {
                float x = (i * 137f) % 1600;
                float y = 80 + ((i * 91) % 420);
                g.circle(x, y, 2.2f, 0x88FFFFFF);
            }
        } else {
            for (int i = 0; i < 7; i++) {
                float x = i * 230f - 120f;
                float hgt = 190 + ((i * 53) % 140);
                Path hill = new Path();
                hill.moveTo(x, baseY);
                hill.lineTo(x + 150, baseY - hgt);
                hill.lineTo(x + 320, baseY);
                hill.close();
                g.path(hill, Painter.withAlpha(deco, 55));
            }
        }
    }

    private void drawArena(Painter g) {
        for (int i = 0; i < arena.hazards.size(); i++) {
            Maps.Hazard hz = arena.hazards.get(i);
            RectF hr = hz.r;
            if (hz.type == Maps.HAZ_WATER) {
                g.rect(hr.left, hr.top, hr.width(), hr.height(), 0xFF1E5A88);
                g.rect(hr.left, hr.top, hr.width(), 12f, 0xFF3D8FC4);
                for (int k = 0; k < 16; k++) {
                    float wx = hr.left + (k / 16f) * hr.width() + 18;
                    float wy = hr.top + 14 + (float) Math.sin(time * 2.2f + k) * 5f;
                    g.stroke(wx, wy, wx + 46, wy, 3, 0x77BFEBFF);
                }
            } else if (hz.type == Maps.HAZ_LAVA) {
                g.rect(hr.left, hr.top, hr.width(), hr.height(), 0xFF6E1E0A);
                g.rect(hr.left, hr.top, hr.width(), 10f, 0xFFFF7A3C);
                for (int k = 0; k < 18; k++) {
                    float wx = hr.left + (k / 18f) * hr.width() + 22;
                    float wy = hr.top + 12 + (float) Math.sin(time * 1.7f + k * 1.3f) * 6f;
                    g.circle(wx, wy, 7, 0x99FFA347);
                }
            } else {
                g.rect(hr.left, hr.top, hr.width(), hr.height(), 0x44140A20);
                for (int k = 0; k < 20; k++) {
                    float wx = hr.left + 16 + k * (hr.width() / 20f);
                    Path spike = new Path();
                    spike.moveTo(wx - 9, hr.top + 26);
                    spike.lineTo(wx, hr.top);
                    spike.lineTo(wx + 9, hr.top + 26);
                    spike.close();
                    g.path(spike, 0xFFB8B8CC);
                }
            }
        }

        for (int i = 0; i < arena.platforms.size(); i++) {
            Maps.Platform p = arena.platforms.get(i);
            RectF r = p.r;
            int top = arena.accent;
            int body = arena.ground;
            if (p.style == 1) {
                body = Painter.mix(arena.ground, 0xFF8A5A2A, 0.45f);
                top = Painter.mix(arena.accent, 0xFFD8A45A, 0.5f);
            } else if (p.style == 2) {
                body = Painter.mix(arena.ground, 0xFF6A7A8A, 0.5f);
                top = Painter.mix(arena.accent, 0xFFB8C8D8, 0.5f);
            } else if (p.style == 3) {
                body = Painter.mix(arena.ground, 0xFF4AD8E8, 0.35f);
                top = Painter.mix(arena.accent, 0xFF9AF0FF, 0.6f);
            }
            g.rrect(r.left, r.top, r.width(), r.height(), 8, body);
            g.rect(r.left, r.top, r.width(), 7f, top);
            g.rect(r.left, r.top + 7f, r.width(), 3f, Painter.withAlpha(0xFF000000, 60));
        }

    }

    private void drawShot(Painter g, Projectile p) {
        g.c.save();
        g.c.translate(p.x, p.y);
        g.c.rotate((float) Math.toDegrees(p.angle));
        if (p.special == 1) {
            g.circle(0, 0, p.r, 0xFF3A3A44);
            g.circle(-3, -3, 3, 0xFF6A6A78);
            g.stroke(6, -12, 14, -20, 2, 0xFFD8A45A);
        } else if (p.weapon == 6) {
            g.stroke(-20, 0, 16, 0, 3, 0xFFE8D8B8);
            g.path(triangle(16, -6, 28, 0, 16, 6), p.color);
        } else if (p.weapon == 9) {
            g.circle(0, 0, 8, Painter.withAlpha(p.color, 90));
            g.path(diamond(0, 0, 13), p.color);
        } else {
            g.rrect(-12, -3, 24, 6, 3, p.color);
            g.circle(11, 0, 4, 0xFFDDDDDD);
        }
        g.c.restore();
        g.reset();
    }

    private void drawPickup(Painter g, Pickup pk) {
        float bob = (float) Math.sin(pk.t * 3f) * 4f;
        g.circle(pk.x, pk.y + bob, 22, Painter.withAlpha(Weapons.color(pk.weapon), 40));
        g.line.setColor(Painter.withAlpha(Weapons.color(pk.weapon), 150));
        g.line.setStrokeWidth(2);
        g.c.drawCircle(pk.x, pk.y + bob, 20, g.line);
        g.c.save();
        g.c.translate(pk.x - 14, pk.y + bob);
        g.circle(0, 0, 7, Weapons.color(pk.weapon));
        g.stroke(-6, 0, 12, 0, 4, Weapons.color(pk.weapon));
        g.c.restore();
        g.reset();
        g.size(11);
        g.textCenter(Weapons.name(pk.weapon), pk.x, pk.y + bob - 28, 0xCCFFFFFF);
    }

    private void drawControls(Painter g) {
        Fighter a = fighters[0];
        drawStick(g, stickA, 0xFF4D9BFF);
        drawButton(g, btnAtkA, "ATK", 0xFFFF6B8A);
        drawButton(g, btnJumpA, "JMP", 0xFF6BD8FF);
        drawButton(g, btnShieldA, "DEF", 0xFF9BE86B);

        if (mode == M_LOCAL) {
            drawStick(g, stickB, 0xFFFF6B8A);
            drawButton(g, btnAtkB, "ATK", 0xFFFF6B8A);
            drawButton(g, btnJumpB, "JMP", 0xFF6BD8FF);
            drawButton(g, btnShieldB, "DEF", 0xFF9BE86B);
        } else {
            drawButton(g, btnAtkB, "ATK", 0xFFFF6B8A);
            drawButton(g, btnJumpB, "JMP", 0xFF6BD8FF);
            drawButton(g, btnShieldB, "DEF", 0xFF9BE86B);
        }

        // pause
        g.rrect(pauseRect.left, pauseRect.top, pauseRect.width(), pauseRect.height(), 10,
                0x66000000);
        g.rect(pauseRect.left + 14, pauseRect.top + 11, 5, 22, 0xCCFFFFFF);
        g.rect(pauseRect.left + 25, pauseRect.top + 11, 5, 22, 0xCCFFFFFF);
    }

    private void drawStick(Painter g, Stick s, int color) {
        g.line.setColor(0x44FFFFFF);
        g.line.setStrokeWidth(3);
        g.c.drawCircle(s.baseX, s.baseY, s.radius, g.line);
        g.circle(s.baseX, s.baseY, s.radius, 0x14000000);
        float kx = s.baseX + s.dx * s.radius * 0.8f;
        float ky = s.baseY + s.dy * s.radius * 0.8f;
        g.circle(kx, ky, 34, Painter.withAlpha(color, 120));
        g.circle(kx, ky, 28, Painter.withAlpha(color, 220));
        g.line.setColor(0x22FFFFFF);
        g.line.setStrokeWidth(2);
        g.c.drawCircle(s.baseX, s.baseY, s.radius * 0.55f, g.line);
    }

    private void drawButton(Painter g, TButton b, String label, int color) {
        int fill = b.held ? Painter.withAlpha(color, 235) : Painter.withAlpha(color, 120);
        g.circle(b.x, b.y, b.r, 0x22000000);
        g.circle(b.x, b.y, b.r, fill);
        g.line.setColor(Painter.withAlpha(0xFFFFFFFF, b.held ? 220 : 110));
        g.line.setStrokeWidth(2);
        g.c.drawCircle(b.x, b.y, b.r, g.line);
        g.size(b.r * 0.52f);
        g.textCenter(label, b.x, b.y + b.r * 0.18f, 0xFFFFFFFF);
    }

    private void drawIntroOverlay(Painter g) {
        if (phase == PH_INTRO) {
            int count = (int) Math.ceil(phaseT - 0.6f);
            String s = count > 0 ? String.valueOf(count) : "FIGHT!";
            if (count > 0) {
                g.line.setColor(0x66FFE38A);
                g.line.setStrokeWidth(4f);
                g.c.drawCircle(viewW / 2, viewH / 2 - 14, 66, g.line);
                g.line.setColor(0x33FFFFFF);
                g.line.setStrokeWidth(2f);
                g.c.drawCircle(viewW / 2, viewH / 2 - 14, 82, g.line);
            }
            g.size(count > 0 ? 96 : 78);
            g.textShadowCenter(s, viewW / 2, viewH / 2 + 20, count > 0 ? 0xFFFFE38A : 0xFFFF7A9A, 5);
            g.size(14);
            g.textCenter(arena.name, viewW / 2, viewH - 26, 0x99FFFFFF);
        } else if (phase == PH_ROUND_END) {
            g.size(84);
            g.textShadowCenter("K.O.", viewW / 2, viewH / 2, 0xFFFF6B8A, 5);
        }
    }

    private void drawHud(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        introShake = 0;
        g.rect(0, 0, viewW, 78, 0xE60A0E16);
        g.rect(0, 78, viewW, 2, 0x22FFFFFF);
        float barW = Math.min(260f, viewW * 0.26f);
        drawHealth(g, 20, 22, barW, fighters[0], false);
        drawHealth(g, viewW - 20 - barW, 22, barW, fighters[1], true);

        g.size(15);
        g.align(Paint.Align.CENTER);
        String mid = mode == M_SURVIVAL ? "WAVE " + wave : "ROUND " + roundNumber;
        g.textCenter(mid, viewW / 2, 40, 0xCCFFFFFF);
        if (mode != M_SURVIVAL) {
            g.textCenter(roundWinsA + " - " + roundWinsB, viewW / 2, 62, 0xFFFFE38A);
        } else {
            g.textCenter("TAKEDOWNS " + (wave - 1), viewW / 2, 62, 0xFFFFE38A);
        }
        g.align(Paint.Align.LEFT);
        c.restore();
    }

    private void drawHealth(Painter g, float x, float y, float w, Fighter f, boolean right) {
        g.rrect(x, y, w, 16, 8, 0x66000000);
        float pct = Math.max(0, f.health / f.maxHealth);
        float fw = (w - 4) * pct;
        int col = pct > 0.55f ? 0xFF6BE88A : (pct > 0.25f ? 0xFFFFC46B : 0xFFFF5D5D);
        if (right) {
            g.rrect(x + 2 + (w - 4 - fw), y + 2, fw, 12, 6, col);
        } else {
            g.rrect(x + 2, y + 2, fw, 12, 6, col);
        }
        if (profile.energyShield) {
            float sp = Math.max(0, f.shield / f.maxShield);
            float sw = (w - 4) * sp;
            int sc = 0xAA6FE8FF;
            if (right) {
                g.rrect(x + 2 + (w - 4 - sw), y + 19, sw, 5, 2.5f, sc);
            } else {
                g.rrect(x + 2, y + 19, sw, 5, 2.5f, sc);
            }
        }
        g.size(13);
        if (right) {
            g.align(Paint.Align.RIGHT);
            g.text(f.name + "  ·  " + Weapons.name(f.weapon)
                    + (f.ammo >= 0 ? " " + f.ammo : ""), x + w, y + 42, 0xCCFFFFFF);
        } else {
            g.text(f.name + "  ·  " + Weapons.name(f.weapon)
                    + (f.ammo >= 0 ? " " + f.ammo : ""), x, y + 42, 0xCCFFFFFF);
        }
        g.align(Paint.Align.LEFT);
    }

    // ------------------------------------------------------------- menu draw

    private void drawMenuBackground(Painter g, int which) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.gradient(0, 0, viewW, viewH, 0xFF0A0E18, 0xFF1C2740);
        // soft vignette blobs
        for (int i = 0; i < 4; i++) {
            float bx = (float) (Math.sin(time * 0.25f + i * 1.7) * 0.5 + 0.5) * viewW;
            float by = (float) (Math.cos(time * 0.2f + i * 2.1) * 0.5 + 0.5) * viewH;
            g.circle(bx, by, 150 + i * 40, Painter.withAlpha(i % 2 == 0 ? 0xFF2A4A8A : 0xFF6A2A5A, 26));
        }
        for (int i = 0; i < 26; i++) {
            float px = (i * 137.5f + time * (12 + i % 7)) % (viewW + 80) - 40;
            float py = (i * 71.3f) % viewH;
            g.circle(px, py, 1.6f, 0x33FFFFFF);
        }
        c.restore();
        g.reset();
    }

    private void drawTitle(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.reset();

        // sparring silhouettes
        float t = time * 2.2f;
        float px1 = viewW * 0.26f + (float) Math.sin(t) * 40f;
        float px2 = viewW * 0.40f + (float) Math.cos(t * 1.1f) * 40f;
        silhouettes(g, px1, viewH - 120, 1, t);
        silhouettes(g, px2, viewH - 120, -1, t + 1.7f);

        g.size(Math.min(56f, viewW * 0.06f));
        g.textShadow("STICK", 46, 108, 0xFFFFFFFF, 3);
        g.textShadow("DUELIST", 46, 108 + Math.min(56f, viewW * 0.06f) + 4f, 0xFFFFC46B, 3);
        g.size(13);
        g.text("RAGDOLL ARENA  ·  v0.1", 48, 108 + Math.min(56f, viewW * 0.06f) * 2f + 12f, 0x88FFFFFF);

        // coins pill
        float pillW = 128;
        g.rrect(viewW - pillW - 18, 16, pillW, 34, 17, 0x33FFFFFF);
        g.circle(viewW - pillW + 2, 33, 10, 0xFFFFC46B);
        g.size(16);
        g.text(String.valueOf(profile.coins), viewW - pillW + 20, 39, 0xFFFFFFFF);

        float by = 128;
        float bx = Math.max(viewW * 0.52f, 40);
        float bw = Math.min(320, viewW - bx - 30);
        bw = Math.min(300, Math.max(220, bw));
        bx = viewW - bw - 30;

        btn(g, bx, by, bw, 52, "QUICK FIGHT", A_QUICK, 0xFFFF6B8A);
        btn(g, bx, by + 60, bw, 52, "LOCAL DUEL  ·  2P", A_LOCAL, 0xFF4D9BFF);
        btn(g, bx, by + 120, bw, 52, "SURVIVAL", A_SURVIVAL, 0xFF9BE86B);
        btn(g, bx, by + 180, bw, 44, "ARSENAL", A_ARSENAL, 0xFF7D8BFF);
        btn(g, bx, by + 232, bw, 44, "CUSTOMIZE", A_STYLE, 0xFFFFC46B);
        btn(g, bx, by + 284, bw, 44, "SETTINGS", A_SETTINGS, 0xFF8A93A8);
        btn(g, bx - 78, by + 284, 68, 44, "?", A_HOW, 0xFF8A93A8);

        g.size(11);
        g.text("Tap a mode to pick your arena", 48, viewH - 24, 0x66FFFFFF);
        c.restore();
    }

    private void silhouettes(Painter g, float x, float y, int face, float t) {
        int col = 0x22FFFFFF;
        g.c.save();
        g.c.translate(x, y);
        float lean = (float) Math.sin(t) * 4f;
        g.stroke(0, -34, 0, 10, 7, col);
        g.circle(0, -46, 13, col);
        g.stroke(0, -24, lean - 14, 0, 5, col);
        g.stroke(0, -24, face * 22, -10, 5, col);
        g.stroke(0, 10, -16, 40, 6, col);
        g.stroke(0, 10, 16, 40, 6, col);
        g.c.restore();
        g.reset();
    }

    private void btn(Painter g, float x, float y, float w, float h, String label, int action, int tint) {
        boolean hot = framesDrawn > 0 && uiBtns.size() >= 0 && pressedAction == action;
        g.rrect(x, y, w, h, 12, Painter.withAlpha(tint, hot ? 200 : 60));
        g.rrectStroke(x, y, w, h, 12, 2f, Painter.withAlpha(tint, 180));
        g.size(Math.min(22f, h * 0.42f));
        g.textShadowCenter(label, x + w / 2, y + h / 2 + 7, 0xFFFFFFFF, 1.5f);
        Btn b = new Btn();
        b.r.set(x, y, x + w, y + h);
        b.action = action;
        uiBtns.add(b);
    }

    private void header(Painter g, String title) {
        g.size(34);
        g.textShadow(title, 40, 68, 0xFFFFFFFF, 2.5f);
        float bw = 96;
        btn(g, viewW - bw - 34, 30, bw, 44, "BACK", A_BACK, 0xFF8A93A8);
    }

    private void drawMaps(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, pendingMode == M_LOCAL ? "LOCAL DUEL" : (pendingMode == M_SURVIVAL ? "SURVIVAL" : "QUICK FIGHT"));
        g.size(14);
        g.text("Choose an arena", 42, 96, 0x99FFFFFF);

        int cols = 3;
        float cw = (viewW - 80 - (cols - 1) * 18) / cols;
        float ch = 96;
        float startX = 40;
        float startY = 118;
        for (int i = 0; i < Maps.ALL.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = startX + col * (cw + 18);
            float y = startY + row * (ch + 16);
            if (y + ch > viewH - 10) {
                break;
            }
            Maps.Arena a = Maps.ALL[i];
            boolean sel = i == arenaIndex;
            g.rrect(x, y, cw, ch, 12, 0x55000000);
            // mini preview
            g.gradient(x + 8, y + 8, cw - 16, ch - 34, a.skyTop, a.skyBottom);
            for (int k = 0; k < a.platforms.size() && k < 6; k++) {
                RectF r = a.platforms.get(k).r;
                float px = x + 8 + (r.left / a.bounds.width()) * (cw - 16);
                float pw = Math.max(8, (r.width() / a.bounds.width()) * (cw - 16));
                float py = y + 8 + (r.top / a.bounds.height()) * (ch - 34);
                g.rect(px, py, Math.min(pw, cw - 16 - (px - x - 8)), 5, a.accent);
            }
            g.rrectStroke(x, y, cw, ch, 12, sel ? 3f : 1.5f,
                    sel ? 0xFFFFC46B : 0x33FFFFFF);
            g.size(15);
            g.textCenter(a.name, x + cw / 2, y + ch - 10, 0xFFFFFFFF);
            addBtn(x, y, cw, ch, A_MAP_BASE + i);
        }
        c.restore();
    }

    private void drawArsenal(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "ARSENAL");
        g.size(14);
        g.text("Tap to equip  ·  locked weapons cost coins  ·  starter: "
                + Weapons.name(profile.starter), 40, 96, 0x99FFFFFF);

        int cols = 4;
        float cw = (viewW - 80 - (cols - 1) * 14) / cols;
        float ch = 104;
        float startY = 114;
        for (int i = 0; i < Weapons.COUNT; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = 40 + col * (cw + 14);
            float y = startY + row * (ch + 12);
            if (y + ch > viewH - 8) {
                break;
            }
            Weapons.Def d = Weapons.DEFS[i];
            boolean owned = profile.unlocked(i);
            boolean equipped = profile.starter == i;
            g.rrect(x, y, cw, ch, 12, equipped ? 0x44FFC46B : 0x44000000);
            g.rrectStroke(x, y, cw, ch, 12, equipped ? 2.5f : 1.2f,
                    equipped ? 0xFFFFC46B : 0x33FFFFFF);
            g.circle(x + 26, y + 30, 15, Painter.withAlpha(d.color, owned ? 220 : 60));
            g.size(15);
            g.text(d.name, x + 48, y + 26, owned ? 0xFFFFFFFF : 0x77FFFFFF);
            g.size(10);
            g.text(d.blurb, x + 10, y + 56, 0x88FFFFFF);
            if (!owned) {
                g.size(13);
                g.text("🔒 " + d.cost, x + 10, y + 84, 0xFFFFC46B);
            } else if (equipped) {
                g.size(12);
                g.text("EQUIPPED", x + 10, y + 84, 0xFFFFC46B);
            } else {
                g.size(12);
                g.text("OWNED", x + 10, y + 84, 0x889BE86B);
            }
            addBtn(x, y, cw, ch, A_WEAPON_BASE + i);
        }
        c.restore();
    }

    private static final int[] SKINS = {
            0xFFF2F2F2, 0xFF2A2A32, 0xFFFF6B8A, 0xFF4D9BFF,
            0xFF9BE86B, 0xFFFFC46B, 0xFFD9A0FF, 0xFF51E5FF,
    };

    private static final String[] DIFF_NAMES = {"EASY", "NORMAL", "HARD", "INSANE"};

    private void drawStyle(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "CUSTOMIZE");

        g.size(16);
        g.text("BODY COLOR", 44, 116, 0xCCFFFFFF);
        for (int i = 0; i < SKINS.length; i++) {
            float x = 60 + i * 74;
            float y = 150;
            g.circle(x, y, 26, SKINS[i]);
            g.line.setColor(profile.skinColor == SKINS[i] ? 0xFFFFC46B : 0x33FFFFFF);
            g.line.setStrokeWidth(profile.skinColor == SKINS[i] ? 4 : 2);
            g.c.drawCircle(x, y, 30, g.line);
            addBtn(x - 32, y - 32, 64, 64, A_COLOR_BASE + i);
            g.reset();
        }

        g.size(16);
        g.text("HAIR", 44, 240, 0xCCFFFFFF);
        String[] hairs = {"NONE", "SPIKY", "BAND", "CAP"};
        for (int i = 0; i < 4; i++) {
            float x = 44 + i * 130;
            float y = 262;
            btn(g, x, y, 116, 44, hairs[i], A_HAIR_BASE + i,
                    profile.hairStyle == i ? 0xFFFFC46B : 0xFF8A93A8);
        }

        // preview
        Fighter p = new Fighter(0, "PREVIEW", 0, 0, false);
        p.color = profile.skinColor;
        p.hair = profile.hairStyle;
        p.weapon = profile.starter;
        p.spawnT = 0;
        p.x = viewW * 0.5f;
        p.y = viewH - 96;
        p.onGround = true;
        g.c.save();
        g.c.translate(p.x, p.y);
        g.c.scale(1.75f, 1.75f);
        g.c.translate(-p.x, -p.y);
        p.draw(g);
        g.c.restore();
        g.size(12);
        g.textCenter("PREVIEW", p.x, viewH - 14, 0x66FFFFFF);
        c.restore();
    }

    private void drawSettings(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "SETTINGS");

        float y = 116;
        float rowH = 62;
        float x = 44;
        float wRow = Math.min(560, viewW - 88);
        toggleRow(g, x, y, wRow, rowH, "Gravity", profile.gravity, A_GRAVITY);
        toggleRow(g, x, y + rowH, wRow, rowH, "Instant KO on hazards", profile.instantKO, A_INSTANT);
        toggleRow(g, x, y + rowH * 2, wRow, rowH, "Energy shield", profile.energyShield, A_SHIELD);
        toggleRow(g, x, y + rowH * 3, wRow, rowH, "Sound", profile.sound, A_SOUND);
        toggleRow(g, x, y + rowH * 4, wRow, rowH, "Voice announcer", profile.voice, A_VOICE);

        float y2 = y + rowH * 5 + 10;
        g.size(15);
        g.text("CPU DIFFICULTY", x + 16, y2 + 26, 0xCCFFFFFF);
        btn(g, x + 180, y2 + 2, 190, 40, DIFF_NAMES[profile.difficulty], A_DIFF, 0xFF7D8BFF);

        float y3 = y2 + 56;
        g.size(12);
        g.text("Wipes coins, unlocks and records", x + 16, y3 + 28, 0x88FFFFFF);
        btn(g, x + 250, y3 + 4, 190, 40, "RESET PROGRESS", A_RESET, 0xFFFF6B8A);

        g.size(11);
        g.text("Stick Duelist v0.1 · built in Arena Agent Mode", x, viewH - 14, 0x66FFFFFF);
        c.restore();
    }

    private void toggleRow(Painter g, float x, float y, float w, float h, String label,
                           boolean on, int action) {
        g.rrect(x, y, w, h - 10, 10, 0x33000000);
        g.size(16);
        g.text(label, x + 16, y + h / 2 + 1, 0xDDEEEEFF);
        float sw = 66, sh = 30;
        float sx = x + w - sw - 16;
        float sy = y + (h - 10 - sh) / 2;
        g.rrect(sx, sy, sw, sh, sh / 2, on ? 0xFF4D9BFF : 0x55FFFFFF);
        g.circle(sx + (on ? sw - sh / 2 : sh / 2), sy + sh / 2, sh / 2 - 3, 0xFFFFFFFF);
        addBtn(x, y, w, h, action);
    }

    private void drawHow(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "HOW TO PLAY");
        String[] lines = {
                "1P — left joystick moves, right buttons ATK / JMP / DEF",
                "2P — each player gets a joystick and buttons on their side",
                "Tap the stick toward a direction, tap ATK to swing",
                "Weapons drop from the sky. Walk over one to grab it",
                "DEF blocks most damage but drains your blue shield bar",
                "Water and lava end you instantly (unless Instant KO is off)",
                "First to 2 rounds wins the duel",
        };
        g.size(15);
        for (int i = 0; i < lines.length; i++) {
            g.text(lines[i], 46, 122 + i * 30, i % 2 == 0 ? 0xDDEEEEFF : 0xBBDDEEFF);
        }
        g.size(13);
        g.text("Tips: grab a Hammer to launch rivals into the lava. "
                + "Energy Blade has the longest reach.", 46, 122 + lines.length * 30 + 12, 0x99FFC46B);
        c.restore();
    }

    private void drawPause(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.rect(0, 0, viewW, viewH, 0xCC06080F);
        uiBtns.clear();
        float pw = 340;
        float px = (viewW - pw) / 2;
        float py = 90;
        g.rrect(px, py, pw, 360, 18, 0xFF141C2C);
        g.rrectStroke(px, py, pw, 360, 18, 2, 0x33FFFFFF);
        g.size(26);
        g.textCenter("PAUSED", viewW / 2, py + 52, 0xFFFFFFFF);
        float bx = px + 30;
        float bw = pw - 60;
        btn(g, bx, py + 82, bw, 48, "RESUME", A_RESUME, 0xFF9BE86B);
        btn(g, bx, py + 138, bw, 48, "RESTART", A_RESTART, 0xFF4D9BFF);
        btn(g, bx, py + 194, bw, 48, "CHANGE ARENA", A_MAPS_FROM_PAUSE, 0xFF7D8BFF);
        btn(g, bx, py + 250, bw, 48, "QUIT TO MENU", A_QUIT, 0xFFFF6B8A);
        c.restore();
    }

    private void drawResult(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.rect(0, 0, viewW, viewH, 0xB306080F);
        uiBtns.clear();
        float pw = 460;
        float ph = 300;
        float px = (viewW - pw) / 2;
        float py = (viewH - ph) / 2;
        g.rrect(px, py, pw, ph, 20, 0xFF141C2C);
        g.rrectStroke(px, py, pw, ph, 20, 2.5f, playerWon ? 0xFFFFC46B : 0xFF8A93A8);

        g.size(46);
        g.textShadowCenter(resultTitle, viewW / 2, py + 72, playerWon ? 0xFFFFD166 : 0xFFB8C0D0, 3);
        g.size(15);
        g.textCenter(resultSub, viewW / 2, py + 108, 0xCCFFFFFF);
        g.size(20);
        g.textCenter("+" + resultCoins + " coins", viewW / 2, py + 146, 0xFFFFC46B);
        g.size(12);
        g.textCenter("Total: " + profile.coins, viewW / 2, py + 166, 0x88FFFFFF);

        float bw = (pw - 80) / 2;
        btn(g, px + 26, py + ph - 86, bw, 52, "REMATCH", A_REMATCH, 0xFF9BE86B);
        btn(g, px + 46 + bw, py + ph - 86, bw, 52, "MENU", A_MENU, 0xFF4D9BFF);
        c.restore();
    }

    private void addBtn(float x, float y, float w, float h, int action) {
        Btn b = new Btn();
        b.r.set(x, y, x + w, y + h);
        b.action = action;
        uiBtns.add(b);
    }

    private void sfx(int id) {
        if (profile.sound) {
            host.sfx(id);
        }
    }

    // ------------------------------------------------------------ test hooks

    void debugStartMatch(int m, int map) {
        pendingMode = m;
        resetMatch(m, map, true);
    }

    Fighter debugFighter(int i) {
        return fighters[i];
    }

    int debugScreen() {
        return screen;
    }

    int debugPhase() {
        return phase;
    }

    // --------------------------------------------------------------- helpers

    private Path triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.close();
        return p;
    }

    private Path diamond(float x, float y, float r) {
        Path p = new Path();
        p.moveTo(x, y - r);
        p.lineTo(x + r, y);
        p.lineTo(x, y + r);
        p.lineTo(x - r, y);
        p.close();
        return p;
    }

    // ------------------------------------------------------------- inner types

    private static final class Btn {
        final RectF r = new RectF();
        int action;
    }

    private static final class Pickup {
        int weapon;
        float x, y, vy;
        float t;
        boolean onGround;
    }

    private static final class TButton {
        float x, y, r;
        int id;
        int pointer = -1;
        boolean held;
        boolean usedAsTap;

        void set(float x, float y, float r, int id) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.id = id;
        }

        boolean press(int pid, float px, float py) {
            float dx = px - x;
            float dy = py - y;
            if (dx * dx + dy * dy <= (r + 16) * (r + 16)) {
                pointer = pid;
                held = true;
                usedAsTap = false;
                return true;
            }
            return false;
        }

        boolean release(int pid) {
            if (pointer == pid) {
                pointer = -1;
                held = false;
                usedAsTap = false;
                return true;
            }
            return false;
        }

        boolean contains(float px, float py) {
            float dx = px - x;
            float dy = py - y;
            return dx * dx + dy * dy < r * r;
        }
    }

    private static final class Stick {
        float baseX, baseY, radius = 84f;
        int pointer = -1;
        float dx, dy;
        float startX, startY;

        void setBase(float x, float y, float r) {
            baseX = x;
            baseY = y;
            radius = r;
        }

        boolean grab(int id, float px, float py) {
            if (pointer != -1) {
                return false;
            }
            float ddx = px - baseX;
            float ddy = py - baseY;
            if (ddx * ddx + ddy * ddy < (radius + 70) * (radius + 70)) {
                pointer = id;
                startX = px;
                startY = py;
                move(id, px, py);
                return true;
            }
            return false;
        }

        boolean grabAnywhere(int id, float px, float py) {
            if (pointer != -1) {
                return false;
            }
            pointer = id;
            startX = px;
            startY = py;
            move(id, px, py);
            return true;
        }

        void move(int id, float px, float py) {
            if (pointer != id) {
                return;
            }
            float ddx = px - baseX;
            float ddy = py - baseY;
            float len = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (len < 0.001f) {
                dx = 0;
                dy = 0;
                return;
            }
            float max = radius;
            float clamped = Math.min(len, max);
            dx = (ddx / len) * (clamped / max);
            dy = (ddy / len) * (clamped / max);
        }

        void release(int id) {
            if (pointer == id) {
                pointer = -1;
                dx = 0;
                dy = 0;
            }
        }

        boolean active() {
            return pointer != -1;
        }
    }
}
