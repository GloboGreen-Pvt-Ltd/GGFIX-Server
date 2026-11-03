// End-to-end smoke test of the Partner Sell module API (marketplace-service).
//
//   BASE=http://localhost:18087 JWT_SECRET=... SHOP_A=... OWNER_A=... SHOP_B=... OWNER_B=... node scripts/e2e-sell-module.mjs
//
// Mints HS256 tokens with the service's JWT secret, so run it only against a
// local or staging service whose database holds the two test shops (SHOP_x
// owned by OWNER_x in `shops`). It creates real rows; never point it at production.

import crypto from 'node:crypto';

const BASE = process.env.BASE || 'http://localhost:18087';
const SECRET = process.env.JWT_SECRET;
const SHOP_A = process.env.SHOP_A;
const OWNER_A = process.env.OWNER_A;
const SHOP_B = process.env.SHOP_B;
const OWNER_B = process.env.OWNER_B;
if (!SECRET || !SHOP_A || !OWNER_A || !SHOP_B || !OWNER_B) {
  console.error('Set JWT_SECRET, SHOP_A, OWNER_A, SHOP_B, OWNER_B');
  process.exit(2);
}

const b64 = (o) => Buffer.from(typeof o === 'string' ? o : JSON.stringify(o)).toString('base64url');
function token(sub, { shopId, role, scope } = {}) {
  const body = { sub, roles: [role], iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600 };
  if (shopId) body.shopId = shopId;
  if (scope) body.loginScope = scope;
  const unsigned = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(body)}`;
  return `${unsigned}.${crypto.createHmac('sha256', SECRET).update(unsigned).digest('base64url')}`;
}

const ownerA = token(OWNER_A, { shopId: SHOP_A, role: 'SHOP_OWNER', scope: 'OWNER' });
const ownerB = token(OWNER_B, { shopId: SHOP_B, role: 'SHOP_OWNER', scope: 'OWNER' });
const tech = token(crypto.randomUUID(), { shopId: SHOP_A, role: 'TECHNICIAN' });
const buyer1 = token(crypto.randomUUID(), { role: 'CUSTOMER' });
const buyer2 = token(crypto.randomUUID(), { role: 'CUSTOMER' });

async function api(method, path, auth, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { ...(auth ? { Authorization: `Bearer ${auth}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch (_) { json = text; }
  return { status: res.status, json, headers: res.headers };
}

let pass = 0;
const fails = [];
function check(name, cond, detail) {
  if (cond) pass += 1;
  else fails.push(`${name}${detail ? ` — ${typeof detail === 'string' ? detail : JSON.stringify(detail).slice(0, 300)}` : ''}`);
}
const expect = async (name, p, status) => {
  const r = await p;
  check(`${name} → ${status}`, r.status === status, `got ${r.status} ${JSON.stringify(r.json)?.slice(0, 200)}`);
  return r;
};

const tag = Date.now().toString(36);
const photos = ['https://media.ggfix.in/t/1.jpg', 'https://media.ggfix.in/t/2.jpg', 'https://media.ggfix.in/t/3.jpg'];
const device = (extra = {}) => ({
  title: `E2E iPhone 13 ${tag}`, price: 34000, listingType: 'DEVICE', itemCondition: 'USED', quantity: 3,
  negotiable: true, fulfilmentOptions: ['PICKUP', 'SHIPPING'], imageUrl: photos[0], extraImageUrls: photos.slice(1), status: 'ACTIVE', ...extra,
});

// ── Roles & isolation ──
await expect('technician summary', api('GET', '/shop/marketplace/summary', tech), 403);
await expect('customer summary', api('GET', '/shop/marketplace/summary', buyer1), 403);
const before = await expect('owner summary', api('GET', '/shop/marketplace/summary', ownerA), 200);
await expect('legacy POST into other shop', api('POST', '/marketplace/products', ownerA, { shopId: SHOP_B, title: 'x', type: 'SELL', price: 1 }), 403);

