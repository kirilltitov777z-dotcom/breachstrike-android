package com.breachstrike.game.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Save {
    private static Save inst;
    private final SharedPreferences sp;

    private Save(Context c) {
        sp = c.getSharedPreferences("breachstrike", Context.MODE_PRIVATE);
        if (!sp.contains("profile")) {
            JSONObject p = new JSONObject();
            try {
                p.put("name", "Player");
                p.put("level", 1);
                p.put("xp", 0);
                p.put("elo", 1000);
                p.put("coins", 2500);
                p.put("kills", 0);
                p.put("deaths", 0);
                p.put("wins", 0);
                p.put("losses", 0);
                p.put("bestStreak", 0);
                p.put("playSeconds", 0);
                p.put("pendingElo", 0);
                p.put("clanName", "");
                p.put("clanTag", "");
                sp.edit().putString("profile", p.toString()).apply();

                JSONArray inv = new JSONArray();
                inv.put("usp|fade|FN");
                inv.put("ak47|asiimov|FT");
                inv.put("butterfly|doppler|FN");
                inv.put("m4a4|hyperbeast|MW");
                sp.edit().putString("inventory", inv.toString()).apply();

                JSONObject lo = new JSONObject();
                lo.put("primary", "ak47");
                lo.put("secondary", "usp");
                lo.put("knife", "butterfly");
                lo.put("skin_ak47", "asiimov|FT");
                lo.put("skin_usp", "fade|FN");
                lo.put("skin_butterfly", "doppler|FN");
                sp.edit().putString("loadout", lo.toString()).apply();
            } catch (Exception ignored) {}
        }
    }

    public static synchronized Save get(Context c) {
        if (inst == null) inst = new Save(c.getApplicationContext());
        return inst;
    }

    public SharedPreferences prefs() { return sp; }

    // ---------- settings ----------
    public float sens() { return sp.getFloat("sens", 1.0f); }
    public void setSens(float v) { sp.edit().putFloat("sens", v).apply(); }

    public float fov() { return sp.getFloat("fov", 75f); }
    public void setFov(float v) { sp.edit().putFloat("fov", v).apply(); }

    public float crossSize() { return sp.getFloat("crossSize", 1.0f); }
    public void setCrossSize(float v) { sp.edit().putFloat("crossSize", v).apply(); }

    public String crossStyle() { return sp.getString("crossStyle", "cross"); }
    public void setCrossStyle(String v) { sp.edit().putString("crossStyle", v).apply(); }

    public String crossColor() { return sp.getString("crossColor", "#39FF6A"); }
    public void setCrossColor(String v) { sp.edit().putString("crossColor", v).apply(); }

    public boolean invertY() { return sp.getBoolean("invertY", false); }
    public void setInvertY(boolean v) { sp.edit().putBoolean("invertY", v).apply(); }

    public boolean showFps() { return sp.getBoolean("showFps", true); }
    public void setShowFps(boolean v) { sp.edit().putBoolean("showFps", v).apply(); }

    public int quality() { return sp.getInt("quality", 2); }
    public void setQuality(int v) { sp.edit().putInt("quality", v).apply(); }

    public float volume() { return sp.getFloat("volume", 0.8f); }
    public void setVolume(float v) { sp.edit().putFloat("volume", v).apply(); }

    public String lastName() { return sp.getString("lastName", "Player"); }
    public void setLastName(String v) { sp.edit().putString("lastName", v).apply(); }

    // ---------- profile ----------
    public JSONObject profile() {
        try {
            return new JSONObject(sp.getString("profile", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void saveProfile(JSONObject p) {
        sp.edit().putString("profile", p.toString()).apply();
    }

    public int elo() { return profile().optInt("elo", 1000); }

    public void addElo(int delta) {
        JSONObject p = profile();
        try {
            p.put("elo", Math.max(0, p.optInt("elo", 1000) + delta));
            saveProfile(p);
        } catch (Exception ignored) {}
    }

    public void addCoins(int c) {
        JSONObject p = profile();
        try {
            p.put("coins", Math.max(0, p.optInt("coins", 0) + c));
            saveProfile(p);
        } catch (Exception ignored) {}
    }

    public void addStats(int kills, int deaths, boolean win, boolean draw, int seconds, int bestStreak, int xp) {
        JSONObject p = profile();
        try {
            p.put("kills", p.optInt("kills", 0) + kills);
            p.put("deaths", p.optInt("deaths", 0) + deaths);
            if (win) p.put("wins", p.optInt("wins", 0) + 1);
            if (!win && !draw) p.put("losses", p.optInt("losses", 0) + 1);
            p.put("playSeconds", p.optInt("playSeconds", 0) + seconds);
            if (bestStreak > p.optInt("bestStreak", 0)) p.put("bestStreak", bestStreak);
            p.put("xp", p.optInt("xp", 0) + xp);
            int lvl = p.optInt("level", 1);
            while (p.optInt("xp", 0) >= lvl * 500) {
                p.put("xp", p.optInt("xp", 0) - lvl * 500);
                lvl++;
            }
            p.put("level", lvl);
            saveProfile(p);
        } catch (Exception ignored) {}
    }

    // ---------- inventory ----------
    public JSONArray inventory() {
        try {
            return new JSONArray(sp.getString("inventory", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public boolean hasSkin(String key) {
        JSONArray inv = inventory();
        for (int i = 0; i < inv.length(); i++) {
            if (key.equals(inv.optString(i))) return true;
        }
        return false;
    }

    public void addSkin(String key) {
        if (hasSkin(key)) return;
        JSONArray inv = inventory();
        inv.put(key);
        sp.edit().putString("inventory", inv.toString()).apply();
    }

    // key format: pattern|wear
    public String equippedSkin(String weapon) {
        return loadout().optString("skin_" + weapon, "");
    }

    public void equipSkin(String weapon, String patternWear) {
        JSONObject lo = loadout();
        try {
            if (patternWear == null || patternWear.length() == 0) lo.remove("skin_" + weapon);
            else lo.put("skin_" + weapon, patternWear);
            sp.edit().putString("loadout", lo.toString()).apply();
        } catch (Exception ignored) {}
    }

    public JSONObject loadout() {
        try {
            return new JSONObject(sp.getString("loadout", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public String slot(String name, String fallback) {
        String v = loadout().optString(name, fallback);
        return v.length() == 0 ? fallback : v;
    }

    public void setSlot(String name, String value) {
        JSONObject lo = loadout();
        try {
            lo.put(name, value);
            sp.edit().putString("loadout", lo.toString()).apply();
        } catch (Exception ignored) {}
    }
}
