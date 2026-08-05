#!/usr/bin/env node
/**
 * Moves master_models images off Cloudinary (and off inline base64) onto
 * media.ggfix.in, filing every object under the same key layout new uploads use.
 *
 *   before  https://res.cloudinary.com/dg6c0g4gi/image/upload/v1784730702/ggfix/master/models/sizjvaqkqfwd9lhdrxy3.png
 *   after   https://media.ggfix.in/mobile/oneplus/nord-series/oneplus-nord-2-5g/main-3f9c11ab.png
 *
 * The key is derived exactly as MediaKeys.java + Slugify.java derive it —
 * {category}/{brand}/{series}/{model}/main-{8 hex}.{ext} — so a row migrated here
 * and a row uploaded through /master/models/with-image are indistinguishable
 * afterwards. Any drift between the two implementations would scatter the
 * catalogue across two folder shapes, so the rules are mirrored deliberately and
 * are the first thing to check if a key ever looks wrong.
 *
 * ---------------------------------------------------------------------------
 * Phases — each is separately runnable and safely repeatable
 * ---------------------------------------------------------------------------
 *   plan    read the catalogue, derive a key per row, write manifest.json.
 *           Touches nothing. Reports every row it had to skip and why.
 *   fetch   download each Cloudinary image (or decode its data: URI) into
 *           staging/<key>, so the staging tree IS the bucket layout.
 *           Re-running only fetches what is missing.
 *   push    aws s3 sync the staging tree into the bucket, one pass per format
 *           so Content-Type and Cache-Control are set explicitly rather than
 *           guessed from the extension. THIS IS THE ONLY PHASE NEEDING AWS.
 *   verify  HEAD every new public URL through the CDN.
 *   apply   point the rows at the new objects, in ONE transaction.
 *
 * Ordering is not arbitrary: bytes reach S3 and are proven fetchable BEFORE any
 * row is repointed, because a row referencing a missing object is a broken image
 * on every storefront, while an object no row references yet is invisible. The
 * same reasoning as ModelMediaService's upload-then-insert.
 *
 * Nothing is destructive until `apply`, and `apply` writes rollback.sql first.
 * The old Cloudinary objects are never deleted — this only stops referencing them.
 *
 * ---------------------------------------------------------------------------
 * Usage
 * ---------------------------------------------------------------------------
 *   node migrate-model-images.mjs plan   [--target models,brands,categories,banners]
 *                                        [--include-base64] [--staging DIR]
 *   node migrate-model-images.mjs fetch  [--staging DIR] [--concurrency 8]
 *   node migrate-model-images.mjs push   [--staging DIR] [--profile NAME] [--dry-run]
 *   node migrate-model-images.mjs verify [--staging DIR]
 *   node migrate-model-images.mjs apply  [--staging DIR] [--dry-run]
 *
 * Database settings come from the environment (DB_HOST, DB_PORT, DB_NAME,
 * DB_USER, DB_PASSWORD) — the same names .env.rds already uses. Requires psql
 * and, for `push`, the aws CLI. No npm dependencies, on purpose: this has to be
 * runnable on the EC2 host, where the instance role is the only thing that can
 * write to the bucket.
 */

