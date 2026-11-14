#!/usr/bin/env python3
"""Download OFFICIAL IGN Red Vial dataset for PBA using the province CKAN API.
Fails closed: never emits test/fallback/OSM data under the IGN label.
"""
import argparse
import json
import pathlib
import urllib.request
import urllib.parse
import urllib.error
import zipfile

CKAN = "https://catalogo.datos.gba.gob.ar/api/3/action/package_show?id=red-vial"
RESOURCE = "c66015c7-9dae-4770-ae6d-44c615771230"

def get_json(url):
    request=urllib.request.Request(url,headers={
        "User-Agent":"DriveLabAuto-OfficialIGN-PBA-Build/1",
        "Accept":"application/json"})
    with urllib.request.urlopen(request,timeout=50) as response:
        return json.loads(response.read(2000000).decode("utf-8"))

def resolve():
    result=get_json(CKAN)
    if result.get("success") is not True:
        raise RuntimeError("Official PBA CKAN package_show did not return success")
    data=result.get("result") or {}
    resources=data.get("resources") or []
    ordered=sorted(resources,key=lambda item:item.get("id")!=RESOURCE)
    for entry in ordered:
        name=str(entry.get("name") or "").lower()
        fmt=str(entry.get("format") or "").lower()
        url=str(entry.get("url") or "").strip()
        if not url.startswith("https://"): continue
        if fmt=="zip" and ("vial" in name or entry.get("id")==RESOURCE):
            return url,entry.get("id","")
    raise RuntimeError("No official PBA Red Vial ZIP in CKAN. Refusing fabricated road dataset")

def download(url,target):
    req=urllib.request.Request(url,headers={"User-Agent":"DriveLabAuto-OfficialIGN-PBA-Build/1"})
    total=0
    with urllib.request.urlopen(req,timeout=150) as response,target.open("wb") as out:
        if response.status!=200: raise RuntimeError(f"IGN download HTTP {response.status}")
        while True:
            chunk=response.read(1024*1024)
            if not chunk:break
            total+=len(chunk)
            if total>800_000_000:raise RuntimeError("IGN source larger than 800 MB")
            out.write(chunk)
    if total<10000 or not zipfile.is_zipfile(target):
        raise RuntimeError(f"Source not a valid official shapefile ZIP ({total} bytes)")
    with zipfile.ZipFile(target) as z:
        shapes=[n for n in z.namelist() if n.lower().endswith(".shp")]
    if not shapes:raise RuntimeError("Official IGN ZIP has no .shp road layer")
    print(f"Downloaded official IGN PBA from {url}; {total} bytes, shapes={shapes}")
    return shapes

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--out",required=True,type=pathlib.Path)
    args=p.parse_args()
    args.out.parent.mkdir(parents=True,exist_ok=True)
    url,_=resolve()
    download(url,args.out)
