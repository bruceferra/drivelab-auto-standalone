#!/usr/bin/env python3
"""Create Overture or IGN shadow map SQLite for DriveLab Receiver.

Reads *already extracted* GeoJSONSeq lines (one Feature per line).
Usage:
  python3 tools/build_shadow_maps.py --source OVERTURE --input overture.geojsonseq --out /var/www/html/drivelab/maps/shadow/overture
  python3 tools/build_shadow_maps.py --source IGN --input ign.geojsonseq --out /var/www/html/drivelab/maps/shadow/ign

Overture input: use "overturemaps download --bbox=-64.5,-42,-56,-33
--type=segment -f geojson -o overture.geojson" and convert with ogr2ogr
to GeoJSONSeq; DO NOT use whole-planet geometries.
IGN input: download the official PBA Red Vial shapefile and convert with
ogr2ogr to GeoJSONSeq with reprojected WGS84 (EPSG:4326).
All road classifications are conservative; uncertain roads stay UNKNOWN.
"""
import argparse
import datetime as dt
import gzip
import hashlib
import json
import os
import pathlib
import sqlite3
import struct
import tempfile
import zlib

REGION = "AR-B+AR-C"
MINLON, MINLAT, MAXLON, MAXLAT = -64.9, -42.1, -56.0, -33.0
DRIVABLE = {"motorway", "trunk", "primary", "secondary", "tertiary",
            "residential", "living_street", "service", "unclassified"}
IGNORE = {"footway", "pedestrian", "cycleway", "path", "steps", "track",
          "bridleway", "parking_aisle", "sidewalk", "ferry", "rail"}
KNOWN = {"motorway", "trunk", "primary", "secondary", "tertiary",
         "residential", "living_street", "service", "unclassified"}
# Observable roadway type is never guessed from speed or proximity alone.
def classification(src, props):
    def find(*names):
        for name in names:
            value = props.get(name)
            if isinstance(value, str) and value.strip():
                return value.strip()
        return ""
    if src == "OVERTURE":
        if props.get("subtype") not in (None, "road"):
            return None
        cls=find("class", "highway").lower()
        if cls in IGNORE or cls not in KNOWN:
            return None
        return cls
    # IGN "red vial" is primarily provincial/national transportation,
    # not a replacement for OSM street-level coverage.
    words=" ".join(str(props.get(k,"")) for k in
                   ("tipo", "TIPO", "clase", "CLASE", "categoria", "CATEGORIA",
                    "nombre", "NOMBRE", "jerarquia", "JERARQUIA")).lower()
    if any(x in words for x in ("ciclov", "sendero", "peaton", "ferro")):
        return None
    if "autopista" in words or "autovía" in words or "autovia" in words:
        return "motorway"
    if any(x in words for x in ("ruta nacional", "ruta provincial",
                                "pavimentad", "ruta primaria")):
        return "primary"
    if any(x in words for x in ("ruta secundaria", "camino rural",
                                "camino vecinal", "ripio")):
        return "secondary"
    # This input is explicitly the IGN **provincial road network** layer,
    # which certifies road existence but not its urban/motorway classification.
    # Treat it as general ROAD in the shadow adapter unless the official
    # attributes explicitly identify motorway. Never classify it as motorway
    # from speed, and NEVER use it for a vehicle profile decision.
    return "primary"

def polylines(geometry):
    if not geometry: return []
    kind=geometry.get("type")
    coords=geometry.get("coordinates")
    if kind=="LineString" and isinstance(coords,list): return [coords]
    if kind=="MultiLineString" and isinstance(coords,list): return coords
    return []