import { spawn, spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import https from 'node:https';
import http from 'node:http';

/* -------------------------------------------------------------------------- */
/* Configuration                                                              */
/* -------------------------------------------------------------------------- */

// Same names application.yml binds app.media.* from, so running this on the EC2
// host with the service's own environment sourced needs no extra configuration.
const BUCKET = process.env.MEDIA_S3_BUCKET || process.env.AWS_S3_BUCKET || 'ggfix-media-1762';
const REGION = process.env.MEDIA_S3_REGION || process.env.AWS_REGION || 'ap-south-1';
const PUBLIC_BASE = (
  process.env.MEDIA_PUBLIC_BASE_URL ||
  process.env.AWS_S3_BASE_URL ||
  'https://media.ggfix.in'
).replace(/\/+$/, '');

/** Mirrors MediaProperties.cacheControl. Keys carry a random suffix and are never
 *  rewritten in place, so objects are immutable and need no CDN invalidation. */
const CACHE_CONTROL = 'public,max-age=31536000,immutable';

/** Mirrors MediaProperties.maxImageBytes. Oversized sources are reported, not stored. */
const MAX_IMAGE_BYTES = Number(process.env.MEDIA_MAX_IMAGE_BYTES || 5 * 1024 * 1024);

const PSQL =
  process.env.PSQL_PATH ||
  (process.platform === 'win32' ? 'C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe' : 'psql');

const AWS_CLI = process.env.AWS_CLI_PATH || 'aws';

/**
 * Named profile for the aws CLI, set by --profile.
 *
 * The bucket lives in a different AWS account from the one a developer machine is
 * usually configured for, so `push` against the default profile fails with a 403
 * that reads like a permissions bug rather than a wrong-account one. Naming the
 * profile explicitly makes which account is being written to visible in the
 * command, and `push` prints the resolved identity before uploading anything.
 */
let AWS_PROFILE = process.env.AWS_PROFILE || null;

/** Prepends --profile so every aws invocation targets the same account. */
const awsArgs = (args) => (AWS_PROFILE ? ['--profile', AWS_PROFILE, ...args] : args);

const DB = {
  host: process.env.DB_HOST,
  port: process.env.DB_PORT || '5432',
  name: process.env.DB_NAME || 'ggfix_server',
  user: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD,
};

/* -------------------------------------------------------------------------- */
/* Slugify — a faithful port of Slugify.java                                  */
/* -------------------------------------------------------------------------- */

const MAX_SEGMENT_LENGTH = 120;

/**
 * @returns the slug, or null when the input has no URL-safe characters. Callers
 *   MUST treat null as a failure rather than substituting a placeholder: a silent
 *   fallback would collapse two different models onto one folder.
 */
function slugify(raw) {
  if (raw === null || raw === undefined) return null;
  // NFD splits "é" into "e" + combining accent so the accent can be dropped on its
  // own; without this the whole character is stripped and "Poco Ç" becomes "poco".
  const normalized = String(raw).normalize('NFD').replace(/\p{M}+/gu, '');
  let slug = normalized
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  if (!slug) return null;
  if (slug.length > MAX_SEGMENT_LENGTH) {
    slug = slug.slice(0, MAX_SEGMENT_LENGTH).replace(/-+$/, '');
  }
  return slug;
}

/** 8 hex chars, as MediaKeys.shortId() produces. */
const shortId = () => randomUUID().replace(/-/g, '').slice(0, 8);

/* -------------------------------------------------------------------------- */
/* Format detection — a faithful port of ImageValidator.detectType             */
/* -------------------------------------------------------------------------- */

const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/**
 * Trusts the bytes, never the URL suffix or a Content-Type header — Cloudinary
 * happily serves a .png URL whose body is a JPEG, and the stored extension has to
 * match what a browser will actually decode.
 *
 * @returns {{ext: string, contentType: string} | null}
 */
function detectType(buf) {
  if (buf.length < 4) return null;
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) {
    return { ext: 'jpg', contentType: 'image/jpeg' };
  }
  if (buf.length >= 8 && buf.subarray(0, 8).equals(PNG_MAGIC)) {
    return { ext: 'png', contentType: 'image/png' };
  }
  // WebP is a RIFF container: "RIFF" <4-byte length> "WEBP".
  if (
    buf.length >= 12 &&
    buf.subarray(0, 4).toString('latin1') === 'RIFF' &&
    buf.subarray(8, 12).toString('latin1') === 'WEBP'
  ) {
    return { ext: 'webp', contentType: 'image/webp' };
  }
  return null;
}

/**
 * True for an AVIF/HEIF box header. Android's Image component renders these blank
 * (see normalizeDeviceImageUrl in the apps), and the backend validator rejects the
 * format outright, so these are re-fetched as JPEG rather than carried across.
 */
function isAvif(buf) {
  return buf.length >= 12 && buf.subarray(4, 8).toString('latin1') === 'ftyp';
}

/** Rewrites a Cloudinary delivery URL to ask for JPEG instead of the stored format. */
function cloudinaryAsJpeg(url) {
  return url.replace('/image/upload/', '/image/upload/f_jpg,q_auto/');
}

/* -------------------------------------------------------------------------- */
/* psql plumbing                                                              */
/* -------------------------------------------------------------------------- */

function connString() {
  if (!DB.host) die('DB_HOST is not set. Source .env.rds (or export DB_* yourself) first.');
  if (!DB.password) die('DB_PASSWORD is not set.');
  return `host=${DB.host} port=${DB.port} dbname=${DB.name} user=${DB.user} sslmode=require`;
}

