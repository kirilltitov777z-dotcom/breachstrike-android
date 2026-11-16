package com.breachstrike.game.game;

import com.breachstrike.game.core.Assets;
import com.breachstrike.game.core.Save;
import com.breachstrike.game.net.Lan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public final class GameState {
    public interface Listener {
        void onMatchEnd(GameState gs);
    }

    public static final class Slot {
        public String id;
        public int ammo;
        public int reserve;
        public Slot(String id, int mag) {
            this.id = id;
            this.ammo = mag;
            this.reserve = mag * 3;
        }
    }

    public final GameConfig cfg;
    public final MapData map;
    public final Assets assets;
    public final Save save;
    private final Listener listener;

    public final Body body = new Body();
    public float yaw, pitch;
    public int hp = 100;
    public boolean alive = true, crouch;
    public int playerTeam = 0;

    public final ArrayList<Bot> bots = new ArrayList<>();
    public String playerName = "Player";

    public Slot primary, secondary, knifeSlot, slot;
    public int slotIndex = 0;
    public float fireCd, reloadTimer, drawTimer, inspectTimer, knifeTimer, recoilKick, viewKick;
    public float bobTime, footTimer, hitMarker, damageFlash, scopeTimer;
    public boolean scoped, firing, reloading, matchOver, win, draw, roundOver;
    public float inputX, inputZ;
    public boolean jumpQueued, crouchHeld, fireHeld, plantHeld;
    public boolean deadHandled;

    public int scoreT, scoreCT, round = 1, kills, deaths, streak, bestStreak, mvps, roundKills;
    public int plants, defuses;
    public float timeLeft = 115f, freeze = 3f, bombTimer, roundEndTimer = 0f;
    public float plantTimer;
    public boolean planted;
    public float[] bombPos;
    public int bombCarrierBot = -2; // -1 player, >=0 bot, -2 none
    public int roundWinner = -1;
    public String resultText = "";
    public String hint = "";
    public float hintTimer;
    public float matchTime;
    public int eloDelta, coinsEarned, xpEarned;
    public boolean eloApplied;
    public float respawnTimer;
    public volatile int localId = 0;

    public volatile Lan lan;
    public final ArrayList<Remote> remotes = new ArrayList<>();
    public String lanStatus = "";
    public int lanOnline = 1;
    public int lanKills;
    private float lanSendTimer;

    public final ArrayList<String> feed = new ArrayList<>();
    private final ArrayList<Float> feedAge = new ArrayList<>();
    public volatile float fps;

    private final String[] names = {"Viper", "Ghost", "Nova", "Rex", "Kilo", "Frost", "Hawk", "Zen", "Wolf", "Blaze"};

    public GameState(GameConfig cfg, Assets assets, Save save, Listener listener) {
        this.cfg = cfg;
        this.map = Maps.get(cfg.mapIndex);
        this.assets = assets;
        this.save = save;
        this.listener = listener;
        this.playerName = cfg.playerName;
        this.playerTeam = 0;

        String p = save.slot("primary", "ak47");
        String s = save.slot("secondary", "usp");
        String k = save.slot("knife", "butterfly");
        if (!Weapons.get(p).cat.equals("knife")) {
            primary = new Slot(p, (int) Weapons.get(p).mag);
        } else {
            primary = new Slot("ak47", 30);
        }
        secondary = new Slot(s, (int) Weapons.get(s).mag);
        knifeSlot = new Slot(k, 0);
        slot = primary;
        slotIndex = 0;

        if (!cfg.lan) setupBots();
        resetRound(true);
    }

    private void setupBots() {
        bots.clear();
        int[][] plan;
        if (cfg.mode == GameConfig.MODE_BOMB) plan = new int[][]{{0, 4}, {1, 5}};
        else if (cfg.mode == GameConfig.MODE_TDM) plan = new int[][]{{0, 4}, {1, 5}};
        else plan = new int[][]{{0, 4}, {1, 3}};

        int id = 0;
        for (int[] p : plan) {
            for (int i = 0; i < p[1]; i++) {
                Bot b = new Bot();
                b.id = id++;
                b.team = p[0];
                b.name = names[b.id % names.length] + (b.team == 0 ? "" : "_" + (b.id + 1));
                b.skill = Math.max(0, Math.min(2, cfg.difficulty + (b.id % 3 == 0 ? -1 : 0)));
                b.weapon = pickWeapon(b);
                b.alive = false;
                bots.add(b);
            }
        }
    }

    private Weapons.Def pickWeapon(Bot b) {
        int n = b.id * 7 + 3;
        if (b.skill <= 0) {
            String[] w = {"glock", "usp", "p250", "mp9", "mac10", "nova"};
            return Weapons.get(w[Math.abs(n) % w.length]);
        } else if (b.skill == 1) {
            String[] w = {"ak47", "m4a4", "famas", "galil", "mp5", "ump", "xm1014", "ssg08"};
            return Weapons.get(w[Math.abs(n) % w.length]);
        }
        String[] w = {"ak47", "m4a1s", "aug", "sg553", "awp", "scar20", "g3sg1", "negev"};
        return Weapons.get(w[Math.abs(n) % w.length]);
    }

    public void resetRound(boolean initial) {
        if (!initial) {
            roundEndTimer = 0;
            freeze = 3f;
        }
        planted = false;
        bombTimer = 0;
        bombPos = null;
        bombCarrierBot = -1;
        roundOver = false;
        roundWinner = -1;
        timeLeft = 115f;
        roundKills = 0;
        hint = "";
        hintTimer = 0;
        plantTimer = 0;

        int si = Math.max(0, (round - 1) % Math.max(1, map.spawnsT.size()));
        float[] sp = map.spawnsT.get(si);
        body.x = sp[0]; body.y = sp[1]; body.z = sp[2];
        body.vx = body.vy = body.vz = 0;
        body.onGround = true;
        body.height = 1.72f;
        yaw = 0;
        pitch = 0;
        hp = 100;
        alive = true;
        crouch = false;
        scoped = false;
        respawnTimer = 0;
        slot = primary;
        slotIndex = 0;
        refill();
        fireCd = 0; reloadTimer = 0; drawTimer = 0.4f; inspectTimer = 0; knifeTimer = 0;
        recoilKick = viewKick = 0;

        int ti = 0, ci = 0;
        for (Bot b : bots) {
            float[] pos;
            if (b.team == 0) {
                pos = map.spawnsT.get((si + 1 + ti) % map.spawnsT.size());
                ti++;
            } else {
                pos = map.spawnsCT.get((si + ci) % map.spawnsCT.size());
                ci++;
            }
            b.reset(pos[0], pos[2], b.team == 0 ? 0 : 180f, false);
            b.body.y = pos[1];
            if (cfg.mode == GameConfig.MODE_BOMB && b.team == 0 && b == bots.get(0)) {
                // fallback carrier if the player dies before planting
                b.hasBomb = false;
            }
        }
        if (cfg.mode != GameConfig.MODE_BOMB) {
            timeLeft = 300f;
            freeze = 0.4f;
        } else {
            freeze = 3f;
        }
        feed.clear();
        feedAge.clear();
    }

    private void refill() {
        primary.ammo = (int) Weapons.get(primary.id).mag;
        primary.reserve = (int) Weapons.get(primary.id).mag * 3;
        secondary.ammo = (int) Weapons.get(secondary.id).mag;
        secondary.reserve = (int) Weapons.get(secondary.id).mag * 3;
    }

    public float eyeY() {
        return body.y + (crouch ? 1.12f : 1.66f);
    }

    public float[] eye() {
        return new float[]{body.x, eyeY(), body.z};
    }

    public float[] dir() {
        double cy = Math.cos(Math.toRadians(pitch));
        return new float[]{
                (float) (-Math.sin(Math.toRadians(yaw)) * cy),
                (float) Math.sin(Math.toRadians(pitch)),
                (float) (-Math.cos(Math.toRadians(yaw)) * cy)
        };
    }

    public Weapons.Def currentDef() {
        return Weapons.get(slot.id);
    }

    public float moveSpeed() {
        float base = 4.7f;
        String cat = currentDef().cat;
        if (cat.equals("awp") || currentDef().id.equals("awp")) base = 3.7f;
        else if (cat.equals("sniper")) base = 4.2f;
        else if (cat.equals("lmg")) base = 4.0f;
        else if (cat.equals("knife")) base = 5.2f;
        if (crouch) base *= 0.45f;
        if (scoped) base *= 0.55f;
        return base;
    }

    // ------------------- input -------------------
    public void setMove(float x, float z) {
        inputX = x;
        inputZ = z;
    }

    public void look(float dx, float dy) {
        float s = save.sens() * 0.13f;
        yaw -= dx * s;
        pitch -= (save.invertY() ? -dy : dy) * s;
        if (pitch > 89) pitch = 89;
        if (pitch < -89) pitch = -89;
    }

    public void jump() {
        jumpQueued = true;
    }

    public void setCrouch(boolean v) { crouchHeld = v; }

    public void setFire(boolean v) {
        firing = v;
    }

    public void requestReload() {
        if (reloadTimer > 0) return;
        Weapons.Def d = currentDef();
        if (d.isKnife()) return;
        if (slot.ammo >= d.mag || slot.reserve <= 0) return;
        reloadTimer = d.reload;
        reloading = true;
        inspectTimer = 0;
        assets.play("reload", 0.8f);
    }

    public void nextWeapon() {
        if (drawTimer > 0.25f) return;
        slotIndex = (slotIndex + 1) % 3;
        if (slotIndex == 0) slot = primary;
        else if (slotIndex == 1) slot = secondary;
        else slot = knifeSlot;
        drawTimer = 0.45f;
        reloadTimer = 0;
        reloading = false;
        inspectTimer = 0;
        scoped = false;
        assets.play("switch", 0.6f);
    }

    public void inspect() {
        if (drawTimer > 0.2f || reloadTimer > 0) return;
        inspectTimer = 2.2f;
        assets.play("inspect", 0.7f);
    }

    public void toggleScope() {
        if (!currentDef().scoped) return;
        scoped = !scoped;
        assets.play("scope", 0.5f);
    }

    public void toggleCrouch() { crouchHeld = !crouchHeld; }

    public void setPlantHeld(boolean v) { plantHeld = v; }

    // ------------------- update -------------------
    public void update(float dt) {
        if (dt > 0.1f) dt = 0.1f;
        for (int i = feedAge.size() - 1; i >= 0; i--) {
            feedAge.set(i, feedAge.get(i) + dt);
            if (feedAge.get(i) > 6f) {
                feedAge.remove(i);
                feed.remove(i);
            }
        }
        if (hintTimer > 0) hintTimer -= dt;
        if (hitMarker > 0) hitMarker -= dt;
        if (damageFlash > 0) damageFlash -= dt;
        viewKick *= Math.max(0, 1f - dt * 9f);
        if (matchOver) return;
        matchTime += dt;

        if (roundEndTimer > 0) {
            roundEndTimer -= dt;
            updateBots(dt, false);
            if (roundEndTimer <= 0) {
                round++;
                if (scoreT >= 8 || scoreCT >= 8) {
                    endMatch(scoreT > scoreCT, scoreT == scoreCT);
                } else {
                    resetRound(false);
                }
            }
            return;
        }

        if (freeze > 0) {
            freeze -= dt;
            crouch = false;
            updateBots(dt, false);
            return;
        }

        if (cfg.mode == GameConfig.MODE_BOMB) {
            if (!planted) {
                timeLeft -= dt;
                if (timeLeft <= 0) {
                    timeLeft = 0;
                    finishRound(1);
                    return;
                }
            } else {
                bombTimer -= dt;
                if (bombTimer <= 0) {
                    bombTimer = 0;
                    explode();
                    finishRound(0);
                    return;
                }
            }
        } else {
            timeLeft -= dt;
            if (timeLeft <= 0) {
                timeLeft = 0;
                if (cfg.mode == GameConfig.MODE_DM) {
                    endMatch(kills >= 20, false);
                } else {
                    endMatch(scoreT > scoreCT, scoreT == scoreCT);
                }
                return;
            }
        }

        updatePlayer(dt);
        updateBots(dt, true);
        updateBomb(dt);
        if (lan != null) updateLan(dt);

        if (cfg.mode == GameConfig.MODE_BOMB) {
            int aliveT = (alive ? 1 : 0);
            int aliveCT = 0;
            for (Bot b : bots) if (b.alive && b.team == 0) aliveT++;
            for (Bot b : bots) if (b.alive && b.team == 1) aliveCT++;
            if (!planted && aliveT == 0) finishRound(1);
            else if (aliveCT == 0) finishRound(0);
        } else if (cfg.mode == GameConfig.MODE_DM) {
            if (kills >= 30) endMatch(true, false);
        } else {
            if (scoreT >= 50) endMatch(true, false);
            else if (scoreCT >= 50) endMatch(false, false);
        }
    }

    private void updatePlayer(float dt) {
        if (crouchHeld) crouch = true;
        else crouch = false;
        body.height = crouch ? 1.25f : 1.72f;

        if (!alive) {
            if (cfg.mode != GameConfig.MODE_BOMB) {
                respawnTimer -= dt;
                if (respawnTimer <= 0) respawnPlayer();
            }
            return;
        }

        float fx = (float) -Math.sin(Math.toRadians(yaw));
        float fz = (float) -Math.cos(Math.toRadians(yaw));
        float rx = (float) Math.cos(Math.toRadians(yaw));
        float rz = (float) -Math.sin(Math.toRadians(yaw));
        float sp = moveSpeed();
        float mx = fx * inputZ + rx * inputX;
        float mz = fz * inputZ + rz * inputX;
        float len = (float) Math.sqrt(mx * mx + mz * mz);
        if (len > 1f) { mx /= len; mz /= len; }
        body.vx = mx * sp;
        body.vz = mz * sp;

        if (jumpQueued) {
            if (body.onGround) {
                body.vy = 7.2f;
                body.onGround = false;
                assets.play("jump", 0.5f);
            }
            jumpQueued = false;
        }

        Physics.step(map, body, dt);

        if (body.onGround && len > 0.15f) {
            footTimer -= dt;
            if (footTimer <= 0) {
                footTimer = crouch ? 0.65f : 0.42f;
                assets.play("step", crouch ? 0.22f : 0.35f);
            }
        } else {
            footTimer = 0.1f;
        }

        if (reloadTimer > 0) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) {
                reloadTimer = 0;
                reloading = false;
                int need = (int) currentDef().mag - slot.ammo;
                int take = Math.min(need, slot.reserve);
                slot.ammo += take;
                slot.reserve -= take;
            }
        }
        if (drawTimer > 0) drawTimer -= dt;
        if (inspectTimer > 0) inspectTimer -= dt;
        if (knifeTimer > 0) knifeTimer -= dt;
        if (fireCd > 0) fireCd -= dt;
        if (recoilKick > 0) recoilKick = Math.max(0f, recoilKick - dt * 7f);
        if (body.onGround && len > 0.15f) bobTime += dt * 9f;

        if (scopeTimer > 0) scopeTimer -= dt;

        if (firing && fireCd <= 0 && reloadTimer <= 0 && drawTimer <= 0.15f) {
            shootPlayer();
        }
        if (scoped && (body.vx != 0 || body.vz != 0)) {
            // scope stays but accuracy suffers, as in CS
        }
    }

    private void respawnPlayer() {
        int n = map.spawnsT.size() + map.spawnsCT.size();
        int i = (int) (Math.random() * n);
        float[] sp = i < map.spawnsT.size() ? map.spawnsT.get(i) : map.spawnsCT.get(i - map.spawnsT.size());
        body.x = sp[0]; body.z = sp[2]; body.y = sp[1];
        body.vx = body.vy = body.vz = 0;
        hp = 100;
        alive = true;
        scoped = false;
        slot = primary;
        slotIndex = 0;
        refill();
        drawTimer = 0.5f;
        damageFlash = 0;
    }

    private void shootPlayer() {
        Weapons.Def d = currentDef();
        fireCd = d.interval();
        recoilKick = 1f;
        viewKick = 1f;
        bobTime = 0;

        if (d.isKnife()) {
            knifeTimer = 0.55f;
            assets.play("knife_swing", 0.9f);
            knifeAttack();
            return;
        }
        if (slot.ammo <= 0) {
            assets.play("empty", 0.7f);
            requestReload();
            return;
        }
        slot.ammo--;
        String snd = "shot_" + d.cat;
        assets.play(snd, 0.9f);

        float spread = d.spread * (float) Math.toRadians(1f);
        float moveFactor = (Math.abs(body.vx) + Math.abs(body.vz)) > 0.4f ? 1.7f : 1f;
        if (body.onGround) spread *= moveFactor; else spread *= 2.6f;
        if (crouch) spread *= 0.65f;
        if (scoped) spread *= 0.35f;

        float[] o = eye();
        float[] base = dir();
        int hits = 0;
        for (int p = 0; p < d.pellets; p++) {
            float[] dd = jitter(base, spread);
            RayHit hit = raycastEnemies(o[0], o[1], o[2], dd, d.range, playerTeam);
            float wallT = map.raycast(o[0], o[1], o[2], dd[0], dd[1], dd[2], d.range);
            if (hit != null && (wallT < 0 || hit.dist < wallT)) {
                float dmg = d.damage * (1f - Math.min(0.45f, hit.dist / d.range * 0.45f));
                if (hit.head) dmg *= 2.6f;
                else if (hit.legs) dmg *= 0.7f;
                damageBot(hit.bot, dmg, hit.head, "Вы", playerTeam);
                hits++;
            } else if (wallT >= 0) {
                // mark impact for feedback
            }
        }
        if (hits > 0) {
            hitMarker = 0.25f;
            assets.play("hitmarker", 0.7f);
        }
        if (lan != null) lan.shot(o[0], o[1], o[2], base);
        // recoil
        pitch += d.recoil * 0.16f;
        if (pitch > 89) pitch = 89;
        yaw += (float) (Math.random() - 0.5) * d.recoil * 0.1f;
    }

    private void knifeAttack() {
        float[] o = eye();
        float[] base = dir();
        float best = 3.2f;
        Bot victim = null;
        for (Bot b : bots) {
            if (!b.alive || b.team == playerTeam) continue;
            float dx = b.body.x - o[0], dy = (b.body.y + 1.0f) - o[1], dz = b.body.z - o[2];
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > best) continue;
            float dot = (dx * base[0] + dy * base[1] + dz * base[2]) / Math.max(0.01f, dist);
            if (dot > 0.55f) { best = dist; victim = b; }
        }
        if (victim != null) {
            damageBot(victim, 65, false, "Вы", playerTeam);
            hitMarker = 0.25f;
            assets.play("knife_hit", 0.9f);
        }
    }

    private float[] jitter(float[] d, float spread) {
        float rx = (float) (Math.random() - 0.5) * 2f;
        float ry = (float) (Math.random() - 0.5) * 2f;
        float rz = (float) (Math.random() - 0.5) * 2f;
        float[] ux = {1, 0, 0};
        float[] out = {
                d[0] + (rx * ux[0] + ry * 0.7f) * spread,
                d[1] + ry * spread,
                d[2] + (rx * ux[2] + rz) * spread
        };
        float len = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        return new float[]{out[0] / len, out[1] / len, out[2] / len};
    }

    public static final class RayHit {
        public Bot bot;
        public float dist;
        public boolean head, legs;
    }

    public RayHit raycastEnemies(float ox, float oy, float oz, float[] d, float range, int myTeam) {
        RayHit best = null;
        for (Bot b : bots) {
            if (!b.alive || b.team == myTeam) continue;
            RayHit h = rayHitBot(b, ox, oy, oz, d, range);
            if (h != null && (best == null || h.dist < best.dist)) best = h;
        }
        return best;
    }

    private RayHit rayHitBot(Bot b, float ox, float oy, float oz, float[] d, float range) {
        float bx = b.body.x, bz = b.body.z, by = b.body.y;
        RayHit best = null;
        // head
        float t = MapData.rayBox(ox, oy, oz, d[0], d[1], d[2],
                bx - 0.21f, by + 1.5f, bz - 0.21f, bx + 0.21f, by + 1.78f, bz + 0.21f);
        if (t >= 0 && t < range) {
            best = new RayHit();
            best.bot = b; best.dist = t; best.head = true;
        }
        // body
        t = MapData.rayBox(ox, oy, oz, d[0], d[1], d[2],
                bx - 0.3f, by + 0.75f, bz - 0.3f, bx + 0.3f, by + 1.5f, bz + 0.3f);
        if (t >= 0 && t < range && (best == null || t < best.dist)) {
            best = new RayHit();
            best.bot = b; best.dist = t;
        }
        // legs
        t = MapData.rayBox(ox, oy, oz, d[0], d[1], d[2],
                bx - 0.24f, by, bz - 0.24f, bx + 0.24f, by + 0.75f, bz + 0.24f);
        if (t >= 0 && t < range && (best == null || t < best.dist)) {
            best = new RayHit();
            best.bot = b; best.dist = t; best.legs = true;
        }
        return best;
    }

    public void damageBot(Bot b, float dmg, boolean head, String from, int fromTeam) {
        if (!b.alive) return;
        b.hp -= (int) dmg;
        if (head) assets.play("headshot", 0.7f);
        if (b.hp <= 0) {
            b.alive = false;
            b.hp = 0;
            b.respawn = 3.2f;
            b.deaths++;
            if (fromTeam == playerTeam && "Вы".equals(from)) {
                kills++;
                roundKills++;
                streak++;
                if (streak > bestStreak) bestStreak = streak;
                assets.play("kill", 1f);
            }
            if (cfg.mode == GameConfig.MODE_TDM) {
                if (fromTeam == 0) scoreT++;
                else scoreCT++;
            }
            addFeed(from + "  ⟶  " + b.name);
            if (b.hasBomb) {
                b.hasBomb = false;
                bombCarrierBot = -2;
                bombPos = new float[]{b.body.x, b.body.y, b.body.z};
            }
            if (cfg.mode == GameConfig.MODE_DM) {
                // free for all, score handled below
            }
        }
    }

    public void damagePlayer(float dmg, String from) {
        if (!alive || matchOver) return;
        hp -= (int) dmg;
        damageFlash = 0.4f;
        assets.play("pain", 0.6f);
        if (hp <= 0) {
            hp = 0;
            alive = false;
            deaths++;
            streak = 0;
            deadHandled = true;
            if (cfg.mode == GameConfig.MODE_TDM) scoreCT++;
            addFeed(from + "  ⟶  " + playerName);
            if (cfg.mode == GameConfig.MODE_BOMB) {
                if (bombCarrierBot == -1) {
                    bombCarrierBot = -2;
                    bombPos = new float[]{body.x, body.y, body.z};
                }
            } else {
                respawnTimer = 3.0f;
            }
        }
    }

    private void addFeed(String s) {
        feed.add(0, s);
        feedAge.add(0, 0f);
        if (feed.size() > 5) {
            feed.remove(feed.size() - 1);
            feedAge.remove(feedAge.size() - 1);
        }
    }

    // ------------------- bots -------------------
    private void updateBots(float dt, boolean active) {
        for (Bot b : bots) {
            if (!b.alive) {
                if (cfg.mode != GameConfig.MODE_BOMB) {
                    b.respawn -= dt;
                    if (b.respawn <= 0) respawnBot(b);
                }
                continue;
            }
            if (!active) { animateIdle(b, dt); continue; }
            updateBotAI(b, dt);
        }
    }

    private void animateIdle(Bot b, float dt) {
        b.body.vx = b.body.vz = 0;
        Physics.step(map, b.body, dt);
    }

    private void respawnBot(Bot b) {
        int n = map.spawnsT.size() + map.spawnsCT.size();
        int i = (int) (Math.random() * n);
        float[] sp = i < map.spawnsT.size() ? map.spawnsT.get(i) : map.spawnsCT.get(i - map.spawnsT.size());
        b.body.x = sp[0]; b.body.z = sp[2]; b.body.y = sp[1];
        b.body.vx = b.body.vy = b.body.vz = 0;
        b.hp = 100;
        b.alive = true;
        b.target = -1;
        b.thinkTimer = 0.2f;
        b.fireTimer = 0.3f;
        b.goalWp = -1;
    }

    private void updateBotAI(Bot b, float dt) {
        Weapons.Def d = b.weapon;
        if (b.fireTimer > 0) b.fireTimer -= dt;
        if (b.reactionTimer > 0) b.reactionTimer -= dt;
        if (b.pathTimer > 0) b.pathTimer -= dt;
        if (b.strafeTimer > 0) b.strafeTimer -= dt;
        if (b.thinkTimer > 0) b.thinkTimer -= dt;

        // target selection
        if (b.thinkTimer <= 0) {
            b.thinkTimer = 0.25f + (float) Math.random() * 0.25f;
            Target t = findTarget(b);
            if (t != null) {
                b.target = t.bot == null ? -1 : t.bot.id;
                b.lastX = t.x;
                b.lastZ = t.z;
            } else {
                b.target = -2;
            }
        }

        Target tgt = resolveTarget(b);
        boolean sees = false;
        if (tgt != null) {
            float dist = dist(b.body.x, b.eyeY(), b.body.z, tgt.x, tgt.eyeY, tgt.z);
            if (dist < 70f && Physics.canSee(map, b.body.x, b.eyeY(), b.body.z, tgt.x, tgt.eyeY, tgt.z)) {
                sees = true;
                b.lastX = tgt.x;
                b.lastZ = tgt.z;
                aimAt(b, tgt.x, tgt.eyeY, tgt.z, dt);
                if (b.reactionTimer <= 0) {
                    b.reactionTimer = reaction(b);
                    b.fireTimer = 0.05f;
                }
                if (b.fireTimer <= 0) {
                    botShoot(b, tgt);
                }
                if (b.strafeTimer <= 0) {
                    b.strafe = Math.random() < 0.5 ? -1 : 1;
                    b.strafeTimer = 0.7f + (float) Math.random();
                }
                float fwd = dist > 14f ? 1f : (dist < 4f ? -0.7f : 0f);
                move(b, b.strafe * 0.7f, fwd, dt, 2.9f);
            }
        }

        if (!sees) {
            b.reactionTimer = reactionDelay(b);
            if (cfg.mode == GameConfig.MODE_BOMB) {
                if (b.team == 0 && b.hasBomb) {
                    b.state = "plant";
                    tryPlant(b, dt);
                    return;
                }
                if (planted) {
                    if (b.team == 1) {
                        float dd = dist(b.body.x, b.body.z, bombPos[0], bombPos[2]);
                        if (dd < 1.6f) {
                            b.state = "defuse";
                            b.plantTimer += dt;
                            b.body.vx = b.body.vz = 0;
                            if (b.plantTimer > 5f) {
                                defuses++;
                                addFeed(b.name + " обезвредил бомбу");
                                planted = false;
                                finishRound(1);
                            }
                            return;
                        }
                        b.plantTimer = 0;
                        goTo(b, bombPos[0], bombPos[2], dt, 3.6f);
                        return;
                    }
                    // T defend
                    float dx = b.body.x - bombPos[0], dz = b.body.z - bombPos[2];
                    if (dx * dx + dz * dz > 100) goTo(b, bombPos[0], bombPos[2], dt, 3.4f);
                    else {
                        b.body.vx = b.body.vz = 0;
                        Physics.step(map, b.body, dt);
                    }
                    return;
                }
                float[] site = (b.team == 0) ? (targetSiteA() ? map.siteA : map.siteB) : (b.team == 0 ? map.siteA : map.siteB);
                if (b.target >= 0) {
                    goTo(b, b.lastX, b.lastZ, dt, 3.5f);
                } else {
                    goTo(b, (site[0] + site[2]) / 2f, (site[1] + site[3]) / 2f, dt, 3.4f);
                }
                return;
            }
            if (b.target >= -1 && b.target != -2) {
                goTo(b, b.lastX, b.lastZ, dt, 3.5f);
            } else {
                wander(b, dt);
            }
        }
    }

    private boolean targetSiteA() {
        return round % 2 == 1;
    }

    private void tryPlant(Bot b, float dt) {
        float[] s = null;
        if (map.inSite(b.body.x, b.body.z, map.siteA)) s = map.siteA;
        if (map.inSite(b.body.x, b.body.z, map.siteB)) s = map.siteB;
        if (s == null) {
            float[] t = map.siteA;
            goTo(b, (t[0] + t[2]) / 2f, (t[1] + t[3]) / 2f, dt, 3.3f);
            return;
        }
        b.body.vx = b.body.vz = 0;
        b.plantTimer += dt;
        if (b.plantTimer > 3.2f) {
            b.hasBomb = false;
            bombCarrierBot = -2;
            planted = true;
            bombPos = new float[]{b.body.x, b.body.y, b.body.z};
            bombTimer = 40f;
            plants++;
            addFeed(b.name + " заложил бомбу");
            assets.play("plant", 1f);
        }
    }

    private static final class Target {
        Bot bot;
        float x, eyeY, z;
    }

    private Target resolveTarget(Bot b) {
        if (b.target == -1 && alive) {
            Target t = new Target();
            t.x = body.x; t.eyeY = eyeY(); t.z = body.z;
            return t;
        }
        if (b.target >= 0) {
            for (Bot o : bots) {
                if (o.id == b.target && o.alive) {
                    Target t = new Target();
                    t.bot = o; t.x = o.body.x; t.eyeY = o.eyeY(); t.z = o.body.z;
                    return t;
                }
            }
        }
        return null;
    }

    private Target findTarget(Bot b) {
        Target best = null;
        float bd = Float.MAX_VALUE;
        boolean hostilePlayer = cfg.mode == GameConfig.MODE_DM || b.team != playerTeam;
        if (alive && hostilePlayer) {
            float d = dist(b.body.x, b.eyeY(), b.body.z, body.x, eyeY(), body.z);
            if (d < 70 && Physics.canSee(map, b.body.x, b.eyeY(), b.body.z, body.x, eyeY(), body.z)) {
                best = new Target();
                best.x = body.x; best.eyeY = eyeY(); best.z = body.z;
                bd = d;
            }
        }
        for (Bot o : bots) {
            if (o == b || !o.alive) continue;
            boolean hostile = cfg.mode == GameConfig.MODE_DM || o.team != b.team;
            if (!hostile) continue;
            float d = dist(b.body.x, b.eyeY(), b.body.z, o.body.x, o.eyeY(), o.body.z);
            if (d >= bd || d > 70) continue;
            if (!Physics.canSee(map, b.body.x, b.eyeY(), b.body.z, o.body.x, o.eyeY(), o.body.z)) continue;
            Target t = new Target();
            t.bot = o;
            t.x = o.body.x; t.eyeY = o.eyeY(); t.z = o.body.z;
            best = t;
            bd = d;
        }
        return best;
    }

    private void aimAt(Bot b, float tx, float ty, float tz, float dt) {
        float dx = tx - b.body.x, dz = tz - b.body.z;
        float dy = ty - b.eyeY();
        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        float wantPitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float speed = b.skill == 2 ? 12f : b.skill == 1 ? 8f : 5f;
        b.yaw = lerpAngle(b.yaw, wantYaw, Math.min(1f, speed * dt));
        b.pitch += (wantPitch - b.pitch) * Math.min(1f, speed * dt);
    }

    private float lerpAngle(float a, float b, float t) {
        float d = ((b - a + 540f) % 360f) - 180f;
        return a + d * t;
    }

    private float reaction(Bot b) {
        return b.skill == 2 ? 0.16f : b.skill == 1 ? 0.3f : 0.5f;
    }

    private float reactionDelay(Bot b) {
        return reaction(b) + 0.1f;
    }

    private void botShoot(Bot b, Target t) {
        Weapons.Def d = b.weapon;
        b.fireTimer = d.interval() * (1.15f + (float) Math.random() * 0.4f);
        assets.play("shot_" + d.cat, 0.4f);
        float inaccuracy;
        if (b.skill == 2) inaccuracy = 0.022f;
        else if (b.skill == 1) inaccuracy = 0.05f;
        else inaccuracy = 0.1f;
        float[] o = {b.body.x, b.eyeY(), b.body.z};
        float dx = t.x - o[0], dy = t.eyeY - o[1], dz = t.z - o[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float[] dir = {dx / len, dy / len, dz / len};
        float[] dd = jitter(dir, inaccuracy);
        float wall = map.raycast(o[0], o[1], o[2], dd[0], dd[1], dd[2], d.range);
        float dmg = d.damage * 0.42f;

        if (t.bot == null) {
            float tHit = MapData.rayBox(o[0], o[1], o[2], dd[0], dd[1], dd[2],
                    body.x - 0.3f, body.y + 0.7f, body.z - 0.3f,
                    body.x + 0.3f, body.y + 1.8f, body.z + 0.3f);
            if (tHit >= 0 && (wall < 0 || tHit < wall)) {
                damagePlayer(dmg, b.name);
            }
        } else {
            RayHit hit = rayHitBot(t.bot, o[0], o[1], o[2], dd, d.range);
            if (hit != null && (wall < 0 || hit.dist < wall)) {
                damageBot(t.bot, dmg, hit.head, b.name, b.team);
            }
        }
    }

    private void move(Bot b, float strafe, float forward, float dt, float speed) {
        float fx = (float) -Math.sin(Math.toRadians(b.yaw));
        float fz = (float) -Math.cos(Math.toRadians(b.yaw));
        float rx = (float) Math.cos(Math.toRadians(b.yaw));
        float rz = (float) -Math.sin(Math.toRadians(b.yaw));
        float mx = fx * forward + rx * strafe;
        float mz = fz * forward + rz * strafe;
        float l = (float) Math.sqrt(mx * mx + mz * mz);
        if (l > 1f) { mx /= l; mz /= l; }
        b.body.vx = mx * speed;
        b.body.vz = mz * speed;
        Physics.step(map, b.body, dt);
    }

    private void goTo(Bot b, float x, float z, float dt, float speed) {
        int from = map.nearestWaypoint(b.body.x, b.body.z);
        int goal = map.nearestWaypoint(x, z);
        if (b.goalWp != goal || b.pathTimer <= 0) {
            b.goalWp = goal;
            b.pathTimer = 1.5f;
        }
        int next = map.nextHop(from, goal);
        float[] wp = map.waypoints.get(next);
        float dx = wp[0] - b.body.x;
        float dz = wp[2] - b.body.z;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < 0.6f) {
            dx = x - b.body.x;
            dz = z - b.body.z;
            d = (float) Math.sqrt(dx * dx + dz * dz);
        }
        if (d < 0.3f) {
            b.body.vx = b.body.vz = 0;
            Physics.step(map, b.body, dt);
            return;
        }
        b.yaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        b.pitch += (0 - b.pitch) * Math.min(1f, 6 * dt);
        float mx = dx / d * speed;
        float mz = dz / d * speed;
        b.body.vx = mx;
        b.body.vz = mz;
        Physics.step(map, b.body, dt);
    }

    private void wander(Bot b, float dt) {
        if (b.goalWp < 0 || b.pathTimer <= 0) {
            b.goalWp = (int) (Math.random() * map.waypoints.size());
            b.pathTimer = 4f;
        }
        float[] wp = map.waypoints.get(b.goalWp);
        goTo(b, wp[0], wp[2], dt, 2.9f);
    }

    // ------------------- bomb -------------------
    private void updateBomb(float dt) {
        if (cfg.mode != GameConfig.MODE_BOMB || planted) return;
        if (plantHeld && alive && bombCarrierBot == -1) {
            float[] site = null;
            if (map.inSite(body.x, body.z, map.siteA)) site = map.siteA;
            if (map.inSite(body.x, body.z, map.siteB)) site = map.siteB;
            if (site != null) {
                float sp = (float) Math.sqrt(body.vx * body.vx + body.vz * body.vz);
                if (sp < 0.8f) {
                    plantTimer += dt;
                    hint = "Закладка: " + Math.min(100, (int) (plantTimer / 3.2f * 100)) + "%";
                    hintTimer = 0.3f;
                    if (plantTimer >= 3.2f) {
                        planted = true;
                        bombCarrierBot = -2;
                        bombPos = new float[]{body.x, body.y, body.z};
                        bombTimer = 40f;
                        plants++;
                        addFeed(playerName + " заложил бомбу");
                        assets.play("plant", 1f);
                        hint = "Бомба заложена!";
                        hintTimer = 3f;
                        plantTimer = 0;
                    }
                } else {
                    plantTimer = Math.max(0, plantTimer - dt);
                    hint = "Держи позицию для закладки";
                    hintTimer = 0.3f;
                }
            } else {
                plantTimer = 0;
                hint = "Бомбу можно заложить на точке A или B";
                hintTimer = 0.3f;
            }
        } else if (!planted && !plantHeld) {
            plantTimer = Math.max(0, plantTimer - dt * 2);
        }

        if (bombCarrierBot == -2 && bombPos != null) {
            // pickup by a T bot
            for (Bot b : bots) {
                if (!b.alive || b.team != 0) continue;
                float d = dist(b.body.x, b.body.z, bombPos[0], bombPos[2]);
                if (d < 1.2f) {
                    b.hasBomb = true;
                    bombCarrierBot = b.id;
                    bombPos = null;
                    addFeed(b.name + " подобрал бомбу");
                    break;
                }
            }
            if (bombPos != null && alive && dist(body.x, body.z, bombPos[0], bombPos[2]) < 1.4f) {
                bombCarrierBot = -1;
                bombPos = null;
                hint = "Бомба у тебя";
                hintTimer = 2f;
            }
        }
    }

    private void explode() {
        assets.play("explosion", 1f);
        if (dist(body.x, body.z, bombPos[0], bombPos[2]) < 8f && alive) {
            damagePlayer(200, "Бомба");
        }
        for (Bot b : bots) {
            if (!b.alive) continue;
            if (dist(b.body.x, b.body.z, bombPos[0], bombPos[2]) < 8f) {
                b.hp = 0;
                b.alive = false;
                b.respawn = 4f;
            }
        }
    }

    // ------------------- rounds -------------------
    public void finishRound(int winner) {
        if (roundOver) return;
        roundOver = true;
        roundWinner = winner;
        roundEndTimer = 4f;
        if (winner == 0) scoreT++;
        else scoreCT++;
        if (roundKills >= 2) mvps++;
        assets.play(winner == 0 ? "round_win" : "round_lose", 0.9f);
    }

    private void endMatch(boolean won, boolean isDraw) {
        if (matchOver) return;
        matchOver = true;
        win = won;
        draw = isDraw;
        roundOver = true;
        applyRewards();
        resultText = won ? "ПОБЕДА" : isDraw ? "НИЧЬЯ" : "ПОРАЖЕНИЕ";
        assets.play(won ? "match_win" : "match_lose", 1f);
        if (listener != null) listener.onMatchEnd(this);
    }

    private void applyRewards() {
        if (eloApplied) return;
        eloApplied = true;
        int myElo = save.elo();
        int opp = 1000 + cfg.difficulty * 280;
        boolean objective = plants > 0 || defuses > 0;
        eloDelta = Elo.delta(myElo, opp, draw ? 0.5 : win ? 1 : 0, kills, mvps > 0, objective);
        JSONObject p = save.profile();
        try {
            if (cfg.lan || cfg.onlineElo) {
                p.put("elo", Math.max(0, myElo + eloDelta));
            } else {
                p.put("pendingElo", p.optInt("pendingElo", 0) + eloDelta);
            }
            save.saveProfile(p);
        } catch (Exception ignored) {}
        int coins = kills * 14 + (win ? 180 : 50) + mvps * 60 + (objective ? 40 : 0);
        coinsEarned = coins;
        save.addCoins(coins);
        xpEarned = Elo.xp(kills, win, mvps > 0);
        save.addStats(kills, deaths, win, draw, (int) (matchTime), bestStreak, xpEarned);
    }

    public int pendingElo() {
        return save.profile().optInt("pendingElo", 0);
    }

    public void syncElo() {
        JSONObject p = save.profile();
        try {
            int pend = p.optInt("pendingElo", 0);
            if (pend == 0) return;
            p.put("elo", Math.max(0, p.optInt("elo", 1000) + pend));
            p.put("pendingElo", 0);
            save.saveProfile(p);
            hint = "Эло синхронизировано: " + (pend > 0 ? "+" : "") + pend;
            hintTimer = 3f;
        } catch (Exception ignored) {}
    }

    public String timerText() {
        int t = (int) Math.max(0, cfg.mode == GameConfig.MODE_BOMB && planted ? bombTimer : timeLeft);
        int m = t / 60, s = t % 60;
        return String.format("%d:%02d", m, s);
    }

    public String ammoText() {
        if (currentDef().isKnife()) return "—";
        return slot.ammo + " / " + slot.reserve;
    }

    public boolean playerHasBomb() {
        return cfg.mode == GameConfig.MODE_BOMB && bombCarrierBot == -1 && !planted;
    }

    public boolean inPlantZone() {
        return map.inSite(body.x, body.z, map.siteA) || map.inSite(body.x, body.z, map.siteB);
    }

    private float dist(float ax, float az, float bx, float bz) {
        float dx = ax - bx, dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private float dist(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = ax - bx, dy = ay - by, dz = az - bz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------- LAN -------------------
    public void startLanHost() {
        try {
            lan = Lan.host(map, playerName);
            localId = lan.id();
            lanStatus = lan.status();
            hint = "LAN хост: " + Lan.localIp() + ":" + Lan.PORT;
            hintTimer = 8f;
            timeLeft = 600f;
            freeze = 0.6f;
        } catch (Throwable e) {
            lanStatus = "LAN: " + e.getMessage();
            hint = lanStatus;
            hintTimer = 6f;
            cfg.lan = false;
        }
    }

    public void startLanClient(final String ip) {
        lanStatus = "Подключение к " + ip + "…";
        hint = lanStatus;
        hintTimer = 6f;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Lan l = Lan.join(map, ip, playerName, 3000);
                    lan = l;
                    localId = l.id();
                    lanStatus = l.status();
                    hint = lanStatus;
                    hintTimer = 5f;
                    timeLeft = 600f;
                    freeze = 0.6f;
                } catch (Throwable e) {
                    lanStatus = "LAN: " + e.getMessage();
                    hint = lanStatus;
                    hintTimer = 8f;
                    cfg.lan = false;
                }
            }
        }, "lan-connect").start();
    }

    private void updateLan(float dt) {
        lanStatus = lan.status();
        lanOnline = lan.playerCount();
        JSONObject ev;
        while ((ev = lan.pollEvent()) != null) {
            String t = ev.optString("t");
            if ("hit".equals(t)) {
                int victim = ev.optInt("victim", -1);
                int dmg = ev.optInt("dmg", 0);
                String by = ev.optString("by", "?");
                boolean dead = ev.optBoolean("dead", false);
                if (victim == localId) {
                    hp -= dmg;
                    damageFlash = 0.5f;
                    assets.play("pain", 0.7f);
                    if (hp <= 0) {
                        hp = 0;
                        alive = false;
                        deaths++;
                        streak = 0;
                        respawnTimer = 3f;
                    }
                } else if (by.equals(playerName)) {
                    hitMarker = 0.25f;
                    assets.play("hitmarker", 0.7f);
                }
                if (dead) addFeed(by + "  ⟶  " + lanName(victim));
            } else if ("status".equals(t)) {
                lanStatus = ev.optString("msg", "");
                hint = lanStatus;
                hintTimer = 4f;
            }
        }

        JSONArray snap = lan.latestSnapshot();
        if (snap != null) {
            boolean localSeen = false;
            ArrayList<Remote> next = new ArrayList<>();
            for (int i = 0; i < snap.length(); i++) {
                JSONObject o = snap.optJSONObject(i);
                if (o == null) continue;
                int id = o.optInt("id", -1);
                if (id == localId) {
                    localSeen = true;
                    int hps = o.optInt("hp", 100);
                    boolean aliveS = o.optBoolean("alive", true);
                    kills = o.optInt("k", kills);
                    lanKills = kills;
                    if (!aliveS && alive) {
                        hp = 0;
                        alive = false;
                        respawnTimer = 3f;
                    } else if (aliveS && !alive && respawnTimer <= 0) {
                        hp = 100;
                        alive = true;
                        refill();
                    } else if (alive && hps > 0 && hps < hp) {
                        hp = hps;
                    }
                    continue;
                }
                Remote r = new Remote();
                r.id = id;
                r.name = o.optString("n", "Player");
                r.x = (float) o.optDouble("x", 0);
                r.y = (float) o.optDouble("y", 0);
                r.z = (float) o.optDouble("z", 0);
                r.yaw = (float) o.optDouble("yaw", 0);
                r.hp = o.optInt("hp", 100);
                r.alive = o.optBoolean("alive", true);
                r.kills = o.optInt("k", 0);
                next.add(r);
            }
            remotes.clear();
            remotes.addAll(next);
            if (!localSeen && lanOnline > 1) {
                lanOnline = snap.length();
            }
        }

        lanSendTimer -= dt;
        if (lanSendTimer <= 0) {
            lanSendTimer = 0.1f;
            lan.move(body.x, body.y, body.z, yaw);
        }
    }

    private String lanName(int id) {
        if (id == localId) return playerName;
        for (Remote r : remotes) if (r.id == id) return r.name;
        return "Player";
    }

    public boolean onlineElo;
}
