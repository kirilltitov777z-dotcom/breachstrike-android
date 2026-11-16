package com.breachstrike.game.game;

public final class GameConfig {
    public static final int MODE_BOMB = 0;
    public static final int MODE_DM = 1;
    public static final int MODE_TDM = 2;

    public int mode = MODE_BOMB;
    public int mapIndex = 0;
    public int difficulty = 1;
    public boolean lan;
    public boolean lanHost;
    public boolean onlineElo;
    public String lanIp = "192.168.1.100";
    public int lanMode = MODE_DM;
    public String playerName = "Player";

    public String modeName() {
        switch (mode) {
            case MODE_BOMB: return "5v5 — Бомба";
            case MODE_DM: return "Deathmatch";
            default: return "Team Deathmatch";
        }
    }
}