/**
 * Runs a .sql file through psql. Output goes to a file rather than stdout so a
 * result set of any size arrives intact — psql's own \o, not a pipe we have to
 * buffer and re-parse.
 */
function psqlFile(sqlPath, { quiet = false } = {}) {
  const res = spawnSync(PSQL, [connString(), '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-q', '-f', sqlPath], {
    env: { ...process.env, PGPASSWORD: DB.password },
    encoding: 'utf8',
    maxBuffer: 256 * 1024 * 1024,
  });
  if (res.error) die(`Could not run psql (${PSQL}): ${res.error.message}`);
  if (res.status !== 0) die(`psql failed:\n${res.stderr || res.stdout}`);
  if (!quiet && res.stderr?.trim()) console.error(res.stderr.trim());
  return res.stdout;
}

/** Runs one statement and returns stdout, unaligned and untitled. */
function psqlQuery(sql) {
  const tmp = path.join(stagingDir(), `.query-${shortId()}.sql`);
  fs.mkdirSync(path.dirname(tmp), { recursive: true });
  fs.writeFileSync(tmp, sql, 'utf8');
  try {
    return psqlFile(tmp);
  } finally {
    fs.rmSync(tmp, { force: true });
  }
}

/* -------------------------------------------------------------------------- */
/* Phase: plan                                                                */
/* -------------------------------------------------------------------------- */

/**
 * Everything that differs between the four image-bearing tables, in one place.
 *
 * They share the whole pipeline — the only real differences are which rows to
 * read, how the key is shaped, and which columns exist to write back. Keeping
 * that as data rather than four near-identical code paths is what stops the
 * taxonomy tables drifting away from the model tables the way the upload
 * endpoints already have.
 *
 * `columns` lists ONLY what the table actually has. master_banners never got the
 * S3 key columns, and only master_models has media_folder_key, so writing a
 * uniform column list would fail on three tables out of four.
 */
const MASTER_BRANDS_ROOT = 'master/brands';
const MASTER_CATEGORIES_ROOT = 'master/categories';
// Deliberately NOT under master/: MediaKeys.BANNER_ROOT in common-media is a bare
// "banner", and a migration that invented its own prefix would leave the existing
// rows somewhere no future upload ever writes to.
const MASTER_BANNERS_ROOT = 'banner';

const TARGETS = {
  models: {
    table: 'master_models',
    label: 'model',
    columns: ['image_key', 'media_folder_key', 'image_original_name', 'image_content_type', 'image_size_bytes'],
    // The catalogue layout: every model of one series shares the first three
    // segments, so the bucket browses like the catalogue.
    select: `
      SELECT m.id::text AS id, m.name AS name, m.image_url AS image_url,
             c.name AS category_name, b.name AS brand_name, s.name AS series_name
      FROM master_models m
      LEFT JOIN master_device_categories c ON c.id = m.category_id
      LEFT JOIN master_brands            b ON b.id = m.brand_id
      LEFT JOIN master_device_series      s ON s.id = m.series_id
      WHERE __FILTER__
      ORDER BY c.name, b.name, s.name, m.name`,
    // Returns { folder } or { error } — a row that cannot be filed is reported,
    // never given a fallback folder a later API upload would not reproduce.
    folderFor(row) {
      const parts = [
        ['category', row.category_name],
        ['brand', row.brand_name],
        ['series', row.series_name],
        ['model name', row.name],
      ];
      const slugs = [];
      for (const [field, value] of parts) {
        const slug = slugify(value);
        if (!slug) {
          return { error: `${field} is missing or has no URL-safe characters (${JSON.stringify(value)})` };
        }
        slugs.push(slug);
      }
      return { folder: slugs.join('/') };
    },
    keyFor: (entry, ext) => `${entry.folder}/main-${shortId()}.${ext}`,
  },

  brands: {
    table: 'master_brands',
    label: 'brand',
    columns: ['image_key', 'image_original_name', 'image_content_type', 'image_size_bytes'],
    select: `
      SELECT id::text AS id, name AS name, image_url AS image_url
      FROM master_brands WHERE __FILTER__ ORDER BY name`,
    // Flat, matching MediaKeys.masterBrandImageKey: master/brands/vivo-4c7d1e02.png
    folderFor: () => ({ folder: MASTER_BRANDS_ROOT }),
    keyFor: (entry, ext) => `${MASTER_BRANDS_ROOT}/${slugify(entry.label)}-${shortId()}.${ext}`,
  },

  categories: {
    table: 'master_device_categories',
    label: 'category',
    columns: ['image_key', 'image_original_name', 'image_content_type', 'image_size_bytes'],
    select: `
      SELECT id::text AS id, name AS name, image_url AS image_url
      FROM master_device_categories WHERE __FILTER__ ORDER BY sort_order, name`,
    folderFor: () => ({ folder: MASTER_CATEGORIES_ROOT }),
    keyFor: (entry, ext) => `${MASTER_CATEGORIES_ROOT}/${slugify(entry.label)}-${shortId()}.${ext}`,
  },

  banners: {
    table: 'master_banners',
    label: 'banner',
    // No key columns on this table — image_url is all there is to update.
    columns: [],
    select: `
      SELECT id::text AS id, title AS name, image_url AS image_url
      FROM master_banners WHERE __FILTER__ ORDER BY sort_order, title`,
    folderFor: () => ({ folder: MASTER_BANNERS_ROOT }),
    keyFor: (entry, ext) => `${MASTER_BANNERS_ROOT}/${slugify(entry.label)}-${shortId()}.${ext}`,
  },
};

