#!/usr/bin/env python3
"""Stage NAV-only opt-in wheel routing on the existing Bridge OEM key receiver.

No new wheel controller, no Binder ownership at ignition, no raw key capture,
no T1Launcher dependency, and no default/physical display routing changes.
Only a USER-ARMED NAV cluster session may use the NAV Maps virtual display.
The original immutable v24 source is never overwritten.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: nav-mosaic-wheel-stage.py DISPOSABLE_STAGE")
stage=Path(sys.argv[1]).resolve()
wheel=stage/"boot/src/main/java/ar/com/rango5gps/drivelabauto/boot/BridgeWheelAccessibilityService.java"
assert wheel.is_file(), "ABORT: original wheel source absent from stage"
s=wheel.read_text(encoding="utf-8")
anchor="""    static String targetFor(Context c){
        // Match the current confirmed cluster map, as Receiver does."""
insert="""    static String targetFor(Context c){
        // The ONLY new path uses an explicitly armed NAV Maps virtual
        // display that is still owned by the current central mosaic.
        // No change to old Display 0/1, T1Launcher or Waze handling.
        if(NavMosaicSessionRegistry.manuallyApprovedWheelMapDisplay(
                c,MAPS)>=2)return MAPS;
        // Match the current confirmed cluster map, as Receiver does."""
assert s.count(anchor)==1, "ABORT: targetFor structure changed"
s=s.replace(anchor,insert,1)
anchor="""    private MapWindow visibleMap(String expected,int selectedDisplay){
        if(!serviceReady||(
            selectedDisplay!=0&&selectedDisplay!=1))return null;"""
insert="""    private MapWindow visibleMap(String expected,int selectedDisplay){
        // A regular owner selection remains restricted to physical
        // Display 0/1. Only the user-approved NAV Maps session may
        // override that to its currently owned virtual display ID.
        int nav=NavMosaicSessionRegistry.manuallyApprovedWheelMapDisplay(
            this,expected);
        if(nav>=2)selectedDisplay=nav;
        if(!serviceReady||(
            selectedDisplay!=0&&selectedDisplay!=1&&nav<2))return null;"""
assert s.count(anchor)==1, "ABORT: visibleMap display gate changed"
s=s.replace(anchor,insert,1)
wheel.write_text(s,encoding="utf-8")
print("PASS: existing Bridge wheel service may read ONLY a user-armed NAV Maps virtual window, with OEM protection unchanged")
