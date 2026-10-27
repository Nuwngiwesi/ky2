package com.lynko.keyboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class LynkoIME extends InputMethodService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    SharedPreferences p;
    Handler hd = new Handler(Looper.getMainLooper());
    boolean shift, caps, sin, listening;
    int sym = 0;      // 0 letters, 1 symbols page 1, 2 symbols page 2
    int panel = 0;    // 0 keys, 1 emoji, 2 clipboard, 3 tools
    int fancy = 0;    // stylish text style
    int kal = 255;    // key alpha
    int[] curT;
    StringBuilder buf = new StringBuilder();
    LinearLayout strip;
    TextView status;
    SpeechRecognizer sr;
    List<String> swipeAlts = new ArrayList<>();
    String lastSwipe = "";
    static ArrayList<String> hist = new ArrayList<>();
    static Bitmap bgBmp;
    Typeface tf;
    HeartView heart;
    boolean late;   // keys inside scrolling panels fire on release
    ArrayList<TextView> letterTvs = new ArrayList<>();
    ArrayList<String> letterBase = new ArrayList<>();
    TextView shiftTv;
    int stripKind = -1;

    static final String LET = "qwertyuiopasdfghjklzxcvbnm";
    static final String[] EN = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    static final String[] EH = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "@", "#", "$", "_", "&", "-", "+", "(", ")", "*", "\"", "'", ":", ";", "!", "?"};
    static final String[] SH = {"ඤ", "ඬ", "ඒ", "ඍ", "ඨ", "ඹ", "ඌ", "ඊ", "ඕ", "ඵ",
            "ආ", "ශ", "ධ", "ෆ", "ඝ", "ඃ", "ඣ", "ඛ", "ළ", "ඥ", "ං", "ඡ", "ෘ", "භ", "ණ", "ඞ"};
    static final String[] SYM1 = {"1234567890", "@#$%&-+()/", "*\"':;!?"};
    static final String[] SYM2 = {"~`|^=_[]{}", "<>\\€£¥•°©®", "™§¶…×÷±¿¡"};

    ClipboardManager.OnPrimaryClipChangedListener clipL = () -> {
        try {
            if (p.getBoolean("privacy", false)) return;
            ClipData d = ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence c = d.getItemAt(0).coerceToText(this);
            if (c == null || c.length() == 0) return;
            String s = c.toString();
            hist.remove(s);
            hist.add(0, s);
            while (hist.size() > 15) hist.remove(hist.size() - 1);
        } catch (Exception ignored) { }
    };

    @Override public void onCreate() {
        super.onCreate();
        p = getSharedPreferences("lynko", MODE_PRIVATE);
        p.registerOnSharedPreferenceChangeListener(this);
        try { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).addPrimaryClipChangedListener(clipL); }
        catch (Exception ignored) { }
    }

    @Override public void onDestroy() {
        p.unregisterOnSharedPreferenceChangeListener(this);
        try { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).removePrimaryClipChangedListener(clipL); }
        catch (Exception ignored) { }
        if (sr != null) sr.destroy();
        super.onDestroy();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences s, String k) { rebuild(); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); rebuild(); }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public View onCreateInputView() {
        try { return build(); } catch (Throwable t) { return errView(t); }
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        buf.setLength(0);
        swipeAlts.clear();
        int cls = info == null ? 0 : (info.inputType & InputType.TYPE_MASK_CLASS);
        int ns = (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) ? 1 : 0;
        boolean need = ns != sym || panel != 0 || shift;
        sym = ns;
        panel = 0;
        shift = false;
        if (need) rebuild(); else refreshStrip(); // rebuild only when something changed (fast open)
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        buf.setLength(0);
        if (sr != null && listening) { sr.cancel(); listening = false; }
    }

    @Override public void onUpdateSelection(int os, int oe, int ns, int ne, int cs, int ce) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce);
        if (buf.length() > 0 && (cs < 0 || ns != ne)) { buf.setLength(0); refreshStrip(); }
    }

    // ---------- helpers ----------
    void rebuild() {
        try { setInputView(build()); }
        catch (Throwable t) { try { setInputView(errView(t)); } catch (Throwable ignored) { } }
    }

    View errView(Throwable t) {
        TextView e = new TextView(this);
        e.setTextColor(0xFFFFFFFF);
        e.setBackgroundColor(0xFF7F1D1D);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        e.setTextSize(11);
        e.setText("LYNKO error: " + t);
        return e;
    }
    int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
    int a(int c, int al) { return (c & 0xFFFFFF) | (al << 24); }
    boolean pb(String k) { return p.getBoolean(k, true); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    void setPanel(int n) { panel = n; rebuild(); }
    String cased(String b) { return (shift || caps) ? b.toUpperCase() : b; }

    // shift / caps: only change the letters, no full rebuild (fast)
    void refreshCase() {
        for (int i = 0; i < letterTvs.size(); i++) letterTvs.get(i).setText(cased(letterBase.get(i)));
        if (shiftTv != null) shiftTv.setText(caps ? "⇪" : shift ? "⬆" : "⇧");
    }

    int[] theme() { // bg, key, text, accent
        int t = p.getInt("theme", 0);
        if (t == 0) {
            boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            t = night ? 1 : 2;
        }
        int[] r;
        switch (t) {
            case 2: r = new int[]{0xE8EAF0, 0xFFFFFF, 0x111111, 0x5B5BD6}; break;
            case 6: r = new int[]{0x2A0A22, 0x5C1A45, 0xFFE4F0, 0xFF3D7F}; break;
            case 3: r = new int[]{0x2A1B4D, 0x4B3380, 0xFFFFFF, 0xFF6FD8}; break;
            case 4: r = new int[]{0x000000, 0x0B1F1A, 0x00FFC8, 0xFF2E93}; break;
            case 5: r = new int[]{0x101018, 0x20202C, 0xFFFFFF, 0xEC4899}; break;
            default: r = new int[]{0x1E1E2E, 0x313244, 0xFFFFFF, 0x7C4DFF};
        }
        int kc = p.getInt("keycolor", 0);
        int ac = p.getInt("accent", 0);
        if (kc != 0) r[1] = kc;
        if (ac != 0) r[3] = ac;
        return r;
    }

    Bitmap logoBmp() {
        if (bgBmp == null) {
            int id = getResources().getIdentifier("logo", "drawable", getPackageName());
            if (id == 0) return null;
            try {
                bgBmp = Engine.decode(getResources(), id, 800);
            } catch (Throwable ignored) { }
        }
        return bgBmp;
    }

    Drawable bg(int[] t, int al) {
        if (p.getInt("theme", 0) == 5) {
            Bitmap b = logoBmp();
            if (b != null) {
                return new LayerDrawable(new Drawable[]{new BitmapDrawable(getResources(), b), new ColorDrawable(0xB0000000)});
            }
        }
        if (p.getInt("theme", 0) == 6) {
            return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{a(0x4A0F35, al), a(0x1E0618, al)});
        }
        return new ColorDrawable(a(t[0], al));
    }

    Typeface font() {
        switch (p.getInt("font", 0)) {
            case 1: return Typeface.create(Typeface.SERIF, Typeface.BOLD);
            case 2: return Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            case 3: return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case 4: return Typeface.create("casual", Typeface.BOLD);
            default: return Typeface.DEFAULT_BOLD;
        }
    }

    int radiusPx() {
        int s = p.getInt("shape", 0);
        return s == 1 ? dp(40) : s == 2 ? 0 : dp(p.getInt("radius", 8));
    }

    // ---------- build ----------
    View build() {
        int[] t = theme();
        curT = t;
        int th = p.getInt("theme", 0);
        int al = p.getInt("alpha", 255);
        kal = th == 5 ? Math.min(al, 150) : al;
        status = null;
        strip = null;
        stripKind = -1;
        shiftTv = null;
        letterTvs.clear();
        letterBase.clear();
        tf = font();
        late = false;
        int h = dp(p.getInt("height", 46));

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackground(bg(t, al));
        r.setPadding(dp(4), dp(4), dp(4), dp(4));

        toolbar(r, t, h * 4 / 5);
        if (panel == 0) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            strip = new LinearLayout(this);
            strip.setGravity(Gravity.CENTER_VERTICAL);
            hs.addView(strip);
            r.addView(hs, new LinearLayout.LayoutParams(-1, -2));
            refreshStrip();
        }
        if (panel == 1) { late = true; emojiPanel(r, t, h); late = false; }
        else if (panel == 2) { late = true; clipPanel(r, t, h); late = false; }
        else if (panel == 3) { late = true; toolsPanel(r, t, h); late = false; }
        else keys(r, t, h);
        bottom(r, t, h);

        int oh = p.getInt("onehand", 0); // 0 off, 1 left, 2 right, 3 center
        FrameLayout f = new FrameLayout(this);
        if (oh == 0) {
            f.addView(r, new FrameLayout.LayoutParams(-1, -2));
        } else {
            int w = (int) (getResources().getDisplayMetrics().widthPixels * p.getInt("compactw", 82) / 100f);
            int g = oh == 1 ? Gravity.START : oh == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL;
            f.addView(r, new FrameLayout.LayoutParams(w, -2, g));
        }
        heart = new HeartView();   // transparent layer on top: hearts fly up when you press keys
        f.addView(heart, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    void toolbar(LinearLayout r, int[] t, int h) {
        LinearLayout tb = row(r);
        key(tb, sin ? "සිං" : "En", 1, h, t, true, this::toggleLang);
        if (pb("tb_emoji")) key(tb, "😀", 1, h, t, panel == 1, () -> setPanel(panel == 1 ? 0 : 1));
        if (pb("tb_clip")) key(tb, "📋", 1, h, t, panel == 2, () -> setPanel(panel == 2 ? 0 : 2));
        if (pb("tb_tools")) key(tb, "🛠", 1, h, t, panel == 3, () -> setPanel(panel == 3 ? 0 : 3));
        if (pb("tb_fancy")) key(tb, fancy == 0 ? "𝒜" : Engine.fancy("A", fancy), 1, h, t, fancy > 0,
                () -> { fancy = (fancy + 1) % 6; rebuild(); });
        if (pb("tb_caps")) key(tb, "Aa", 1, h, t, caps, () -> { caps = !caps; shift = false; rebuild(); });
        if (pb("tb_mic")) key(tb, listening ? "⏺" : "🎙", 1, h, t, listening, this::voice);
        key(tb, "⌄", 1, h, t, false, () -> requestHideSelf(0));
    }

    void keys(LinearLayout r, int[] t, int h) {
        if (sym > 0) {
            String[] rows = sym == 1 ? SYM1 : SYM2;
            fillPlain(row(r), rows[0], h, t);
            fillPlain(row(r), rows[1], h, t);
            LinearLayout l = row(r);
            key(l, sym == 1 ? "=\\<" : "?123", 1.5f, h, t, true, () -> { sym = sym == 1 ? 2 : 1; rebuild(); });
            fillPlain(l, rows[2], h, t);
            delKey(l, "⌫", 1.5f, h, t);
            return;
        }
        if (p.getBoolean("numrow", false)) fillPlain(row(r), "1234567890", h, t);
        SwipeArea area = new SwipeArea();
        r.addView(area, new LinearLayout.LayoutParams(-1, -2));
        fillK(row(area), EN[0], h, t);
        LinearLayout l2 = row(area);
        spacer(l2, 0.5f);
        fillK(l2, EN[1], h, t);
        spacer(l2, 0.5f);
        LinearLayout l3 = row(area);
        View sk = key(l3, caps ? "⇪" : shift ? "⬆" : "⇧", 1.5f, h, t, true, () -> {
            if (caps) caps = false; else shift = !shift;
            refreshCase();
        });
        shiftTv = (TextView) ((FrameLayout) sk).getChildAt(0);
        fillK(l3, EN[2], h, t);
        delKey(l3, "⌫", 1.5f, h, t);
    }

    void bottom(LinearLayout r, int[] t, int h) {
        LinearLayout b = row(r);
        key(b, (panel != 0 || sym > 0) ? "ABC" : "123", 1.5f, h, t, true, () -> {
            if (panel != 0) panel = 0; else if (sym > 0) sym = 0; else sym = 1;
            rebuild();
        });
        key(b, "🌐", 1.2f, h, t, false, this::nextIme);
        key(b, ",", 1, h, t, false, () -> typeText(","));
        key(b, (sin ? "LYNKO · සිං" : "LYNKO") + (p.getInt("theme", 0) == 6 ? " ❤" : ""), 4, h, t, false,
                () -> typeText(" "), null, null, this::picker);
        key(b, ".", 1, h, t, false, () -> typeText("."));
        key(b, "⏎", 1.5f, h, t, true, this::enter);
    }

    void emojiPanel(LinearLayout r, int[] t, int h) {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        r.addView(sv, new LinearLayout.LayoutParams(-1, h * 4));
        List<String> em = Engine.emojis();
        for (int i = 0; i < em.size(); i += 8) {
            LinearLayout l = row(col);
            for (int j = 0; j < 8; j++) {
                if (i + j < em.size()) {
                    final String e = em.get(i + j);
                    key(l, e, 1, h, t, false, () -> typeText(e));
                } else spacer(l, 1);
            }
        }
    }

    void clipPanel(LinearLayout r, int[] t, int h) {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        r.addView(sv, new LinearLayout.LayoutParams(-1, h * 4));
        if (hist.isEmpty()) {
            TextView m = new TextView(this);
            m.setText("Copy some text and it shows up here.");
            m.setTextColor(a(t[2], 200));
            m.setPadding(dp(8), dp(12), dp(8), dp(12));
            col.addView(m);
        }
        for (String s : new ArrayList<>(hist)) {
            final String item = s;
            wide(row(col), item.replace('\n', ' '), h, t, () -> typeText(item));
        }
        wide(row(col), "🗑 Clear all", h, t, () -> { clearClip(); setPanel(2); });
    }

    void toolsPanel(LinearLayout r, int[] t, int h) {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        r.addView(sv, new LinearLayout.LayoutParams(-1, h * 4));
        status = new TextView(this);
        status.setTextColor(a(t[2], 200));
        status.setTextSize(13);
        status.setPadding(dp(6), dp(2), dp(6), dp(4));
        status.setText(p.getString("apikey", "").isEmpty()
                ? "AI tools need an API key (LYNKO app → Settings). Translate works online."
                : "Select text or type, then tap a tool.");
        col.addView(status);
        String[] names = {"🌐 Translate", "✍ Fix grammar", "🤝 Polite", "✂ Shorter", "💬 Smart reply", "🎙 Voice",
                "⚙ Settings", "Split: " + (p.getBoolean("split", false) ? "ON" : "OFF"),
                "Swipe: " + (p.getBoolean("swipe", true) ? "ON" : "OFF"),
                "✂ Cut", "⧉ Copy", "📋 Paste", "Select all", "◀ Cursor", "Cursor ▶", "🗑 Clipboard"};
        Runnable[] acts = {
                this::translate,
                () -> ai("Fix spelling and grammar"),
                () -> ai("Rewrite politely and professionally"),
                () -> ai("Make it shorter and clearer"),
                () -> ai("smart"),
                this::voice,
                () -> openApp(4, false),
                () -> p.edit().putBoolean("split", !p.getBoolean("split", false)).apply(),
                () -> p.edit().putBoolean("swipe", !p.getBoolean("swipe", true)).apply(),
                () -> ctx(android.R.id.cut),
                () -> ctx(android.R.id.copy),
                () -> { ctx(android.R.id.paste); if (p.getBoolean("privacy", false)) clearClip(); },
                () -> ctx(android.R.id.selectAll),
                () -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT),
                () -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT),
                this::clearClip};
        for (int i = 0; i < names.length; i += 3) {
            LinearLayout l = row(col);
            for (int j = 0; j < 3; j++) {
                if (i + j < names.length) {
                    View k = key(l, names[i + j], 1, h, t, false, acts[i + j]);
                    TextView tv = (TextView) ((FrameLayout) k).getChildAt(0);
                    tv.setTextSize(13);
                } else spacer(l, 1);
            }
        }
    }

    LinearLayout row(LinearLayout parent) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(l, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    void spacer(LinearLayout l, float w) { l.addView(new View(this), new LinearLayout.LayoutParams(0, 1, w)); }

    void fillPlain(LinearLayout l, String s, int h, int[] t) {
        for (char c : s.toCharArray()) {
            final String x = String.valueOf(c);
            key(l, x, 1, h, t, false, () -> typeText(x));
        }
    }

    void fillK(LinearLayout l, String s, int h, int[] t) {
        boolean split = p.getBoolean("split", false);
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (split && i == n / 2) spacer(l, 1.2f);
            final String base = String.valueOf(c);
            final String hint = sin ? SH[LET.indexOf(c)] : EH[LET.indexOf(c)];
            View kv = key(l, cased(base), 1, h, t, false, () -> typeLetter(cased(base)), hint, base, () -> typeText(hint));
            letterTvs.add((TextView) ((FrameLayout) kv).getChildAt(0));
            letterBase.add(base);
        }
    }

    View key(LinearLayout l, String label, float w, int h, int[] t, boolean accent, Runnable act) {
        return key(l, label, w, h, t, accent, act, null, null, null);
    }

    View key(LinearLayout l, String label, float w, int h, int[] t, boolean accent, Runnable act,
             String hint, String tag, Runnable longAct) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(a(accent ? t[3] : t[1], kal));
        g.setCornerRadius(radiusPx());
        int m = dp(p.getInt("gap", 2));
        FrameLayout f = new FrameLayout(this);
        // the gap between keys is part of the key (inset), so there are no dead zones between keys
        f.setBackground(new InsetDrawable(g, m));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(accent ? 0xFFFFFFFF : a(t[2], 255));
        tv.setTextSize(p.getInt("textsize", 18));
        tv.setTypeface(tf != null ? tf : font());
        f.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        if (hint != null && p.getBoolean("hints", true)) {
            TextView ht = new TextView(this);
            ht.setText(hint);
            ht.setTextSize(10);
            ht.setTextColor(a(t[2], 160));
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            hp.setMargins(0, dp(1) + m, dp(4) + m, 0);
            f.addView(ht, hp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h + 2 * m, w);
        bindTap(f, act, longAct, late);
        if (tag != null) f.setTag(tag);
        l.addView(f, lp);
        return f;
    }

    /** Key press: fires when you lift the finger, even if it slipped a little outside the key
     *  (a plain click listener drops those taps, which is why some keys "didn't press"). */
    void bindTap(final View f, final Runnable act, final Runnable longAct, final boolean lateFeedback) {
        final boolean[] longFired = new boolean[1];
        final int d = p.getInt("anim", 0);
        final Runnable lr = () -> {
            if (longAct != null) { longFired[0] = true; feedback(f); longAct.run(); }
        };
        f.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    longFired[0] = false;
                    v.setAlpha(0.7f);
                    if (d > 0) v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(d).start();
                    if (!lateFeedback) feedback(v);
                    if (longAct != null) hd.postDelayed(lr, 450);
                    return true;
                case MotionEvent.ACTION_UP:
                    hd.removeCallbacks(lr);
                    v.setAlpha(1f);
                    if (d > 0) v.animate().scaleX(1f).scaleY(1f).setDuration(d).start();
                    if (!longFired[0]) {
                        if (lateFeedback) feedback(v);
                        act.run();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    hd.removeCallbacks(lr);
                    v.setAlpha(1f);
                    if (d > 0) v.animate().scaleX(1f).scaleY(1f).setDuration(d).start();
                    return true;
                default:
                    return true;
            }
        });
    }

    void wide(LinearLayout l, String label, int h, int[] t, Runnable act) {
        View v = key(l, label, 1, h, t, false, act);
        TextView tv = (TextView) ((FrameLayout) v).getChildAt(0);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(10), 0, dp(10), 0);
    }

    View delKey(LinearLayout l, String label, float w, int h, int[] t) {
        View v = key(l, label, w, h, t, true, this::del);
        final Runnable rep = new Runnable() {
            @Override public void run() { del(); hd.postDelayed(this, 55); }
        };
        v.setOnTouchListener((x, e) -> {
            int d = p.getInt("anim", 0);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    feedback(x);
                    del();
                    x.animate().scaleX(0.9f).scaleY(0.9f).setDuration(d).start();
                    hd.postDelayed(rep, 380);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    hd.removeCallbacks(rep);
                    x.animate().scaleX(1f).scaleY(1f).setDuration(d).start();
                    return true;
                default:
                    return true;
            }
        });
        return v;
    }

    // ---------- suggestion strip ----------
    void chip(String label, Runnable act) {
        if (strip == null) return;
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(20);
        tv.setTextColor(a(curT[2], 255));
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        tv.setOnClickListener(v -> { feedback(v); act.run(); });
        strip.addView(tv);
    }

    void refreshStrip() {
        if (strip == null) return;
        int kind = buf.length() > 0 ? 1 : !swipeAlts.isEmpty() ? 2 : 0;
        if (kind == 1) {
            if (stripKind != 1) { // create the two candidate chips once, then only update their text
                strip.removeAllViews();
                chip("", () -> { endCompose(); commit(" "); refreshStrip(); });
                chip("", () -> {
                    InputConnection ic = getCurrentInputConnection();
                    String raw = buf.toString();
                    buf.setLength(0);
                    if (ic != null) { ic.setComposingText(raw, 1); ic.finishComposingText(); ic.commitText(" ", 1); }
                    refreshStrip();
                });
                stripKind = 1;
            }
            String raw = buf.toString();
            ((TextView) strip.getChildAt(0)).setText(Engine.si(raw));
            ((TextView) strip.getChildAt(1)).setText(raw);
            return;
        }
        if (kind == 0 && stripKind == 0) return; // emoji row already shown: nothing to do per keypress
        strip.removeAllViews();
        stripKind = kind;
        if (kind == 2) {
            for (String w : swipeAlts) {
                final String c = w;
                chip(c, () -> replaceSwipe(c));
            }
        } else {
            List<String> em = p.getInt("theme", 0) == 6 ? Engine.romEmojis() : Engine.emojis();
            for (int i = 0; i < 16 && i < em.size(); i++) {
                final String e = em.get(i);
                chip(e, () -> typeText(e));
            }
        }
    }

    // ---------- typing ----------
    void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    void endCompose() {
        if (buf.length() > 0) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            buf.setLength(0);
        }
    }

    void typeText(String s) {
        endCompose();
        swipeAlts.clear();
        commit(s);
        refreshStrip();
    }

    void typeLetter(String lab) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        swipeAlts.clear();
        if (sin) {
            buf.append(lab);
            ic.setComposingText(Engine.si(buf.toString()), 1);
        } else {
            ic.commitText(Engine.fancy(lab, fancy), 1);
        }
        if (shift) { shift = false; refreshCase(); }
        refreshStrip();
    }

    void del() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        swipeAlts.clear();
        if (buf.length() > 0) {
            buf.setLength(buf.length() - 1);
            if (buf.length() == 0) { ic.setComposingText("", 1); ic.finishComposingText(); }
            else ic.setComposingText(Engine.si(buf.toString()), 1);
            refreshStrip();
            return;
        }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) { ic.commitText("", 1); return; }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    void enter() {
        endCompose();
        EditorInfo ei = getCurrentInputEditorInfo();
        int act = ei == null ? 0 : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && act != EditorInfo.IME_ACTION_NONE && act != EditorInfo.IME_ACTION_UNSPECIFIED)
            sendDefaultEditorAction(true);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    void toggleLang() { endCompose(); sin = !sin; rebuild(); }

    void ctx(int id) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.performContextMenuAction(id);
    }

    void clearClip() {
        hist.clear();
        try { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).clearPrimaryClip(); } catch (Exception ignored) { }
        toast("Clipboard cleared");
    }

    void nextIme() {
        if (!switchToNextInputMethod(false)) picker();
    }

    void picker() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
    }

    boolean heartsOn() {
        return p.getBoolean("hearts", true) && (p.getInt("theme", 0) == 6 || p.getBoolean("heartsall", false));
    }

    void feedback(View v) {
        if (heart != null && heartsOn()) heart.burstAt(v);
        if (p.getBoolean("vibrate", true))
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        if (p.getBoolean("sound", false))
            ((AudioManager) getSystemService(AUDIO_SERVICE)).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
    }

    void openApp(int screen, boolean mic) {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("screen", screen).putExtra("mic", mic);
        startActivity(i);
    }

    // ---------- tools: AI / translate / voice ----------
    void setStatus(String s) { if (status != null) status.setText(s); else toast(s); }

    String[] grab() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return null;
        CharSequence s = ic.getSelectedText(0);
        if (s != null && s.length() > 0) return new String[]{s.toString(), "sel"};
        CharSequence b = ic.getTextBeforeCursor(500, 0);
        if (b == null) return null;
        String t = b.toString();
        int nl = t.lastIndexOf('\n');
        if (nl >= 0) t = t.substring(nl + 1);
        if (t.trim().isEmpty()) return null;
        return new String[]{t, "line"};
    }

    void replace(String[] g, String res) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (g[1].equals("line")) ic.deleteSurroundingText(g[0].length(), 0);
        ic.commitText(res, 1);
    }

    void translate() {
        endCompose();
        final String[] g = grab();
        if (g == null) { setStatus("Type or select some text first"); return; }
        final String tl = Engine.hasSinhala(g[0]) ? "en" : "si";
        setStatus("Translating…");
        new Thread(() -> {
            String res = null, err = null;
            try { res = Engine.translate(g[0], tl); } catch (Exception e) { err = String.valueOf(e.getMessage()); }
            final String fr = res, fe = err;
            hd.post(() -> {
                if (fr == null) { setStatus("Error: " + fe); return; }
                replace(g, fr);
                setStatus("Translated ✓");
            });
        }).start();
    }

    void ai(final String task) {
        final String key = p.getString("apikey", "");
        if (key.isEmpty()) { setStatus("Add your API key: LYNKO app → Settings"); return; }
        endCompose();
        final boolean smart = task.equals("smart");
        final String[] g = grab();
        String src;
        if (smart) src = !hist.isEmpty() ? hist.get(0) : (g == null ? null : g[0]);
        else src = g == null ? null : g[0];
        if (src == null) { setStatus(smart ? "Copy a message first" : "Type or select some text first"); return; }
        final String fs = src;
        final String model = p.getString("model", "claude-haiku-5-5");
        setStatus("Working…");
        new Thread(() -> {
            String res = null, err = null;
            try { res = Engine.ai(key, model, task, fs); } catch (Exception e) { err = String.valueOf(e.getMessage()); }
            final String fr = res, fe = err;
            hd.post(() -> {
                if (fr == null) { setStatus("Error: " + fe); return; }
                if (smart || g == null) commit(fr); else replace(g, fr);
                setStatus("Done ✓");
            });
        }).start();
    }

    void voice() {
        if (listening) { if (sr != null) sr.stopListening(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Allow the microphone in the LYNKO app");
            openApp(1, true);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("No speech service on this phone"); return; }
        endCompose();
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) { }
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float v) { }
                public void onBufferReceived(byte[] b) { }
                public void onEndOfSpeech() { }
                public void onPartialResults(Bundle b) { }
                public void onEvent(int type, Bundle b) { }
                public void onError(int e) { listening = false; rebuild(); toast("Voice error " + e); }
                public void onResults(Bundle b) {
                    listening = false;
                    ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty()) commit(r.get(0) + " ");
                    rebuild();
                }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sin ? "si-LK" : "en-US");
        listening = true;
        sr.startListening(i);
        rebuild();
    }

    // ---------- swipe typing ----------
    class SwipeArea extends LinearLayout {
        boolean swiping;
        float sx, sy;
        final ArrayList<float[]> pts = new ArrayList<>();

        SwipeArea() { super(LynkoIME.this); setOrientation(VERTICAL); }

        boolean on() { return p.getBoolean("swipe", true) && !sin && sym == 0 && panel == 0; }

        boolean multi;   // a second finger touched: that is fast typing, never a swipe

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!on()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getX(); sy = e.getY();
                    pts.clear();
                    pts.add(new float[]{sx, sy});
                    swiping = false;
                    multi = false;
                    return false;
                case MotionEvent.ACTION_POINTER_DOWN:
                    multi = true;
                    return swiping;
                case MotionEvent.ACTION_MOVE:
                    if (multi || e.getPointerCount() > 1) return swiping;
                    pts.add(new float[]{e.getX(), e.getY()});
                    if (!swiping && Math.hypot(e.getX() - sx, e.getY() - sy) > dp(30)) swiping = true;
                    return swiping;
                default:
                    return swiping;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!swiping) return super.onTouchEvent(e);
            int ac = e.getActionMasked();
            if (ac == MotionEvent.ACTION_MOVE) pts.add(new float[]{e.getX(), e.getY()});
            else if (ac == MotionEvent.ACTION_UP) { swiping = false; swipeDone(new ArrayList<>(pts), this); }
            else if (ac == MotionEvent.ACTION_CANCEL) swiping = false;
            return true;
        }
    }

    /** Transparent layer: little hearts float up from the pressed key. Touches pass straight through. */
    class HeartView extends View {
        final ArrayList<float[]> hs = new ArrayList<>(); // x, y, vx, vy, size, age, colorIdx, sway
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final java.util.Random rnd = new java.util.Random();
        final int[] cols = {0xFFFF3D7F, 0xFFFF6FA5, 0xFFFF8FB8, 0xFFE11D48, 0xFFFFB3D1, 0xFFFF5C93};
        long last;

        HeartView() {
            super(LynkoIME.this);
            setWillNotDraw(false);
            path.moveTo(0f, 0.9f);
            path.cubicTo(-1.3f, -0.1f, -0.7f, -1.0f, 0f, -0.35f);
            path.cubicTo(0.7f, -1.0f, 1.3f, -0.1f, 0f, 0.9f);
            path.close();
        }

        void burstAt(View k) {
            int[] a = new int[2], b = new int[2];
            k.getLocationInWindow(a);
            getLocationInWindow(b);
            burst(a[0] - b[0] + k.getWidth() / 2f, a[1] - b[1] + k.getHeight() * 0.35f);
        }

        void burst(float x, float y) {
            for (int i = 0; i < 3 && hs.size() < 45; i++) {
                hs.add(new float[]{x + (rnd.nextFloat() - 0.5f) * dp(22), y,
                        (rnd.nextFloat() - 0.5f) * dp(46), -(dp(80) + rnd.nextFloat() * dp(110)),
                        dp(7) + rnd.nextFloat() * dp(8), 0f, rnd.nextInt(cols.length), rnd.nextFloat() * 6.28f});
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 16f : Math.min(now - last, 50);
            last = now;
            final float life = 1100f;
            for (int i = hs.size() - 1; i >= 0; i--) {
                float[] q = hs.get(i);
                q[5] += dt;
                if (q[5] >= life) { hs.remove(i); continue; }
                q[0] += q[2] * dt / 1000f;
                q[1] += q[3] * dt / 1000f;
                float left = (life - q[5]) / 450f;
                int al = (int) (255 * Math.min(1f, left));
                float sway = (float) Math.sin(q[5] / 170f + q[7]) * dp(5);
                pt.setColor(cols[(int) q[6]]);
                pt.setAlpha(al);
                c.save();
                c.translate(q[0] + sway, q[1]);
                c.scale(q[4], q[4]);
                c.drawPath(path, pt);
                c.restore();
            }
            if (hs.isEmpty()) last = 0; else postInvalidateOnAnimation();
        }
    }

    String keyAt(ViewGroup area, float x, float y) {
        for (int i = 0; i < area.getChildCount(); i++) {
            View rw = area.getChildAt(i);
            if (y >= rw.getTop() && y <= rw.getBottom() && rw instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) rw;
                float yy = y - rw.getTop();
                for (int j = 0; j < g.getChildCount(); j++) {
                    View k = g.getChildAt(j);
                    if (x >= k.getLeft() && x <= k.getRight() && yy >= k.getTop() && yy <= k.getBottom())
                        return k.getTag() == null ? null : k.getTag().toString();
                }
            }
        }
        return null;
    }

    void swipeDone(ArrayList<float[]> pts, ViewGroup area) {
        StringBuilder path = new StringBuilder();
        for (float[] q : pts) {
            String c = keyAt(area, q[0], q[1]);
            if (c != null && (path.length() == 0 || path.charAt(path.length() - 1) != c.charAt(0))) path.append(c);
        }
        if (path.length() < 2) return;
        List<String> w = Engine.swipe(path.toString());
        if (w.isEmpty()) { toast("No word found"); return; }
        endCompose();
        boolean up = shift || caps;
        swipeAlts = new ArrayList<>();
        for (String s : w) swipeAlts.add(up ? Engine.cap(s) : s);
        lastSwipe = swipeAlts.get(0);
        commit(lastSwipe + " ");
        if (shift) { shift = false; refreshCase(); }
        stripKind = -1;
        refreshStrip();
    }

    void replaceSwipe(String w) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.deleteSurroundingText(lastSwipe.length() + 1, 0);
        ic.commitText(w + " ", 1);
        lastSwipe = w;
    }
}