function phasePlan(opts) {
  fs.mkdirSync(stagingDir(), { recursive: true });

  const names = (opts.target || 'models').split(',').map((s) => s.trim()).filter(Boolean);
  for (const n of names) {
    if (!TARGETS[n]) die(`Unknown --target "${n}". Valid: ${Object.keys(TARGETS).join(', ')}.`);
  }

  const host = PUBLIC_BASE.replace(/^https?:\/\//, '');
  const entries = [];
  const skipped = [];

  for (const name of names) {
    const target = TARGETS[name];
    // Never re-migrate a row already pointing at the CDN — that is what makes the
    // whole script safe to re-run after a partial failure.
    const filter =
      `image_url IS NOT NULL AND image_url <> '' AND image_url NOT LIKE '%${host}%' ` +
      `AND (image_url LIKE '%res.cloudinary.com%'` +
      (opts.includeBase64 ? ` OR image_url LIKE 'data:image/%'` : '') +
      `)`;

    const sql = `SELECT json_agg(row_to_json(t)) FROM (${target.select.replace('__FILTER__', filter)}) t;`;
    const out = psqlQuery(sql).trim();
    const rows = out ? JSON.parse(out) : [];
    console.log(`  ${name.padEnd(11)} ${String(rows.length).padStart(5)} candidate row(s)`);

    for (const row of rows) {
      const { folder, error } = target.folderFor(row);
      if (error) {
        skipped.push({ target: name, id: row.id, label: row.name, reason: error });
        continue;
      }
      entries.push({
        target: name,
        id: row.id,
        label: row.name,
        source: row.image_url,
        sourceKind: row.image_url.startsWith('data:') ? 'base64' : 'cloudinary',
        folder,
        // Extension and key stay null until `fetch` proves the real format — the
        // URL suffix is not evidence of what the bytes actually are.
        key: null,
        contentType: null,
        sizeBytes: null,
        originalName: originalNameOf(row.image_url),
        status: 'planned',
      });
    }
  }

  const manifest = {
    createdAt: new Date().toISOString(),
    database: `${DB.host}/${DB.name}`,
    bucket: BUCKET,
    region: REGION,
    publicBase: PUBLIC_BASE,
    targets: names,
    includeBase64: !!opts.includeBase64,
    entries,
    skipped,
  };
  writeManifest(manifest);

  const byKind = entries.reduce((a, e) => ({ ...a, [e.sourceKind]: (a[e.sourceKind] || 0) + 1 }), {});
  console.log('');
  console.log(`  planned  ${entries.length}  (${JSON.stringify(byKind)})`);
  console.log(`  skipped  ${skipped.length}`);
  if (skipped.length) {
    console.log('');
    console.log('  Skipped (these keep their current image_url):');
    for (const s of skipped.slice(0, 20)) console.log(`    [${s.target}] ${s.label} — ${s.reason}`);
    if (skipped.length > 20) console.log(`    ... and ${skipped.length - 20} more, see manifest.json`);
  }
  console.log('');
  console.log(`Manifest: ${manifestPath()}`);
  console.log('Next: node migrate-model-images.mjs fetch');
}

/** The Cloudinary leaf filename, kept for image_original_name (audit only). */
function originalNameOf(url) {
  if (url.startsWith('data:')) return null;
  try {
    return decodeURIComponent(new URL(url).pathname.split('/').pop()) || null;
  } catch {
    return null;
  }
}

/* -------------------------------------------------------------------------- */
/* Phase: fetch                                                               */
/* -------------------------------------------------------------------------- */

async function phaseFetch(opts) {
  const manifest = readManifest();
  const pending = manifest.entries.filter((e) => e.status !== 'fetched' || !stagedExists(e));
  console.log(`${pending.length} of ${manifest.entries.length} image(s) still to fetch.`);

  let done = 0;
  let failed = 0;
  const concurrency = Math.max(1, Number(opts.concurrency) || 8);

  await runPool(pending, concurrency, async (entry) => {
    try {
      let bytes = await loadBytes(entry.source);
      let type = detectType(bytes);

      // AVIF renders blank on Android and the backend validator would reject it;
      // ask Cloudinary to transcode rather than carrying the format across.
      if (!type && isAvif(bytes) && entry.sourceKind === 'cloudinary') {
        bytes = await loadBytes(cloudinaryAsJpeg(entry.source));
        type = detectType(bytes);
        if (type) entry.transcodedFrom = 'avif';
      }

      if (!type) throw new Error('not a JPEG, PNG or WebP (first bytes did not match any signature)');
      if (bytes.length === 0) throw new Error('source is empty');
      if (bytes.length > MAX_IMAGE_BYTES) {
        throw new Error(`${(bytes.length / 1048576).toFixed(2)} MB exceeds the ${(MAX_IMAGE_BYTES / 1048576).toFixed(2)} MB ceiling`);
      }

      // The key is minted here, once the format is known, and then reused by every
      // later phase — regenerating it per run would orphan whatever `push` uploaded.
      entry.key = TARGETS[entry.target].keyFor(entry, type.ext);
      entry.contentType = type.contentType;
      entry.sizeBytes = bytes.length;
      entry.publicUrl = `${PUBLIC_BASE}/${entry.key}`;

      const dest = path.join(stagingRoot(), ...entry.key.split('/'));
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, bytes);

      entry.status = 'fetched';
      delete entry.error;
      done += 1;
    } catch (e) {
      entry.status = 'failed';
      entry.error = String(e.message || e);
      failed += 1;
    }
    if ((done + failed) % 50 === 0) {
      process.stdout.write(`  ${done + failed}/${pending.length}\r`);
      writeManifest(manifest);
    }
  });

  writeManifest(manifest);
  const ready = manifest.entries.filter((e) => e.status === 'fetched');
  const totalBytes = ready.reduce((n, e) => n + (e.sizeBytes || 0), 0);

  console.log('');
  console.log(`  fetched  ${done}`);
  console.log(`  failed   ${failed}`);
  console.log(`  ready    ${ready.length} object(s), ${(totalBytes / 1048576).toFixed(1)} MB`);
  for (const e of manifest.entries.filter((x) => x.status === 'failed').slice(0, 15)) {
    console.log(`    [${e.target}] ${e.label} — ${e.error}`);
  }
  console.log('');
  console.log(`Staged under: ${stagingRoot()}`);
  console.log('Next: node migrate-model-images.mjs push');
}

