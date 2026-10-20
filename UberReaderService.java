package com.example.uberleitor;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de acessibilidade: só LÊ os textos da tela do Uber Motorista.
 * Não clica, não aceita e não recusa nada.
 */
public class UberReaderService extends AccessibilityService {

    static final String UBER_PKG = "com.ubercab.driver";
    static final String PREFS = "cfg";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout box;
    private TextView tvKm, tvHour, tvInfo, tvFrom, tvTo;
    private WindowManager.LayoutParams params;
    private boolean shown = false;
    private boolean scheduled = false;
    private String lastSaved = "";

    private final Runnable processRunnable = new Runnable() {
        @Override public void run() {
            scheduled = false;
            process();
        }
    };
    private final Runnable hideRunnable = new Runnable() {
        @Override public void run() {
            removeOverlay();
        }
    };

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !UBER_PKG.contentEquals(pkg)) return;
        if (!scheduled) {
            scheduled = true;
            handler.postDelayed(processRunnable, 200);
        }
    }

    @Override
    public void onInterrupt() {
        removeOverlay();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        super.onDestroy();
    }

    private void process() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        CharSequence p = root.getPackageName();
        if (p == null || !UBER_PKG.contentEquals(p)) return;

        List<String> lines = new ArrayList<>();
        collect(root, lines, 0);

        StringBuilder sb = new StringBuilder();
        for (String s : lines) sb.append(s).append('\n');
        String joined = sb.toString();
        if (joined.length() > 6000) joined = joined.substring(0, 6000);

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean changed = !joined.equals(lastSaved);
        if (changed) {
            lastSaved = joined;
            sp.edit().putString("last_text", joined).putLong("last_text_time", System.currentTimeMillis()).apply();
        }

        OfferParser.Offer offer = OfferParser.parse(lines);
        if (offer != null) {
            if (changed) {
                sp.edit().putString("last_offer_text", joined).apply();
            }
            show(offer, sp);
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, 4000);
        }
    }

    private void collect(AccessibilityNodeInfo n, List<String> out, int depth) {
        if (n == null || depth > 40) return;
        if (!n.isVisibleToUser()) return;
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        if (t != null && t.length() > 0) {
            for (String part : t.toString().split("\\n")) {
                String s = part.trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                collect(c, out, depth + 1);
                c.recycle();
            }
        }
    }

    // ---------------- painel flutuante ----------------

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private TextView text(float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private void ensureOverlay() {
        if (box != null) return;
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF0333333);
        box.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        tvKm = text(22, true);
        tvHour = text(22, true);
        tvHour.setGravity(Gravity.END);
        row.addView(tvKm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvHour, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row);

        tvInfo = text(14, true);
        tvFrom = text(13, false);
        tvTo = text(13, false);
        box.addView(tvInfo);
        box.addView(tvFrom);
        box.addView(tvTo);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        params = new WindowManager.LayoutParams(
                (int) (dm.widthPixels * 0.94f),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(48);
    }

    private void show(OfferParser.Offer o, SharedPreferences sp) {
        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensureOverlay();

        float minKm = sp.getFloat("min_km", 1.8f);
        float minHour = sp.getFloat("min_hour", 30f);
        boolean okKm = o.perKm() >= minKm;
        boolean okHour = o.perHour() >= minHour;
        int color = (okKm && okHour) ? 0xF01B8A4B : (okKm || okHour) ? 0xF0B7791F : 0xF0B3261E;
        ((GradientDrawable) box.getBackground()).setColor(color);

        tvKm.setText(OfferParser.money(o.perKm()) + "/km");
        tvHour.setText(OfferParser.money(o.perHour()) + "/h");
        String nota = o.rating >= 0 ? "★ " + OfferParser.dec(o.rating, 2) : "★ —";
        tvInfo.setText(nota + "  ·  " + OfferParser.dec(o.totalKm(), 1) + " km  ·  "
                + OfferParser.dec(o.totalMin(), 0) + " min  ·  " + OfferParser.money(o.price));
        tvFrom.setText("Partida: " + (o.pickupAddress.isEmpty() ? "—" : o.pickupAddress));
        tvTo.setText("Chegada: " + (o.dropAddress.isEmpty() ? "—" : o.dropAddress));

        if (!shown) {
            try {
                wm.addView(box, params);
                shown = true;
            } catch (Exception ignored) { }
        }
    }

    private void removeOverlay() {
        if (shown && wm != null && box != null) {
            try { wm.removeView(box); } catch (Exception ignored) { }
        }
        shown = false;
    }
}