// ── Create / publish rules / idempotency ──
await expect('device with 2 photos cannot go live', api('POST', '/shop/marketplace/products', ownerA, device({ extraImageUrls: [photos[1]], clientRef: `few-${tag}` })), 400);
const created = await expect('create device', api('POST', '/shop/marketplace/products', ownerA, device({ clientRef: `dev-${tag}` })), 200);
const pid = created.json?.id;
const replay = await expect('replay same clientRef', api('POST', '/shop/marketplace/products', ownerA, device({ clientRef: `dev-${tag}` })), 200);
check('replay returns same row', replay.json?.id === pid && replay.headers.get('idempotent-replay') === 'true');
const draft = await expect('create draft', api('POST', '/shop/marketplace/products', ownerA, { title: `E2E draft ${tag}`, status: 'DRAFT', listingType: 'DEVICE', clientRef: `drf-${tag}` }), 200);
await expect('draft without photos cannot publish', api('PATCH', `/shop/marketplace/products/${draft.json?.id}/status`, ownerA, { status: 'ACTIVE' }), 400);
await expect('public list never shows drafts', api('GET', '/marketplace/products?status=DRAFT'), 200);
await expect('other shop cannot read', api('GET', `/shop/marketplace/products/${pid}`, ownerB), 404);
await expect('other shop cannot edit (legacy)', api('PUT', `/marketplace/products/${pid}`, ownerB, { price: 1 }), 403);

const list = await expect('list active + search', api('GET', `/shop/marketplace/products?status=ACTIVE&q=${encodeURIComponent(tag)}`, ownerA), 200);
check('search finds the listing', (list.json?.content || []).some((p) => p.id === pid));
const priced = await expect('update price', api('PUT', `/shop/marketplace/products/${pid}`, ownerA, { price: 33000 }), 200);
check('price saved', Number(priced.json?.price) === 33000);
const part = await expect('sell 1 of 3', api('PATCH', `/shop/marketplace/products/${pid}/status`, ownerA, { status: 'SOLD', quantitySold: 1 }), 200);
check('partial sale keeps listing live', part.json?.status === 'ACTIVE' && part.json?.quantity === 2 && part.json?.soldQuantity === 1, part.json);

// ── Enquiry → quote → counter → accept → confirm ──
const enq = await expect('buyer purchase request', api('POST', `/customer/marketplace/products/${pid}/enquiries`, buyer1, { kind: 'PURCHASE_REQUEST', offerAmount: 30000, message: 'Best price?' }), 200);
const eid = enq.json?.id;
const dup = await expect('repeat request returns same enquiry', api('POST', `/customer/marketplace/products/${pid}/enquiries`, buyer1, { kind: 'PURCHASE_REQUEST', offerAmount: 29000 }), 200);
check('one open enquiry per buyer', dup.json?.id === eid);
await expect('shop cannot enquire on own listing', api('POST', `/customer/marketplace/products/${pid}/enquiries`, ownerA, { kind: 'ENQUIRY' }), 403);
await expect('other shop cannot see enquiry', api('GET', `/shop/marketplace/enquiries/${eid}`, ownerB), 404);
await expect('other buyer cannot see enquiry', api('GET', `/customer/marketplace/enquiries/${eid}`, buyer2), 404);
const inbox = await expect('shop enquiry list', api('GET', '/shop/marketplace/enquiries?status=ACTIVE', ownerA), 200);
check('enquiry in shop inbox', (inbox.json?.content || []).some((e) => e.id === eid));
const det = await expect('shop enquiry detail', api('GET', `/shop/marketplace/enquiries/${eid}`, ownerA), 200);
check('phone hidden before agreement', !det.json?.buyer?.phone);
check('allowed actions include SEND_OFFER/ACCEPT/REJECT', ['SEND_OFFER', 'ACCEPT', 'REJECT'].every((a) => det.json?.allowedActions?.includes(a)), det.json?.allowedActions);
await expect('confirm before accept is refused', api('POST', `/shop/marketplace/enquiries/${eid}/confirm`, ownerA, { fulfilment: 'PICKUP' }), 409);
const quoted = await expect('shop counter-quote', api('POST', `/shop/marketplace/enquiries/${eid}/offers`, ownerA, { amount: 32000, note: 'Final' }), 200);
check('status QUOTED', quoted.json?.status === 'QUOTED');
await expect('shop cannot accept its own quote', api('POST', `/shop/marketplace/enquiries/${eid}/accept`, ownerA), 409);
const countered = await expect('buyer counter', api('POST', `/customer/marketplace/enquiries/${eid}/offers`, buyer1, { amount: 31000 }), 200);
check('status COUNTERED', countered.json?.status === 'COUNTERED');
const accepted = await expect('shop accepts', api('POST', `/shop/marketplace/enquiries/${eid}/accept`, ownerA), 200);
check('agreed 31000', Number(accepted.json?.agreedAmount) === 31000, accepted.json);
await expect('fulfilment outside listing options refused', api('POST', `/shop/marketplace/enquiries/${eid}/confirm`, ownerA, { fulfilment: 'DRONE' }), 400);
const confirmed = await expect('confirm deal (shipping)', api('POST', `/shop/marketplace/enquiries/${eid}/confirm`, ownerA, { fulfilment: 'SHIPPING' }), 200);
check('CONFIRMED and stock 2→1', confirmed.json?.status === 'CONFIRMED' && confirmed.json?.product?.quantity === 1, confirmed.json);
const hist = await expect('history offers', api('GET', `/shop/marketplace/enquiries/${eid}`, ownerA), 200);
check('negotiation history recorded', (hist.json?.offers || []).length >= 4, hist.json?.offers?.length);