/** Fetches an http(s) URL or decodes a data: URI into a Buffer. */
async function loadBytes(source) {
  if (source.startsWith('data:')) {
    const comma = source.indexOf(',');
    if (comma < 0) throw new Error('malformed data: URI');
    const meta = source.slice(5, comma);
    const payload = source.slice(comma + 1);
    return meta.includes(';base64')
      ? Buffer.from(payload, 'base64')
      : Buffer.from(decodeURIComponent(payload), 'binary');
  }
  return download(source);
}

/** GET with redirect following and a short retry — Cloudinary occasionally 5xxs. */
function download(url, { redirects = 5, attempt = 1 } = {}) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('http://') ? http : https;
    const req = client.get(url, { timeout: 30000 }, (res) => {
      const { statusCode, headers } = res;
      if (statusCode >= 300 && statusCode < 400 && headers.location) {
        res.resume();
        if (redirects <= 0) return reject(new Error('too many redirects'));
        return resolve(download(new URL(headers.location, url).toString(), { redirects: redirects - 1 }));
      }
      if (statusCode !== 200) {
        res.resume();
        return reject(new Error(`HTTP ${statusCode}`));
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
      res.on('error', reject);
    });
    req.on('timeout', () => req.destroy(new Error('timed out')));
    req.on('error', (e) => {
      if (attempt >= 3) return reject(e);
      setTimeout(
        () => resolve(download(url, { redirects, attempt: attempt + 1 })),
        250 * 2 ** attempt,
      );
    });
  });
}

