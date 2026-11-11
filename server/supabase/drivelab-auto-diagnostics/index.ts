// DriveLab Auto Receiver cloud CSV upload - QR enrollment, no PIN.
// Device key: 256-bit random only on T1, only its SHA-256 stored on server.
// GET QR claim activates a pending registration for the physically scanned screen.
// No read API for uploaded CSV; private RLS-protected records.
// API does not require a Supabase JWT because it enforces its own device authentication.
const BASE="https://wgqbvbzhdnkkrpkrohej.supabase.co/functions/v1/drivelab-auto-diagnostics";
const JSON_HEADERS={"content-type":"application/json; charset=utf-8","cache-control":"no-store","referrer-policy":"no-referrer"};
const HTML_HEADERS={"content-type":"text/html; charset=utf-8","cache-control":"no-store","referrer-policy":"no-referrer","x-robots-tag":"noindex, nofollow"};
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const HEX64=/^[0-9a-f]{64}$/;
const HEX48=/^[0-9a-f]{48}$/;
const FILE=/^(?:phev_modes|oem_callbacks|code_diag)_[0-9]{8}_[0-9]{6}\.csv$/;
const MAX_BODY=60000;
const result=(code:number,body:object)=>new Response(JSON.stringify(body),{status:code,headers:JSON_HEADERS});
const sha=async(x:string)=>Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(x))))
  .map(n=>n.toString(16).padStart(2,"0")).join("");
