package com.breachstrike.game.game;

import java.util.Locale;

public final class Elo {
    public static final String[] RANKS = {
            "Silver", "Gold Nova", "Master Guardian", "DMG",
            "Legendary Eagle", "LEM", "Supreme", "Global Elite"
    };
    public static final int[] MIN = {0, 1200, 1400, 1600, 1800, 2000, 2200, 2400};
    public static final int K = 32;
    public static final int START = 1000;

    private Elo() {}

    public static int rankIndex(int elo) {
        int r = 0;
        for (int i = 0; i < MIN.length; i++) if (elo >= MIN[i]) r = i;
        return r;
    }

    public static String rankName(int elo) {
        return RANKS[rankIndex(elo)];
    }

    public static String rankShort(int elo) {
        switch (rankIndex(elo)) {
            case 0: return "SILVER";
            case 1: return "GOLD NOVA";
            case 2: return "MG";
            case 3: return "DMG";
            case 4: return "LE";
            case 5: return "LEM";
            case 6: return "SUPREME";
            default: return "GLOBAL";
        }
    }

    public static int nextRankElo(int elo) {
        int r = rankIndex(elo);
        return r >= MIN.length - 1 ? MIN[MIN.length - 1] : MIN[r + 1];
    }

    public static double expected(int my, int opp) {
        return 1.0 / (1.0 + Math.pow(10.0, (opp - my) / 400.0));
    }

    /**
     * @param score 1 win, 0.5 draw, 0 loss
     */
    public static int delta(int myElo, int oppElo, double score, int kills, boolean mvp, boolean objective) {
        double base = K * (score - expected(myElo, oppElo));
        double bonus = 0;
        if (score > 0) {
            bonus = Math.min(8, kills * 1.2);
            if (mvp) bonus += 4;
            if (objective) bonus += 5;
            if (score == 0.5) bonus *= 0.5;
        }
        int d = (int) Math.round(base + bonus);
        if (d == 0) d = score > 0.5 ? 1 : score < 0.5 ? -1 : 0;
        return d;
    }

    public static int xp(int kills, boolean win, boolean mvp) {
        return 60 + kills * 8 + (win ? 60 : 0) + (mvp ? 25 : 0);
    }

    public static String fmt(int elo) {
        return String.format(Locale.US, "%d", elo);
    }
}