/** Bounded-concurrency map. Keeps the pool full rather than working in batches. */
async function runPool(items, limit, worker) {
  let cursor = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const item = items[cursor++];
      await worker(item);
    }
  });
  await Promise.all(runners);
}

/* -------------------------------------------------------------------------- */
/* Phase: push                                                                */
/* -------------------------------------------------------------------------- */

/**
 * One sync per format. `aws s3 sync` would guess Content-Type from the extension,
 * which happens to be right here — but the guess is silent when it is wrong, and
 * an object served as application/octet-stream downloads instead of rendering.
 * Setting it explicitly costs three passes and removes the guess.
 */
async function phasePush(opts) {
  const manifest = readManifest();
  const ready = manifest.entries.filter((e) => e.status === 'fetched' && e.key);
  if (!ready.length) die('Nothing staged. Run `fetch` first.');

  const missing = ready.filter((e) => !stagedExists(e));
  if (missing.length) {
    die(`${missing.length} staged file(s) are gone (e.g. ${missing[0].key}). Re-run \`fetch\`.`);
  }

  console.log(`Pushing ${ready.length} object(s) to s3://${BUCKET}/ (${REGION}).`);
  requireAws();

  const formats = [
    { ext: 'jpg', contentType: 'image/jpeg' },
    { ext: 'png', contentType: 'image/png' },
    { ext: 'webp', contentType: 'image/webp' },
  ];

  for (const fmt of formats) {
    const count = ready.filter((e) => e.key.endsWith(`.${fmt.ext}`)).length;
    if (!count) continue;

    const args = [
      's3', 'sync', stagingRoot(), `s3://${BUCKET}/`,
      '--exclude', '*',
      '--include', `*.${fmt.ext}`,
      '--content-type', fmt.contentType,
      '--cache-control', CACHE_CONTROL,
      '--region', REGION,
      '--only-show-errors',
    ];
    if (opts.dryRun) args.push('--dryrun');

    console.log(`  ${fmt.ext.padEnd(4)} ${String(count).padStart(5)} object(s)...`);
    const code = await runInherit(AWS_CLI, awsArgs(args));
    if (code !== 0) die(`aws s3 sync failed for *.${fmt.ext} (exit ${code}).`);
  }

  if (opts.dryRun) {
    console.log('\nDry run — nothing was uploaded.');
    return;
  }
  for (const e of ready) e.status = 'pushed';
  writeManifest(manifest);
  console.log('\nNext: node migrate-model-images.mjs verify');
}

/**
 * Proves, before uploading a single byte, that the credentials in play can write
 * to this bucket. A plain sts:GetCallerIdentity is not enough — it succeeds for
 * ANY valid credentials, including the wrong account's, and the failure then
 * surfaces 1,133 objects later as a wall of 403s.
 */
function requireAws() {
  const id = spawnSync(AWS_CLI, awsArgs(['sts', 'get-caller-identity']), { encoding: 'utf8' });
  if (id.error) die(`aws CLI not found (${AWS_CLI}): ${id.error.message}`);
  if (id.status !== 0) die(`aws CLI has no usable credentials:\n${(id.stderr || '').trim()}`);

  const { Arn, Account } = JSON.parse(id.stdout);
  console.log(`  identity: ${Arn}  (account ${Account})`);

  const probe = spawnSync(
    AWS_CLI,
    awsArgs(['s3api', 'head-bucket', '--bucket', BUCKET, '--region', REGION]),
    { encoding: 'utf8' },
  );
  if (probe.status !== 0) {
    die(
      `Account ${Account} cannot reach s3://${BUCKET}:\n${(probe.stderr || '').trim()}\n\n` +
        `The bucket is owned by a different account. Configure a profile for that one\n` +
        `(aws configure --profile ggfix-media) and re-run with --profile ggfix-media.`,
    );
  }
}