/** Sinhala (Singlish) transliteration, swipe word matching, stylish text, translate + AI calls. */
class Engine {
    static final String[][] CONS = {
        {"kh","ඛ"},{"gh","ඝ"},{"ch","ච"},{"Ch","ඡ"},{"jh","ඣ"},{"nj","ඤ"},{"th","ථ"},{"Th","ඨ"},
        {"dh","ධ"},{"Dh","ඪ"},{"ph","ඵ"},{"bh","භ"},{"sh","ශ"},{"Sh","ෂ"},{"ng","ඟ"},{"nG","ඟ"},
        {"nD","ඬ"},{"nB","ඹ"},{"Ng","ඞ"},{"gn","ඥ"},
        {"k","ක"},{"g","ග"},{"c","ච"},{"j","ජ"},{"t","ත"},{"T","ට"},{"d","ද"},{"D","ඩ"},{"N","ණ"},
        {"n","න"},{"p","ප"},{"b","බ"},{"m","ම"},{"y","ය"},{"r","ර"},{"l","ල"},{"L","ළ"},{"w","ව"},
        {"v","ව"},{"s","ස"},{"S","ෂ"},{"h","හ"},{"f","ෆ"},{"G","ඝ"},{"K","ඛ"},{"C","ඡ"},{"J","ඣ"},
        {"P","ඵ"},{"B","භ"}};
    static final String[][] VOW = {
        {"aae","ඈ","ෑ"},{"aa","ආ","ා"},{"ae","ඇ","ැ"},{"ai","ඓ","ෛ"},{"au","ඖ","ෞ"},{"ii","ඊ","ී"},
        {"ee","ඒ","ේ"},{"oo","ඕ","ෝ"},{"uu","ඌ","ූ"},
        {"a","අ",""},{"A","ඇ","ැ"},{"i","ඉ","ි"},{"I","ඊ","ී"},{"u","උ","ු"},{"U","ඌ","ූ"},
        {"e","එ","ෙ"},{"E","ඒ","ේ"},{"o","ඔ","ො"},{"O","ඕ","ෝ"}};
    static final String[][] SIGN = {{"x","ං"},{"H","ඃ"}};

