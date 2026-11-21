#!/usr/bin/env python3
"""Brand the disposable, pinned Bridge v24 build as DriveLab NAV v0.2.0-lab.25.

Never edit the immutable archived source, Android package, pairing keys,
OEM hooks, updater origin, or the old bridge-lab OTA channel during migration.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: nav-transition-branding.py STAGING_DIRECTORY")
root = Path(sys.argv[1]).resolve()
if not (root / "boot/build.gradle").is_file():
    raise SystemExit("ABORT: not a staged Bridge source tree")

def edit(relative, replacements):
    p = root / relative
    before = p.read_text(encoding="utf-8")
    after = before
    for old, new in replacements:
        expected = 2 if old == "manifestPlaceholders.bridgeLabel = 'DriveLab Bridge LAB'" else 1
        if after.count(old) != expected:
            raise SystemExit(f"ABORT: expected {expected} {old!r} in {relative}; got {after.count(old)}")
        after = after.replace(old, new, expected)
    p.write_text(after, encoding="utf-8")

edit("boot/build.gradle", [
    ("versionCode 28", "versionCode 29"),
    ("versionName '0.1.27'", "versionName '0.2.0'"),
    ("versionNameSuffix '-lab.24'", "versionNameSuffix '-lab.25'"),
    ("manifestPlaceholders.bridgeLabel = 'DriveLab Bridge LAB'",
     "manifestPlaceholders.bridgeLabel = 'DriveLab NAV'"),
])
edit("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootActivity.java", [
    ('text("DRIVELAB BRIDGE",24,', 'text("DRIVELAB NAV",24,'),
    ('button("BUSCAR ACTUALIZACIÓN · LAB OTA",',
     'button("BUSCAR ACTUALIZACIÓN · NAV OTA",'),
])
edit("boot/src/main/AndroidManifest.xml", [
    ('android:label="DriveLab Bridge · Volante para mapas"',
     'android:label="DriveLab NAV · Volante para mapas"'),
])
edit("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootLinkService.java", [
    ('"DriveLab Bridge · Xiaomi"', '"DriveLab NAV · Xiaomi"'),
    ('"DriveLab Bridge · receptor Xiaomi activo"',
     '"DriveLab NAV · receptor Xiaomi activo"'),
])
edit("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootAutoService.java", [
    ('"drivelab_boot_auto","DriveLab Bridge"', '"drivelab_boot_auto","DriveLab NAV"'),
    ('.setContentTitle("DriveLab Bridge")',
     '.setContentTitle("DriveLab NAV")'),
])
edit("boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java", [
    ('"Buscando actualización firmada de Bridge LAB…"',
     '"Buscando actualización firmada de DriveLab NAV…"'),
])


# Same DriveLab NAV product version on Xiaomi, but Android updater requires
# Sender's exact X.Y.Z versionName (the Jetour LAB retains its OTA suffix).
# Keep Sender's original package, shared pairing preferences and intent scheme.
edit("sender/build.gradle", [
    ("versionCode 25", "versionCode 29"),
    ("versionName '0.2.24'", "versionName '0.2.0'"),
])
edit("sender/src/main/AndroidManifest.xml", [
    ('android:label="DriveLab Auto Sender"',
     'android:label="DriveLab NAV Sender"'),
    ('android:label="DriveLab Auto Sender · Navegación"',
     'android:label="DriveLab NAV Sender · Navegación"'),
])
edit("sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderActivity.java", [
    ('title(body,"DRIVELAB AUTO · SENDER",24);',
     'title(body,"DRIVELAB NAV · SENDER",24);'),
    ('title(body,"Xiaomi · Navegación Uber / Google Maps / Waze",14);',
     'title(body,"VERSIÓN "+ownVersion()+" · Xiaomi · Uber / Google Maps / Waze",14);'),
    ('button(body,"ACTUALIZAR SENDER · INSTALACIÓN DIRECTA",',
     'button(body,"ACTUALIZAR NAV SENDER · INSTALACIÓN DIRECTA",'),
    ('button(body,"Desvincular Receiver",',
     'button(body,"Desvincular NAV de la T1",'),
    ('button(body,"RECONECTAR BRIDGE SIN QR · CLAVE GUARDADA",',
     'button(body,"RECONECTAR NAV SIN QR · CLAVE GUARDADA",'),
    ('"Bridge no encuentra UNA tarea Maps en pantalla virtual; "+',
     '"NAV no encuentra UNA tarea Maps en pantalla virtual; "+'),
    ('"Bridge buscará una única tarea Google Maps en un "+',
     '"NAV buscará una única tarea Google Maps en un "+'),
    ('"del Receiver.",13);',
     '"de NAV en la Jetour.",13);'),
    ('field.setHint("IP del Receiver, por ej. 192.168.43.120");',
     'field.setHint("IP de NAV en la T1, por ej. 192.168.43.120");'),
    ('.setMessage("En la T1 iniciá el Receiver y tocá VINCULAR XIAOMI · 6 DÍGITOS.")',
     '.setMessage("En la T1 abrí DriveLab NAV y tocá VINCULAR XIAOMI · 6 DÍGITOS.")'),
    ('"En Uber: elegí DriveLab Auto Sender para enviar destinos. "+',
     '"En Uber: elegí DriveLab NAV Sender para enviar destinos. "+'),
    ('"Ese QR no corresponde a DriveLab Auto. "+',
     '"Ese QR no corresponde a DriveLab NAV. "+'),
    ('info("Primero vinculá el Receiver");',
     'info("Primero vinculá DriveLab NAV en la T1");'),
])
edit("sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/RouteDispatchService.java", [
    ('CHANNEL,"DriveLab destinos",NotificationManager.IMPORTANCE_LOW)',
     'CHANNEL,"DriveLab NAV · Destinos",NotificationManager.IMPORTANCE_LOW)'),
    ('.setContentTitle("DriveLab · enviando destino")',
     '.setContentTitle("DriveLab NAV · enviando destino")'),
    ('.setContentTitle("DriveLab · destino no confirmado")',
     '.setContentTitle("DriveLab NAV · destino no confirmado")'),
])
# Add a version sourced from Android PackageManager, not a hard-coded build label.
activity_path=root/"sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderActivity.java"
activity=activity_path.read_text(encoding="utf-8")
method_anchor="    private void render(){"
if activity.count(method_anchor)!=1:
    raise SystemExit("ABORT: SenderActivity structure changed before version display")
activity=activity.replace(method_anchor, """    private String ownVersion(){
        try {
            return getPackageManager().getPackageInfo(getPackageName(),0).versionName;
        } catch(Exception notInstalled){
            return "DESCONOCIDA";
        }
    }
