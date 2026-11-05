// E2E: collection method on a purchase (order-service
// POST /sell-orders/shop-purchases/{id}/fulfilment {fulfilment: PICKUP|SHOP_VISIT}).
// Local / staging only — mints tokens with the service's JWT secret and writes rows.
//
//   ORDER=http://localhost:18092 JWT_SECRET=... SHOP_A=... OWNER_A=... SHOP_B=... OWNER_B=... TECH_A=<technicians.id in shop A> \
//   PSQL=path/to/psql PGDB=... node scripts/e2e-purchase-fulfilment.mjs

import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const { ORDER, JWT_SECRET, SHOP_A, OWNER_A, SHOP_B, OWNER_B, TECH_A, PSQL, PGDB } = process.env;
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const token = (claims) => {
  const u = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600, ...claims })}`;
  return `${u}.${crypto.createHmac('sha256', JWT_SECRET).update(u).digest('base64url')}`;
};
const sql = (q) => execFileSync(PSQL, ['-U', 'postgres', '-h', 'localhost', '-d', PGDB, '-tAc', q], { encoding: 'utf8' }).trim().split(/\r?\n/)[0].trim();
const api = async (method, path, auth, body) => {
  const res = await fetch(ORDER + path, { method, headers: { Authorization: `Bearer ${auth}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await res.text();
  let json = null; try { json = t ? JSON.parse(t) : null; } catch (_) { json = t; }
  return { status: res.status, json };
};
let pass = 0; const fails = [];
const check = (n, c, d) => { if (c) pass += 1; else fails.push(`${n}${d ? ` — ${JSON.stringify(d).slice(0, 300)}` : ''}`); };

const customerId = crypto.randomUUID();
const shopA = token({ sub: OWNER_A, shopId: SHOP_A, roles: ['SHOP_OWNER'] });
const shopB = token({ sub: OWNER_B, shopId: SHOP_B, roles: ['SHOP_OWNER'] });
const customer = token({ sub: customerId, roles: ['CUSTOMER'] });
const model = sql('SELECT id FROM master_models ORDER BY name LIMIT 1');
const brand = sql(`SELECT brand_id FROM master_models WHERE id = '${model}'`);
sql(`INSERT INTO customer_users (id, full_name, mobile) VALUES ('${customerId}', 'E2E Fulfil', '9${String(Date.now()).slice(-9)}')`);
const L = sql(`INSERT INTO marketplace_listings (seller_type, seller_id, brand_id, model_id, product_name, product_image, condition, description, expected_price, address, city, latitude, longitude, status)
  VALUES ('CUSTOMER', '${customerId}', '${brand}', '${model}', 'E2E OnePlus 12R', 'https://example.com/p.jpg', 'GOOD', 'Gray, 8 GB / 256 GB', 0, '12 Main Rd', 'Chennai', 13.08, 80.27, 'AVAILABLE') RETURNING id`);

let r = await api('POST', '/sell-orders/listing-quotations', shopA, { listingId: L, quotationPrice: 20000, note: 'Box included', fulfilment: 'PICKUP' });
const so = r.json?.sellOrderId;
r = await api('GET', `/sell-orders/${so}`, customer);
const qa = (r.json?.quotations || []).find((q) => q.shopId === SHOP_A);
r = await api('POST', `/sell-orders/${so}/choose-quotation`, customer, { quotationId: qa?.id });
check('accepted', r.status === 200, r.json);
const F = (body, auth = shopA) => api('POST', `/sell-orders/shop-purchases/${so}/fulfilment`, auth, body);
const notes = (key) => sql(`SELECT COUNT(*) FROM customer_notifications WHERE customer_user_id = '${customerId}' AND status_key = '${key}'`);

check('bad value → 400', (await F({ fulfilment: 'DRONE' })).status === 400);
check('shop B → 403', (await F({ fulfilment: 'SHOP_VISIT' }, shopB)).status === 403);
r = await F({ fulfilment: 'PICKUP' });
check('same method → 200, no notification', r.status === 200 && r.json?.fulfilment === 'PICKUP' && notes('SELL_FULFILMENT_CHANGED') === '0', r.json);