    static String[] m(String[][] tab, String s, int i) {
        for (String[] e : tab) if (s.startsWith(e[0], i)) return e;
        return null;
    }

    static String lowerAt(String s, int i) {
        return s.substring(0, i) + Character.toLowerCase(s.charAt(i)) + s.substring(i + 1);
    }

    /** Singlish (Latin letters) -> Sinhala script */
    static String si(String s) {
        StringBuilder o = new StringBuilder();
        boolean pend = false; // a consonant is waiting for its vowel
        int i = 0;
        while (i < s.length()) {
            boolean up = Character.isUpperCase(s.charAt(i));
            String[] e = m(CONS, s, i);
            if (e == null && up) e = m(CONS, lowerAt(s, i), i);
            if (e != null) {
                if (pend) {
                    o.append('\u0DCA');
                    if (e[0].equals("r") || e[0].equals("y")) o.append('\u200D');
                }
                o.append(e[1]);
                pend = true;
                i += e[0].length();
                continue;
            }
            String[] v = m(VOW, s, i);
            if (v == null && up) v = m(VOW, lowerAt(s, i), i);
            if (v != null) {
                o.append(pend ? v[2] : v[1]);
                pend = false;
                i += v[0].length();
                continue;
            }
            String[] g = m(SIGN, s, i);
            if (g != null) {
                pend = false;
                o.append(g[1]);
                i += g[0].length();
                continue;
            }
            if (pend) { o.append('\u0DCA'); pend = false; }
            o.append(s.charAt(i));
            i++;
        }
        if (pend) o.append('\u0DCA');
        return o.toString();
    }

