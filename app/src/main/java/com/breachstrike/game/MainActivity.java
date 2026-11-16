package com.breachstrike.game;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.breachstrike.game.ui.MenuUI;

public final class MainActivity extends Activity {
    private MenuUI menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(this);
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(0xFF0B0E14);
                getWindow().setNavigationBarColor(0xFF0B0E14);
            }
            hideSystemUi();
            menu = new MenuUI(this);
            setContentView(menu.root());
            String crash = CrashLog.read(this);
            if (crash != null) menu.showCrash(crash);
        } catch (Throwable t) {
            CrashLog.write(this, "MainActivity", t);
            FrameLayout box = new FrameLayout(this);
            box.setBackgroundColor(0xFF0B0E14);
            TextView tv = new TextView(this);
            tv.setTextColor(0xFFFF6A5A);
            tv.setTextSize(14);
            tv.setPadding(24, 24, 24, 24);
            tv.setText("Ошибка запуска:\n\n" + android.util.Log.getStackTraceString(t));
            box.addView(tv);
            setContentView(box);
        }
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (menu != null) menu.onActivityPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (menu != null) menu.onActivityResume();
    }

    @Override
    public void onBackPressed() {
        if (menu == null || !menu.back()) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (menu != null) menu.dispose();
        super.onDestroy();
    }
}
