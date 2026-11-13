package ar.com.rango5gps.drivelabauto.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/**
 * One-tap voice destination inside DriveLab Receiver.
 *
 * Uses an installed Android speech-recognition provider when available; does
 * not depend on or launch the legacy standalone DriveLab Voz application.
 * Manual text entry remains available on Jetour ROMs without a speech engine.
 * Waze is launched only after the owner confirms the recognized destination.
 * There is NO recording service, background microphone or vehicle OEM write.
 */
public final class T1VoiceRouteActivity extends T1ImmersiveActivity {
    private static final int VOICE_REQUEST = 7042;
    private static final int BG = T1JetourTheme.NIGHT;
    private static final int FG = T1JetourTheme.WHITE;
    private EditText destination;
    private TextView status;

    @Override protected int overlayClearBackgroundColor() { return BG; }

    private TextView label(String content, int size) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(FG);
        view.setPadding(12, 10, 12, 10);
        return view;
    }

    private Button button(LinearLayout root, String title,
                          android.view.View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(title);
        T1JetourTheme.button(b,title.contains("NAVEGAR")?
            T1JetourTheme.GREEN_BUTTON:0xFF426E73);
        b.setOnClickListener(click);
        root.addView(b, new LinearLayout.LayoutParams(-1, 58));
        return b;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(24, 12, 24, 24);
        root.setBackground(T1JetourTheme.backdrop());
        root.addView(label("MICRÓFONO · DESTINO EN CLÚSTER", 21));
        root.addView(label("Decí, por ejemplo, \"navegar a Plaza de Mayo\". " +
            "Revisá el destino antes de iniciar Waze. El reconocimiento " +
            "necesita un proveedor de voz instalado en la T1.", 14));
        button(root, "🎤 HABLAR · INDICAR DESTINO", v -> listen());
        destination = new EditText(this);
        destination.setSingleLine(true);
        destination.setTextColor(Color.WHITE);
        destination.setHintTextColor(0xFF9BB0C1);
        destination.setHint("Destino o dirección (alternativa por teclado)");
        destination.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        T1JetourTheme.number(destination);
        root.addView(destination, new LinearLayout.LayoutParams(-1, 58));
        button(root, "NAVEGAR EN CLÚSTER", v -> confirm(destination.getText().toString()));
        status = label("Listo · sin escucha en segundo plano", 14);
        root.addView(status);
        button(root, "VOLVER", v -> finish());
        setContentView(root);
    }

    private void listen() {
        Intent voice = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        voice.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        voice.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR");
        voice.putExtra(RecognizerIntent.EXTRA_PROMPT,
            "¿A dónde querés navegar en el clúster?");
        voice.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            status.setText("Escuchando mediante el reconocimiento de Android…");
            startActivityForResult(voice, VOICE_REQUEST);
        } catch (ActivityNotFoundException | SecurityException ex) {
            status.setText("Esta ROM no tiene un reconocedor de voz accesible. " +
                "Escribí el destino en el campo inferior.");
            destination.requestFocus();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VOICE_REQUEST) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            status.setText("No se reconoció un destino · probá otra vez o escribilo.");
            return;
        }
        ArrayList<String> matches = data.getStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS);
        if (matches == null || matches.isEmpty()) {
            status.setText("La voz no devolvió resultados · usá el teclado.");
            return;
        }
        String heard = matches.get(0);
        if (heard == null || heard.trim().isEmpty()) {
            status.setText("Resultado de voz vacío.");
            return;
        }
        destination.setText(stripCommand(heard));
        destination.setSelection(destination.length());
        status.setText("Escuché: " + heard + " · verificá el destino.");
        confirm(destination.getText().toString());
    }

    static String stripCommand(String phrase) {
        if (phrase == null) return "";
        String clean = phrase.trim();
        return clean.replaceFirst(
            "(?iu)^(?:por\\s+favor\\s+)?(?:(?:naveg[aá]r|ir|vamos|ll[eé]vame)" +
            "\\s+(?:hacia\\s+|hasta\\s+|a\\s+)?|c[oó]mo\\s+llegar\\s+a\\s+)",
            "").trim();
    }

    private void confirm(String raw) {
        String location = stripCommand(raw);
        if (location.length() < 3) {
            status.setText("Ingresá un destino válido antes de navegar.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("CONFIRMAR DESTINO PARA WAZE")
            .setMessage("Destino reconocido: " + location + "\n\n" +
                "Waze puede resolver un lugar distinto si hay nombres " +
                "similares. Revisá el pin y la ruta en el clúster.")
            .setNegativeButton("EDITAR", null)
            .setPositiveButton("NAVEGAR", (dialog, which) -> navigate(location))
            .show();
    }

    private void navigate(String location) {
        Uri route = Uri.parse("waze://?q=" + Uri.encode(location) + "&navigate=yes");
        Intent intent = new Intent(Intent.ACTION_VIEW, route)
            .setPackage("com.waze");
        try {
            startActivity(intent);
            status.setText("Waze solicitado en T1: " + location +
                " · verificá que el clúster muestre el destino correcto.");
        } catch (ActivityNotFoundException | SecurityException ex) {
            status.setText("No se pudo abrir Waze T1 (" +
                ex.getClass().getSimpleName() + "). Revisá instalación y permisos.");
        }
    }
}
