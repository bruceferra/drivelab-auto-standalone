#!/usr/bin/env python3
"""Inject the independent NAV native 2-VirtualDisplay mosaic into a disposable
Bridge LAB stage. Do NOT alter the pinned upstream v24 sources or publish OTA.

The feature is explicitly parked/manual and limited to the central screen.
No T1Launcher dependency, Receiver, Cluster, wheel or ADB commands.
"""
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: nav-virtual-mosaic-stage.py STAGE SOURCE_REPO")
stage=Path(sys.argv[1]).resolve()
repo=Path(sys.argv[2]).resolve()
target=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot"
source=repo/"apps/nav/bridge-runtime-overlay/NavVirtualMosaicActivity.java"
if not (stage/"boot/build.gradle").is_file() or not source.is_file():
    raise SystemExit("ABORT: stage or NAV source unavailable")
if (target/"NavVirtualMosaicActivity.java").exists():
    raise SystemExit("ABORT: staging already contains NAV mosaic activity")
shutil.copy2(source,target/"NavVirtualMosaicActivity.java")
# The live NAV app ships ONLY its own virtual-display modules;
# external-launcher diagnostic sources remain historical tests, not APK code.
for filename in ("NavMosaicExitPolicy.java",
                 "NavOwnClusterProbe.java",
                 "NavOwnClusterWazePolicy.java",
                 "NavWazeDisplayTrace.java",
                 "NavMosaicLifecyclePolicy.java",
                 "NavMosaicMapsRoute.java"):
    source_file=repo/"apps/nav/bridge-runtime-overlay"/filename
    target_file=target/filename
    if not source_file.is_file() or target_file.exists():
        raise SystemExit("ABORT: missing or duplicate read-only Cluster module")
    shutil.copy2(source_file,target_file)

manifest=stage/"boot/src/main/AndroidManifest.xml"
s=manifest.read_text(encoding="utf-8")
anchor='        <!-- Manual, private on-device surface proof only; never a launcher. -->'
addon='''        <!-- NAV central-only 2-VirtualDisplay LAB, opt-in and non-exported.
             Uses no OEM permissions or third-party launcher dependency. -->
        <activity android:name=".NavVirtualMosaicActivity"
            android:exported="false" android:resizeableActivity="true"
            android:excludeFromRecents="true" />
'''
if s.count(anchor)!=1 or '.NavVirtualMosaicActivity' in s:
    raise SystemExit("ABORT: source manifest anchor mismatch")
manifest.write_text(s.replace(anchor,addon+anchor,1),encoding="utf-8")

# Only an owner-enabled Android accessibility service can dispatch gestures.
# This service has NO wheel/Binder/key filter privileges or OEM interface.
touch=repo/"apps/nav/bridge-runtime-overlay/NavMosaicTouchService.java"
xml=repo/"apps/nav/bridge-runtime-overlay/nav_mosaic_touch_accessibility.xml"
if not touch.is_file() or not xml.is_file():
    raise SystemExit("ABORT: missing opt-in touch source")
shutil.copy2(touch,target/"NavMosaicTouchService.java")
# Jetour blocks opening Android accessibility Settings. The ADB-local
# recovery flow requires a separate explicit owner confirmation.
recovery=repo/"apps/nav/bridge-runtime-overlay/NavMosaicTouchRecovery.java"
if not recovery.is_file() or (target/"NavMosaicTouchRecovery.java").exists():
    raise SystemExit("ABORT: missing or duplicate NAV touch ADB recovery")
shutil.copy2(recovery,target/"NavMosaicTouchRecovery.java")
resource=stage/"boot/src/main/res/xml"
resource.mkdir(parents=True,exist_ok=True)
if (resource/"nav_mosaic_touch_accessibility.xml").exists():
    raise SystemExit("ABORT: duplicate virtual touch accessibility descriptor")
shutil.copy2(xml,resource/"nav_mosaic_touch_accessibility.xml")
strings=stage/"boot/src/main/res/values/nav_mosaic_strings.xml"
if strings.exists():
    raise SystemExit("ABORT: duplicate NAV touch string resource")
strings.write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources><string name="nav_mosaic_touch_description">Toques voluntarios dentro de las dos pantallas virtuales NAV. No controla volante ni funciones del vehículo.</string></resources>
""",encoding="utf-8")
manifest=stage/"boot/src/main/AndroidManifest.xml"
s=manifest.read_text(encoding="utf-8")
anchor='        <service android:name=".BridgeWheelAccessibilityService"'
addon='''        <!-- Deliberately separate from OEM wheel service. No key capture.
             Android system and owner must opt in to accessibility. -->
        <service android:name=".NavMosaicTouchService"
            android:label="DriveLab NAV · táctil mosaico"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data android:name="android.accessibilityservice"
                android:resource="@xml/nav_mosaic_touch_accessibility" />
        </service>
'''
if s.count(anchor)!=1 or '.NavMosaicTouchService' in s:
    raise SystemExit("ABORT: unexpected wheel/accessibility manifest anchor")
manifest.write_text(s.replace(anchor,addon+anchor,1),encoding="utf-8")

activity=target/"BootActivity.java"
s=activity.read_text(encoding="utf-8")
anchor='        button("MOSAICO · SPOTIFY TIPO CELULAR · PANEL ANGOSTO",'
addon='''        button("NAV LAB · MOSAICO PROPIO (CENTRAL, SIN T1LAUNCHER)",()->
            new AlertDialog.Builder(this)
                .setTitle("NAV · MOSAICO PROPIO EN DESARROLLO")
                .setMessage("Sólo con el vehículo ESTACIONADO, sin ruta activa. " +
                    "Crea DOS pantallas virtuales públicas e independientes para " +
                    "Maps y Spotify, con superficies dentro de NAV. La rueda " +
                    "y el Cluster NO participan. Android puede rechazar abrir " +
                    "apps de terceros; el tacto requiere permiso manual " +
                    "Esta prueba NO es el mosaico final ni confirma su visibilidad. " +
                    "¿Abrir el compositor de laboratorio?")
                .setNegativeButton("CANCELAR",null)
                .setPositiveButton("ABRIR LAB",(d,w)->
                    startActivity(new Intent(this,NavVirtualMosaicActivity.class)))
                .show());
'''
if s.count(anchor)!=1 or 'NavVirtualMosaicActivity' in s:
    raise SystemExit("ABORT: staged BootActivity anchor mismatch")
activity.write_text(s.replace(anchor,addon+anchor,1),encoding="utf-8")
print("PASS: staged NAV private central mosaic only, new Activity not exported")
