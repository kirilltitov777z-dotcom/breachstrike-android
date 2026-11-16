package com.breachstrike.game.net;

import com.breachstrike.game.game.MapData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny line-delimited JSON protocol. The host is authoritative for hits,
 * health, kills and respawns; clients stream their own position and shot rays.
 */
public final class Lan {
    public static final int PORT = 27015;

    private final MapData map;
    private final boolean host;
    private final int myId;
    private volatile boolean closed;
    private volatile String status = "";

    private final ConcurrentHashMap<Integer, JSONObject> players = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<JSONObject> events = new ConcurrentLinkedQueue<>();
    private volatile JSONArray snapshot;
    private final List<Conn> conns = new CopyOnWriteArrayList<>();

    private ServerSocket server;
    private Socket socket;
    private PrintWriter out;
    private int nextId = 1;

    private Lan(MapData map, boolean host, int myId) {
        this.map = map;
        this.host = host;
        this.myId = myId;
    }

    public static Lan host(MapData map, String name) throws IOException {
        Lan l = new Lan(map, true, 0);
        JSONObject self = l.player(0, name);
        l.players.put(0, self);
        l.server = new ServerSocket(PORT);
        l.status = "Хост LAN :" + PORT + "  " + localIp();
        l.startHostThreads();
        l.tick();
        return l;
    }

    public static Lan join(MapData map, String ip, String name, int timeoutMs) throws IOException {
        Lan l = new Lan(map, false, -1);
        Socket s = new Socket();
        s.connect(new java.net.InetSocketAddress(ip, PORT), timeoutMs);
        s.setSoTimeout(6000);
        l.socket = s;
        l.out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        JSONObject hello = new JSONObject();
        try {
            hello.put("t", "hello");
            hello.put("name", name);
        } catch (Exception ignored) {}
        l.out.println(hello.toString());
        String line = in.readLine();
        if (line == null) throw new IOException("Сервер закрыл соединение");
        JSONObject welcome;
        try {
            welcome = new JSONObject(line);
        } catch (org.json.JSONException e) {
            throw new IOException("Неверный ответ сервера: " + e.getMessage());
        }
        if (!"welcome".equals(welcome.optString("t"))) throw new IOException("Неверный ответ сервера");
        Lan joined = new Lan(map, false, welcome.optInt("id", -1));
        joined.socket = s;
        joined.out = l.out;
        JSONArray ps = welcome.optJSONArray("players");
        if (ps != null) joined.applySnapshot(ps);
        joined.status = "Подключено к " + ip + ":" + PORT;
        joined.startClientReader(in);
        return joined;
    }

    public int id() { return myId; }
    public boolean isHost() { return host; }
    public boolean isClosed() { return closed; }
    public String status() { return status; }
    public int playerCount() { return players.size(); }

    public JSONObject pollEvent() { return events.poll(); }
    public JSONArray latestSnapshot() { return snapshot; }

