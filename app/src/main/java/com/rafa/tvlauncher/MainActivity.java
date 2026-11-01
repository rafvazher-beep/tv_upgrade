package com.rafa.tvlauncher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Launcher sencillo estilo Apple TV. Solo vistas nativas: sin blur, sin video,
 * sin consultas de red. Configuracion en res/values/config.xml.
 */
public class MainActivity extends Activity {

    private static class AppEntry {
        String pkg;
        ComponentName cn;
        String label;
        Drawable img;
        boolean isBanner;
        int glow;
    }

    private SharedPreferences prefs;
    private int W, H, margin, pad, gap, tileW, tileH, labelH, cols;
    private FrameLayout root;
    private ScrollView scroll;
    private FrameLayout content;
    private View highlight;
    private boolean highlightPlaced;
    private TextView dockTitle, greeting, netPill;
    private GradientDrawable glowDrawable;
    private int glowCur = 0;
    private ValueAnimator glowAnim;
    private final List<View> pills = new ArrayList<>();
    private final List<View> dockTiles = new ArrayList<>();
    private final List<View> gridTiles = new ArrayList<>();
    private Typeface fThin, fLight, fReg;
    private String builtKey = "";
    private boolean resumed;
    private LinearLayout dropdown;
    private View dropdownOwner;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = dm.widthPixels;
        H = dm.heightPixels;
        cols = getResources().getInteger(R.integer.columns);
        margin = (int) (W * 0.045f);
        pad = (int) (W * 0.010f);
        gap = (int) (W * 0.010f);
        int bandW = W - 2 * margin;
        tileW = (bandW - 2 * pad - (cols - 1) * gap) / cols;
        tileH = tileW * 9 / 16;
        labelH = (int) (H * 0.05f);
        fThin = loadFont(200);
        fLight = loadFont(300);
        fReg = loadFont(400);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        List<AppEntry> all = loadApps();
        StringBuilder k = new StringBuilder();
        for (AppEntry e : all) k.append(e.pkg).append(';');
        if (!k.toString().equals(builtKey)) {
            builtKey = k.toString();
            build(all);
            content.post(new Runnable() {
                @Override
                public void run() {
                    restoreFocus();
                }
            });
        }
        refreshStatus();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (resumed) {
            closeDropdown(false);
            if (!dockTiles.isEmpty()) dockTiles.get(0).requestFocus();
            scroll.smoothScrollTo(0, 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (dropdown != null) {
            closeDropdown(true);
        } else {
            scroll.smoothScrollTo(0, 0);
            if (!dockTiles.isEmpty()) dockTiles.get(0).requestFocus();
        }
    }

    // ------------------------------------------------------------ datos

    private Typeface loadFont(int wght) {
        try {
            Typeface.Builder tb = new Typeface.Builder(getAssets(), "Outfit.ttf");
            tb.setFontVariationSettings("'wght' " + wght);
            Typeface t = tb.build();
            if (t != null) return t;
        } catch (Exception ignored) {
        }
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    private List<AppEntry> loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        if (list.isEmpty()) {
            i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            list = pm.queryIntentActivities(i, 0);
        }
        Set<String> hidden = new HashSet<>();
        Collections.addAll(hidden, getResources().getStringArray(R.array.hidden_apps));
        Set<String> seen = new HashSet<>();
        List<AppEntry> out = new ArrayList<>();
        for (ResolveInfo ri : list) {
            String p = ri.activityInfo.packageName;
            if (hidden.contains(p) || !seen.add(p)) continue;
            AppEntry e = new AppEntry();
            e.pkg = p;
            e.cn = new ComponentName(p, ri.activityInfo.name);
            e.label = String.valueOf(ri.loadLabel(pm));
            Drawable d = ri.activityInfo.loadBanner(pm);
            if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
            e.isBanner = d != null;
            if (d == null) d = ri.loadIcon(pm);
            e.glow = sampleColor(d);
            e.img = d;
            out.add(e);
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry c) {
                return a.label.compareToIgnoreCase(c.label);
            }
        });
        return out;
    }

    private int sampleColor(Drawable d) {
        try {
            Bitmap bm = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 16, 9);
            d.draw(c);
            float r = 0, g = 0, bl = 0, wsum = 0;
            float[] hsv = new float[3];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 9; y++) {
                    int p = bm.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.colorToHSV(p, hsv);
                    if (hsv[2] < 0.25f) continue;
                    float w = 0.2f + hsv[1];
                    r += Color.red(p) * w;
                    g += Color.green(p) * w;
                    bl += Color.blue(p) * w;
                    wsum += w;
                }
            }
            bm.recycle();
            if (wsum > 0) {
                return Color.argb(0x90, (int) (r / wsum), (int) (g / wsum), (int) (bl / wsum));
            }
        } catch (Exception ignored) {
        }
        return Color.argb(0x90, 0x3A, 0x6B, 0xFF);
    }

    // ------------------------------------------------------------ interfaz

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private TextView text(String s, float sizePx, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void build(List<AppEntry> all) {
        pills.clear();
        dockTiles.clear();
        gridTiles.clear();
        highlightPlaced = false;
        dropdown = null;

        String[] dockPk = getResources().getStringArray(R.array.dock_apps);
        List<AppEntry> dock = new ArrayList<>();
        Set<String> dockSet = new HashSet<>();
        for (String p : dockPk) {
            for (AppEntry e : all) {
                if (e.pkg.equals(p) && dock.size() < cols) {
                    dock.add(e);
                    dockSet.add(p);
                    break;
                }
            }
        }
        List<AppEntry> grid = new ArrayList<>();
        for (AppEntry e : all) if (!dockSet.contains(e.pkg)) grid.add(e);

        int padV = (int) (tileH * 0.22f);
        int bandH = tileH + 2 * padV;
        int titleH = (int) (H * 0.10f);
        int bottomGap = (int) (H * 0.05f);
        int headerH = H - bottomGap - bandH - titleH;

        root = new FrameLayout(this);
        root.setBackgroundColor(getResources().getColor(R.color.bg, getTheme()));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFocusable(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        scroll.addView(content, new ViewGroup.LayoutParams(W, ViewGroup.LayoutParams.WRAP_CONTENT));

        // brillo ambiental detras del titulo del dock
        View glow = new View(this);
        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(W * 0.30f);
        glowDrawable.setGradientCenter(0.5f, 0.5f);
        glowDrawable.setColors(new int[]{glowCur, glowCur & 0x00FFFFFF});
        glow.setBackground(glowDrawable);
        int glowH = (int) (H * 0.55f);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams((int) (W * 0.7f), glowH);
        glp.leftMargin = -(int) (W * 0.05f);
        glp.topMargin = headerH + titleH / 2 - glowH / 2;
        content.addView(glow, glp);

        // un unico resaltado de cristal que se desplaza entre tiles
        highlight = new View(this);
        GradientDrawable hd = rounded(0x38FFFFFF, tileH * 0.2f);
        hd.setStroke(Math.max(2, W / 900), 0x66FFFFFF);
        highlight.setBackground(hd);
        highlight.setAlpha(0f);
        content.addView(highlight, new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.START));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        content.addView(column, new FrameLayout.LayoutParams(W, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- cabecera
        FrameLayout header = new FrameLayout(this);
        column.addView(header, new LinearLayout.LayoutParams(W, headerH));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = text("", H * 0.034f, fReg, 0xCCFFFFFF);
        left.addView(greeting);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, H * 0.15f);
        clock.setTypeface(fThin);
        clock.setTextColor(Color.WHITE);
        clock.setIncludeFontPadding(false);
        left.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d 'de' MMMM");
        date.setFormat12Hour("EEEE d 'de' MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, H * 0.034f);
        date.setTypeface(fReg);
        date.setTextColor(0x99FFFFFF);
        date.setIncludeFontPadding(false);
        left.addView(date);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        llp.leftMargin = margin + pad;
        llp.topMargin = (int) (H * 0.06f);
        header.addView(left, llp);

        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        plp.rightMargin = margin;
        plp.topMargin = (int) (H * 0.06f);
        header.addView(pillRow, plp);

        View pInputs = makePill("Entradas");
        netPill = (TextView) makePill("Red");
        View pSettings = makePill("Ajustes");
        pills.add(pInputs);
        pills.add(netPill);
        pills.add(pSettings);
        for (View p : pills) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            l.leftMargin = (int) (W * 0.012f);
            pillRow.addView(p, l);
        }
        pInputs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleInputs(v);
            }
        });
        netPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    openSettings();
                }
            }
        });
        pSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

        // ---- titulo del dock
        dockTitle = text(dock.isEmpty() ? "" : dock.get(0).label, H * 0.062f, fLight, Color.WHITE);
        dockTitle.setGravity(Gravity.CENTER_VERTICAL);
        dockTitle.setPadding(margin + pad, 0, 0, 0);
        column.addView(dockTitle, new LinearLayout.LayoutParams(W, titleH));

        // ---- dock
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        band.setPadding(pad, 0, pad, 0);
        band.setBackground(rounded(0xFF161618, bandH * 0.26f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(W - 2 * margin, bandH);
        blp.leftMargin = margin;
        column.addView(band, blp);
        for (int i = 0; i < dock.size(); i++) {
            View t = makeTile(dock.get(i), true, i);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(tileW, tileH);
            l.rightMargin = gap;
            band.addView(t, l);
            dockTiles.add(t);
        }

        column.addView(new View(this), new LinearLayout.LayoutParams(W, bottomGap));

        // ---- cuadricula
        int rowH = tileH + labelH;
        int rowGap = (int) (H * 0.045f);
        LinearLayout gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setPadding(margin + pad, (int) (H * 0.03f), 0, 0);
        column.addView(gridBox, new LinearLayout.LayoutParams(W, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = null;
        for (int i = 0; i < grid.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(W, rowH);
                rl.bottomMargin = rowGap;
                gridBox.addView(row, rl);
            }
            View t = makeTile(grid.get(i), false, i);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(tileW, rowH);
            l.rightMargin = gap;
            row.addView(t, l);
            gridTiles.add(t);
        }
        column.addView(new View(this), new LinearLayout.LayoutParams(W, (int) (H * 0.6f)));

        wireFocus();
        setContentView(root);
    }

    private View makePill(String label) {
        final TextView t = text(label, H * 0.03f, fReg, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        int ph = (int) (H * 0.016f);
        t.setPadding((int) (W * 0.014f), ph, (int) (W * 0.014f), ph);
        final float r = H * 0.05f;
        t.setBackground(rounded(0x33FFFFFF, r));
        t.setFocusable(true);
        t.setClickable(true);
        t.setDefaultFocusHighlightEnabled(false);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                t.setBackground(rounded(has ? 0x88FFFFFF : 0x33FFFFFF, r));
                t.setTextColor(has ? Color.BLACK : Color.WHITE);
                float s = has ? 1.08f : 1f;
                t.animate().scaleX(s).scaleY(s).setDuration(150).start();
                if (has) {
                    highlight.animate().alpha(0f).setDuration(120).start();
                    scroll.smoothScrollTo(0, 0);
                }
            }
        });
        return t;
    }

    private View makeTile(final AppEntry e, final boolean dock, final int idx) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setFocusable(true);
        t.setClickable(true);
        t.setDefaultFocusHighlightEnabled(false);

        ImageView iv = new ImageView(this);
        iv.setBackground(rounded(0xFF1C1C1E, tileH * 0.12f));
        iv.setScaleType(e.isBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
        if (!e.isBanner) iv.setPadding(tileH / 6, tileH / 6, tileH / 6, tileH / 6);
        iv.setImageDrawable(e.img);
        final float rad = tileH * 0.12f;
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        t.addView(iv, new LinearLayout.LayoutParams(tileW, tileH));

        final TextView lab;
        if (!dock) {
            lab = text(e.label, H * 0.028f, fReg, Color.WHITE);
            lab.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            lab.setEllipsize(android.text.TextUtils.TruncateAt.END);
            lab.setVisibility(View.INVISIBLE);
            t.addView(lab, new LinearLayout.LayoutParams(tileW, labelH));
        } else {
            lab = null;
        }

        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(e);
            }
        });
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                float s = has ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(150).start();
                if (lab != null) lab.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
                if (!has) return;
                moveHighlight(v);
                if (dock) {
                    dockTitle.setText(e.label);
                    animateGlow(e.glow);
                    scroll.smoothScrollTo(0, 0);
                } else {
                    int[] o = offset(v);
                    scroll.smoothScrollTo(0, Math.max(0, o[1] - (int) (H * 0.18f)));
                }
                prefs.edit().putString("last", (dock ? "dock:" : "grid:") + idx).apply();
            }
        });
        return t;
    }

    // ------------------------------------------------------------ foco y animacion

    private int[] offset(View v) {
        int x = 0, y = 0;
        View c = v;
        while (c != null && c != content) {
            x += c.getLeft();
            y += c.getTop();
            c = (c.getParent() instanceof View) ? (View) c.getParent() : null;
        }
        return new int[]{x, y};
    }

    private void moveHighlight(View tile) {
        if (tile.getWidth() == 0) return;
        int[] o = offset(tile);
        int p = (int) (tile.getWidth() * 0.08f);
        int w = tile.getWidth() + 2 * p;
        int h = tile.getHeight() + 2 * p;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) highlight.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            highlight.setLayoutParams(lp);
        }
        float tx = o[0] - p;
        float ty = o[1] - p;
        if (!highlightPlaced) {
            highlight.setTranslationX(tx);
            highlight.setTranslationY(ty);
            highlight.setAlpha(1f);
            highlightPlaced = true;
        } else {
            highlight.animate().alpha(1f).translationX(tx).translationY(ty)
                    .setDuration(280).setInterpolator(new OvershootInterpolator(1.1f)).start();
        }
    }

    private void animateGlow(int target) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowCur, target);
        glowAnim.setDuration(350);
        glowAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                glowCur = (Integer) a.getAnimatedValue();
                glowDrawable.setColors(new int[]{glowCur, glowCur & 0x00FFFFFF});
            }
        });
        glowAnim.start();
    }

    private void setIds(List<View> list) {
        for (View v : list) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
    }

    /** Foco determinista: Arriba/Abajo mueven exactamente una fila y los extremos no se escapan. */
    private void wireFocus() {
        setIds(pills);
        setIds(dockTiles);
        setIds(gridTiles);
        int pn = pills.size(), dn = dockTiles.size(), gn = gridTiles.size();

        for (int p = 0; p < pn; p++) {
            View v = pills.get(p);
            v.setNextFocusLeftId(p == 0 ? v.getId() : pills.get(p - 1).getId());
            v.setNextFocusRightId(p == pn - 1 ? v.getId() : pills.get(p + 1).getId());
            v.setNextFocusUpId(v.getId());
            if (dn > 0) {
                v.setNextFocusDownId(dockTiles.get(Math.max(0, Math.min(dn - 1, dn - pn + p))).getId());
            } else if (gn > 0) {
                v.setNextFocusDownId(gridTiles.get(0).getId());
            } else {
                v.setNextFocusDownId(v.getId());
            }
        }
        for (int c = 0; c < dn; c++) {
            View v = dockTiles.get(c);
            v.setNextFocusLeftId(c == 0 ? v.getId() : dockTiles.get(c - 1).getId());
            v.setNextFocusRightId(c == dn - 1 ? v.getId() : dockTiles.get(c + 1).getId());
            v.setNextFocusUpId(pills.get(Math.max(0, Math.min(pn - 1, c - (dn - pn)))).getId());
            v.setNextFocusDownId(gn > 0 ? gridTiles.get(Math.min(c, gn - 1)).getId() : v.getId());
        }
        for (int i = 0; i < gn; i++) {
            View v = gridTiles.get(i);
            int row = i / cols, col = i % cols;
            v.setNextFocusLeftId(col == 0 ? v.getId() : gridTiles.get(i - 1).getId());
            v.setNextFocusRightId((col == cols - 1 || i == gn - 1) ? v.getId() : gridTiles.get(i + 1).getId());
            if (row == 0) {
                v.setNextFocusUpId(dn > 0 ? dockTiles.get(Math.min(col, dn - 1)).getId() : pills.get(0).getId());
            } else {
                v.setNextFocusUpId(gridTiles.get(i - cols).getId());
            }
            int lastRow = (gn - 1) / cols;
            if (i + cols < gn) {
                v.setNextFocusDownId(gridTiles.get(i + cols).getId());
            } else if (row < lastRow) {
                v.setNextFocusDownId(gridTiles.get(gn - 1).getId());
            } else {
                v.setNextFocusDownId(v.getId());
            }
        }
    }

    private void restoreFocus() {
        View v = null;
        try {
            String[] p = prefs.getString("last", "dock:0").split(":");
            int i = Integer.parseInt(p[1]);
            if (p[0].equals("grid") && i < gridTiles.size()) v = gridTiles.get(i);
            else if (i < dockTiles.size()) v = dockTiles.get(i);
        } catch (Exception ignored) {
        }
        if (v == null && !dockTiles.isEmpty()) v = dockTiles.get(0);
        if (v == null && !pills.isEmpty()) v = pills.get(0);
        if (v != null) v.requestFocus();
    }

    // ------------------------------------------------------------ acciones

    private void launch(AppEntry e) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            i.setComponent(e.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "No se puede abrir " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            Toast.makeText(this, "No se pueden abrir los ajustes", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshStatus() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (greeting != null) {
            greeting.setText(h < 6 ? "Buenas noches" : h < 13 ? "Buenos días" : h < 21 ? "Buenas tardes" : "Buenas noches");
        }
        if (netPill != null) netPill.setText(networkLabel());
    }

    private String networkLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "Sin red";
            if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) return "Ethernet";
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm.getConnectionInfo();
                String s = wi == null ? null : wi.getSSID();
                if (s != null && !s.contains("unknown")) {
                    return s.replace("\"", "");
                }
                return "Wi-Fi";
            }
            return ni.getTypeName();
        } catch (Exception ex) {
            return "Red";
        }
    }

    // ------------------------------------------------------------ menu de entradas

    private int inputRank(TvInputInfo i) {
        if (i.getType() == TvInputInfo.TYPE_HDMI) return 0;
        if (i.isPassthroughInput()) return 1;
        return 2;
    }

    private void toggleInputs(View pill) {
        if (dropdown != null) {
            closeDropdown(true);
            return;
        }
        TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        boolean tunerAdded = false;
        if (m != null) {
            for (TvInputInfo i : m.getTvInputList()) {
                if (i.isPassthroughInput()) {
                    inputs.add(i);
                } else if (i.getType() == TvInputInfo.TYPE_TUNER && !tunerAdded) {
                    inputs.add(i);
                    tunerAdded = true;
                }
            }
        }
        Collections.sort(inputs, new Comparator<TvInputInfo>() {
            @Override
            public int compare(TvInputInfo a, TvInputInfo c) {
                return inputRank(a) - inputRank(c);
            }
        });

        final LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        int dp = (int) (H * 0.012f);
        d.setPadding(dp, dp, dp, dp);
        d.setBackground(rounded(0xF02A2A2D, H * 0.03f));
        final List<View> items = new ArrayList<>();
        if (inputs.isEmpty()) {
            TextView t = text("Sin entradas", H * 0.03f, fReg, 0x99FFFFFF);
            t.setPadding(dp * 2, dp, dp * 2, dp);
            d.addView(t);
        }
        for (final TvInputInfo info : inputs) {
            CharSequence lbl = info.loadCustomLabel(this);
            if (lbl == null || lbl.length() == 0) lbl = info.loadLabel(this);
            final TextView t = text(String.valueOf(lbl), H * 0.03f, fReg, Color.WHITE);
            t.setPadding(dp * 2, dp, dp * 2, dp);
            t.setFocusable(true);
            t.setClickable(true);
            t.setDefaultFocusHighlightEnabled(false);
            t.setId(View.generateViewId());
            final float r = H * 0.02f;
            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    t.setBackground(has ? rounded(0x55FFFFFF, r) : null);
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDropdown(false);
                    switchInput(info);
                }
            });
            items.add(t);
            d.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        for (int i = 0; i < items.size(); i++) {
            View v = items.get(i);
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(v.getId());
            v.setNextFocusUpId(i == 0 ? v.getId() : items.get(i - 1).getId());
            v.setNextFocusDownId(i == items.size() - 1 ? v.getId() : items.get(i + 1).getId());
        }

        int[] loc = new int[2];
        pill.getLocationInWindow(loc);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (W * 0.17f), -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.max(0, Math.min(loc[0], W - (int) (W * 0.17f) - margin / 2));
        lp.topMargin = loc[1] + pill.getHeight() + (int) (H * 0.015f);
        root.addView(d, lp);
        dropdown = d;
        dropdownOwner = pill;
        if (!items.isEmpty()) items.get(0).requestFocus();
    }

    private void closeDropdown(boolean refocus) {
        if (dropdown == null) return;
        root.removeView(dropdown);
        dropdown = null;
        if (refocus && dropdownOwner != null) dropdownOwner.requestFocus();
    }

    private void switchInput(TvInputInfo info) {
        try {
            if (info.isPassthroughInput()) {
                startActivity(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (ActivityNotFoundException ex) {
            Intent li = getPackageManager().getLaunchIntentForPackage(info.getServiceInfo().packageName);
            if (li != null) {
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(li);
            } else {
                Toast.makeText(this, "No se puede abrir la entrada", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "No se puede abrir la entrada", Toast.LENGTH_SHORT).show();
        }
    }
}