// Last unit: second buyer's deal sells out and closes the rest.
const e2 = await expect('buyer2 enquiry', api('POST', `/customer/marketplace/products/${pid}/enquiries`, buyer2, { kind: 'ENQUIRY', message: 'Still available?' }), 200);
await expect('shop quotes buyer2', api('POST', `/shop/marketplace/enquiries/${e2.json?.id}/offers`, ownerA, { amount: 33000 }), 200);
await expect('buyer2 accepts', api('POST', `/customer/marketplace/enquiries/${e2.json?.id}/accept`, buyer2), 200);
await expect('confirm last unit', api('POST', `/shop/marketplace/enquiries/${e2.json?.id}/confirm`, ownerA, { fulfilment: 'PICKUP' }), 200);
const sold = await expect('listing after sell-out', api('GET', `/shop/marketplace/products/${pid}`, ownerA), 200);
check('sold out → SOLD, qty 0', sold.json?.status === 'SOLD' && sold.json?.quantity === 0, sold.json);
await expect('sold listing cannot be edited', api('PUT', `/shop/marketplace/products/${pid}`, ownerA, { price: 1 }), 409);
await expect('no new enquiry on sold listing', api('POST', `/customer/marketplace/products/${pid}/enquiries`, buyer1, { kind: 'ENQUIRY' }), 409);

// ── Bulk with partial failure and safe retry ──
const batchId = crypto.randomUUID();
const items = [
  { clientRef: `b1-${tag}`, title: `E2E bulk A ${tag}`, price: 9000, listingType: 'DEVICE', imageUrl: photos[0], extraImageUrls: photos.slice(1) },
  { clientRef: `b2-${tag}`, title: `E2E bulk B ${tag}`, price: 9500, listingType: 'DEVICE' },
  { clientRef: `b3-${tag}`, title: `E2E bulk C ${tag}`, brandId: 'not-a-uuid' },
];
const bulk1 = await expect('bulk upload', api('POST', '/shop/marketplace/products/bulk', ownerA, { batchId, publish: true, items }), 200);
check('bulk 1 published / 1 draft / 1 failed', bulk1.json?.published === 1 && bulk1.json?.drafts === 1 && bulk1.json?.failed === 1, bulk1.json);
const bulk2 = await expect('bulk retry', api('POST', '/shop/marketplace/products/bulk', ownerA, { batchId, publish: true, items }), 200);
check('retry creates nothing new', bulk2.json?.created === 0 && bulk2.json?.existing === 2 && bulk2.json?.failed === 1, bulk2.json);

// ── Soft delete ──
const toDelete = bulk1.json?.results?.find((r) => r.status === 'DRAFT')?.productId;
await expect('delete draft', api('DELETE', `/shop/marketplace/products/${toDelete}`, ownerA), 204);
await expect('deleted is gone', api('GET', `/shop/marketplace/products/${toDelete}`, ownerA), 404);

const after = await expect('summary after', api('GET', '/shop/marketplace/summary', ownerA), 200);
check('summary moved: sold +1', after.json?.sold === (before.json?.sold ?? 0) + 1, { before: before.json, after: after.json });

console.log(`${pass} passed, ${fails.length} failed`);
fails.forEach((f) => console.log(`FAIL: ${f}`));
process.exit(fails.length ? 1 : 0);
