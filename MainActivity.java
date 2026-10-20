package com.example.uberleitor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private TextView tvStatus, tvOut;
    private EditText etKm, etHour;
    private SharedPreferences sp;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView label(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences(UberReaderService.PREFS, MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(30));
        sv.addView(col);

        col.addView(label("Leitor Uber", 26, true));
        col.addView(label("Lê a tela do Uber Motorista e mostra no topo: ganho por km, ganho por hora, "
                + "nota e endereços de partida e chegada. Só lê, não toca em nada.", 14, false));

        tvStatus = label("", 16, true);
        col.addView(tvStatus);

        col.addView(button("1. Ativar leitura da tela", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));

        col.addView(label("2. Seus mínimos (fica verde se passar nos dois)", 16, true));
        etKm = new EditText(this);
        etKm.setHint("Mínimo R$ por km (ex.: 1,80)");
        etKm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etKm.setText(OfferParser.dec(sp.getFloat("min_km", 1.8f), 2));
        col.addView(etKm);
        etHour = new EditText(this);
        etHour.setHint("Mínimo R$ por hora (ex.: 30)");
        etHour.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etHour.setText(OfferParser.dec(sp.getFloat("min_hour", 30f), 2));
        col.addView(etHour);
        col.addView(button("Salvar mínimos", new View.OnClickListener() {
            @Override public void onClick(View v) { saveMins(); }
        }));

        col.addView(label("3. Conferência", 16, true));
        col.addView(button("Testar com corrida de exemplo", new View.OnClickListener() {
            @Override public void onClick(View v) { runSample(); }
        }));
        col.addView(button("Ver último texto lido do Uber", new View.OnClickListener() {
            @Override public void onClick(View v) { showLastText("last_text", "o último texto lido"); }
        }));
        col.addView(button("Ver texto da última corrida detectada", new View.OnClickListener() {
            @Override public void onClick(View v) { showLastText("last_offer_text", "a última corrida detectada"); }
        }));
        col.addView(button("Copiar o que está abaixo", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Leitor Uber", tvOut.getText()));
                Toast.makeText(MainActivity.this, "Copiado", Toast.LENGTH_SHORT).show();
            }
        }));

        tvOut = new TextView(this);
        tvOut.setTextIsSelectable(true);
        tvOut.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvOut.setPadding(0, dp(12), 0, 0);
        col.addView(tvOut);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isServiceOn()) {
            tvStatus.setText("Leitura ATIVADA ✔");
            tvStatus.setTextColor(0xFF1B8A4B);
        } else {
            tvStatus.setText("Leitura DESLIGADA — toque no botão 1 e ligue \"Leitor Uber\"");
            tvStatus.setTextColor(0xFFB3261E);
        }
    }

    private boolean isServiceOn() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/");
    }

    private float parse(EditText e, float fallback) {
        try {
            return Float.parseFloat(e.getText().toString().trim().replace(',', '.'));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void saveMins() {
        float km = parse(etKm, 1.8f);
        float h = parse(etHour, 30f);
        sp.edit().putFloat("min_km", km).putFloat("min_hour", h).apply();
        Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show();
    }

    private void runSample() {
        List<String> sample = Arrays.asList(
                "UberX",
                "R$ 18,40",
                "★ 4,89",
                "5 min (1,8 km) de distância",
                "Rua das Flores, 120 - Tirol",
                "Viagem de 14 min (6,2 km)",
                "Av. Hermes da Fonseca, 800 - Tirol",
                "Aceitar");
        OfferParser.Offer o = OfferParser.parse(sample);
        tvOut.setText(o == null ? "Não consegui ler o exemplo." : OfferParser.describe(o));
    }

    private void showLastText(String key, String nome) {
        String t = sp.getString(key, null);
        if (t == null || t.isEmpty()) {
            tvOut.setText("Ainda não li nada. Ative a leitura (botão 1), abra o Uber Motorista, "
                    + "fique online e espere uma chamada.");
        } else {
            tvOut.setText("Texto de " + nome + " (uma linha por item da tela):\n\n" + t);
        }
    }
}
