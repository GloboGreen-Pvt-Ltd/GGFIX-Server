// End-to-end test: shops quote on a customer's marketplace sell request
// (order-service POST /sell-orders/listing-quotations) and the customer sees
// and accepts the quote on their sell order. Local / staging only — it mints
// tokens with the service's JWT secret and writes rows.
//
//   ORDER=http://localhost:18092 JWT_SECRET=... SHOP_A=... OWNER_A=... SHOP_B=... OWNER_B=... \
//   PG="psql connection args" node scripts/e2e-listing-quotes.mjs
//
// Seeds use psql (PSQL env: path to psql, PGPASSWORD set by the caller).

import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const { ORDER, JWT_SECRET, SHOP_A, OWNER_A, SHOP_B, OWNER_B, PSQL, PGDB } = process.env;
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const token = (claims) => {
  const u = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600, ...claims })}`;
  return `${u}.${crypto.createHmac('sha256', JWT_SECRET).update(u).digest('base64url')}`;
};
// First output line only (RETURNING also prints "INSERT 0 1"); CRLF-safe on Windows.
const sql = (q) => execFileSync(PSQL, ['-U', 'postgres', '-h', 'localhost', '-d', PGDB, '-tAc', q], { encoding: 'utf8' }).trim().split(/\r?\n/)[0].trim();

const customerId = crypto.randomUUID();
const shopA = token({ sub: OWNER_A, shopId: SHOP_A, roles: ['SHOP_OWNER'] });
const shopB = token({ sub: OWNER_B, shopId: SHOP_B, roles: ['SHOP_OWNER'] });
const tech = token({ sub: crypto.randomUUID(), shopId: SHOP_A, roles: ['TECHNICIAN'] });
const customer = token({ sub: customerId, roles: ['CUSTOMER'] });

async function api(method, path, auth, body) {
  const res = await fetch(ORDER + path, { method, headers: { Authorization: `Bearer ${auth}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await res.text();
  let json = null; try { json = t ? JSON.parse(t) : null; } catch (_) { json = t; }
  return { status: res.status, json };
}
let pass = 0; const fails = [];
const check = (n, c, d) => { if (c) pass += 1; else fails.push(`${n}${d ? ` — ${JSON.stringify(d).slice(0, 300)}` : ''}`); };

// ── Seed: a customer and two listings (one with its own sell order) ──
const model = sql("SELECT id FROM master_models ORDER BY name LIMIT 1");
const brand = sql(`SELECT brand_id FROM master_models WHERE id = '${model}'`);
sql(`INSERT INTO customer_users (id) VALUES ('${customerId}')`);
const L1 = sql(`INSERT INTO marketplace_listings (seller_type, seller_id, brand_id, model_id, product_name, condition, description, expected_price, status)
  VALUES ('CUSTOMER', '${customerId}', '${brand}', '${model}', 'E2E iPhone', 'GOOD', 'Midnight Green - 256 GB', 0, 'AVAILABLE') RETURNING id`).split('\n')[0];

// 1) No sell order yet → created from the listing, linked, quote PROPOSED.
let r = await api('POST', '/sell-orders/listing-quotations', shopA, { listingId: L1, quotationPrice: 41000, note: 'Cash on pickup', fulfilment: 'PICKUP' });
check('shop A quote → 200', r.status === 200, r);
const so1 = r.json?.sellOrderId;
check('sell order created & linked', !!so1 && sql(`SELECT sell_order_id FROM marketplace_listings WHERE id = '${L1}'`) === so1);
check('customer order row for the customer', sql(`SELECT COUNT(*) FROM customer_orders WHERE reference_id = '${so1}' AND order_type = 'SELL' AND customer_user_id = '${customerId}'`) === '1');
check('quote PROPOSED with fulfilment in note', r.json?.quotation?.status === 'PROPOSED' && /pickup/i.test(r.json?.quotation?.note || ''), r.json?.quotation);
check('customer notified', sql(`SELECT COUNT(*) FROM customer_notifications WHERE customer_user_id = '${customerId}' AND status_key = 'SELL_QUOTE'`) === '1');

