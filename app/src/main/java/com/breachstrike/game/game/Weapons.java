package com.breachstrike.game.game;

import java.util.ArrayList;
import java.util.List;

public final class Weapons {
    public static final class Def {
        public String id, name, cat;
        public int price;
        public float damage, rpm, mag, reload, spread, range, recoil;
        public int pellets = 1;
        public boolean scoped;
        public float scopeFov = 40f;

        public float interval() { return 60f / rpm; }
        public boolean isKnife() { return "knife".equals(cat); }
    }

    public static final List<Def> ALL = new ArrayList<>();

    private static Def w(String id, String name, String cat, int price, float dmg,
                         float rpm, float mag, float reload, float spread, float range,
                         float recoil) {
        Def d = new Def();
        d.id = id; d.name = name; d.cat = cat; d.price = price;
        d.damage = dmg; d.rpm = rpm; d.mag = mag; d.reload = reload;
        d.spread = spread; d.range = range; d.recoil = recoil;
        ALL.add(d);
        return d;
    }

    static {
        w("glock", "Glock-18", "pistol", 200, 20, 400, 20, 1.4f, 0.9f, 45, 0.9f);
        w("usp", "USP-S", "pistol", 250, 24, 350, 12, 2.0f, 0.8f, 50, 1.0f);
        w("p250", "P250", "pistol", 300, 28, 400, 13, 2.0f, 0.85f, 48, 1.1f);
        w("fiveseven", "Five-SeveN", "pistol", 500, 26, 450, 20, 2.0f, 0.8f, 52, 1.0f);
        w("deagle", "Desert Eagle", "pistol", 700, 55, 267, 7, 2.4f, 1.1f, 60, 2.4f);

        w("ak47", "AK-47", "rifle", 2700, 36, 600, 30, 2.5f, 1.0f, 90, 2.0f);
        w("m4a4", "M4A4", "rifle", 3100, 33, 666, 30, 2.5f, 0.9f, 90, 1.8f);
        w("m4a1s", "M4A1-S", "rifle", 2900, 30, 600, 25, 2.7f, 0.75f, 90, 1.5f);
        w("aug", "AUG", "rifle", 3300, 32, 600, 30, 2.6f, 0.8f, 90, 1.7f);
        w("sg553", "SG 553", "rifle", 3000, 34, 545, 30, 2.6f, 0.95f, 90, 2.0f);
        w("famas", "FAMAS", "rifle", 2050, 28, 666, 25, 2.5f, 1.0f, 85, 1.8f);
        w("galil", "Galil AR", "rifle", 1800, 30, 666, 35, 2.8f, 1.05f, 85, 1.9f);

        w("mp9", "MP9", "smg", 1250, 26, 800, 30, 2.1f, 1.2f, 60, 1.2f);
        w("mac10", "MAC-10", "smg", 1050, 27, 800, 30, 2.1f, 1.35f, 55, 1.3f);
        w("mp5", "MP5-SD", "smg", 1500, 28, 750, 30, 2.2f, 1.1f, 65, 1.2f);
        w("p90", "P90", "smg", 2350, 25, 850, 50, 2.6f, 1.25f, 60, 1.2f);
        w("ump", "UMP-45", "smg", 1200, 29, 666, 25, 2.3f, 1.15f, 65, 1.3f);

        w("nova", "Nova", "shotgun", 1050, 13, 90, 8, 3.0f, 2.6f, 25, 3.2f).pellets = 8;
        w("xm1014", "XM1014", "shotgun", 2000, 12, 200, 8, 3.0f, 2.4f, 25, 2.6f).pellets = 7;

        w("negev", "Negev", "lmg", 1700, 26, 800, 150, 4.8f, 1.9f, 80, 1.8f);
        w("m249", "M249", "lmg", 5200, 28, 750, 100, 5.0f, 1.8f, 85, 1.8f);

        w("awp", "AWP", "sniper", 4750, 115, 41, 5, 3.6f, 2.2f, 120, 5.0f);
        w("ssg08", "SSG 08", "sniper", 1700, 80, 48, 10, 3.2f, 1.8f, 120, 3.6f);
        w("scar20", "SCAR-20", "sniper", 5000, 70, 240, 20, 3.4f, 1.3f, 110, 3.0f);
        w("g3sg1", "G3SG1", "sniper", 5000, 65, 240, 20, 3.4f, 1.4f, 110, 3.0f);

        Def awp = get("awp"); awp.scoped = true; awp.scopeFov = 25f;
        Def ssg = get("ssg08"); ssg.scoped = true; ssg.scopeFov = 30f;
        Def scar = get("scar20"); scar.scoped = true; scar.scopeFov = 35f;
        Def g3 = get("g3sg1"); g3.scoped = true; g3.scopeFov = 35f;
        Def aug = get("aug"); aug.scoped = true; aug.scopeFov = 45f;
        Def sg = get("sg553"); sg.scoped = true; sg.scopeFov = 45f;

        w("butterfly", "Бабочка", "knife", 1000, 55, 120, 0, 0, 0, 2.0f, 0);
        w("karambit", "Керамбит", "knife", 1200, 60, 120, 0, 0, 0, 2.0f, 0);
        w("bayonet", "Штык-нож", "knife", 900, 58, 120, 0, 0, 0, 2.0f, 0);
        w("talon", "Талон", "knife", 1300, 58, 120, 0, 0, 0, 2.0f, 0);
        w("flip", "Флип", "knife", 800, 52, 120, 0, 0, 0, 2.0f, 0);
    }

    public static Def get(String id) {
        for (Def d : ALL) if (d.id.equals(id)) return d;
        return ALL.get(0);
    }

    public static List<Def> guns() {
        List<Def> out = new ArrayList<>();
        for (Def d : ALL) if (!d.isKnife()) out.add(d);
        return out;
    }

    public static List<Def> knives() {
        List<Def> out = new ArrayList<>();
        for (Def d : ALL) if (d.isKnife()) out.add(d);
        return out;
    }

    public static String categoryName(String cat) {
        switch (cat) {
            case "pistol": return "Пистолеты";
            case "rifle": return "Винтовки";
            case "smg": return "ПП";
            case "shotgun": return "Дробовики";
            case "lmg": return "Пулемёты";
            case "sniper": return "Снайперские";
            case "knife": return "Ножи";
            default: return cat;
        }
    }
}
