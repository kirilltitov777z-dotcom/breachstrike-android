package com.breachstrike.game.game;

public final class Bot {
    public Body body = new Body();
    public int id;
    public int team; // 0 = T (orange), 1 = CT (blue)
    public String name;
    public int hp = 100;
    public boolean alive = true;
    public float yaw, pitch;
    public int skill; // 0 easy, 1 normal, 2 hard
    public Weapons.Def weapon;
    public float respawn = 0f;
    public int kills, deaths;
    public boolean hasBomb;

    // ai internals
    public float thinkTimer, reactionTimer, fireTimer, strafeTimer, pathTimer, plantTimer;
    public int target = -1;       // -1 player, >=0 bot id
    public float lastX, lastZ;
    public float strafe = 1f;
    public int goalWp = -1;
    public String state = "roam";

    public float eyeY() { return body.y + 1.55f; }

    public void reset(float x, float z, float yaw, boolean bombCarrier) {
        body.x = x; body.z = z;
        body.y = Math.max(0f, 0f);
        body.vx = body.vy = body.vz = 0;
        this.yaw = yaw;
        pitch = 0;
        hp = 100;
        alive = true;
        respawn = 0;
        target = -1;
        plantTimer = 0;
        hasBomb = bombCarrier;
        state = "roam";
        goalWp = -1;
        thinkTimer = 0.1f + (float) Math.random() * 0.4f;
        reactionTimer = 0;
        fireTimer = 0.4f;
    }
}
