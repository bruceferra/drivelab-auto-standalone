# DriveLab Cloud — Hostinger VPS

Independent ingestion service for DriveLab diagnostics; **not** part of the ProManager website or PM2 application.

## Existing infrastructure verified in ProManager repository

- VPS SSH: `servidor.eventing.com.ar:1977`.
- Production ProManager checkout: `/var/www/promanager` (do not modify).
- Existing Windows self-hosted runner: `promanager-android-windows`.
- Existing Windows-to-VPS SSH credential is configured on the runner. **It is not available as plaintext in this repository.**
- DriveLab publication path referenced in existing deployment: `/var/www/drivelab/public/t1/`.

## Target

- Proposed HTTPS host: `cloud.promanager.com.ar` (DNS and TLS **not yet verified**).
- Service: `server/hostinger/drivelab_cloud.py`, listening **only on 127.0.0.1:8787**.
- Storage: `/var/lib/drivelab-cloud`, outside the public web root.
- Endpoint: `POST /v1/upload`; headers `Authorization: Bearer <token>`, `X-DriveLab-Category`, `X-DriveLab-Filename`, `X-Content-SHA256`. Raw file body, maximum 8 MiB.
- Categories: informes, screenshots, cluster, optimizador, viajes, energia, errores, versiones.
- Files stored as `category/YYYY/MM/<sha256>_<filename>`, immutable and idempotent.
- Health check: `GET /health`.

## Deployment prerequisites / security gates

1. Confirm VPS connectivity via the **existing** self-hosted runner and SSH delegation, without printing secrets.
2. Create a dedicated unprivileged service account and writable private storage; keep ProManager's checkout and PM2 service untouched.
3. Provision a unique random 32+ character upload token in an environment file with restricted permissions; never commit or print it.
4. Configure DNS and TLS for `cloud.promanager.com.ar`; reverse proxy only `/v1/upload` to loopback, with body-size and rate limits. Do not expose the storage directory or the Python port.
5. Start service under systemd with restart policy and filesystem hardening.
6. Run integration tests against loopback and then HTTPS: auth rejection, malformed filename, digest mismatch, successful upload, idempotent retry, private storage.
7. Only after a verified server receipt, migrate the Android client from Supabase to the new Hostinger transport. Keep existing export path until verified.
8. Screenshot support needs explicit Android MediaProjection consent; no covert capture. Avoid persisting screenshots in public/shared media storage.

**Status:** server source committed; DNS, service deployment, HTTPS, Android client integration, and end-to-end verification pending.
