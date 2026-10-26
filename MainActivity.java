package com.lynko.keyboard;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends Activity {
    static final int BG = 0xFF0F0E17, CARD = 0xFF1B1A2B, CARD2 = 0xFF2A2840,
            ACC = 0xFF8B5CF6, ACC2 = 0xFFEC4899, SUB = 0xFFA7A3C2, WHITE = 0xFFFFFFFF;
    static final String[] THEMES = {"Auto", "Dark", "Light", "Purple", "Neon", "LYNKO"};
    static final int[] TCOL = {0xFF64748B, 0xFF1E1E2E, 0xFFE8EAF0, 0xFF4B3380, 0xFF00C8A0, 0xFFEC4899};

    SharedPreferences p;
    ScrollView sv;
    LinearLayout content, nav;
    int cur = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("lynko", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(CARD);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        sv = new ScrollView(this);
        sv.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(36), dp(16), dp(16));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this);
        nav.setBackgroundColor(CARD);
        nav.setPadding(0, dp(6), 0, dp(6));
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handle(i); }

    @Override protected void onResume() { super.onResume(); if (cur == 1) show(1); }

    @Override public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (cur == 1) show(1);
    }

    @Override public void onBackPressed() { if (cur != 2 && cur != 0) show(2); else super.onBackPressed(); }

    void handle(Intent i) {
        if (i.getBooleanExtra("mic", false)) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
        show(i.getIntExtra("screen", p.getBoolean("seen", false) ? 2 : 0));
    }

    // ---------- ui helpers ----------
    int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    GradientDrawable shape(int color, int rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(rDp));
        return g;
    }

    TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    void add(LinearLayout parent, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        parent.addView(v, lp);
    }

    LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackground(shape(CARD, 20));
        add(content, l);
        return l;
    }

    Button btn(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(14);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACC, ACC2});
        g.setCornerRadius(dp(24));
        b.setBackground(g);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    Button ghost(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(14);
        b.setBackground(shape(CARD2, 24));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    View logo(int size) {
        int id = getResources().getIdentifier("logo", "drawable", getPackageName());
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        if (id == 0) {
            g.setColors(new int[]{ACC, ACC2});
            TextView t = tv("L", size / 2, WHITE, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(g);
            t.setLayoutParams(lp);
            return t;
        }
        g.setColor(CARD2);
        ImageView iv = new ImageView(this);
        iv.setImageResource(id);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(g);
        iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        iv.setClipToOutline(true);
        iv.setLayoutParams(lp);
        return iv;
    }

    void header(String title, String sub) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACC, ACC2});
        g.setCornerRadius(dp(24));
        h.setBackground(g);
        h.addView(logo(56));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(14), 0, 0, 0);
        tx.addView(tv(title, 24, WHITE, true));
        tx.addView(tv(sub, 13, 0xCCFFFFFF, false));
        h.addView(tx);
        add(content, h);
    }

    void buildNav() {
        nav.removeAllViews();
        String[] labels = {"🏠\nHome", "👁\nPreview", "⚙\nSettings", "ℹ\nAbout"};
        for (int i = 0; i < 4; i++) {
            final int s = i + 2;
            TextView t = new TextView(this);
            t.setText(labels[i]);
            t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(cur == s ? ACC : SUB);
            t.setOnClickListener(v -> show(s));
            nav.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    void show(int s) {
        final int keep = (s == cur) ? sv.getScrollY() : 0;
        cur = s;
        content.removeAllViews();
        content.setGravity(Gravity.TOP);
        buildNav();
        nav.setVisibility(s >= 2 ? View.VISIBLE : View.GONE);
        switch (s) {
            case 0: welcome(); break;
            case 1: setup(); break;
            case 2: home(); break;
            case 3: preview(); break;
            case 4: settings(); break;
            default: about();
        }
        sv.post(() -> sv.scrollTo(0, keep));
    }

    // ---------- screens ----------
    void welcome() {
        content.setGravity(Gravity.CENTER);
        View lg = logo(150);
        lg.setAlpha(0f); lg.setScaleX(0.4f); lg.setScaleY(0.4f);
        lg.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1000).start();
        content.addView(lg);
        TextView t = tv("LYNKO", 44, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setAlpha(0f);
        t.animate().alpha(1f).setStartDelay(500).setDuration(800).start();
        add(content, t);
        TextView s = tv("Type your style.", 16, SUB, false);
        s.setGravity(Gravity.CENTER);
        add(content, s);
        add(content, btn("Get started", () -> { p.edit().putBoolean("seen", true).apply(); show(1); }));
    }

    boolean enabled() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        return s != null && s.contains(getPackageName());
    }

    boolean selected() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return s != null && s.contains(getPackageName());
    }

    boolean micOk() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    void setup() {
        header("Setup", "3 quick steps");
        LinearLayout c = card();
        c.addView(tv((enabled() ? "✅" : "❌") + "  1. Enable LYNKO Keyboard", 16, WHITE, true));
        add(c, btn("Open keyboard settings", () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        LinearLayout c2 = card();
        c2.addView(tv((selected() ? "✅" : "❌") + "  2. Select LYNKO as your keyboard", 16, WHITE, true));
        add(c2, btn("Choose keyboard", () ->
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()));
        LinearLayout c3 = card();
        c3.addView(tv((micOk() ? "✅" : "⬜") + "  3. Microphone (only for voice typing)", 16, WHITE, true));
        add(c3, ghost("Allow microphone", () -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7)));
        add(content, btn("Done", () -> show(2)));
    }

    void home() {
        header("LYNKO", "Your keyboard, your style");
        LinearLayout c = card();
        c.addView(tv(enabled() && selected() ? "✅ Keyboard is ready" : "⚠ Setup not finished", 15, WHITE, true));
        if (!(enabled() && selected())) add(c, ghost("Open setup guide", () -> show(1)));

        LinearLayout t = card();
        t.addView(tv("Themes", 18, WHITE, true));
        themeRow(t);
        add(t, ghost("Save current look", () -> {
            String e = p.getInt("theme", 0) + "," + p.getInt("alpha", 255) + "," + p.getInt("radius", 8) + "," + p.getInt("height", 46);
            String old = p.getString("saved", "");
            p.edit().putString("saved", old.isEmpty() ? e : old + "|" + e).apply();
            show(2);
        }));
        String saved = p.getString("saved", "");
        if (!saved.isEmpty()) {
            t.addView(tv("Saved themes", 14, SUB, false));
            for (String e : saved.split("\\|")) {
                final String[] f = e.split(",");
                add(t, ghost("Apply: " + THEMES[Integer.parseInt(f[0])] + " · alpha " + f[1], () ->
                        p.edit().putInt("theme", Integer.parseInt(f[0])).putInt("alpha", Integer.parseInt(f[1]))
                                .putInt("radius", Integer.parseInt(f[2])).putInt("height", Integer.parseInt(f[3])).apply()));
            }
            add(t, ghost("Delete saved themes", () -> { p.edit().remove("saved").apply(); show(2); }));
        }

        LinearLayout q = card();
        q.addView(tv("Quick switches", 18, WHITE, true));
        sw(q, "Swipe typing", "swipe", true);
        sw(q, "Split keyboard", "split", false);
        sw(q, "Privacy mode", "privacy", false);

        LinearLayout s = card();
        s.addView(tv("Stylish text", 18, WHITE, true));
        final EditText in = new EditText(this);
        in.setHint("Type here");
        in.setTextColor(WHITE);
        in.setHintTextColor(SUB);
        s.addView(in);
        final TextView out = tv("", 20, ACC, false);
        add(s, ghost("Convert & copy (cycle styles)", () -> {
            int st = p.getInt("stylish", 0) % 5 + 1;
            p.edit().putInt("stylish", st).apply();
            String r = Engine.fancy(in.getText().toString(), st);
            out.setText(r);
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("lynko", r));
            toast("Copied");
        }));
        s.addView(out);

        LinearLayout k = card();
        k.addView(tv("Clipboard", 18, WHITE, true));
        add(k, ghost("Clear clipboard", () -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).clearPrimaryClip();
            LynkoIME.hist.clear();
            toast("Clipboard cleared");
        }));
    }

    void themeRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        final TextView[] chips = new TextView[THEMES.length];
        for (int i = 0; i < THEMES.length; i++) {
            final int k = i;
            TextView c = new TextView(this);
            c.setText(THEMES[i]);
            c.setTextSize(11);
            c.setGravity(Gravity.CENTER);
            c.setTextColor(i == 2 ? 0xFF111111 : WHITE);
            c.setPadding(0, dp(14), 0, dp(14));
            chips[i] = c;
            c.setOnClickListener(v -> {
                p.edit().putInt("theme", k).apply();
                for (int j = 0; j < chips.length; j++) chips[j].setBackground(chipBg(j));
            });
            c.setBackground(chipBg(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            row.addView(c, lp);
        }
        add(parent, row);
    }

    GradientDrawable chipBg(int i) {
        GradientDrawable g = shape(TCOL[i], 14);
        g.setStroke(dp(2), p.getInt("theme", 0) == i ? WHITE : 0x00000000);
        return g;
    }

    void preview() {
        header("Live preview", "Change the look and type to test");
        LinearLayout c = card();
        c.addView(tv("Tap the box, then change the theme below. The keyboard updates instantly.", 13, SUB, false));
        EditText e = new EditText(this);
        e.setHint("Test typing here (En / සිං / swipe / emoji)");
        e.setTextColor(WHITE);
        e.setHintTextColor(SUB);
        e.setMinLines(4);
        e.setGravity(Gravity.TOP);
        c.addView(e);
        LinearLayout t = card();
        t.addView(tv("Theme", 16, WHITE, true));
        themeRow(t);
        seek(t, "Transparency", "alpha", 60, 255, 255);
        seek(t, "Key roundness", "radius", 0, 24, 8);
        LinearLayout tips = card();
        tips.addView(tv("Tips", 16, WHITE, true));
        tips.addView(tv("• Long-press a key to type its small hint character.\n"
                + "• සිං mode: type Singlish (mama → මම). Tap the Sinhala suggestion to finish the word.\n"
                + "• Swipe across the letters to type a word (English).\n"
                + "• 🛠 opens Translate, AI tools, voice and edit tools.\n"
                + "• Long-press the space bar to switch keyboards.", 13, SUB, false));
    }

    void settings() {
        header("Settings", "Every option works live");
        LinearLayout a = card();
        a.addView(tv("Look", 18, WHITE, true));
        seek(a, "Keyboard height", "height", 36, 64, 46);
        seek(a, "Key text size", "textsize", 14, 26, 18);
        seek(a, "Key transparency", "alpha", 60, 255, 255);
        seek(a, "Key roundness", "radius", 0, 24, 8);
        seek(a, "Key gap", "gap", 0, 6, 2);
        seek(a, "Press animation (ms, 0 = off)", "anim", 0, 200, 0);
        cycle(a, "Key shape: ", "shape", new String[]{"Rounded", "Pill", "Square"});
        cycle(a, "Font: ", "font", new String[]{"Default", "Serif", "Mono", "Condensed", "Casual"});
        swatches(a, "Key color", "keycolor");
        swatches(a, "Accent color", "accent");

        LinearLayout b = card();
        b.addView(tv("Typing", 18, WHITE, true));
        sw(b, "Vibration", "vibrate", true);
        sw(b, "Key sound", "sound", false);
        sw(b, "Key hints (small characters)", "hints", true);
        sw(b, "Number row", "numrow", false);
        sw(b, "Swipe typing", "swipe", true);
        sw(b, "Split keyboard", "split", false);
        sw(b, "Privacy mode (clear clipboard after paste, no history)", "privacy", false);
        cycle(b, "Compact / one-handed: ", "onehand", new String[]{"Off", "Left", "Right", "Center"});
        seek(b, "Compact width %", "compactw", 60, 95, 82);

        LinearLayout t = card();
        t.addView(tv("Toolbar", 18, WHITE, true));
        sw(t, "😀 Emoji", "tb_emoji", true);
        sw(t, "📋 Clipboard", "tb_clip", true);
        sw(t, "🛠 Tools", "tb_tools", true);
        sw(t, "𝒜 Stylish text", "tb_fancy", true);
        sw(t, "Aa Caps lock", "tb_caps", true);
        sw(t, "🎙 Voice", "tb_mic", true);

        LinearLayout ai = card();
        ai.addView(tv("AI & Translate", 18, WHITE, true));
        ai.addView(tv("AI tools (grammar, polite, shorter, smart reply) use your own Anthropic API key. "
                + "The key stays on this phone. Text is sent only when you tap a tool.", 12, SUB, false));
        final EditText key = new EditText(this);
        key.setHint("API key");
        key.setTextColor(WHITE);
        key.setHintTextColor(SUB);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(p.getString("apikey", ""));
        ai.addView(key);
        final EditText model = new EditText(this);
        model.setHint("Model");
        model.setTextColor(WHITE);
        model.setHintTextColor(SUB);
        model.setText(p.getString("model", "claude-haiku-5-5"));
        ai.addView(model);
        add(ai, btn("Save AI settings", () -> {
            p.edit().putString("apikey", key.getText().toString().trim())
                    .putString("model", model.getText().toString().trim()).apply();
            toast("Saved");
        }));

        LinearLayout d = card();
        d.addView(tv("Backup", 18, WHITE, true));
        add(d, ghost("Backup settings (copy)", () -> {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, ?> en : p.getAll().entrySet())
                if (!en.getKey().equals("apikey")) sb.append(en.getKey()).append('=').append(en.getValue()).append('\n');
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("lynko", sb));
            toast("Backup copied (API key not included)");
        }));
        add(d, ghost("Restore settings (from clipboard)", () -> {
            try {
                ClipData cd = ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip();
                String s = cd.getItemAt(0).getText().toString();
                SharedPreferences.Editor ed = p.edit();
                for (String line : s.split("\n")) {
                    String[] kv = line.split("=", 2);
                    if (kv.length < 2 || kv[0].equals("apikey")) continue;
                    if (kv[1].equals("true") || kv[1].equals("false")) ed.putBoolean(kv[0], Boolean.parseBoolean(kv[1]));
                    else if (kv[1].matches("-?\\d+")) ed.putInt(kv[0], Integer.parseInt(kv[1]));
                    else ed.putString(kv[0], kv[1]);
                }
                ed.apply();
                show(4);
                toast("Restored");
            } catch (Exception ex) { toast("Nothing to restore"); }
        }));
        add(d, ghost("Reset to defaults", () -> {
            SharedPreferences.Editor ed = p.edit();
            for (String k : new ArrayList<>(p.getAll().keySet()))
                if (!k.equals("apikey") && !k.equals("seen") && !k.equals("model")) ed.remove(k);
            ed.apply();
            show(4);
        }));
    }

    void about() {
        header("About LYNKO", "Version 2.0");
        LinearLayout c = card();
        c.addView(tv("Privacy", 18, WHITE, true));
        c.addView(tv("Everything you type is handled on your phone. The internet is used only when you tap "
                + "Translate (text goes to Google Translate) or an AI tool (text goes to Anthropic with your key). "
                + "Voice typing uses your phone's speech service. Clipboard history stays in memory and is never saved; "
                + "Privacy mode turns it off.", 14, SUB, false));
        LinearLayout s = card();
        s.addView(tv("Support", 18, WHITE, true));
        s.addView(tv("Open an issue on the project's GitHub page.", 14, SUB, false));
    }

    // ---------- controls ----------
    void seek(LinearLayout parent, final String label, final String key, final int min, int max, int def) {
        final TextView t = tv(label + ": " + p.getInt(key, def), 13, SUB, false);
        parent.addView(t);
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(p.getInt(key, def) - min);
        s.getProgressDrawable().setTint(ACC);
        s.getThumb().setTint(ACC2);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int v, boolean user) {
                if (user) { p.edit().putInt(key, v + min).apply(); t.setText(label + ": " + (v + min)); }
            }
            public void onStartTrackingTouch(SeekBar b) { }
            public void onStopTrackingTouch(SeekBar b) { }
        });
        parent.addView(s);
    }

    void sw(LinearLayout parent, String label, final String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(WHITE);
        s.setChecked(p.getBoolean(key, def));
        s.setOnCheckedChangeListener((b, on) -> p.edit().putBoolean(key, on).apply());
        s.setPadding(0, dp(8), 0, dp(8));
        parent.addView(s);
    }

    void cycle(LinearLayout parent, final String label, final String key, final String[] names) {
        final Button b = ghost(label + names[p.getInt(key, 0) % names.length], () -> { });
        b.setOnClickListener(v -> {
            int n = (p.getInt(key, 0) + 1) % names.length;
            p.edit().putInt(key, n).apply();
            b.setText(label + names[n]);
        });
        add(parent, b);
    }

    void swatches(LinearLayout parent, String label, final String key) {
        parent.addView(tv(label + " (A = theme default)", 13, SUB, false));
        LinearLayout row = new LinearLayout(this);
        int[] cols = {0, 0x7C4DFF, 0xEC4899, 0x10B981, 0x3B82F6, 0xF59E0B, 0xEF4444, 0x111827, 0xFFFFFF};
        for (final int cc : cols) {
            TextView s = new TextView(this);
            s.setText(cc == 0 ? "A" : "");
            s.setGravity(Gravity.CENTER);
            s.setTextColor(WHITE);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(cc == 0 ? CARD2 : (0xFF000000 | cc));
            g.setStroke(dp(2), p.getInt(key, 0) == cc ? WHITE : 0x33FFFFFF);
            s.setBackground(g);
            s.setOnClickListener(v -> { p.edit().putInt(key, cc).apply(); show(4); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(s, lp);
        }
        add(parent, row);
    }
}
