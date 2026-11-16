package com.breachstrike.game.game;

import java.util.ArrayList;

public final class MapData {
    public static final class Box {
        public float x, y, z; // center
        public float hx, hy, hz;
        public String mat;
        public boolean solid;
    }

    public String id = "";
    public String name = "";
    public int skyColor = 0xFF8FB3D9;
    public ArrayList<Box> boxes = new ArrayList<>();
    public ArrayList<float[]> spawnsT = new ArrayList<>();
    public ArrayList<float[]> spawnsCT = new ArrayList<>();
    public ArrayList<float[]> waypoints = new ArrayList<>();
    public float[] siteA = {-1, -1, 1, 1};
    public float[] siteB = {-1, -1, 1, 1};
    public float minX = -40, maxX = 40, minZ = -40, maxZ = 40;
    public int[][] edges;
    public String floorMat = "concrete";

    public void add(float x, float yBottom, float z, float w, float h, float d, String mat) {
        Box b = new Box();
        b.x = x; b.y = yBottom + h / 2f; b.z = z;
        b.hx = w / 2f; b.hy = h / 2f; b.hz = d / 2f;
        b.mat = mat;
        b.solid = true;
        boxes.add(b);
    }

    public void deco(float x, float yBottom, float z, float w, float h, float d, String mat) {
        Box b = new Box();
        b.x = x; b.y = yBottom + h / 2f; b.z = z;
        b.hx = w / 2f; b.hy = h / 2f; b.hz = d / 2f;
        b.mat = mat;
        b.solid = false;
        boxes.add(b);
    }

    public void spawnT(float x, float z) { spawnsT.add(new float[]{x, 0, z}); }
    public void spawnCT(float x, float z) { spawnsCT.add(new float[]{x, 0, z}); }
    public void wp(float x, float z) { waypoints.add(new float[]{x, 0, z}); }
    public void wp(float x, float y, float z) { waypoints.add(new float[]{x, y, z}); }