r = await api('POST', `/sell-orders/shop-purchases/${so}/assign`, shopA, { technicianId: TECH_A });
check('assigned', r.status === 200 && r.json?.status === 'PICKUP_ASSIGNED', r.json);
r = await F({ fulfilment: 'SHOP_VISIT' });
check('→ shop visit', r.status === 200 && r.json?.fulfilment === 'SHOP_VISIT', r.json);
check('pickup person released', r.json?.status === 'SELL_CONFIRMED' && !r.json?.pickup?.personName && sql(`SELECT assigned_pickup_person_id IS NULL FROM sell_orders WHERE id = '${so}'`) === 't', r.json);
check('shop visit actions', r.json?.allowedActions?.includes('RECEIVE') && !r.json?.allowedActions?.includes('ASSIGN'), r.json?.allowedActions);
// ASCII-only SQL (Windows passes psql arguments in the ANSI codepage); chr(183) = '·'.
check('note keeps the shop text', sql(`SELECT note = 'Visit the shop to sell ' || chr(183) || ' Box included' FROM sell_order_quotations WHERE sell_order_id = '${so}' AND status = 'ACCEPTED'`) === 't');
check('customer notified', notes('SELL_FULFILMENT_CHANGED') === '1');
r = await api('GET', `/sell-orders/listing-quotations/${L}`, shopA);
check('quote status shows shop visit', /visit the shop/i.test(r.json?.quotation?.note || ''), r.json?.quotation);

r = await F({ fulfilment: 'PICKUP' });
check('→ pickup again', r.status === 200 && r.json?.fulfilment === 'PICKUP' && r.json?.allowedActions?.includes('ASSIGN'), r.json);
await F({ fulfilment: 'SHOP_VISIT' });
r = await api('POST', `/sell-orders/shop-purchases/${so}/receive`, shopA);
check('shop visit receive', r.status === 200 && r.json?.status === 'DEVICE_RECEIVED', r.json);
r = await F({ fulfilment: 'PICKUP' });
check('locked after receipt → 409', r.status === 409, r);

// Checklist inspection (Partner "Device Inspection" page): display required; battery / accessories optional.
const P = (step, body, auth = shopA) => api('POST', `/sell-orders/shop-purchases/${so}/${step}`, auth, body);
const ins = { imei: '490154203237518', modelVerified: true, condition: 'Good', photos: ['https://example.com/a.jpg', 'https://example.com/b.jpg'] };
check('inspection without display → 400', (await P('inspection', { ...ins, checks: {} })).status === 400);
check('battery 120% → 400', (await P('inspection', { ...ins, checks: { display: true }, batteryHealth: 120 })).status === 400);
r = await P('inspection', { ...ins, checks: { display: true }, batteryHealth: 92, accessories: ['Charger', 'Box', 'Box'] });
check('checklist inspection saved', r.status === 200 && r.json?.status === 'INSPECTED' && r.json?.inspection?.batteryHealth === 92
  && JSON.stringify(r.json?.inspection?.accessories) === '["Charger","Box"]' && r.json?.inspection?.checks?.display === true, r.json?.inspection || r.json);
r = await P('price/confirm');
check('price confirmed', r.status === 200 && r.json?.status === 'PRICE_CONFIRMED', r.json);

// Payment: UPI / bank need the UTR; cash may omit it; the customer still confirms.
check('UPI without reference → 400', (await P('payout', { method: 'UPI' })).status === 400);
check('bad method → 400', (await P('payout', { method: 'CHEQUE', reference: 'ABC12345' })).status === 400);
r = await P('payout', { method: 'CASH' });
check('cash payout recorded', r.status === 200 && r.json?.status === 'PAYMENT_PENDING' && r.json?.payment?.method === 'CASH' && !r.json?.payment?.account && r.json?.payment?.status === 'AWAITING_CONFIRMATION', r.json);
check('complete before customer confirms → 409', (await P('complete')).status === 409);
r = await api('POST', `/sell-orders/${so}/payout/confirm`, customer);
check('customer confirms cash', r.status === 200, r.json);
r = await P('complete');
check('completed', r.status === 200 && r.json?.status === 'COMPLETED', r.json);
r = await api('GET', '/sell-orders/shop-purchases', shopA);
check('list has pickupPersonId key', r.status === 200 && r.json.some((x) => 'pickupPersonId' in x), r.json?.[0]);

console.log(`${pass} passed, ${fails.length} failed`);
fails.forEach((f) => console.log(`FAIL: ${f}`));
process.exit(fails.length ? 1 : 0);