""" + method_anchor, 1)
activity_path.write_text(activity,encoding="utf-8")

# Do not touch common/Updater transport, signer, old OTA endpoint,
# SenderClient's "drivelab_sender" SharedPreferences or QR/URI protocol.
sender_gradle=(root/"sender/build.gradle").read_text(encoding="utf-8")
sender_manifest=(root/"sender/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
sender_activity=(root/"sender/src/main/java/ar/com/rango5gps/drivelabauto/sender/SenderActivity.java").read_text(encoding="utf-8")
assert "applicationId 'ar.com.rango5gps.drivelabauto.sender'" in sender_gradle
assert "versionCode 29" in sender_gradle
assert "versionName '0.2.0'" in sender_gradle
assert 'android:label="DriveLab NAV Sender"' in sender_manifest
assert 'android:scheme="drivelabauto"' in sender_manifest
assert 'title(body,"DRIVELAB NAV · SENDER",24);' in sender_activity

gradle = (root / "boot/build.gradle").read_text(encoding="utf-8")
updater = (root / "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootUpdater.java").read_text(encoding="utf-8")
activity = (root / "boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BootActivity.java").read_text(encoding="utf-8")
assert "applicationIdSuffix '.lab'" in gradle
assert "versionCode 29" in gradle
assert "versionName '0.2.0'" in gradle
assert "versionNameSuffix '-lab.25'" in gradle
assert "LAB_PACKAGE=PACKAGE+\".lab\"" in updater
assert "bridge-lab-ota/bridge-lab.json" in updater
assert "https://raw.githubusercontent.com/bruceferra/drivelab-auto-standalone/" in updater
assert 'text("DRIVELAB NAV",24,' in activity
assert "BridgeStandaloneWindows" in activity and "BootLinkService" in activity
print("PASS: staged DriveLab NAV (Jetour code 29) + DriveLab NAV Sender (Xiaomi code 29), OTA and pairing intact")