    public void site(float x1, float z1, float x2, float z2, boolean a) {
        float cx = (x1 + x2) / 2f, cz = (z1 + z2) / 2f;
        float w = Math.abs(x2 - x1), d = Math.abs(z2 - z1);
        deco(cx, 0.005f, cz, w, 0.01f, d, a ? "site_a" : "site_b");
        if (a) siteA = new float[]{Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)};
        else siteB = new float[]{Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)};
    }

    /** Stairs along +z to -z (dir=-1) or -z to +z (dir=1). */
    public void stairs(float x, float zStart, float zEnd, float width, float y0, float y1, String mat) {
        int n = 14;
        float from = Math.min(zStart, zEnd);
        float depth = Math.abs(zEnd - zStart) / n;
        for (int i = 0; i < n; i++) {
            float t = (i + 1f) / n;
            float top = y0 + (y1 - y0) * t;
            float z = zStart > zEnd ? zStart - depth * (i + 0.5f) : zStart + depth * (i + 0.5f);
            add(x, y0, z, width, top - y0, depth + 0.02f, mat);
        }
        deco(x, y0, (from + Math.max(zStart, zEnd)) / 2f, width, 0.02f, 0.02f, mat);
    }

    public boolean collides(float x0, float y0, float z0, float x1, float y1, float z1) {
        for (int i = 0, n = boxes.size(); i < n; i++) {
            Box b = boxes.get(i);
            if (!b.solid) continue;
            if (x0 < b.x + b.hx && x1 > b.x - b.hx
                    && y0 < b.y + b.hy && y1 > b.y - b.hy
                    && z0 < b.z + b.hz && z1 > b.z - b.hz) return true;
        }
        return false;
    }

    /** Highest solid top under the point, no higher than maxTop. */
    public float groundAt(float x, float z, float maxTop) {
        float best = -100f;
        for (int i = 0, n = boxes.size(); i < n; i++) {
            Box b = boxes.get(i);
            if (!b.solid) continue;
            if (x > b.x - b.hx - 0.01f && x < b.x + b.hx + 0.01f
                    && z > b.z - b.hz - 0.01f && z < b.z + b.hz + 0.01f) {
                float top = b.y + b.hy;
                if (top <= maxTop + 0.001f && top > best) best = top;
            }
        }
        return best;
    }

    /** Distance to the first solid hit, or -1 if nothing within maxDist. */
    public float raycast(float ox, float oy, float oz, float dx, float dy, float dz, float maxDist) {
        float best = maxDist;
        boolean hit = false;
        for (int i = 0, n = boxes.size(); i < n; i++) {
            Box b = boxes.get(i);
            if (!b.solid) continue;
            float t = rayBox(ox, oy, oz, dx, dy, dz,
                    b.x - b.hx, b.y - b.hy, b.z - b.hz,
                    b.x + b.hx, b.y + b.hy, b.z + b.hz);
            if (t >= 0 && t < best) { best = t; hit = true; }
        }
        return hit ? best : -1f;
    }

    public static float rayBox(float ox, float oy, float oz, float dx, float dy, float dz,
                               float x0, float y0, float z0, float x1, float y1, float z1) {
        float tmin = 0f, tmax = 1e9f;
        float[] o = {ox, oy, oz};
        float[] d = {dx, dy, dz};
        float[] lo = {x0, y0, z0};
        float[] hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-8f) {
                if (o[i] < lo[i] || o[i] > hi[i]) return -1f;
            } else {
                float inv = 1f / d[i];
                float t1 = (lo[i] - o[i]) * inv;
                float t2 = (hi[i] - o[i]) * inv;
                if (t1 > t2) { float tt = t1; t1 = t2; t2 = tt; }
                if (t1 > tmin) tmin = t1;
                if (t2 < tmax) tmax = t2;
                if (tmin > tmax) return -1f;
            }
        }
        return tmin;
    }

    public boolean lineOfSight(float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01f) return true;
        float t = raycast(a[0], a[1], a[2], dx / len, dy / len, dz / len, len - 0.15f);
        return t < 0;
    }

    public boolean inSite(float x, float z, float[] s) {
        return x > s[0] && x < s[2] && z > s[1] && z < s[3];
    }

    public void finish() {
        for (Box b : boxes) {
            minX = Math.min(minX, b.x - b.hx);
            maxX = Math.max(maxX, b.x + b.hx);
            minZ = Math.min(minZ, b.z - b.hz);
            maxZ = Math.max(maxZ, b.z + b.hz);
        }
        for (float[] s : spawnsT) s[1] = groundAt(s[0], s[2], 2f);
        for (float[] s : spawnsCT) s[1] = groundAt(s[0], s[2], 2f);
        for (float[] s : waypoints) {
            if (s[1] <= 0.01f) s[1] = groundAt(s[0], s[2], 2f);
        }
        buildEdges();
    }

    private void buildEdges() {
        int n = waypoints.size();
        edges = new int[n][];
        ArrayList<Integer> tmp = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float[] a = waypoints.get(i);
            tmp.clear();
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                float[] b = waypoints.get(j);
                float dx = b[0] - a[0], dz = b[2] - a[2];
                float dy = Math.abs(b[1] - a[1]);
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > 15f) continue;
                if (dy > 1.3f && !(dist < 4.2f && dy < 3.6f)) continue;
                float[] ea = {a[0], a[1] + 1.0f, a[2]};
                float[] eb = {b[0], b[1] + 1.0f, b[2]};
                if (!lineOfSight(ea, eb)) continue;
                tmp.add(j);
            }
            edges[i] = new int[tmp.size()];
            for (int k = 0; k < tmp.size(); k++) edges[i][k] = tmp.get(k);
        }
    }

    public int nearestWaypoint(float x, float z) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < waypoints.size(); i++) {
            float[] w = waypoints.get(i);
            float d = (w[0] - x) * (w[0] - x) + (w[2] - z) * (w[2] - z);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    public int nextHop(int from, int to) {
        if (from == to || edges == null) return to;
        int n = waypoints.size();
        boolean[] seen = new boolean[n];
        int[] prev = new int[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        queue[tail++] = from;
        seen[from] = true;
        prev[from] = -1;
        while (head < tail) {
            int cur = queue[head++];
            if (cur == to) break;
            for (int nb : edges[cur]) {
                if (!seen[nb]) {
                    seen[nb] = true;
                    prev[nb] = cur;
                    queue[tail++] = nb;
                }
            }
        }
        if (!seen[to]) return to;
        int cur = to;
        while (prev[cur] != from && prev[cur] != -1) cur = prev[cur];
        return cur;
    }
}