    private JSONObject player(int id, String name) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("n", name);
            float[] sp = map.spawnsT.get((int) (Math.random() * map.spawnsT.size()));
            o.put("x", sp[0]);
            o.put("y", sp[1]);
            o.put("z", sp[2]);
            o.put("yaw", 0);
            o.put("hp", 100);
            o.put("alive", true);
            o.put("k", 0);
        } catch (Exception ignored) {}
        return o;
    }

    public void move(float x, float y, float z, float yaw) {
        if (closed) return;
        JSONObject o = players.get(myId);
        if (o == null) return;
        try {
            o.put("x", x); o.put("y", y); o.put("z", z); o.put("yaw", yaw);
        } catch (Exception ignored) {}
        if (!host) send(oMove(x, y, z, yaw));
    }

    private JSONObject oMove(float x, float y, float z, float yaw) {
        JSONObject o = new JSONObject();
        try {
            o.put("t", "move");
            o.put("x", x); o.put("y", y); o.put("z", z); o.put("yaw", yaw);
        } catch (Exception ignored) {}
        return o;
    }

    public void shot(float ox, float oy, float oz, float[] d) {
        shot(ox, oy, oz, d, 32f);
    }

    public void shot(float ox, float oy, float oz, float[] d, float dmg) {
        if (closed) return;
        if (host) {
            resolveShot(myId, ox, oy, oz, d, dmg);
        } else {
            JSONObject o = new JSONObject();
            try {
                o.put("t", "shot");
                o.put("ox", ox); o.put("oy", oy); o.put("oz", oz);
                o.put("dx", d[0]); o.put("dy", d[1]); o.put("dz", d[2]);
                o.put("dmg", dmg);
            } catch (Exception ignored) {}
            send(o);
        }
    }

    // ---------------- host ----------------
    private void startHostThreads() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!closed) {
                        Socket s = server.accept();
                        if (conns.size() >= 9) {
                            s.close();
                            continue;
                        }
                        Conn c = new Conn(s);
                        conns.add(c);
                        new Thread(c).start();
                    }
                } catch (Throwable ignored) {}
            }
        }, "lan-accept").start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!closed) {
                    try {
                        Thread.sleep(120);
                        tick();
                        broadcast(snapshotObj());
                    } catch (Throwable ignored) {}
                }
            }
        }, "lan-tick").start();
    }

    private void tick() {
        JSONArray arr = new JSONArray();
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, JSONObject> e : players.entrySet()) {
            JSONObject o = e.getValue();
            try {
                if (!o.optBoolean("alive", true) && now >= o.optLong("rs", 0)) {
                    float[] sp = map.spawnsT.get((int) (Math.random() * map.spawnsT.size()));
                    o.put("x", sp[0]); o.put("y", sp[1]); o.put("z", sp[2]);
                    o.put("hp", 100);
                    o.put("alive", true);
                    o.put("rs", 0);
                }
                arr.put(new JSONObject(o.toString()));
            } catch (Exception ignored) {}
        }
        snapshot = arr;
    }

    private JSONObject snapshotObj() {
        JSONObject o = new JSONObject();
        try {
            o.put("t", "snap");
            o.put("players", snapshot == null ? new JSONArray() : snapshot);
        } catch (Exception ignored) {}
        return o;
    }

    private void resolveShot(int shooterId, float ox, float oy, float oz, float[] d, float dmg) {
        if (dmg < 1f) dmg = 1f;
        if (dmg > 260f) dmg = 260f;
        int victimId = -1;
        float best = 100f;
        boolean head = false;
        for (Map.Entry<Integer, JSONObject> e : players.entrySet()) {
            if (e.getKey() == shooterId) continue;
            JSONObject o = e.getValue();
            if (!o.optBoolean("alive", true)) continue;
            float x = (float) o.optDouble("x", 0);
            float y = (float) o.optDouble("y", 0);
            float z = (float) o.optDouble("z", 0);
            float th = MapData.rayBox(ox, oy, oz, d[0], d[1], d[2],
                    x - 0.22f, y + 1.48f, z - 0.22f, x + 0.22f, y + 1.8f, z + 0.22f);
            if (th >= 0 && th < best) {
                best = th; victimId = e.getKey(); head = true;
            }
            float tb = MapData.rayBox(ox, oy, oz, d[0], d[1], d[2],
                    x - 0.32f, y, z - 0.32f, x + 0.32f, y + 1.48f, z + 0.32f);
            if (tb >= 0 && tb < best) {
                best = tb; victimId = e.getKey(); head = false;
            }
        }
        if (victimId < 0) return;
        float wall = map.raycast(ox, oy, oz, d[0], d[1], d[2], best - 0.05f);
        if (wall >= 0) return;
        JSONObject victim = players.get(victimId);
        JSONObject killer = players.get(shooterId);
        if (victim == null || killer == null) return;
        try {
            int dealt = Math.max(1, Math.round(dmg * (head ? 2.2f : 1f)));
            int hp = victim.optInt("hp", 100) - dealt;
            victim.put("hp", Math.max(0, hp));
            if (hp <= 0) {
                victim.put("alive", false);
                victim.put("rs", System.currentTimeMillis() + 3000);
                killer.put("k", killer.optInt("k", 0) + 1);
            }
            JSONObject ev = new JSONObject();
            ev.put("t", "hit");
            ev.put("victim", victimId);
            ev.put("dmg", dealt);
            ev.put("head", head);
            ev.put("by", killer.optString("n", "?"));
            ev.put("dead", hp <= 0);
            events.add(ev);
            broadcast(ev);
        } catch (Exception ignored) {}
    }

    private void broadcast(JSONObject o) {
        String line = o.toString();
        for (Conn c : conns) c.send(line);
    }

    private void send(JSONObject o) {
        if (out != null) out.println(o.toString());
    }

    // ---------------- client ----------------
    private void startClientReader(final BufferedReader in) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while (!closed && (line = in.readLine()) != null) {
                        JSONObject o = new JSONObject(line);
                        String t = o.optString("t");
                        if ("snap".equals(t)) {
                            JSONArray arr = o.optJSONArray("players");
                            if (arr != null) applySnapshot(arr);
                        } else if ("hit".equals(t)) {
                            events.add(o);
                        }
                    }
                } catch (Throwable ignored) {}
                if (!closed) status = "Соединение с хостом потеряно";
            }
        }, "lan-read").start();
    }

    private void applySnapshot(JSONArray arr) {
        snapshot = arr;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            int id = o.optInt("id", -1);
            if (id < 0) continue;
            JSONObject copy = players.get(id);
            if (copy == null) {
                try { copy = new JSONObject(o.toString()); } catch (Exception e) { continue; }
                players.put(id, copy);
            } else {
                for (String k : new String[]{"n", "x", "y", "z", "yaw", "hp", "alive", "k"}) {
                    if (o.has(k)) {
                        try { copy.put(k, o.get(k)); } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    private final class Conn implements Runnable {
        final Socket s;
        final int id;
        BufferedReader in;
        PrintWriter out;
        String name = "Player";

        Conn(Socket s) {
            this.s = s;
            this.id = nextId++;
        }

        void send(String line) {
            try {
                if (out != null) out.println(line);
            } catch (Throwable ignored) {}
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
                String line = in.readLine();
                if (line == null) { close(); return; }
                JSONObject hello = new JSONObject(line);
                name = hello.optString("name", "Player");
                JSONObject p = player(id, name);
                players.put(id, p);
                JSONObject welcome = new JSONObject();
                welcome.put("t", "welcome");
                welcome.put("id", id);
                JSONArray arr = new JSONArray();
                synchronized (players) {
                    for (JSONObject o : players.values()) arr.put(new JSONObject(o.toString()));
                }
                welcome.put("players", arr);
                send(welcome.toString());
                events.add(statusEvent(name + " подключился"));
                while (!closed && (line = in.readLine()) != null) {
                    JSONObject o = new JSONObject(line);
                    String t = o.optString("t");
                    if ("move".equals(t)) {
                        JSONObject me = players.get(id);
                        if (me != null) {
                            try {
                                me.put("x", o.optDouble("x", 0));
                                me.put("y", o.optDouble("y", 0));
                                me.put("z", o.optDouble("z", 0));
                                me.put("yaw", o.optDouble("yaw", 0));
                            } catch (Exception ignored) {}
                        }
                    } else if ("shot".equals(t)) {
                        float[] d = {(float) o.optDouble("dx", 0), (float) o.optDouble("dy", 0), (float) o.optDouble("dz", -1)};
                        float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                        if (len > 0.01f) { d[0] /= len; d[1] /= len; d[2] /= len; }
                        resolveShot(id, (float) o.optDouble("ox", 0), (float) o.optDouble("oy", 0),
                                (float) o.optDouble("oz", 0), d, (float) o.optDouble("dmg", 32));
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                close();
            }
        }

        void close() {
            players.remove(id);
            conns.remove(this);
            try { s.close(); } catch (IOException ignored) {}
            events.add(statusEvent(name + " отключился"));
        }
    }

    private JSONObject statusEvent(String s) {
        JSONObject o = new JSONObject();
        try {
            o.put("t", "status");
            o.put("msg", s);
        } catch (Exception ignored) {}
        return o;
    }

    public void close() {
        closed = true;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        for (Conn c : conns) {
            try { c.s.close(); } catch (IOException ignored) {}
        }
        conns.clear();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Throwable ignored) {}
        return "10.0.0.2";
    }
}
