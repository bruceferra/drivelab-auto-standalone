#!/usr/bin/env python3
"""Apply read-only NAV UI preview to a disposable Bridge v24 build copy only."""
import pathlib
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: bridge-nav-preview-patch.py STAGED_BOOT_ACTIVITY")
p = pathlib.Path(sys.argv[1])
source = p.read_text(encoding="utf-8")
anchor = "    private void draw(){"
new_method = """    // NAV core is read-only here: no ADB, WindowManager task moves, or OEM writes.
    private void showNavCoreLayoutPreview(){
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        android.graphics.Insets insets=android.graphics.Insets.NONE;
        android.view.WindowInsets wi=getWindow().getDecorView().getRootWindowInsets();
        if(wi!=null)insets=wi.getInsets(android.view.WindowInsets.Type.systemBars());
        String result=BridgeNavCorePreview.describe(metrics.widthPixels,
            metrics.heightPixels,metrics.density,insets.left,insets.top,
            insets.right,insets.bottom,BridgeStandaloneWindows.ratio(this));
        new AlertDialog.Builder(this)
            .setTitle("NAV · DISEÑO RESPONSIVE (SOLO LECTURA)")
            .setMessage(result+"\\n\\nEste cálculo NO abre, mueve ni reutiliza tareas. "+
                "No modifica T1Launcher, Spotify, Waze, Maps ni Cluster. "+
                "Verificación de mosaicos reales: sólo con Jetour estacionada.")
            .setPositiveButton("ENTENDIDO",null).show();
    }
"""
button_anchor = '        button("MOSAICO · SPOTIFY TIPO CELULAR · PANEL ANGOSTO",'
new_button = '        button("NAV · PREVISUALIZAR MOSAICO RESPONSIVE",this::showNavCoreLayoutPreview);\n'
if source.count(anchor) != 1 or source.count(button_anchor) != 1:
    raise SystemExit("ABORT: Bridge v24 activity structure changed")
out = source.replace(anchor,new_method+anchor,1).replace(button_anchor,new_button+button_anchor,1)
p.write_text(out,encoding="utf-8")
print("PASS: NAV read-only responsive preview wired into staged Bridge UI")
