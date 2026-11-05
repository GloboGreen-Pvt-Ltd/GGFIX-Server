# visual-search-service

Image-to-image device identification for the Partner App's Visual Device
Scanner ("Lens" mode). Given a captured or picked photo, it returns ranked
GGFIX catalog devices whose own product photos look like it. Matching uses
CLIP image embeddings, not filename or text matching.

Unlike the other services here it is **Python** (FastAPI + open_clip), not a
Maven module. It runs beside the JVMs on port **8199**.

| | |
|---|---|
| Endpoint | `POST /visual-search/device` (multipart: `file`, optional `ocrText`, `barcode`, `limit`) |
| Health | `GET /health` (details), `GET /actuator/health` (`{"status":"UP"}`, same as the Spring services) |
| Public URL | `https://api.ggfix.in/visual-search/visual-search/device` (nginx strips `/visual-search/`, like every other prefix) |
| Catalog source | `GET /master/brands`, `/master/models`, `/master/device-categories` (master-data-service, public reads) |
| Index | `data/embeddings.npy` + `data/embeddings_meta.json`, a flat local index (pgvector migration path below) |
| Auth | The app's normal `Authorization: Bearer` token, verified with the shared `JWT_SECRET`; optional `X-API-Key` |

## Running it locally

From the backend root (Windows):

```powershell
.\run-visual-search-dev.ps1            # first run creates .venv + installs requirements
.\run-visual-search-dev.ps1 -Reindex   # refresh the catalog index first
```

Or by hand:

```
cd services/visual-search-service
python -m venv .venv
./.venv/Scripts/pip install -r requirements.txt        # Windows
# source .venv/bin/activate && pip install -r requirements.txt   # macOS/Linux

./.venv/Scripts/python scripts/index_all.py            # build the index
./.venv/Scripts/python -m uvicorn app.main:app --host 0.0.0.0 --port 8199
```

The first full index run downloads and embeds every catalog product photo
(~2,900 images, about 6 minutes on a laptop CPU). After that only new or
changed images are embedded. The model weights (~600 MB) download from
Hugging Face on first start and are cached.

```
curl http://localhost:8199/health
# {"status":"ok","indexedImages":2875,"index":"ready","modelLoaded":true,"authRequired":false}
```

`--host 0.0.0.0` lets a phone on the same Wi-Fi reach it. Windows Firewall
needs an inbound rule for that (run as Administrator):

```
New-NetFirewallRule -DisplayName "GGFIX Visual Search (8199)" -Direction Inbound -Protocol TCP -LocalPort 8199 -Action Allow
```

### Tests

The tests stub out CLIP, so they need no torch install:

```
pip install -r requirements-dev.txt
python -m pytest -q
```

The deploy workflow runs them before every deploy.

### Indexing options

```
python scripts/index_all.py                 # index everything new/changed
python scripts/index_all.py --force          # re-embed the whole catalog
python scripts/index_all.py --limit 200      # bounded run, for a quick check
python scripts/index_all.py --concurrency 12 # parallel image downloads
```

A running API serves a rebuilt index on its next request, with no restart.
Devices deleted from the catalog are removed from the index. A device whose
image fails to download keeps its previous embedding and is retried on the
next run.

## Deployment (EC2)

> **Not wired into this repo's deploy yet.** `deploy/aws` here runs the Java
> services under systemd and has no `visual-search` compose profile, nginx
> `/visual-search/` route or health-gate entry. The steps below describe the
> Docker deploy this service was built for. Until it's ported, run the
> service by hand on a host with about 1.5 GB of free RAM.

Deployed by the same GitHub workflow as the Java services, as a Docker
container (`deploy/aws/docker-compose.yml`, profile `visual-search`), **only
on hosts that opt in**. It needs about 1.5 GB of RAM and 2 GB of disk for the
image, which a small instance already running the JVMs may not have.

1. On the host, set `VISUAL_SEARCH_ENABLED=1` in `/opt/repair-shop-saas/.env`.
   Deploys add the key as `0` if it is missing.
2. Re-run the deploy. `deploy-from-artifact.sh` builds the image on the host
   (the first build downloads torch and the CLIP weights, then later builds
   reuse the layer cache) and starts `ggfix-visual-search` on
   `127.0.0.1:8199`.