// 2) Revise (double submit) → same quote, one row.
r = await api('POST', '/sell-orders/listing-quotations', shopA, { listingId: L1, quotationPrice: 42500, fulfilment: 'SHOP_VISIT' });
check('revise → revised:true, same id', r.status === 200 && r.json?.revised === true, r.json);
check('one quote per shop', sql(`SELECT COUNT(*) FROM sell_order_quotations WHERE sell_order_id = '${so1}' AND shop_id = '${SHOP_A}'`) === '1');
r = await api('GET', `/sell-orders/listing-quotations/${L1}`, shopA);
check('GET mine shows revised price', r.status === 200 && Number(r.json?.quotation?.quotationPrice) === 42500, r.json);

// 3) Second shop quotes; the customer sees both on their sell order.
r = await api('POST', '/sell-orders/listing-quotations', shopB, { listingId: L1, quotationPrice: 40000, fulfilment: 'PICKUP' });
check('shop B quote → same order', r.status === 200 && r.json?.sellOrderId === so1, r.json);
r = await api('GET', `/sell-orders/${so1}`, customer);
const quotes = r.json?.quotations || [];
check('customer sees 2 quotes', r.status === 200 && quotes.length === 2, r.json);

// 3b) Old quote endpoints: a shop sees only its own quote; strangers are refused;
//     the quoting shop always comes from the token.
r = await api('GET', `/sell-orders/${so1}/quotations`, shopB);
check('shop B lists only its own quote', r.status === 200 && r.json?.length === 1 && r.json[0].shopId === SHOP_B, r.json);
r = await api('GET', `/sell-orders/${so1}/quotations`, token({ sub: crypto.randomUUID(), roles: ['CUSTOMER'] }));
check('other customer cannot list quotes → 403', r.status === 403, r);
r = await api('GET', `/sell-orders/${so1}/quotations`, customer);
check('order customer lists all quotes', r.status === 200 && r.json?.length === 2, r.json);
r = await api('POST', `/sell-orders/${so1}/quotations`, customer, { shopId: SHOP_A, quotationPrice: 1 });
check('customer cannot quote as a shop → 403', r.status === 403, r);

// 4) Customer accepts shop A → further quotes refused.
const qa = quotes.find((q) => q.shopId === SHOP_A);
r = await api('POST', `/sell-orders/${so1}/choose-quotation`, customer, { quotationId: qa?.id });
check('customer chooses shop A', r.status === 200, r.json);
r = await api('POST', '/sell-orders/listing-quotations', shopB, { listingId: L1, quotationPrice: 45000 });
check('quote after customer chose → 409', r.status === 409, r);

// 5) Customer's own sell order is reused, not duplicated.
r = await api('POST', '/sell-orders', customer, { brandId: brand, modelId: model, color: 'Gold', workingCondition: 'WORKING' });
const ownSo = r.json?.id;
check('customer created own sell order', r.status === 200 && !!ownSo, r.json);
const L2 = sql(`INSERT INTO marketplace_listings (seller_type, seller_id, brand_id, model_id, product_name, condition, description, expected_price, status)
  VALUES ('CUSTOMER', '${customerId}', '${brand}', '${model}', 'E2E iPhone 2', 'GOOD', 'Gold - 128 GB', 0, 'AVAILABLE') RETURNING id`).split('\n')[0];
r = await api('POST', '/sell-orders/listing-quotations', shopA, { listingId: L2, quotationPrice: 30000 });
check('links to the customer\'s existing sell order', r.status === 200 && r.json?.sellOrderId === ownSo, { got: r.json?.sellOrderId, want: ownSo });

// 6) Guards.
check('technician → 403', (await api('POST', '/sell-orders/listing-quotations', tech, { listingId: L2, quotationPrice: 1 })).status === 403);
check('zero price → 400', (await api('POST', '/sell-orders/listing-quotations', shopA, { listingId: L2, quotationPrice: 0 })).status === 400);
check('unknown listing → 404', (await api('POST', '/sell-orders/listing-quotations', shopA, { listingId: crypto.randomUUID(), quotationPrice: 10 })).status === 404);
const ghost = sql(`INSERT INTO marketplace_listings (seller_type, seller_id, product_name, expected_price, status) VALUES ('CUSTOMER', '${crypto.randomUUID()}', 'Ghost', 0, 'AVAILABLE') RETURNING id`).split('\n')[0];
check('listing of unknown customer → 409', (await api('POST', '/sell-orders/listing-quotations', shopA, { listingId: ghost, quotationPrice: 10 })).status === 409);

console.log(`${pass} passed, ${fails.length} failed`);
fails.forEach((f) => console.log(`FAIL: ${f}`));
process.exit(fails.length ? 1 : 0);
