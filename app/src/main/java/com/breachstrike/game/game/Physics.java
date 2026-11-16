package com.breachstrike.game.game;

public final class Physics {
    private Physics() {}

    public static void step(MapData map, Body b, float dt) {
        if (dt > 0.05f) dt = 0.05f;
        float r = b.radius, h = b.height;

        // horizontal X
        float nx = b.x + b.vx * dt;
        if (!map.collides(nx - r, b.y + 0.02f, b.z - r, nx + r, b.y + h, b.z + r)) {
            b.x = nx;
        } else if (b.onGround && !map.collides(nx - r, b.y + b.step, b.z - r, nx + r, b.y + b.step + h, b.z + r)) {
            b.x = nx;
            b.y += b.step;
        } else {
            b.vx = 0;
        }

        // horizontal Z
        float nz = b.z + b.vz * dt;
        if (!map.collides(b.x - r, b.y + 0.02f, nz - r, b.x + r, b.y + h, nz + r)) {
            b.z = nz;
        } else if (b.onGround && !map.collides(b.x - r, b.y + b.step, nz - r, b.x + r, b.y + b.step + h, nz + r)) {
            b.z = nz;
            b.y += b.step;
        } else {
            b.vz = 0;
        }

        // vertical
        b.vy -= b.gravity * dt;
        float ny = b.y + b.vy * dt;

        if (b.vy > 0) {
            if (map.collides(b.x - r, ny, b.z - r, b.x + r, ny + h, b.z + r)) {
                b.vy = 0;
                b.onGround = false;
            } else {
                b.y = ny;
                b.onGround = false;
            }
        } else {
            float support = map.groundAt(b.x, b.z, b.y + 0.06f);
            if (support < -50f) support = 0f;
            if (ny <= support) {
                b.y = support;
                b.vy = 0;
                b.onGround = true;
            } else {
                b.y = ny;
                b.onGround = false;
            }
        }

        if (b.y < -10f) {
            b.y = Math.max(0f, map.groundAt(b.x, b.z, 50f));
            b.vy = 0;
            b.onGround = true;
        }

        if (map.collides(b.x - r, b.y + 0.05f, b.z - r, b.x + r, b.y + h - 0.02f, b.z + r)) {
            float top = map.groundAt(b.x, b.z, b.y + 2.2f);
            if (top > b.y) b.y = top + 0.01f;
        }
    }

    public static boolean canSee(MapData map, float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01f) return true;
        float t = map.raycast(ax, ay, az, dx / len, dy / len, dz / len, len - 0.2f);
        return t < 0;
    }
}