3. The container builds its own index in the background on first start
   (`VS_AUTO_INDEX=1`), then refreshes it every 24 h
   (`VS_REINDEX_INTERVAL_HOURS`). Until the first build finishes, scans
   answer 503 ("index has not been built yet"), which the app shows as
   "starting up".

`setup-nginx-tls.sh` adds the `/visual-search/` route to an existing
certbot-managed vhost. That vhost is otherwise never rewritten. The health
gate reports `visual-search-service` but only fails on it with
`REQUIRE_VISUAL_SEARCH=1`. A visual-search build or start failure never
fails the deploy, because the Java services are already live by then.

Operating it:

```bash
sudo docker logs -f ggfix-visual-search
sudo docker exec ggfix-visual-search python scripts/index_all.py   # re-index now
curl -s http://127.0.0.1:8199/health
```

## Wiring it to the app

In `GGFIX-Partner-App`, point `EXPO_PUBLIC_VISUAL_SEARCH_BASE` at the service:

| Where the service runs | `EXPO_PUBLIC_VISUAL_SEARCH_BASE` |
|---|---|
| EC2, through the edge | `https://api.ggfix.in/visual-search` |
| This PC on the LAN | `http://<LAN-IP>:8199` (or just set `EXPO_PUBLIC_API_HOST`) |

The app's call path stays `/visual-search/device` in both cases.

## Configuration

Environment variables, or a `.env` file next to this README (see
`.env.example`). Real environment variables win, and blank values mean "use
the default".

| Env var | Default | Meaning |
|---|---|---|
| `GGFIX_API_BASE` | `https://api.ggfix.in` | Catalog API origin |
| `JWT_SECRET` | *(unset)* | Shared Spring JWT secret. When set, scans need a valid Bearer token |
| `VS_API_KEY` | *(unset)* | Optional shared secret accepted as `X-API-Key` |
| `VS_HIGH_CONFIDENCE` | `0.85` | Cosine similarity ≥ this → "Device Found" |
| `VS_MEDIUM_CONFIDENCE` | `0.62` | ≥ this → "Possible matches"; below → "couldn't confidently identify" |
| `VS_TOP_K` | `5` | Default number of matches returned (max 20) |
| `VS_DATA_DIR` | `./data` | Index location (relative paths resolve against this folder) |
| `VS_AUTO_INDEX` | `0` (`1` in Docker deploy) | Build the index in the background when it is empty |
| `VS_REINDEX_INTERVAL_HOURS` | `0` (`24` in Docker deploy) | Periodic incremental re-index; 0 = never |
| `VS_MAX_CONCURRENT` | `2` | CLIP inferences allowed at once |
| `VS_MAX_UPLOAD_BYTES` | `15728640` | Upload size limit (15 MB) |
| `VS_CORS_ORIGINS` | `*` | Allowed browser origins (Expo web), comma separated |
| `VS_CLIP_MODEL` / `VS_CLIP_PRETRAINED` | `ViT-B-32` / `laion2b_s34b_b79k` | Changing these needs `--force` re-index (and a Dockerfile change) |

With neither `JWT_SECRET` nor `VS_API_KEY` set, the endpoint is open, and the
service logs a warning at startup. That setting is for local development only.

An exact model-number match (from OCR text or a barcode payload, checked
against the catalog's `modelNumber` field) wins outright, regardless of the
visual score. Codes are compared ignoring punctuation and spacing
("SM-L315F" = "SM L315F"). A code must match a whole word on the label, and
codes of 6+ characters may also sit inside a longer token such as
"SM-L315F/DS". When one code names several catalog entries, the visual
ranking decides. See `app/matching.py`.

## Productionising: moving the index into Postgres

The flat local index is a stand-in for pgvector, and replacing it is a fairly
direct port:

1. Add `image_embedding VECTOR(512)` (CLIP ViT-B-32's output size) to
   `master_models`, enable the `pgvector` extension, and add an `hnsw`/`ivfflat`
   index once a flat scan gets slow.
2. Have `app/indexer.py` upsert per-model embeddings into Postgres instead of
   `store.save()` (and trigger it on image upload, not just on a timer).
3. Replace `app/store.py`'s `search()` (`vectors @ query_vec`, top-k) with
   `ORDER BY image_embedding <=> :query_vector LIMIT :k`.

The app's contract (`POST /visual-search/device` and its response shape) does
not change.