    static boolean hasSinhala(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0D80 && c <= 0x0DFF) return true;
        }
        return false;
    }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ---- stylish text ----
    static final int[][] FB = {{0, 0}, {0x1D4D0, 0x1D4EA}, {0x1D400, 0x1D41A}, {0x1D670, 0x1D68A},
            {0x1D5D4, 0x1D5EE}, {0x1D468, 0x1D482}};

    static String fancy(String s, int st) {
        if (st <= 0 || st >= FB.length) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') b.appendCodePoint(FB[st][0] + c - 'A');
            else if (c >= 'a' && c <= 'z') b.appendCodePoint(FB[st][1] + c - 'a');
            else b.append(c);
        }
        return b.toString();
    }

    /** Decode a drawable without ever creating a giant bitmap (a huge logo.jpg used to crash the app). */
    static Bitmap decode(android.content.res.Resources res, int id, int maxPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, id, o);
            int sample = 1;
            while (o.outWidth / sample > maxPx || o.outHeight / sample > maxPx) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeResource(res, id, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    static final String[] ROM = {"❤", "💕", "💖", "💗", "💘", "💝", "😍", "😘", "🥰", "💋", "🌹", "💞", "😻", "💑", "🌸", "✨"};
    static List<String> romEmojis() { return java.util.Arrays.asList(ROM); }

    // ---- emoji ----
    static final String EMO = "😂❤😍🔥👍🙏😭😊🥰😎😘🤣😁🥺😀😃😄😆😅😇🙂😉😋🤩🥳😏😒😞😔😢😤😠😡🤯😳😱😨🤗🤔🤭🤫😐🙄😬😴🤤😷🤒🤧🥵🥶🤪😜🤑😈💀👻👽🤖💩🙈🙉🙊🧡💛💚💙💜🖤🤍💔💕💖💗💯✨⭐🌈🎉🎊🎁👎👏🙌🤝💪👌🤞👋🤙👀🧠🍕🍔🍟🍿🍩🍪🍺⚽🎮🎵🚗🏠";
    static List<String> EM;

    static List<String> emojis() {
        if (EM == null) {
            EM = new ArrayList<>();
            for (int i = 0; i < EMO.length(); ) {
                int cp = EMO.codePointAt(i);
                EM.add(new String(Character.toChars(cp)));
                i += Character.charCount(cp);
            }
        }
        return EM;
    }

    // ---- swipe typing ----
    static final String WORDS = "the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us is are was were been has had did does am hello hi hey thanks thank please sorry yes okay ok love life home house friend friends family mother father brother sister night morning evening today tomorrow yesterday week month hour minute again always never sometimes really very much many more less here where why great nice happy sad tired hungry busy free ready late early soon still already maybe probably together alone call phone message text send sent reply read write wrote speak talk tell ask answer help need try tried start stop wait keep let put mean leave left right turn move live believe bring happen begin show hear play run ran walk eat drink sleep buy pay sell open close learn change follow meet include continue set understand watch feel become found find lost lose win won build stay fall cut reach remain suggest raise pass require report decide pull school class teacher student study exam book paper word name number money price cost shop store market food water rice tea coffee car bus train road city town country world place room door window table chair bed light music song movie video photo picture game team match ball party birthday wedding holiday trip travel ticket flight hotel job office company business boss meeting project problem question idea plan reason result story news email internet online computer keyboard screen battery charge download upload install update app application website link password account login settings theme color blue red green black white yellow big small long short high low old young hot cold fast slow hard easy best better worse worst same different important possible beautiful wonderful amazing awesome cool fine sure true real whole little own able every each both few another such while since until before between through during without within against around under above behind near far inside outside though although however whether should might must shall cannot cant dont wont didnt doesnt isnt arent wasnt im ive ill youre thats whats lets going coming doing being having getting making taking looking thinking wanting trying working talking eating sleeping waiting";
    static String[] W;

    static String[] words() {
        if (W == null) W = new java.util.LinkedHashSet<>(java.util.Arrays.asList(WORDS.split(" +"))).toArray(new String[0]);
        return W;
    }

    static boolean subseq(String w, String p) {
        int j = 0;
        char prev = 0;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c == prev) continue;
            prev = c;
            int k = p.indexOf(c, j);
            if (k < 0) return false;
            j = k + 1;
        }
        return true;
    }

    static List<String> swipe(String path) {
        ArrayList<String> m = new ArrayList<>();
        char f = path.charAt(0), l = path.charAt(path.length() - 1);
        for (String w : words()) {
            if (w.charAt(0) == f && w.charAt(w.length() - 1) == l && subseq(w, path)) m.add(w);
        }
        java.util.Collections.sort(m, (x, y) -> y.length() - x.length());
        return new ArrayList<>(m.subList(0, Math.min(5, m.size())));
    }

    // ---- network ----
    static String read(java.io.InputStream in) throws java.io.IOException {
        if (in == null) return "";
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] x = new byte[4096];
        int n;
        while ((n = in.read(x)) > 0) b.write(x, 0, n);
        return b.toString("UTF-8");
    }

    static String translate(String text, String tl) throws Exception {
        java.net.URL u = new java.net.URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                + tl + "&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8"));
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        int code = c.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code);
        org.json.JSONArray a = new org.json.JSONArray(read(c.getInputStream())).getJSONArray(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) sb.append(a.getJSONArray(i).getString(0));
        return sb.toString();
    }

    static String ai(String key, String model, String task, String text) throws Exception {
        String prompt = task.equals("smart")
                ? "Write one short, natural reply to this message. Reply in the same language and style as the message. Output only the reply.\n\nMessage:\n" + text
                : task + ". Keep the original language and meaning. Output only the resulting text, nothing else.\n\nText:\n" + text;
        org.json.JSONObject body = new org.json.JSONObject()
                .put("model", model)
                .put("max_tokens", 500)
                .put("messages", new org.json.JSONArray().put(new org.json.JSONObject().put("role", "user").put("content", prompt)));
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://api.anthropic.com/v1/messages").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(40000);
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        c.setRequestProperty("x-api-key", key);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        java.io.OutputStream o = c.getOutputStream();
        o.write(body.toString().getBytes("UTF-8"));
        o.close();
        int code = c.getResponseCode();
        String resp = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new Exception("HTTP " + code + " (check key/model)");
        return new org.json.JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text").trim();
    }
}