const constantTime=(a:string,b:string)=>{if(a.length!==b.length)return false;let d=0;for(let j=0;j<a.length;j++)d|=a.charCodeAt(j)^b.charCodeAt(j);return d===0};
const envUrl=Deno.env.get("SUPABASE_URL");
const serviceKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
function headers(){return {"content-type":"application/json","apikey":serviceKey!,"authorization":"Bearer "+serviceKey!}}
async function rest(path:string,init:RequestInit={}) {
  const response=await fetch(envUrl+"/rest/v1/"+path,{...init,headers:{...headers(),...(init.headers||{})}});
  if(!response.ok)throw new Error("db_"+response.status);
  const body=await response.text();
  return body?JSON.parse(body):null;
}
function safeId(id:unknown):id is string{return typeof id==="string"&&UUID.test(id)}
async function device(id:string) {
  const data=await rest("drivelab_auto_cloud_devices?device_id=eq."+encodeURIComponent(id)+
    "&select=device_id,auth_hash,activated,created_at,claim_hash,claim_until&limit=1");
  return Array.isArray(data)?data[0]:null;
}
async function authorized(id:string,secret:string|null) {
  if(!secret||!HEX64.test(secret))return null;
  const record=await device(id);
  if(!record||!constantTime(record.auth_hash,await sha(secret)))return null;
  return record;
}
const errorHtml="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='font-family:sans-serif;padding:32px'><h2>DriveLab Auto</h2><p>El QR venció o ya fue utilizado. Generá otro desde la pantalla de la T1.</p></body></html>";
const okHtml="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='font-family:sans-serif;padding:32px'><h2>DriveLab Auto</h2><p>✅ T1 autorizada. Las futuras exportaciones llegarán al servidor automáticamente, sin PIN.</p><p>Podés volver a la camioneta.</p></body></html>";
Deno.serve(async(req:Request)=>{
 if(!envUrl||!serviceKey)return result(503,{error:"service_unconfigured"});
 try{
  const u=new URL(req.url);
  if(req.method==="GET"){
    const claim=u.searchParams.get("claim")||"";
    if(!HEX48.test(claim))return new Response(errorHtml,{status:404,headers:HTML_HEADERS});
    const digest=await sha(claim);
    const rows=await rest("drivelab_auto_cloud_devices?claim_hash=eq."+digest+
      "&activated=eq.false&claim_until=gt."+encodeURIComponent(new Date().toISOString())+
      "&select=device_id&limit=1");
    if(!Array.isArray(rows)||rows.length!==1)
      return new Response(errorHtml,{status:410,headers:HTML_HEADERS});
    const response=await rest("drivelab_auto_cloud_devices?device_id=eq."+rows[0].device_id+
      "&claim_hash=eq."+digest+"&activated=eq.false",{
      method:"PATCH",headers:{"prefer":"return=representation"},
      body:JSON.stringify({activated:true,claim_hash:null,claim_until:null})
    });
    return new Response(Array.isArray(response)&&response.length===1?okHtml:errorHtml,
      {status:Array.isArray(response)&&response.length===1?200:410,headers:HTML_HEADERS});
  }
  if(req.method!=="POST")return result(405,{error:"method_not_allowed"});
  if(Number(req.headers.get("content-length")||0)>MAX_BODY)return result(413,{error:"too_large"});
  const raw=await req.text();
  if(new TextEncoder().encode(raw).length>MAX_BODY)return result(413,{error:"too_large"});
  let b:Record<string,unknown>;
  try{b=JSON.parse(raw);if(!b||Array.isArray(b)||typeof b!=="object")throw Error();}
  catch{return result(400,{error:"bad_json"});}
  const op=b.action,id=b.deviceId;
  if(!safeId(id))return result(422,{error:"bad_device_id"});
  if(op==="start"){
    const hash=b.authHash,claim=b.claim;
    if(typeof hash!=="string"||!HEX64.test(hash)||
       typeof claim!=="string"||!HEX48.test(claim))
      return result(422,{error:"invalid_registration"});
    const existing=await device(id);
    if(existing&&(!constantTime(existing.auth_hash,hash)||existing.activated))
      return result(409,{error:"already_registered"});
    const ip=(req.headers.get("cf-connecting-ip")||req.headers.get("x-forwarded-for")||"unknown")
      .split(",")[0].trim().slice(0,128);
    const ipHash=await sha(ip);
    if(!existing){
      const rows=await rest("drivelab_auto_cloud_devices?origin_hash=eq."+ipHash+
        "&created_at=gt."+encodeURIComponent(new Date(Date.now()-3600000).toISOString())+
        "&select=device_id&limit=5");
      if(Array.isArray(rows)&&rows.length>=4)return result(429,{error:"registration_rate_limit"});
    }
    const row={device_id:id,auth_hash:hash,claim_hash:await sha(claim),
      claim_until:new Date(Date.now()+300000).toISOString(),origin_hash:ipHash,
      activated:false};
    if(existing){
      await rest("drivelab_auto_cloud_devices?device_id=eq."+id,{
        method:"PATCH",body:JSON.stringify({claim_hash:row.claim_hash,claim_until:row.claim_until})
      });
    }else{
      await rest("drivelab_auto_cloud_devices",{
        method:"POST",body:JSON.stringify(row)
      });
    }
    return result(201,{ok:true,url:BASE+"?claim="+claim,expiresSeconds:300});
  }
  const token=req.headers.get("x-drivelab-device-key");
  const record=await authorized(id,token);
  if(!record)return result(401,{error:"device_not_authorized"});
  if(op==="status")return result(200,{ok:true,active:record.activated===true});
  if(op==="trip_batch"){
    if(record.activated!==true)return result(403,{error:"scan_qr_first"});
    const trip=b.tripId,offset=b.rowOffset,samples=b.samples;
    if(typeof trip!=="string"||!/^[0-9]{8}_[0-9]{6}$/.test(trip)||
       typeof offset!=="number"||!Number.isInteger(offset)||offset<0||
       !Array.isArray(samples)||samples.length<1||samples.length>50)
      return result(422,{error:"bad_trip_batch"});
    const limits:Record<string,[number,number]>={
      ts:[1600000000000,2200000000000],gear:[-1,4],speed:[0,300],
      fuel:[0,75],socInternal:[0,110],socDisplay:[0,110],
      voltage:[0,1500],current:[-2000,2000],
      rpm:[0,12000],motorOn:[-1,1],tripKm:[0,20000],
      gasProxy:[0,1000],hvOut:[0,10000],hvIn:[0,10000],
      oemElectricRolling50:[-200,200],oemFuelRolling50:[0,200],
      oemElectricAverage500m:[-300,300],oemFuelAverage500m:[0,500],
      rpmValid1034:[-1,100],
      oemElectric500mInt:[-50000,50000],oemFuel500mInt:[-1,50000],
      voltage1063Int:[-1,1500],current1064Int:[-2000,2000],
      raw1090Float:[-100000,100000],raw1090Int:[-1000000000,1000000000]
    };
    const cleaned:Record<string,number|null>[]=[];
    for(const sample of samples){
      if(!sample||typeof sample!=="object"||Array.isArray(sample))
        return result(422,{error:"bad_trip_sample"});
      const src=sample as Record<string,unknown>;
      const out:Record<string,number|null>={};
      for(const [key,range] of Object.entries(limits)){
        const value=src[key];
        out[key]=typeof value==="number"&&Number.isFinite(value)&&
          value>=range[0]&&value<=range[1]?value:null;
      }
      if(out.ts===null)return result(422,{error:"missing_trip_timestamp"});
      cleaned.push(out);
    }
    await rest("drivelab_auto_trip_batches?on_conflict=trip_id,row_offset",{
      method:"POST",headers:{"prefer":"resolution=ignore-duplicates,return=minimal"},
      body:JSON.stringify({trip_id:trip,row_offset:offset,
        row_count:cleaned.length,snapshot:cleaned})
    });
    return result(200,{ok:true,tripId:trip,rowOffset:offset,count:cleaned.length});
  }
  if(op!=="upload")return result(422,{error:"unknown_action"});
  if(record.activated!==true)return result(403,{error:"scan_qr_first"});
  const {fileId,filename,chunkIndex,chunkTotal,sha256,content}=b;
  if(!safeId(fileId)||typeof filename!=="string"||!FILE.test(filename)||
     typeof sha256!=="string"||!HEX64.test(sha256)||
     !Number.isInteger(chunkIndex)||!Number.isInteger(chunkTotal)||
     typeof chunkIndex!=="number"||typeof chunkTotal!=="number"||
     chunkIndex<0||chunkTotal<1||chunkTotal>128||chunkIndex>=chunkTotal||
     typeof content!=="string"||new TextEncoder().encode(content).length<1||
     new TextEncoder().encode(content).length>18000)
    return result(422,{error:"invalid_csv_chunk"});
  // Idempotent chunk uploads: same ID/index returns success without duplication.
  await rest("drivelab_auto_diagnostic_csv_chunks?on_conflict=file_id,chunk_index",{
    method:"POST",headers:{"prefer":"resolution=ignore-duplicates,return=minimal"},
    body:JSON.stringify({device_id:id,file_id:fileId,filename,chunk_index:chunkIndex,
      chunk_total:chunkTotal,csv_chunk:content,file_sha256:sha256,source:"drivelab-auto-receiver"})
  });
  await rest("drivelab_auto_cloud_devices?device_id=eq."+id,{
    method:"PATCH",body:JSON.stringify({last_uploaded_at:new Date().toISOString()})
  });
  return result(200,{ok:true,filename,chunkIndex,chunkTotal});
 }catch(e){
  const err=e instanceof Error?e.message:"unknown";
  return result(503,{error:err.startsWith("db_")?err:"temporary_error"});
 }
});
