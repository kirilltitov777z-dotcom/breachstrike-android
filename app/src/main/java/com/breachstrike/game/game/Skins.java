package com.breachstrike.game.game;

public final class Skins {
    public static final String[] PATTERNS = {
            "fade", "casehardened", "doppler", "slaughter", "asiimov", "hyperbeast", "dragonlore"
    };
    public static final String[] PATTERN_NAMES = {
            "Fade", "Case Hardened", "Doppler", "Slaughter", "Asiimov", "Hyper Beast", "Dragon Lore"
    };
    /** 0 Mil-Spec .. 4 Contraband */
    public static final int[] RARITY = {4, 2, 4, 3, 2, 3, 4};
    public static final String[] RARITY_NAMES = {
            "Mil-Spec", "Restricted", "Classified", "Covert", "Contraband"
    };
    public static final int[] RARITY_COLORS = {
            0xFF4B69FF, 0xFF8847FF, 0xFFD32CE6, 0xFFEB4B4B, 0xFFE4AE39
    };
    public static final String[] WEARS = {"FN", "MW", "FT", "WW", "BS"};
    public static final String[] WEAR_NAMES = {
            "Factory New", "Minimal Wear", "Field-Tested", "Well-Worn", "Battle-Scarred"
    };
    private static final float[] WEAR_MULT = {1.0f, 0.8f, 0.62f, 0.48f, 0.34f};

    private Skins() {}

    public static String key(String weapon, String pattern, String wear) {
        return weapon + "|" + pattern + "|" + wear;
    }

    public static String patternOf(String key) {
        String[] p = key.split("\\|");
        return p.length > 1 ? p[1] : "fade";
    }

    public static String wearOf(String key) {
        String[] p = key.split("\\|");
        return p.length > 2 ? p[2] : "FN";
    }

    public static String weaponOf(String key) {
        String[] p = key.split("\\|");
        return p.length > 0 ? p[0] : "";
    }

    public static String display(String weapon, String patternWear) {
        if (patternWear == null || patternWear.length() == 0) return "Без скина";
        String[] p = patternWear.split("\\|");
        String pattern = p.length > 0 ? p[0] : "fade";
        String wear = p.length > 1 ? p[1] : "FN";
        return patternName(pattern) + " (" + wearName(wear) + ")";
    }

    public static String patternName(String pattern) {
        for (int i = 0; i < PATTERNS.length; i++) if (PATTERNS[i].equals(pattern)) return PATTERN_NAMES[i];
        return "Fade";
    }

    public static String wearName(String wear) {
        for (int i = 0; i < WEARS.length; i++) if (WEARS[i].equals(wear)) return WEAR_NAMES[i];
        return "Factory New";
    }

    public static int rarity(String pattern) {
        for (int i = 0; i < PATTERNS.length; i++) if (PATTERNS[i].equals(pattern)) return RARITY[i];
        return 0;
    }

    public static String rarityName(String pattern) {
        return RARITY_NAMES[rarity(pattern)];
    }

    public static int rarityColor(String pattern) {
        return RARITY_COLORS[rarity(pattern)];
    }

    public static int price(String weaponId, String pattern, String wear) {
        Weapons.Def d = Weapons.get(weaponId);
        float base = Math.max(120f, d.price * 0.45f);
        float pat = 0.6f + rarity(pattern) * 0.6f;
        pat *= 1f + Math.abs(pattern.hashCode() % 100) / 100f;
        float wr = 1f;
        for (int i = 0; i < WEARS.length; i++) if (WEARS[i].equals(wear)) wr = WEAR_MULT[i];
        int p = (int) (base * pat * wr);
        return Math.max(150, (p / 10) * 10);
    }

    public static int wearInt(String wear) {
        for (int i = 0; i < WEARS.length; i++) if (WEARS[i].equals(wear)) return i;
        return 0;
    }

    public static String skinTexture(String pattern) {
        return "skin_" + pattern;
    }

    /** Deterministic weapon tint, used together with the pattern texture. */
    public static float[] tint(String weaponId) {
        int h = Math.abs(weaponId.hashCode());
        float hue = (h % 360) / 360f;
        int rgb = android.graphics.Color.HSVToColor(new float[]{hue, 0.25f + (h % 20) / 100f, 0.95f});
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }
}
