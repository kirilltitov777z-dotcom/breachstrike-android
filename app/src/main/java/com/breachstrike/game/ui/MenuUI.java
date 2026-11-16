package com.breachstrike.game.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.breachstrike.game.CrashLog;
import com.breachstrike.game.core.Save;
import com.breachstrike.game.game.Elo;
import com.breachstrike.game.game.GameConfig;
import com.breachstrike.game.game.GameState;
import com.breachstrike.game.game.Maps;
import com.breachstrike.game.game.Skins;
import com.breachstrike.game.game.Weapons;
import com.breachstrike.game.net.Lan;
import com.breachstrike.game.render.GameView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public final class MenuUI implements GameView.Listener {
    private static final int BG = 0xFF0B0E14;
    private static final int PANEL = 0xFF141A24;
    private static final int PANEL2 = 0xFF1C2532;
    private static final int ACCENT = 0xFFFF8A1E;
    private static final int ACCENT_DARK = 0xFF9C5210;
    private static final int TEXT = 0xFFEDEFF4;
    private static final int MUTED = 0xFF8B93A7;
    private static final int GREEN = 0xFF57D97A;
    private static final int RED = 0xFFE0503C;

    private final Activity act;
    private final Save save;
    private final FrameLayout root;
    private final LinearLayout menuLayer;
    private final FrameLayout gameLayer;
    private final LinearLayout contentBox;
    private final TextView headerInfo;
    private final TextView headerCoins;

    private GameView game;
    private GameConfig lastCfg;
    private String lastLanIp;
    private int tab = 0;
    private int shopWeapon = 0;
    private String shopWear = "FT";
    private int modeSel;
    private int mapSel;
    private int diffSel;

    private final String[] botNames = {
            "s1mple", "ZywOo", "NiKo", "device", "sh1ro", "donk", "m0NESY", "ropz",
            "Spinx", "broky", "frozen", "electronic", "b1t", "huNter-", "jks", "Twistzz"
    };
    private final int[] botElo = {
            2780, 2740, 2690, 2640, 2610, 2590, 2540, 2500,
            2460, 2420, 2380, 2340, 2300, 2260, 2200, 2150
    };
    private final String[] clanNames = {"NAVI", "Vitality", "FaZe", "G2", "Spirit", "MOUZ", "Astralis", "Heroic"};
    private final String[] clanTags = {"NAVI", "VIT", "FAZE", "G2", "TS", "MOUZ", "AST", "HER"};
    private final int[] clanElo = {2740, 2710, 2680, 2630, 2600, 2550, 2480, 2420};

    public MenuUI(Activity act) {
        this.act = act;
        this.save = Save.get(act);
        this.modeSel = GameConfig.MODE_BOMB;
        this.mapSel = 0;
        this.diffSel = 1;

        root = new FrameLayout(act);
        root.setBackgroundColor(BG);

        menuLayer = new LinearLayout(act);
        menuLayer.setOrientation(LinearLayout.VERTICAL);
        root.addView(menuLayer, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(6), dp(12), dp(6));
        header.setBackground(bg(PANEL, 0, 0));
        menuLayer.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout titleBox = new LinearLayout(act);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView logo = tv("BREACH STRIKE", 19, ACCENT, true);
        logo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        titleBox.addView(logo);
        TextView sub = tv("OpenGL ES FPS • 5v5 / DM / TDM • LAN", 10, MUTED, false);
        titleBox.addView(sub);
        header.addView(titleBox);

        View spacer = new View(act);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        headerInfo = tv("", 12, TEXT, false);
        headerInfo.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(headerInfo);

        headerCoins = tv("", 14, ACCENT, true);
        headerCoins.setPadding(dp(14), 0, dp(14), 0);
        header.addView(headerCoins);

        Button sync = smallButton("SYNC ELO", new View.OnClickListener() {
            @Override
            public void onClick(View v) { syncElo(); }
        });
        header.addView(sync);

        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.HORIZONTAL);
        menuLayer.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(act);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackground(bg(PANEL, 0, 0));
        body.addView(nav, new LinearLayout.LayoutParams(dp(132), -1));

        String[] tabs = {"ИГРАТЬ", "ИНВЕНТАРЬ", "МАГАЗИН", "ПРОФИЛЬ", "КЛАНЫ", "ЛИДЕРЫ", "НАСТРОЙКИ"};
        final LinearLayout navRef = nav;
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            TextView t = tv(tabs[i], 12, TEXT, i == 0);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(dp(12), dp(10), dp(8), dp(10));
            t.setBackground(bg(i == 0 ? PANEL2 : PANEL, 8, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
            lp.bottomMargin = dp(4);
            nav.addView(t, lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = index;
                    for (int k = 0; k < navRef.getChildCount(); k++) {
                        View c = navRef.getChildAt(k);
                        c.setBackground(bg(k == index ? PANEL2 : PANEL, 8, 0));
                    }
                    rebuildContent();
                }
            });
        }

        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));

        contentBox = new LinearLayout(act);
        contentBox.setOrientation(LinearLayout.VERTICAL);
        contentBox.setPadding(dp(14), dp(10), dp(14), dp(24));
        scroll.addView(contentBox, new ScrollView.LayoutParams(-1, -2));

        gameLayer = new FrameLayout(act);
        gameLayer.setVisibility(View.GONE);
        root.addView(gameLayer, new FrameLayout.LayoutParams(-1, -1));

        refreshHeader();
        rebuildContent();
    }

    public View root() { return root; }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                act.getResources().getDisplayMetrics());
    }

    private int sp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                act.getResources().getDisplayMetrics());
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable bg(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        if (radius > 0) g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private Button smallButton(String text, View.OnClickListener l) {
        Button b = new Button(act);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        b.setTextColor(TEXT);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(10), dp(5), dp(10), dp(5));
        b.setBackground(bg(PANEL2, 8, 0x33FFFFFF));
        b.setOnClickListener(l);
        return b;
    }

    private Button chip(String text, boolean active, View.OnClickListener l) {
        Button b = new Button(act);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        b.setTextColor(active ? 0xFF0B0E14 : TEXT);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(11), dp(6), dp(11), dp(6));
        b.setBackground(bg(active ? ACCENT : PANEL2, 9, active ? 0 : 0x22FFFFFF));
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(12));
        c.setBackground(bg(PANEL, 12, 0x18FFFFFF));
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private void addSpace(LinearLayout parent, int h) {
        View v = new View(act);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s, 15, TEXT, true);
        t.setPadding(0, dp(6), 0, dp(8));
        return t;
    }

    private void addToContent(View v) {
        contentBox.addView(v);
    }

    private void refreshHeader() {
        JSONObject p = save.profile();
        String name = p.optString("name", "Player");
        int lvl = p.optInt("level", 1);
        int elo = p.optInt("elo", 1000);
        int pending = p.optInt("pendingElo", 0);
        headerInfo.setText(name + "   •   LVL " + lvl + "   •   " + Elo.rankShort(elo) + " " + elo
                + (pending != 0 ? "   (офлайн " + (pending > 0 ? "+" : "") + pending + ")" : ""));
        headerCoins.setText("CR " + p.optInt("coins", 0));
    }

    private void rebuildContent() {
        contentBox.removeAllViews();
        switch (tab) {
            case 0: playScreen(); break;
            case 1: inventoryScreen(); break;
            case 2: shopScreen(); break;
            case 3: profileScreen(); break;
            case 4: clansScreen(); break;
            case 5: leadersScreen(); break;
            default: settingsScreen(); break;
        }
    }

    // ------------------- PLAY -------------------
    private void playScreen() {
        addToContent(sectionTitle("БЫСТРАЯ ИГРА"));
        addToContent(tv("Выбери режим, карту и сложность ботов. Всё офлайн, сеть нужна только для LAN.",
                11, MUTED, false));
        addSpace(contentBox, 8);

        LinearLayout modes = row();
        String[] mn = {"5v5 БОМБА", "DEATHMATCH", "TDM"};
        for (int i = 0; i < mn.length; i++) {
            final int v = i;
            Button b = chip(mn[i], modeSel == i, new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    modeSel = v;
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(6);
            modes.addView(b, lp);
        }
        addToContent(modes);

        addSpace(contentBox, 6);
        LinearLayout maps = row();
        for (int i = 0; i < Maps.ALL.length; i++) {
            final int v = i;
            Button b = chip(Maps.ALL[i].name, mapSel == i, new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    mapSel = v;
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(6);
            maps.addView(b, lp);
        }
        addToContent(maps);

        addSpace(contentBox, 6);
        LinearLayout diffs = row();
        String[] dn = {"ЛЕГКО", "НОРМА", "СЛОЖНО"};
        for (int i = 0; i < dn.length; i++) {
            final int v = i;
            Button b = chip(dn[i], diffSel == i, new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    diffSel = v;
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(6);
            diffs.addView(b, lp);
        }
        addToContent(diffs);

        addSpace(contentBox, 10);
        Button start = new Button(act);
        start.setText("В БОЙ");
        start.setAllCaps(false);
        start.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(0xFF140C04);
        start.setBackground(bg(ACCENT, 12, 0));
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame(false, null);
            }
        });
        addToContent(start);
        LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) start.getLayoutParams();
        slp.height = dp(56);
        slp.topMargin = dp(4);
        slp.bottomMargin = dp(10);
        start.setLayoutParams(slp);

        LinearLayout lan = card();
        lan.addView(sectionTitle("LAN ПО WI-FI (до 10 игроков)"));
        lan.addView(tv("Хост раздаёт IP, остальные подключаются. Выстрелы и урон считает хост.",
                11, MUTED, false));
        addSpace(lan, 6);
        LinearLayout lanRow = row();
        Button host = chip("СОЗДАТЬ LAN", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) { hostLan(); }
        });
        lanRow.addView(host);
        EditText ip = new EditText(act);
        ip.setHint("192.168.1.100");
        ip.setTextColor(TEXT);
        ip.setHintTextColor(0xFF5D687C);
        ip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ip.setInputType(InputType.TYPE_CLASS_TEXT);
        ip.setSingleLine(true);
        ip.setBackground(bg(PANEL2, 8, 0x22FFFFFF));
        ip.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams iplp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        iplp.leftMargin = dp(8);
        iplp.rightMargin = dp(8);
        lanRow.addView(ip, iplp);
        Button join = chip("ПОДКЛЮЧИТЬСЯ", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = ip.getText().toString().trim();
                if (s.length() < 7) {
                    toast("Введи IP хоста");
                    return;
                }
                startGame(true, s);
            }
        });
        lanRow.addView(join);
        lan.addView(lanRow);
        addSpace(lan, 4);
        lan.addView(tv("Порт " + Lan.PORT + " • карта Foundry • deathmatch", 10, MUTED, false));
        addToContent(lan);

        addSpace(contentBox, 10);
        LinearLayout load = card();
        load.addView(sectionTitle("ТЕКУЩИЙ ЛОАДАУТ"));
        String pr = save.slot("primary", "ak47");
        String sc = save.slot("secondary", "usp");
        String kn = save.slot("knife", "butterfly");
        load.addView(tv("Основное: " + Weapons.get(pr).name + "  —  "
                + Skins.display(pr, save.equippedSkin(pr)), 12, TEXT, false));
        load.addView(tv("Пистолет: " + Weapons.get(sc).name + "  —  "
                + Skins.display(sc, save.equippedSkin(sc)), 12, TEXT, false));
        load.addView(tv("Нож: " + Weapons.get(kn).name + "  —  "
                + Skins.display(kn, save.equippedSkin(kn)), 12, TEXT, false));
        Button goInv = smallButton("СМЕНИТЬ В ИНВЕНТАРЕ", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = 1;
                rebuildContent();
            }
        });
        load.addView(goInv);
        addToContent(load);
    }

    private void hostLan() {
        new AlertDialog.Builder(act)
                .setTitle("LAN хост")
                .setMessage("IP этого телефона: " + Lan.localIp() + "\nПорт: " + Lan.PORT
                        + "\n\nВсе игроки должны быть в одной Wi-Fi сети.\nКарта: Foundry, режим: deathmatch.")
                .setPositiveButton("Запустить", (d, w) -> {
                    startGame(true, "");
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ------------------- INVENTORY -------------------
    private void inventoryScreen() {
        addToContent(sectionTitle("ИНВЕНТАРЬ"));
        JSONArray inv = save.inventory();
        addToContent(tv("Скинов в коллекции: " + inv.length() + ". Скины надеваются на конкретное оружие.",
                11, MUTED, false));
        addSpace(contentBox, 6);

        for (Weapons.Def d : Weapons.ALL) {
            LinearLayout row = row();
            LinearLayout info = new LinearLayout(act);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(0, dp(5), dp(8), dp(5));
            TextView name = tv(d.name, 13, TEXT, true);
            info.addView(name);
            String eq = save.equippedSkin(d.id);
            TextView skin = tv(Skins.display(d.id, eq), 10, eq.length() > 0 ? Skins.rarityColor(Skins.patternOf(eq)) : MUTED, false);
            info.addView(skin);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            Button b = smallButton("СКИНЫ", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skinDialog(d);
                }
            });
            row.addView(b);
            row.setBackground(bg(PANEL, 8, 0));
            row.setPadding(dp(8), 0, dp(6), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(3);
            addToContent(row);
            ((LinearLayout) contentBox.getChildAt(contentBox.getChildCount() - 1)).setLayoutParams(lp);
        }
    }

    private void skinDialog(Weapons.Def d) {
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        labels.add("Без скина");
        keys.add("");
        JSONArray inv = save.inventory();
        String equipped = save.equippedSkin(d.id);
        for (int i = 0; i < inv.length(); i++) {
            String key = inv.optString(i);
            if (!Skins.weaponOf(key).equals(d.id)) continue;
            String pw = Skins.patternOf(key) + "|" + Skins.wearOf(key);
            keys.add(pw);
            labels.add(Skins.display(d.id, pw) + "  •  " + Skins.rarityName(Skins.patternOf(key)));
        }
        String[] arr = labels.toArray(new String[0]);
        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle(d.name);
        b.setItems(arr, (dialog, which) -> {
            save.equipSkin(d.id, keys.get(which));
            toast(which == 0 ? "Скин снят" : "Надето: " + labels.get(which));
            refreshHeader();
            rebuildContent();
        });
        b.setPositiveButton("Купить скины", (dialog, which) -> {
            for (int i = 0; i < Weapons.ALL.size(); i++) {
                if (Weapons.ALL.get(i).id.equals(d.id)) shopWeapon = i;
            }
            tab = 2;
            rebuildContent();
        });
        b.show();
    }

    // ------------------- SHOP -------------------
    private void shopScreen() {
        addToContent(sectionTitle("МАГАЗИН СКИНОВ"));
        addToContent(tv("Покупай скины за монеты. Монеты дают за матчи: фраги, победы, MVP.",
                11, MUTED, false));
        addSpace(contentBox, 4);

        HorizontalScrollView hs = new HorizontalScrollView(act);
        LinearLayout chips = row();
        for (int i = 0; i < Weapons.ALL.size(); i++) {
            final int idx = i;
            Weapons.Def d = Weapons.ALL.get(i);
            Button b = chip(d.name, shopWeapon == i, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shopWeapon = idx;
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(4);
            chips.addView(b, lp);
        }
        hs.addView(chips);
        addToContent(hs);
        addSpace(contentBox, 4);

        LinearLayout wears = row();
        for (String w : Skins.WEARS) {
            final String wv = w;
            Button b = chip(w, shopWear.equals(w), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shopWear = wv;
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(4);
            wears.addView(b, lp);
        }
        addToContent(wears);
        addSpace(contentBox, 6);

        Weapons.Def d = Weapons.ALL.get(shopWeapon);
        addToContent(tv("Скины для " + d.name + " • " + Skins.wearName(shopWear), 12, MUTED, false));
        addSpace(contentBox, 4);

        for (String pattern : Skins.PATTERNS) {
            String key = Skins.key(d.id, pattern, shopWear);
            boolean owned = save.hasSkin(key);
            int price = Skins.price(d.id, pattern, shopWear);
            LinearLayout card = row();
            card.setBackground(bg(PANEL, 10, 0));
            card.setPadding(dp(10), dp(8), dp(10), dp(8));

            LinearLayout info = new LinearLayout(act);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = tv(Skins.patternName(pattern), 13, Skins.rarityColor(pattern), true);
            info.addView(name);
            info.addView(tv(Skins.rarityName(pattern) + "  •  " + Skins.wearName(shopWear), 10, MUTED, false));
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            if (owned) {
                TextView ownedTv = tv("КУПЛЕНО", 11, GREEN, true);
                card.addView(ownedTv);
            } else {
                Button buy = smallButton("CR " + price, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        buySkin(d.id, pattern, shopWear, price);
                    }
                });
                card.addView(buy);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(4);
            addToContent(card);
            card.setLayoutParams(lp);
        }
    }

    private void buySkin(String weapon, String pattern, String wear, int price) {
        JSONObject p = save.profile();
        int coins = p.optInt("coins", 0);
        if (coins < price) {
            toast("Не хватает монет: нужно " + price + ", есть " + coins);
            return;
        }
        save.addCoins(-price);
        save.addSkin(Skins.key(weapon, pattern, wear));
        toast("Куплено: " + Skins.patternName(pattern) + " (" + wear + ")");
        refreshHeader();
        rebuildContent();
    }

    // ------------------- PROFILE -------------------
    private void profileScreen() {
        addToContent(sectionTitle("ПРОФИЛЬ"));
        JSONObject p = save.profile();
        int elo = p.optInt("elo", 1000);
        int level = p.optInt("level", 1);
        int xp = p.optInt("xp", 0);

        LinearLayout nameCard = card();
        LinearLayout nr = row();
        EditText name = new EditText(act);
        name.setText(p.optString("name", "Player"));
        name.setSingleLine(true);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setBackground(bg(PANEL2, 8, 0x22FFFFFF));
        name.setPadding(dp(8), dp(4), dp(8), dp(4));
        nr.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button saveName = smallButton("СОХРАНИТЬ", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.length() < 2) { toast("Минимум 2 символа"); return; }
                JSONObject pr = save.profile();
                try { pr.put("name", n); } catch (Exception ignored) {}
                save.saveProfile(pr);
                refreshHeader();
                toast("Имя сохранено");
            }
        });
        LinearLayout.LayoutParams snlp = new LinearLayout.LayoutParams(-2, dp(42));
        snlp.leftMargin = dp(8);
        nr.addView(saveName, snlp);
        nameCard.addView(nr);
        addToContent(nameCard);
        addSpace(contentBox, 8);

        LinearLayout rankCard = card();
        rankCard.addView(sectionTitle("РАНГ И ЭЛО"));
        LinearLayout rr = row();
        TextView rank = tv(Elo.rankName(elo), 20, ACCENT, true);
        rr.addView(rank, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView eloTv = tv(String.valueOf(elo), 22, TEXT, true);
        rr.addView(eloTv);
        rankCard.addView(rr);
        rankCard.addView(tv("До следующего ранга: " + Math.max(0, Elo.nextRankElo(elo) - elo) + " эло",
                11, MUTED, false));
        int pending = p.optInt("pendingElo", 0);
        rankCard.addView(tv("Отложенное эло: " + (pending > 0 ? "+" : "") + pending + "  "
                + (pending != 0 ? "(синхронизируй кнопкой в шапке или ниже)" : ""), 11,
                pending != 0 ? ACCENT : MUTED, false));
        Button sync = smallButton("СИНХРОНИЗИРОВАТЬ ЭЛО", new View.OnClickListener() {
            @Override
            public void onClick(View v) { syncElo(); }
        });
        rankCard.addView(sync);
        addToContent(rankCard);
        addSpace(contentBox, 8);

        LinearLayout lvl = card();
        lvl.addView(sectionTitle("УРОВЕНЬ " + level));
        int need = level * 500;
        lvl.addView(tv("XP: " + xp + " / " + need, 11, MUTED, false));
        SeekBar progress = new SeekBar(act);
        progress.setMax(need);
        progress.setProgress(Math.min(xp, need));
        progress.setEnabled(false);
        progress.setPadding(0, dp(2), 0, dp(2));
        lvl.addView(progress);
        addToContent(lvl);
        addSpace(contentBox, 8);

        int k = p.optInt("kills", 0), dth = p.optInt("deaths", 0);
        int wins = p.optInt("wins", 0), losses = p.optInt("losses", 0);
        int best = p.optInt("bestStreak", 0), secs = p.optInt("playSeconds", 0);
        LinearLayout stats = card();
        stats.addView(sectionTitle("СТАТИСТИКА"));
        stats.addView(tv(String.format(Locale.US, "K/D: %.2f", dth > 0 ? (float) k / dth : (float) k), 13, TEXT, false));
        stats.addView(tv("Фраги: " + k + "   Смерти: " + dth, 12, MUTED, false));
        stats.addView(tv("Победы: " + wins + "   Поражения: " + losses, 12, MUTED, false));
        stats.addView(tv("Винрейт: " + (wins + losses > 0 ? Math.round(100f * wins / (wins + losses)) + "%" : "—"), 12, MUTED, false));
        stats.addView(tv("Лучшая серия: " + best + "   Время: " + (secs / 60) + " мин", 12, MUTED, false));
        addToContent(stats);

        Button reset = smallButton("СБРОСИТЬ СТАТИСТИКУ", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(act).setTitle("Сброс").setMessage("Обнулить статистику и эло?")
                        .setPositiveButton("Да", (dl, w) -> {
                            JSONObject pr = save.profile();
                            try {
                                pr.put("kills", 0); pr.put("deaths", 0);
                                pr.put("wins", 0); pr.put("losses", 0);
                                pr.put("bestStreak", 0); pr.put("playSeconds", 0);
                                pr.put("elo", 1000); pr.put("pendingElo", 0);
                                pr.put("level", 1); pr.put("xp", 0);
                            } catch (Exception ignored) {}
                            save.saveProfile(pr);
                            refreshHeader();
                            rebuildContent();
                        }).setNegativeButton("Нет", null).show();
            }
        });
        addSpace(contentBox, 8);
        addToContent(reset);
    }

    private void syncElo() {
        JSONObject p = save.profile();
        try {
            int pend = p.optInt("pendingElo", 0);
            if (pend == 0) {
                toast("Нечего синхронизировать");
                return;
            }
            p.put("elo", Math.max(0, p.optInt("elo", 1000) + pend));
            p.put("pendingElo", 0);
            save.saveProfile(p);
            toast("Эло синхронизировано: " + (pend > 0 ? "+" : "") + pend);
            refreshHeader();
            if (tab == 3) rebuildContent();
        } catch (Exception e) {
            toast("Ошибка синхронизации");
        }
    }

    // ------------------- CLANS -------------------
    private void clansScreen() {
        addToContent(sectionTitle("КЛАНЫ"));
        JSONObject p = save.profile();
        String clan = p.optString("clanName", "");
        String tag = p.optString("clanTag", "");
        if (!clan.isEmpty()) {
            LinearLayout card = card();
            card.addView(tv("[" + tag + "] " + clan, 20, ACCENT, true));
            card.addView(tv("Участников: 5   Рейтинг: " + (p.optInt("elo", 1000) + 150), 12, MUTED, false));
            card.addView(tv("Клан хранится локально, сетевые кланы появятся позже.", 11, MUTED, false));
            Button leave = smallButton("ВЫЙТИ ИЗ КЛАНА", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JSONObject pr = save.profile();
                    try {
                        pr.put("clanName", "");
                        pr.put("clanTag", "");
                    } catch (Exception ignored) {}
                    save.saveProfile(pr);
                    rebuildContent();
                }
            });
            card.addView(leave);
            addToContent(card);
            addSpace(contentBox, 8);
        } else {
            LinearLayout make = card();
            make.addView(sectionTitle("СОЗДАТЬ КЛАН"));
            EditText cn = new EditText(act);
            cn.setHint("Название клана");
            cn.setSingleLine(true);
            cn.setTextColor(TEXT);
            cn.setHintTextColor(0xFF5D687C);
            cn.setBackground(bg(PANEL2, 8, 0x22FFFFFF));
            cn.setPadding(dp(8), dp(4), dp(8), dp(4));
            make.addView(cn, new LinearLayout.LayoutParams(-1, dp(42)));
            EditText ct = new EditText(act);
            ct.setHint("ТЕГ (2-5 символов)");
            ct.setSingleLine(true);
            ct.setTextColor(TEXT);
            ct.setHintTextColor(0xFF5D687C);
            ct.setBackground(bg(PANEL2, 8, 0x22FFFFFF));
            ct.setPadding(dp(8), dp(4), dp(8), dp(4));
            LinearLayout.LayoutParams ctlp = new LinearLayout.LayoutParams(-1, dp(42));
            ctlp.topMargin = dp(6);
            make.addView(ct, ctlp);
            Button create = smallButton("СОЗДАТЬ", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String n = cn.getText().toString().trim();
                    String t = ct.getText().toString().trim().toUpperCase(Locale.US);
                    if (n.length() < 2 || t.length() < 2) {
                        toast("Нужны название и тег");
                        return;
                    }
                    JSONObject pr = save.profile();
                    try {
                        pr.put("clanName", n);
                        pr.put("clanTag", t);
                    } catch (Exception ignored) {}
                    save.saveProfile(pr);
                    rebuildContent();
                }
            });
            make.addView(create);
            addToContent(make);
            addSpace(contentBox, 8);
        }

        addToContent(tv("ТОП КЛАНЫ", 13, TEXT, true));
        for (int i = 0; i < clanNames.length; i++) {
            LinearLayout row = row();
            row.setBackground(bg(PANEL, 8, 0));
            row.setPadding(dp(10), dp(7), dp(10), dp(7));
            TextView place = tv("#" + (i + 1), 12, i < 3 ? ACCENT : MUTED, true);
            place.setWidth(dp(36));
            row.addView(place);
            row.addView(tv("[" + clanTags[i] + "] " + clanNames[i], 12, TEXT, false),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(tv(String.valueOf(clanElo[i]), 12, MUTED, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(3);
            addToContent(row);
            row.setLayoutParams(lp);
        }
    }

    // ------------------- LEADERS -------------------
    private void leadersScreen() {
        addToContent(sectionTitle("ТАБЛИЦА ЛИДЕРОВ"));
        int myElo = save.elo();
        String me = save.profile().optString("name", "Player");

        ArrayList<String> names = new ArrayList<>();
        ArrayList<Integer> elos = new ArrayList<>();
        for (int i = 0; i < botNames.length; i++) {
            names.add(botNames[i]);
            elos.add(botElo[i]);
        }
        names.add(me);
        elos.add(myElo);
        ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) order.add(i);
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return elos.get(b) - elos.get(a);
            }
        });

        for (int n = 0; n < order.size(); n++) {
            int i = order.get(n);
            boolean isMe = names.get(i).equals(me);
            LinearLayout row = row();
            row.setBackground(bg(isMe ? 0x33FF8A1E : PANEL, 8, 0));
            row.setPadding(dp(10), dp(7), dp(10), dp(7));
            TextView place = tv("#" + (n + 1), 12, n < 3 ? ACCENT : MUTED, true);
            place.setWidth(dp(40));
            row.addView(place);
            row.addView(tv(names.get(i) + (isMe ? "  (ты)" : ""), 12, isMe ? ACCENT : TEXT, isMe),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(tv(Elo.rankShort(elos.get(i)) + "  " + elos.get(i), 11, MUTED, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(3);
            addToContent(row);
            row.setLayoutParams(lp);
        }
        addSpace(contentBox, 6);
        addToContent(tv("Таблица локальная: твоё эло сравнивается с эталонными игроками.", 10, MUTED, false));
    }

    // ------------------- SETTINGS -------------------
    private void settingsScreen() {
        addToContent(sectionTitle("НАСТРОЙКИ"));
        addToContent(tv("Чувствительность, прицел, FOV, качество и звук.", 11, MUTED, false));
        addSpace(contentBox, 6);

        addSlider("Чувствительность", 30, 250, (int) (save.sens() * 100), new SliderListener() {
            @Override
            public void onValue(int v) { save.setSens(v / 100f); }
        });
        addSlider("FOV", 60, 100, (int) save.fov(), new SliderListener() {
            @Override
            public void onValue(int v) { save.setFov(v); }
        });
        addSlider("Размер прицела", 60, 160, (int) (save.crossSize() * 100), new SliderListener() {
            @Override
            public void onValue(int v) { save.setCrossSize(v / 100f); }
        });
        addSlider("Громкость", 0, 100, (int) (save.volume() * 100), new SliderListener() {
            @Override
            public void onValue(int v) { save.setVolume(v / 100f); }
        });

        addToContent(tv("СТИЛЬ ПРИЦЕЛА", 12, TEXT, true));
        LinearLayout styles = row();
        String[] st = {"cross", "dot", "circle"};
        String[] stn = {"КРЕСТ", "ТОЧКА", "КРУГ"};
        for (int i = 0; i < st.length; i++) {
            final String sv = st[i];
            Button b = chip(stn[i], save.crossStyle().equals(sv), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    save.setCrossStyle(sv);
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(4);
            styles.addView(b, lp);
        }
        addToContent(styles);
        addSpace(contentBox, 6);

        addToContent(tv("ЦВЕТ ПРИЦЕЛА", 12, TEXT, true));
        LinearLayout colors = row();
        String[] cs = {"#39FF6A", "#FF4B4B", "#47D7FF", "#FFE14B", "#FFFFFF"};
        for (final String col : cs) {
            Button b = new Button(act);
            b.setBackground(bg(Color.parseColor(col), 20, save.crossColor().equalsIgnoreCase(col) ? 0xFFFFFFFF : 0x00000000));
            b.setMinWidth(0); b.setMinHeight(0);
            b.setPadding(0, 0, 0, 0);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    save.setCrossColor(col);
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.rightMargin = dp(8);
            colors.addView(b, lp);
        }
        addToContent(colors);
        addSpace(contentBox, 6);

        addToContent(tv("КАЧЕСТВО ГРАФИКИ", 12, TEXT, true));
        LinearLayout q = row();
        String[] qn = {"НИЗКОЕ", "СРЕДНЕЕ", "ВЫСОКОЕ"};
        for (int i = 0; i < qn.length; i++) {
            final int qv = i;
            Button b = chip(qn[i], save.quality() == i, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    save.setQuality(qv);
                    rebuildContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(4);
            q.addView(b, lp);
        }
        addToContent(q);
        addSpace(contentBox, 6);

        Switch invert = new Switch(act);
        invert.setText("Инверсия Y");
        invert.setTextColor(TEXT);
        invert.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        invert.setChecked(save.invertY());
        invert.setOnCheckedChangeListener((btn, val) -> save.setInvertY(val));
        addToContent(invert);

        Switch fps = new Switch(act);
        fps.setText("Показывать FPS");
        fps.setTextColor(TEXT);
        fps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        fps.setChecked(save.showFps());
        fps.setOnCheckedChangeListener((btn, val) -> save.setShowFps(val));
        addToContent(fps);

        addSpace(contentBox, 8);
        addToContent(tv("Настройки сохраняются сразу.", 10, MUTED, false));
    }

    private interface SliderListener { void onValue(int v); }

    private void addSlider(String label, int min, int max, int value, final SliderListener l) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView title = tv(label + ": " + value, 12, TEXT, false);
        box.addView(title);
        SeekBar sb = new SeekBar(act);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                title.setText(label + ": " + v);
                l.onValue(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        box.addView(sb);
        addToContent(box);
    }

    // ------------------- GAME -------------------
    private void startGame(boolean lan, String ip) {
        GameConfig cfg = new GameConfig();
        cfg.mode = lan ? GameConfig.MODE_DM : modeSel;
        cfg.mapIndex = lan ? 0 : mapSel;
        cfg.difficulty = diffSel;
        cfg.lan = lan;
        cfg.lanHost = lan && (ip == null || ip.isEmpty());
        cfg.lanIp = ip == null ? "" : ip;
        cfg.playerName = save.profile().optString("name", "Player");
        cfg.onlineElo = lan;
        lastCfg = cfg;
        lastLanIp = ip;

        if (game != null) {
            game.dispose();
            game = null;
        }
        gameLayer.removeAllViews();
        game = new GameView(act, cfg, save, this);
        gameLayer.addView(game, new FrameLayout.LayoutParams(-1, -1));
        gameLayer.addView(game.hud, new FrameLayout.LayoutParams(-1, -1));
        gameLayer.setVisibility(View.VISIBLE);
        menuLayer.setVisibility(View.GONE);
        game.requestFocus();

        if (cfg.lan && cfg.lanHost) {
            game.getState().startLanHost();
            toast("LAN хост: " + Lan.localIp() + ":" + Lan.PORT);
        } else if (cfg.lan) {
            game.getState().startLanClient(ip);
        }
    }

    private void exitToMenu() {
        if (game != null) {
            try {
                game.pauseGame();
                game.dispose();
            } catch (Throwable ignored) {}
            game = null;
        }
        gameLayer.removeAllViews();
        gameLayer.setVisibility(View.GONE);
        menuLayer.setVisibility(View.VISIBLE);
        refreshHeader();
        rebuildContent();
    }

    @Override
    public void onMatchEnd(final GameState gs) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (game != null) game.pauseGame();
                String msg = "Результат: " + gs.resultText + "\n\n"
                        + "Фраги: " + gs.kills + "\n"
                        + "Смерти: " + gs.deaths + "\n"
                        + "MVP раундов: " + gs.mvps + "\n"
                        + "Закладок: " + gs.plants + "\n\n"
                        + "Эло: " + (gs.eloDelta >= 0 ? "+" : "") + gs.eloDelta
                        + (gs.cfg.lan ? " (начислено сразу)" : " (отложено, кнопка SYNC)") + "\n"
                        + "Монеты: +" + gs.coinsEarned + "\n"
                        + "XP: +" + gs.xpEarned;
                new AlertDialog.Builder(act)
                        .setTitle((gs.win ? "ПОБЕДА" : gs.draw ? "НИЧЬЯ" : "ПОРАЖЕНИЕ"))
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton("В МЕНЮ", (d, w) -> exitToMenu())
                        .setNegativeButton("ЕЩЁ РАЗ", (d, w) -> {
                            GameConfig c = lastCfg;
                            exitToMenu();
                            if (c != null) {
                                modeSel = c.mode;
                                mapSel = c.mapIndex;
                                diffSel = c.difficulty;
                                startGame(c.lan, lastLanIp);
                            }
                        })
                        .show();
            }
        });
    }

    @Override
    public void onPausePressed(final GameView view) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.pauseGame();
                new AlertDialog.Builder(act)
                        .setTitle("Пауза")
                        .setMessage("Матч остановлен.")
                        .setCancelable(false)
                        .setPositiveButton("ПРОДОЛЖИТЬ", (d, w) -> view.resumeGame())
                        .setNegativeButton("В МЕНЮ", (d, w) -> exitToMenu())
                        .show();
            }
        });
    }

    public void onActivityPause() {
        if (game != null) game.onPause();
    }

    public void onActivityResume() {
        if (game != null) game.onResume();
    }

    public void dispose() {
        if (game != null) {
            game.dispose();
            game = null;
        }
    }

    public boolean back() {
        if (gameLayer.getVisibility() == View.VISIBLE) {
            View child = gameLayer.getChildAt(0);
            if (child instanceof GameView) onPausePressed((GameView) child);
            return true;
        }
        if (tab != 0) {
            tab = 0;
            rebuildContent();
            return true;
        }
        return false;
    }

    public void showCrash(String text) {
        try {
            TextView tv = new TextView(act);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTextColor(TEXT);
            tv.setPadding(dp(12), dp(12), dp(12), dp(12));
            ScrollView sv = new ScrollView(act);
            sv.addView(tv);
            new AlertDialog.Builder(act)
                    .setTitle("Прошлый запуск завершился с ошибкой")
                    .setView(sv)
                    .setPositiveButton("Очистить лог", (d, w) -> CrashLog.clear(act))
                    .setNegativeButton("Закрыть", null)
                    .show();
        } catch (Throwable ignored) {}
    }

    private void toast(String s) {
        Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
    }
}
