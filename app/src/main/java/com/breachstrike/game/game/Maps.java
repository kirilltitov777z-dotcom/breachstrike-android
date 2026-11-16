package com.breachstrike.game.game;

public final class Maps {
    public static final MapData[] ALL = new MapData[2];

    static {
        ALL[0] = foundry();
        ALL[1] = docks();
    }

    private Maps() {}

    public static MapData get(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    private static MapData foundry() {
        MapData m = new MapData();
        m.id = "foundry";
        m.name = "Foundry";
        m.skyColor = 0xFF7D93A8;
        m.floorMat = "concrete";

        // shell
        m.add(0, -0.5f, 0, 62, 1, 54, "concrete");
        m.add(0, 0, -26, 62, 7, 1, "concrete2");
        m.add(0, 0, 26, 62, 7, 1, "concrete2");
        m.add(-30, 0, 0, 1, 7, 52, "concrete2");
        m.add(30, 0, 0, 1, 7, 52, "concrete2");

        // middle building
        m.add(0, 0, -2, 8, 5, 12, "brick");
        m.add(2.5f, 5, -2, 3, 0.4f, 12.4f, "metal");

        // east balcony over the yard
        m.add(25.5f, 2.8f, 1, 7, 0.2f, 18, "metal");
        m.add(25.5f, 0, -6, 0.4f, 2.8f, 0.4f, "metal");
        m.add(25.5f, 0, 8, 0.4f, 2.8f, 0.4f, "metal");
        m.add(22.1f, 3.0f, 1, 0.2f, 1.1f, 18, "metal");
        m.stairs(25.5f, 14, 10, 6.4f, 0, 3.0f, "metal");

        // site A cover
        m.add(16, 0, -14, 2.6f, 2.6f, 2.6f, "crate");
        m.add(23, 0, -19, 3, 1.4f, 2.2f, "crate");
        m.add(18, 0, -11.5f, 4, 1.0f, 0.6f, "concrete");
        m.add(26.5f, 0, -15, 0.6f, 2.0f, 5, "metal");

        // site B cover
        m.add(-16, 0, -14, 2.6f, 2.6f, 2.6f, "crate");
        m.add(-23, 0, -19, 3, 1.4f, 2.2f, "crate");
        m.add(-18, 0, -11.5f, 4, 1.0f, 0.6f, "concrete");
        m.add(-26.5f, 0, -15, 0.6f, 2.0f, 5, "metal");

        // yard cover
        m.add(9, 0, 8, 2.2f, 2.2f, 2.2f, "crate");
        m.add(-9, 0, 8, 2.2f, 2.2f, 2.2f, "crate");
        m.add(10.5f, 0, -2, 0.6f, 1.6f, 8, "brick");
        m.add(-10.5f, 0, -2, 0.6f, 1.6f, 8, "brick");
        m.add(-8, 0, 17, 0.8f, 4, 7, "brick");
        m.add(8, 0, 17, 0.8f, 4, 7, "brick");
        m.add(0, 0, 10, 1.4f, 1.4f, 1.4f, "barrel");
        m.add(3, 0, 12, 1.4f, 1.4f, 1.4f, "barrel");
        m.add(-3, 0, 3, 1.4f, 1.4f, 1.4f, "barrel");

        // site floors
        m.site(12, -23, 27, -11, true);
        m.site(-27, -23, -12, -11, false);

        // spawns
        m.spawnT(0, 22); m.spawnT(4, 23); m.spawnT(-4, 23); m.spawnT(8, 21); m.spawnT(-8, 21);
        m.spawnCT(0, -24); m.spawnCT(4, -24); m.spawnCT(-4, -24); m.spawnCT(8, -24); m.spawnCT(-8, -24);

        // waypoints
        m.wp(0, 22); m.wp(8, 20); m.wp(-8, 20); m.wp(0, 15); m.wp(10, 14); m.wp(-10, 14);
        m.wp(14, 8); m.wp(-14, 8); m.wp(20, 10); m.wp(-20, 10);
        m.wp(25.5f, 13); m.wp(25.5f, 8); m.wp(25.5f, 2); m.wp(25.5f, -6);
        m.wp(16, 2); m.wp(-16, 2); m.wp(18, -10); m.wp(-18, -10);
        m.wp(20, -16); m.wp(-20, -16); m.wp(14, -20); m.wp(-14, -20);
        m.wp(24, -22); m.wp(-24, -22); m.wp(8, -6); m.wp(-8, -6);
        m.wp(0, 6); m.wp(0, -12); m.wp(0, -20); m.wp(8, -24); m.wp(-8, -24);
        m.wp(4, 4); m.wp(-4, 4); m.wp(4, -8); m.wp(-4, -8);
        m.wp(12, 18); m.wp(-12, 18);

        m.finish();
        return m;
    }

    private static MapData docks() {
        MapData m = new MapData();
        m.id = "docks";
        m.name = "Neon Docks";
        m.skyColor = 0xFF1E2A3A;
        m.floorMat = "tile";

        m.add(0, -0.5f, 0, 58, 1, 58, "tile");
        m.add(0, 0, -28, 58, 8, 1, "rust");
        m.add(0, 0, 28, 58, 8, 1, "rust");
        m.add(-28, 0, 0, 1, 8, 56, "rust");
        m.add(28, 0, 0, 1, 8, 56, "rust");

        // central container maze
        m.add(-7, 0, -5, 9, 2.6f, 2.6f, "container");
        m.add(7, 0, 4, 9, 2.6f, 2.6f, "container2");
        m.add(-7, 0, 9, 2.6f, 2.6f, 8, "container2");
        m.add(8, 0, -11, 2.6f, 2.6f, 8, "container");
        m.add(0, 0, 0, 1.2f, 2.2f, 8, "crate");

        // catwalk across the yard
        m.add(0, 2.2f, 12, 34, 0.2f, 2.6f, "grate");
        m.add(0, 3.3f, 12, 34, 0.9f, 0.15f, "rust");
        m.stairs(-20, 17, 12, 3.5f, 0, 2.2f, "metal");
        m.stairs(20, 17, 12, 3.5f, 0, 2.2f, "metal");

        // site A warehouse corner
        m.add(17, 0, -20, 8, 3, 0.6f, "container");
        m.add(17, 0, -10, 0.6f, 3, 10, "container2");
        m.add(10, 0, -14, 0.6f, 3, 6, "container");
        m.add(21, 0, -13, 2.4f, 2.4f, 2.4f, "crate");

        // site B
        m.add(-17, 0, -20, 8, 3, 0.6f, "container2");
        m.add(-17, 0, -10, 0.6f, 3, 10, "container");
        m.add(-10, 0, -14, 0.6f, 3, 6, "container2");
        m.add(-21, 0, -13, 2.4f, 2.4f, 2.4f, "crate");

        // yard props
        m.add(0, 0, 20, 4, 2.2f, 2.2f, "crate");
        m.add(-22, 0, 22, 2.2f, 2.2f, 2.2f, "barrel");
        m.add(22, 0, 20, 2.2f, 2.2f, 2.2f, "barrel");
        m.add(14, 0, 7, 3, 1.2f, 3, "crate");
        m.add(-14, 0, 1, 3, 1.2f, 3, "crate");

        m.site(10, -22, 24, -11, true);
        m.site(-24, -22, -10, -11, false);

        m.spawnT(0, 24); m.spawnT(5, 24); m.spawnT(-5, 24); m.spawnT(10, 22); m.spawnT(-10, 22);
        m.spawnCT(0, -24); m.spawnCT(5, -24); m.spawnCT(-5, -24); m.spawnCT(10, -24); m.spawnCT(-10, -24);

        m.wp(0, 24); m.wp(6, 22); m.wp(-6, 22); m.wp(12, 20); m.wp(-12, 20);
        m.wp(0, 16); m.wp(14, 14); m.wp(-14, 14);
        m.wp(20, 17); m.wp(-20, 17); m.wp(20, 12); m.wp(-20, 12);
        m.wp(15, 12); m.wp(-15, 12); m.wp(9, 12); m.wp(-9, 12);
        m.wp(0, 12); m.wp(0, 6); m.wp(0, -2); m.wp(0, -8);
        m.wp(9, -2); m.wp(-9, -2); m.wp(9, -8); m.wp(-9, -8);
        m.wp(17, -8); m.wp(-17, -8); m.wp(17, -16); m.wp(-17, -16);
        m.wp(12, -22); m.wp(-12, -22); m.wp(20, -22); m.wp(-20, -22);
        m.wp(0, -16); m.wp(4, -20); m.wp(-4, -20);
        m.wp(14, 4); m.wp(-14, 4); m.wp(22, 4); m.wp(-22, 4);

        m.finish();
        return m;
    }
}