def polyblob(linestring):
    coords=[]
    for xy in linestring:
        if len(xy)<2: continue
        lon,lat=xy[0],xy[1]
        if not all(isinstance(v,(int,float)) for v in (lon,lat)): continue
        if not (MINLAT<=lat<=MAXLAT and MINLON<=lon<=MAXLON): continue
        coords.append((lat,lon))
    if len(coords)<2:return None
    bounds=(min(x[0] for x in coords),max(x[0] for x in coords),
            min(x[1] for x in coords),max(x[1] for x in coords))
    packed=b"".join(struct.pack("<ii",int(round(lat*1e7)),int(round(lon*1e7)))
                    for lat,lon in coords)
    return bounds,zlib.compress(packed,level=6)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--source",required=True,choices=["OVERTURE","IGN"])
    ap.add_argument("--input",required=True,type=pathlib.Path)
    ap.add_argument("--out",required=True,type=pathlib.Path)
    ap.add_argument("--license-note",default="")
    args=ap.parse_args()
    if not args.input.is_file():ap.error("Input missing: "+str(args.input))
    args.out.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="drivelab-shadow-") as work:
        sq=pathlib.Path(work)/"roads.sqlite"
        db=sqlite3.connect(sq)
        db.execute("PRAGMA journal_mode=DELETE")
        db.execute("PRAGMA synchronous=FULL")
        db.executescript("""
        CREATE TABLE metadata(k TEXT PRIMARY KEY,value TEXT);
        CREATE TABLE roads(
          osm_id TEXT PRIMARY KEY, highway TEXT NOT NULL, ref TEXT,
          vertices BLOB NOT NULL, maxspeed TEXT, motorroad TEXT, expressway TEXT);
        CREATE TABLE roads_bounds(
          osm_id TEXT PRIMARY KEY,min_lon REAL,max_lon REAL,
          min_lat REAL,max_lat REAL);
        CREATE INDEX idx_roads_bounds_lon ON roads_bounds(min_lon,max_lon);
        CREATE INDEX idx_roads_bounds_lat ON roads_bounds(min_lat,max_lat);
        """)
        count=0;features=0;ignored=0
        with args.input.open(encoding="utf-8") as data:
            for raw in data:
                raw=raw.strip()
                if not raw or raw[0]!="{": continue
                feature=json.loads(raw)
                if feature.get("type")!="Feature":continue
                features+=1
                props=feature.get("properties") or {}
                kind=classification(args.source,props)
                if kind is None: ignored+=1;continue
                ref=str(props.get("ref") or props.get("ruta") or
                    props.get("RUTA") or props.get("rtn") or "").strip()[:160]
                maxspeed=str(props.get("maxspeed") or "")[:60]
                roadid=str(feature.get("id") or f"row-{features}")
                for i,shape in enumerate(polylines(feature.get("geometry"))):
                    result=polyblob(shape)
                    if result is None:continue
                    bounds,vertices=result
                    # Keep each geometry segment independent in v3 portable BTree.
                    key=f"{args.source}:{roadid}:{i}"
                    db.execute("INSERT INTO roads VALUES (?,?,?,?,?,?,?)",
                        (key,kind,ref,vertices,maxspeed,"",""))
                    db.execute("INSERT INTO roads_bounds VALUES (?,?,?,?,?)",
                        (key,bounds[2],bounds[3],bounds[0],bounds[1]))
                    count+=1
                    if count%10000==0: db.commit()
        if count<100:
            raise RuntimeError(f"Insufficient classified driveable roads: {count}")
        fields={
            "source":args.source,"region_code":REGION,
            "spatial_index":"portable_btree_bounds_v3",
            "extract_complete":"1","features_scanned":str(features),
            "features_unclassified":str(ignored),
            "dataset_note":args.license_note,
            "built_at_utc":dt.datetime.now(dt.timezone.utc).isoformat()}
        db.executemany("INSERT INTO metadata VALUES (?,?)",fields.items())
        db.commit();db.close()
        gz=args.out/"roads.sqlite.gz"
        with sq.open("rb") as raw,gzip.open(gz,"wb",compresslevel=6) as dest:
            while chunk:=raw.read(1024*1024):dest.write(chunk)
        digest=hashlib.sha256(gz.read_bytes()).hexdigest()
        meta={"schema":3,"source":args.source,"region":REGION,
              "filename":"roads.sqlite.gz","bytes":gz.stat().st_size,
              "sha256":digest,"roadCount":count,
              "spatialIndex":"portable_btree_bounds_v3",
              "extractComplete":True,
              "licenseNote":args.license_note,"sourceFeatures":features,
              "unclassifiedSourceFeatures":ignored,
              "builtAt":fields["built_at_utc"]}
        temp=args.out/"manifest.json.part"
        temp.write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding="utf-8")
        os.replace(temp,args.out/"manifest.json")
        print(json.dumps(meta,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