function runInherit(cmd, args) {
  return new Promise((resolve) => {
    const child = spawn(cmd, args, { stdio: 'inherit' });
    child.on('close', resolve);
    child.on('error', (e) => die(`${cmd} failed: ${e.message}`));
  });
}

/* -------------------------------------------------------------------------- */
/* Phase: verify                                                              */
/* -------------------------------------------------------------------------- */

/**
 * Proves every object is fetchable through the CDN — not just present in the
 * bucket. A CloudFront/OAC misconfiguration serves 403 for objects that uploaded
 * perfectly well, and finding that out after `apply` means a blank catalogue.
 */
async function phaseVerify() {
  const manifest = readManifest();
  const pushed = manifest.entries.filter((e) => e.status === 'pushed' || e.status === 'applied');
  if (!pushed.length) die('Nothing pushed yet. Run `push` first.');

  console.log(`Checking ${pushed.length} URL(s) through ${PUBLIC_BASE} ...`);
  const bad = [];
  let checked = 0;

  await runPool(pushed, 12, async (entry) => {
    const status = await head(entry.publicUrl);
    if (status !== 200) bad.push({ url: entry.publicUrl, status });
    checked += 1;
    if (checked % 100 === 0) process.stdout.write(`  ${checked}/${pushed.length}\r`);
  });

  console.log('');
  console.log(`  ok      ${pushed.length - bad.length}`);
  console.log(`  broken  ${bad.length}`);
  for (const b of bad.slice(0, 15)) console.log(`    ${b.status}  ${b.url}`);
  if (bad.length) die('\nNot repointing rows while objects are unreachable.');
  console.log('\nNext: node migrate-model-images.mjs apply');
}

function head(url) {
  return new Promise((resolve) => {
    const req = https.request(url, { method: 'HEAD', timeout: 20000 }, (res) => {
      res.resume();
      resolve(res.statusCode);
    });
    req.on('timeout', () => { req.destroy(); resolve(0); });
    req.on('error', () => resolve(0));
    req.end();
  });
}

/* -------------------------------------------------------------------------- */
/* Phase: apply                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Repoints the rows.
 *
 * image_url is written as well as image_key, even though ModelMediaService leaves
 * it null for new uploads: every client reads `imageUrl` off the serialized
 * MasterModel entity and none of them know about imageKey, so a null there is a
 * blank tile in the admin, the shop app and the customer app. Writing both keeps
 * the catalogue rendering today and leaves the keys correct for whenever the read
 * path starts composing the URL itself.
 *
 * image_base64 is cleared: it is a second, now-stale copy of the same picture and
 * the reason the Models screen once OOMed the service.
 */
function phaseApply(opts) {
  const manifest = readManifest();
  const ready = manifest.entries.filter((e) => e.status === 'pushed' && e.key);
  if (!ready.length) die('Nothing verified for apply. Run `push` (and `verify`) first.');

  // Rollback first — before anything is overwritten, not after. Built per target,
  // because the tables do not share a column set: only master_models has
  // media_folder_key, and master_banners has no key columns at all.
  const rollbackFile = path.join(stagingDir(), 'rollback.sql');
  const restores = [];

  for (const name of [...new Set(ready.map((e) => e.target))]) {
    const target = TARGETS[name];
    const ids = ready.filter((e) => e.target === name).map((e) => `'${e.id}'`).join(',');
    const pieces = ["'UPDATE " + target.table + " SET image_url=' || coalesce(quote_literal(image_url), 'NULL')"];
    for (const col of target.columns) {
      const cast = col === 'image_size_bytes' ? `${col}::text` : `quote_literal(${col})`;
      pieces.push(`', ${col}=' || coalesce(${cast}, 'NULL')`);
    }
    pieces.push(`' WHERE id=' || quote_literal(id::text) || ';'`);
    restores.push(
      psqlQuery(`SELECT ${pieces.join(' || ')} FROM ${target.table} WHERE id IN (${ids});`).trim(),
    );
  }

  fs.writeFileSync(
    rollbackFile,
    `-- Restores the media columns to their pre-migration values.\n` +
      `-- Generated ${new Date().toISOString()} for ${ready.length} row(s).\n` +
      `BEGIN;\n${restores.join('\n')}\nCOMMIT;\n`,
    'utf8',
  );
  console.log(`Rollback written: ${rollbackFile}`);

  // Dollar-quoting, because every value here is a machine-generated slug or URL —
  // there is no way for a $$ to appear inside one.
  const statements = ready.map((e) => {
    const target = TARGETS[e.target];
    const sets = [`image_url=$$${e.publicUrl}$$`];

    if (target.columns.includes('image_key')) sets.push(`image_key=$$${e.key}$$`);
    if (target.columns.includes('media_folder_key')) sets.push(`media_folder_key=$$${e.folder}$$`);
    if (target.columns.includes('image_content_type')) sets.push(`image_content_type=$$${e.contentType}$$`);
    if (target.columns.includes('image_size_bytes')) sets.push(`image_size_bytes=${e.sizeBytes}`);
    if (target.columns.includes('image_original_name') && e.originalName) {
      sets.push(`image_original_name=$$${e.originalName}$$`);
    }
    // Always cleared: a stale inline copy of the same picture is what once OOMed
    // the service, and it would keep winning in any client that prefers it.
    sets.push('image_base64=NULL', 'updated_at=now()');

    return `UPDATE ${target.table} SET ${sets.join(', ')} WHERE id='${e.id}';`;
  });

  const updatesFile = path.join(stagingDir(), 'updates.sql');
  fs.writeFileSync(
    updatesFile,
    `-- Repoints ${ready.length} master_models row(s) at media.ggfix.in.\n` +
      `BEGIN;\n${statements.join('\n')}\nCOMMIT;\n`,
    'utf8',
  );
  console.log(`Updates written: ${updatesFile}`);

  if (opts.dryRun) {
    console.log('\nDry run — the transaction was not executed.');
    return;
  }

  psqlFile(updatesFile);
  for (const e of ready) e.status = 'applied';
  writeManifest(manifest);

  console.log('');
  console.log(`  updated  ${ready.length}`);
  for (const name of [...new Set(ready.map((e) => e.target))]) {
    const target = TARGETS[name];
    const left = psqlQuery(
      `SELECT count(*) FROM ${target.table} WHERE image_url LIKE '%res.cloudinary.com%' OR image_url LIKE 'data:image/%';`,
    ).trim();
    const n = ready.filter((e) => e.target === name).length;
    console.log(`    ${name.padEnd(11)} ${String(n).padStart(5)} migrated, ${left} still on Cloudinary/base64`);
  }
  console.log('');
  console.log('The Cloudinary originals were left in place; nothing was deleted there.');
}

/* -------------------------------------------------------------------------- */
/* Manifest and staging paths                                                 */
/* -------------------------------------------------------------------------- */

let STAGING = process.env.MEDIA_MIGRATION_DIR || path.resolve('media-migration');

const stagingDir = () => STAGING;
const stagingRoot = () => path.join(STAGING, 'staging');
const manifestPath = () => path.join(STAGING, 'manifest.json');
const stagedExists = (e) => !!e.key && fs.existsSync(path.join(stagingRoot(), ...e.key.split('/')));

function readManifest() {
  if (!fs.existsSync(manifestPath())) die(`No manifest at ${manifestPath()}. Run \`plan\` first.`);
  return JSON.parse(fs.readFileSync(manifestPath(), 'utf8'));
}

function writeManifest(manifest) {
  fs.mkdirSync(stagingDir(), { recursive: true });
  fs.writeFileSync(manifestPath(), JSON.stringify(manifest, null, 2), 'utf8');
}

/* -------------------------------------------------------------------------- */
/* Entry point                                                                */
/* -------------------------------------------------------------------------- */

function die(message) {
  console.error(`\n${message}\n`);
  process.exit(1);
}

function parseArgs(argv) {
  const opts = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--dry-run') opts.dryRun = true;
    else if (a === '--include-base64') opts.includeBase64 = true;
    else if (a === '--staging') STAGING = path.resolve(argv[++i]);
    else if (a === '--profile') AWS_PROFILE = argv[++i];
    else if (a === '--target') opts.target = argv[++i];
    else if (a === '--concurrency') opts.concurrency = argv[++i];
    else opts._.push(a);
  }
  return opts;
}

const opts = parseArgs(process.argv.slice(2));
const phase = opts._[0];

const PHASES = {
  plan: phasePlan,
  fetch: phaseFetch,
  push: phasePush,
  verify: phaseVerify,
  apply: phaseApply,
};

if (!PHASES[phase]) {
  console.error('Usage: node migrate-model-images.mjs <plan|fetch|push|verify|apply> [options]');
  process.exit(1);
}

await PHASES[phase](opts);
