// End-to-end test of the Partner purchase workflow after a customer accepts a
// shop's quote: assign a pickup person (order-service), the Employee App steps
// (ticket-service /technicians/me/sell-pickups), receipt, inspection, revised
// price with customer approval / rejection, payout recorded by the shop and
// confirmed (or disputed) by the customer, completion, pickup failure,
// cancellation and a shop visit. Local / staging only — mints tokens with the
// JWT secret and writes rows.
//
// Runs in three phases so only one service needs to be up at a time:
//   PHASE=order1 ORDER=http://localhost:18092 ...   (seed, assign)
//   PHASE=ticket TICKET=http://localhost:18082 ...  (employee steps)
//   PHASE=order2 ORDER=http://localhost:18092 ...   (receipt → completion)
// Common env: JWT_SECRET, SHOP_A, OWNER_A, SHOP_B, OWNER_B, PSQL, PGDB, STATE (json file path).

import crypto from 'node:crypto';
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';

const { PHASE, ORDER, TICKET, JWT_SECRET, SHOP_A, OWNER_A, SHOP_B, OWNER_B, PSQL, PGDB, STATE } = process.env;
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const token = (claims) => {
  const u = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600, ...claims })}`;
  return `${u}.${crypto.createHmac('sha256', JWT_SECRET).update(u).digest('base64url')}`;
};
const sql = (q) => execFileSync(PSQL, ['-U', 'postgres', '-h', 'localhost', '-d', PGDB, '-tAc', q], { encoding: 'utf8' }).trim().split(/\r?\n/)[0].trim();
const shopA = token({ sub: OWNER_A, shopId: SHOP_A, roles: ['SHOP_OWNER'] });
const shopB = token({ sub: OWNER_B, shopId: SHOP_B, roles: ['SHOP_OWNER'] });

async function api(base, method, path, auth, body) {
  const res = await fetch(base + path, { method, headers: { Authorization: `Bearer ${auth}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await res.text();
  let json = null; try { json = t ? JSON.parse(t) : null; } catch (_) { json = t; }
  return { status: res.status, json };
}
const O = (m, p, a, b) => api(ORDER, m, p, a, b);
const T = (m, p, a, b) => api(TICKET, m, p, a, b);
let pass = 0; const fails = [];
const check = (n, c, d) => { if (c) pass += 1; else fails.push(`${n}${d ? ` — ${JSON.stringify(d).slice(0, 300)}` : ''}`); };
const notified = (table, who, id, key) => sql(`SELECT COUNT(*) FROM ${table} WHERE ${who} AND booking_id = '${id}' AND status_key = '${key}'`) !== '0';
const custN = (st, id, key) => notified('customer_notifications', `customer_user_id = '${st.customerId}'`, id, key);
const shopN = (id, key) => notified('shop_notifications', `shop_id = '${SHOP_A}'`, id, key);

// A customer sell request quoted by shop A and accepted by the customer.
async function acceptedOrder(st, fulfilment = 'PICKUP') {
  const L = sql(`INSERT INTO marketplace_listings (seller_type, seller_id, brand_id, model_id, product_name, condition, expected_price, status)
    VALUES ('CUSTOMER', '${st.customerId}', '${st.brand}', '${st.model}', 'E2E iPhone 14', 'GOOD', 21000, 'AVAILABLE') RETURNING id`);
  let r = await O('POST', '/sell-orders/listing-quotations', shopA, { listingId: L, quotationPrice: 20000, fulfilment });
  const so = r.json?.sellOrderId;
  sql(`UPDATE sell_orders SET address_id = '${st.addressId}', pickup_date = CURRENT_DATE + 1, pickup_slot_start = '16:00', pickup_slot_end = '17:00' WHERE id = '${so}'`);
  r = await O('GET', `/sell-orders/${so}`, st.customer);
  const q = (r.json?.quotations || []).find((x) => x.shopId === SHOP_A);
  await O('POST', `/sell-orders/${so}/choose-quotation`, st.customer, { quotationId: q?.id });
  return { so, L };
}

if (PHASE === 'order1') {
  const st = { customerId: crypto.randomUUID() };
  st.customer = token({ sub: st.customerId, roles: ['CUSTOMER'] });
  st.model = sql('SELECT id FROM master_models ORDER BY name LIMIT 1');
  st.brand = sql(`SELECT brand_id FROM master_models WHERE id = '${st.model}'`);
  sql(`INSERT INTO customer_users (id, full_name, mobile) VALUES ('${st.customerId}', 'E2E Rahul', '9${String(Date.now()).slice(-9)}')`);
  st.addressId = sql(`INSERT INTO customer_addresses (customer_user_id, full_name, mobile, address_line, city, state, pincode, latitude, longitude)
    VALUES ('${st.customerId}', 'Rahul Mehta', '9876543210', '12 Main Rd', 'Chennai', 'Tamil Nadu', '600017', 13.04, 80.23) RETURNING id`);
  // Pickup staff: tech1 / tech2 in shop A, techB in shop B.
  const tech = (shop, name) => {
    const uid = crypto.randomUUID();
    sql(`INSERT INTO users (id, email, role) VALUES ('${uid}', '${uid}@e2e.local', 'TECHNICIAN')`);
    return { uid, id: sql(`INSERT INTO technicians (shop_id, user_id, name, phone) VALUES ('${shop}', '${uid}', '${name}', '9000000001') RETURNING id`) };
  };
  st.tech1 = tech(SHOP_A, 'Arun Pickup');
  st.tech2 = tech(SHOP_A, 'Bala Pickup');
  st.techB = tech(SHOP_B, 'Other Shop Staff');

  const main = await acceptedOrder(st);
  st.main = main;
  let r = await O('GET', `/sell-orders/shop-purchases/${main.so}`, shopA);
  const d = r.json;
  check('detail: customer-confirmed pickup + contact + sell order id', r.status === 200 && d.pickupDate && d.pickupSlotStart === '16:00' && /12 Main Rd/.test(d.address?.line || '')
    && d.address?.contactPhone === '9876543210' && d.sellOrderId === main.so && d.orderNumber, d);
  check('detail: actions before pickup', ['ASSIGN', 'SCHEDULE', 'CANCEL'].every((a) => d.allowedActions?.includes(a)) && !d.allowedActions.includes('RECEIVE') && !d.allowedActions.includes('INSPECT'), d.allowedActions);
  check('inspection locked before receipt → 409', (await O('POST', `/sell-orders/shop-purchases/${main.so}/inspection`, shopA, { imei: '490154203237518' })).status === 409);
  check('receive before pickup → 409', (await O('POST', `/sell-orders/shop-purchases/${main.so}/receive`, shopA)).status === 409);
  check('payout before price → 409', (await O('POST', `/sell-orders/shop-purchases/${main.so}/payout`, shopA, { method: 'UPI', account: 'a@ok', reference: 'UTR12345' })).status === 409);
  check('complete before payment → 409', (await O('POST', `/sell-orders/shop-purchases/${main.so}/complete`, shopA)).status === 409);
  check('assign other shop’s employee → 400', (await O('POST', `/sell-orders/shop-purchases/${main.so}/assign`, shopA, { technicianId: st.techB.id })).status === 400);
  check('shop B cannot assign → 403', (await O('POST', `/sell-orders/shop-purchases/${main.so}/assign`, shopB, { technicianId: st.techB.id })).status === 403);
  const noLogin = sql(`INSERT INTO technicians (shop_id, name, phone) VALUES ('${SHOP_A}', 'No Login', '9000000009') RETURNING id`);
  r = await O('POST', `/sell-orders/shop-purchases/${main.so}/assign`, shopA, { technicianId: noLogin });
  check('employee without app login → 400', r.status === 400 && /login/i.test(r.json?.message || ''), r.json);
  r = await O('POST', `/sell-orders/shop-purchases/${main.so}/assign`, shopA, { technicianId: st.tech2.id });
  check('assign → PICKUP_ASSIGNED', r.status === 200 && r.json?.status === 'PICKUP_ASSIGNED' && r.json?.pickup?.personName === 'Bala Pickup', r.json);
  r = await O('POST', `/sell-orders/shop-purchases/${main.so}/assign`, shopA, { technicianId: st.tech1.id });
  check('reassign → new person, acceptance reset', r.status === 200 && r.json?.pickup?.personName === 'Arun Pickup' && !r.json?.pickup?.acceptedAt, r.json?.pickup);
  check('customer notified of pickup person', custN(st, main.so, 'SELL_PICKUP_ASSIGNED'));
  r = await O('GET', `/sell-orders/${main.so}/purchase`, st.customer);
  check('customer sees pickup person', r.status === 200 && r.json?.pickup?.personName === 'Arun Pickup' && r.json?.allowedActions?.length === 0, r.json);

  // Pickup failure and decline orders.
  st.fail = await acceptedOrder(st);
  await O('POST', `/sell-orders/shop-purchases/${st.fail.so}/assign`, shopA, { technicianId: st.tech1.id });
  st.decl = await acceptedOrder(st);
  await O('POST', `/sell-orders/shop-purchases/${st.decl.so}/assign`, shopA, { technicianId: st.tech1.id });
  // Customer cancels before pickup.
  st.cc = await acceptedOrder(st);
  await O('POST', `/sell-orders/shop-purchases/${st.cc.so}/assign`, shopA, { technicianId: st.tech2.id });
  r = await O('POST', `/sell-orders/${st.cc.so}/cancel`, st.customer);
  check('customer cancels before pickup', r.status === 200 && sql(`SELECT status FROM sell_orders WHERE id = '${st.cc.so}'`) === 'CANCELLED', r.json);
  check('shop notified of customer cancel', shopN(st.cc.so, 'SELL_CANCELLED_BY_CUSTOMER'));
  // Shop visit: no pickup person, the shop confirms receipt.
  st.visit = await acceptedOrder(st, 'SHOP_VISIT');
  r = await O('GET', `/sell-orders/shop-purchases/${st.visit.so}`, shopA);
  check('shop visit: RECEIVE, no ASSIGN', r.json?.allowedActions?.includes('RECEIVE') && !r.json?.allowedActions?.includes('ASSIGN'), r.json?.allowedActions);
  r = await O('POST', `/sell-orders/shop-purchases/${st.visit.so}/receive`, shopA);
  check('shop visit received', r.status === 200 && r.json?.status === 'DEVICE_RECEIVED' && r.json?.allowedActions?.includes('INSPECT'), r.json);
  fs.writeFileSync(STATE, JSON.stringify(st));
}

if (PHASE === 'ticket') {
  const st = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const t1 = token({ sub: st.tech1.uid, shopId: SHOP_A, roles: ['TECHNICIAN'] });
  const t2 = token({ sub: st.tech2.uid, shopId: SHOP_A, roles: ['TECHNICIAN'] });
  const so = st.main.so;
  let r = await T('GET', '/technicians/me/sell-pickups', t1);
  const row = (r.json || []).find((x) => x.id === so);
  check('employee list has the sell pickup', r.status === 200 && row?.kind === 'SELL' && row?.status === 'PICKUP_PERSON_ASSIGNED' && row?.accepted === false, r.json);
  check('contact hidden before accept', row && !row.customerMobile && !row.pickupAddressText, row);
  r = await T('GET', '/technicians/me/sell-pickups', t2);
  check('reassigned-away employee doesn’t see it', r.status === 200 && !(r.json || []).some((x) => x.id === so), r.json);
  check('other employee cannot accept → 409', (await T('POST', `/technicians/me/sell-pickups/${so}/accept`, t2)).status === 409);
  r = await T('PATCH', `/technicians/me/sell-pickups/${so}/status`, t1, { status: 'PICKUP_ON_THE_WAY' });
  check('start before accept → ACCEPT_REQUIRED', r.status === 409 && r.json?.code === 'ACCEPT_REQUIRED', r);
  r = await T('POST', `/technicians/me/sell-pickups/${so}/accept`, t1);
  check('accept → contact + address shown', r.status === 200 && r.json?.accepted === true && r.json?.customerMobile === '9876543210' && /12 Main Rd/.test(r.json?.pickupAddressText || ''), r.json);
  check('accept again is idempotent', (await T('POST', `/technicians/me/sell-pickups/${so}/accept`, t1)).status === 200);
  r = await T('PATCH', `/technicians/me/sell-pickups/${so}/status`, t1, { status: 'DEVICE_PICKED_UP' });
  check('skip a step → STALE_STATUS', r.status === 409 && r.json?.code === 'STALE_STATUS', r);
  r = await T('PATCH', `/technicians/me/sell-pickups/${so}/status`, t1, { status: 'PICKUP_ON_THE_WAY' });
  check('start journey', r.status === 200 && r.json?.status === 'PICKUP_ON_THE_WAY', r.json);
  r = await T('PATCH', `/technicians/me/sell-pickups/${so}/status`, t1, { status: 'DEVICE_PICKED_UP' });
  check('picked up', r.status === 200 && r.json?.status === 'DEVICE_PICKED_UP', r.json);
  r = await T('PATCH', `/technicians/me/sell-pickups/${so}/status`, t1, { status: 'RECEIVED_AT_SHOP' });
  check('handed over', r.status === 200 && r.json?.status === 'RECEIVED_AT_SHOP' && r.json?.events?.length === 5, r.json);
  check('customer + shop notified of progress', custN(st, so, 'SELL_PICKUP_ON_THE_WAY') && custN(st, so, 'SELL_PICKED_UP') && custN(st, so, 'SELL_DEVICE_RECEIVED') && shopN(so, 'SELL_PICKUP_ACCEPTED') && shopN(so, 'SELL_DEVICE_RECEIVED'));
  // Pickup failure: unable to pick up after starting.
  await T('POST', `/technicians/me/sell-pickups/${st.fail.so}/accept`, t1);
  await T('PATCH', `/technicians/me/sell-pickups/${st.fail.so}/status`, t1, { status: 'PICKUP_ON_THE_WAY' });
  check('unable without reason → 400', (await T('PATCH', `/technicians/me/sell-pickups/${st.fail.so}/status`, t1, { status: 'CANCELLED' })).status === 400);
  r = await T('PATCH', `/technicians/me/sell-pickups/${st.fail.so}/status`, t1, { status: 'CANCELLED', note: 'Customer not reachable' });
  check('unable to pick up → back to the shop', r.status === 200 && sql(`SELECT status || '|' || COALESCE(assigned_pickup_person_id::text, '-') FROM sell_orders WHERE id = '${st.fail.so}'`) === 'PICKUP_SCHEDULED|-', r.json);
  check('shop + customer told about the failed pickup', shopN(st.fail.so, 'SELL_PICKUP_FAILED') && custN(st, st.fail.so, 'SELL_PICKUP_FAILED'));
  // Decline before accepting.
  r = await T('POST', `/technicians/me/sell-pickups/${st.decl.so}/decline`, t1, { reason: 'On leave' });
  check('decline → unassigned', r.status === 200 && sql(`SELECT COALESCE(assigned_pickup_person_id::text, '-') FROM sell_orders WHERE id = '${st.decl.so}'`) === '-', r.json);
  r = await T('GET', '/technicians/me/sell-pickups', t2);
  check('cancelled order shows as CANCELLED to its employee', (r.json || []).find((x) => x.id === st.cc.so)?.status === 'CANCELLED', r.json);
}

if (PHASE === 'order2') {
  const st = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const so = st.main.so;
  let r = await O('GET', `/sell-orders/shop-purchases/${so}`, shopA);
  const pk = r.json?.pickup || {};
  check('detail after handover: received + tracking', r.json?.status === 'DEVICE_RECEIVED' && pk.assignedAt && pk.acceptedAt && pk.startedAt && pk.pickedUpAt && pk.receivedAt && r.json?.allowedActions?.includes('INSPECT'), r.json);
  check('customer cannot cancel once the device is with the shop → 409', (await O('POST', `/sell-orders/${so}/cancel`, st.customer)).status === 409);
  const good = { imei: '490154203237518', modelVerified: true, condition: 'Good', checks: { display: true, touch: true, cameras: true, speakerMic: true, charging: true, buttons: true, connectivity: true, biometrics: false }, photos: ['https://media.ggfix.in/a.jpg', 'https://media.ggfix.in/b.jpg'] };
  check('bad IMEI → 400', (await O('POST', `/sell-orders/shop-purchases/${so}/inspection`, shopA, { ...good, imei: '490154203237519' })).status === 400);
  check('one photo → 400', (await O('POST', `/sell-orders/shop-purchases/${so}/inspection`, shopA, { ...good, photos: ['https://media.ggfix.in/a.jpg'] })).status === 400);
  check('display unchecked → 400', (await O('POST', `/sell-orders/shop-purchases/${so}/inspection`, shopA, { ...good, checks: { touch: true } })).status === 400);
  check('model mismatch without notes → 400', (await O('POST', `/sell-orders/shop-purchases/${so}/inspection`, shopA, { ...good, modelVerified: false })).status === 400);
  r = await O('POST', `/sell-orders/shop-purchases/${so}/inspection`, shopA, good);
  check('inspection saved', r.status === 200 && r.json?.status === 'INSPECTED' && r.json?.inspection?.imei === good.imei && r.json?.allowedActions?.includes('REVISE_PRICE'), r.json);
  check('revise without reason → 400', (await O('POST', `/sell-orders/shop-purchases/${so}/price/revise`, shopA, { amount: 18000, reason: 'low' })).status === 400);
  r = await O('POST', `/sell-orders/shop-purchases/${so}/price/revise`, shopA, { amount: 18000, reason: 'Face ID not working, battery 74%' });
  check('revised price pending', r.status === 200 && r.json?.status === 'PRICE_REVISION_PENDING' && r.json?.revision?.status === 'PENDING', r.json);
  check('payout while pending → 409', (await O('POST', `/sell-orders/shop-purchases/${so}/payout`, shopA, { method: 'UPI', account: 'rahul@okaxis', reference: 'UTR123456' })).status === 409);
  r = await O('GET', `/sell-orders/${so}/purchase`, st.customer);
  check('customer can approve / reject', r.json?.allowedActions?.includes('APPROVE_REVISION') && Number(r.json?.revision?.amount) === 18000, r.json);
  check('other customer cannot see it → 403', (await O('GET', `/sell-orders/${so}/purchase`, token({ sub: crypto.randomUUID(), roles: ['CUSTOMER'] }))).status === 403);
  r = await O('POST', `/sell-orders/${so}/revision/reject`, st.customer, { note: 'Too low' });
  check('customer rejects', r.status === 200 && r.json?.status === 'REVISION_REJECTED' && shopN(so, 'SELL_REVISION_REJECTED'), r.json);
  r = await O('GET', `/sell-orders/shop-purchases/${so}`, shopA);
  check('after rejection: confirm / revise / cancel', ['CONFIRM_PRICE', 'REVISE_PRICE', 'CANCEL'].every((a) => r.json?.allowedActions?.includes(a)) && r.json?.revision?.customerNote === 'Too low', r.json);
  await O('POST', `/sell-orders/shop-purchases/${so}/price/revise`, shopA, { amount: 19000, reason: 'Face ID not working, best we can do' });
  r = await O('POST', `/sell-orders/${so}/revision/approve`, st.customer);
  check('customer approves → final price 19000', r.status === 200 && r.json?.status === 'PRICE_CONFIRMED' && Number(r.json?.finalPrice) === 19000, r.json);
  check('bad UPI → 400', (await O('POST', `/sell-orders/shop-purchases/${so}/payout`, shopA, { method: 'UPI', account: 'nope', reference: 'UTR123456' })).status === 400);
  check('no reference → 400', (await O('POST', `/sell-orders/shop-purchases/${so}/payout`, shopA, { method: 'UPI', account: 'rahul@okaxis', reference: '' })).status === 400);
  r = await O('POST', `/sell-orders/shop-purchases/${so}/payout`, shopA, { method: 'UPI', account: 'rahul@okaxis', reference: 'UTR123456' });
  check('payout recorded, not paid', r.status === 200 && r.json?.status === 'PAYMENT_PENDING' && r.json?.payment?.status === 'AWAITING_CONFIRMATION' && Number(r.json?.payment?.amount) === 19000, r.json);
  check('complete before customer confirms → 409', (await O('POST', `/sell-orders/shop-purchases/${so}/complete`, shopA)).status === 409);
  check('cancel after payout → 409', (await O('POST', `/sell-orders/shop-purchases/${so}/cancel`, shopA, { reason: 'Changed my mind' })).status === 409);
  r = await O('POST', `/sell-orders/${so}/payout/dispute`, st.customer, { note: 'Not received yet' });
  check('customer disputes → back to price confirmed', r.status === 200 && r.json?.status === 'PRICE_CONFIRMED' && shopN(so, 'SELL_PAYOUT_DISPUTED'), r.json);
  r = await O('POST', `/sell-orders/shop-purchases/${so}/payout`, shopA, { method: 'BANK', account: '123456789012/HDFC0001234', reference: 'NEFT998877' });
  check('payout recorded again', r.status === 200 && r.json?.status === 'PAYMENT_PENDING', r.json);
  r = await O('POST', `/sell-orders/${so}/payout/confirm`, st.customer);
  check('customer confirms → PAID', r.status === 200 && r.json?.status === 'PAID' && r.json?.payment?.status === 'PAID', r.json);
  r = await O('POST', `/sell-orders/shop-purchases/${so}/complete`, shopA);
  check('complete', r.status === 200 && r.json?.status === 'COMPLETED' && r.json?.completedAt, r.json);
  check('listing SOLD + customer order COMPLETED', sql(`SELECT status FROM marketplace_listings WHERE id = '${st.main.L}'`) === 'SOLD'
    && sql(`SELECT status FROM customer_orders WHERE order_number = (SELECT sell_number FROM sell_orders WHERE id = '${so}')`) === 'COMPLETED');
  check('complete again is idempotent', (await O('POST', `/sell-orders/shop-purchases/${so}/complete`, shopA)).status === 200);
  // Failed pickup → reassign → shop cancels.
  r = await O('GET', `/sell-orders/shop-purchases/${st.fail.so}`, shopA);
  check('failed pickup visible to the shop', /Unable to pick up/.test(r.json?.pickup?.issue || '') && r.json?.allowedActions?.includes('ASSIGN'), r.json?.pickup);
  r = await O('POST', `/sell-orders/shop-purchases/${st.fail.so}/assign`, shopA, { technicianId: st.tech2.id });
  check('reassign after failure', r.status === 200 && r.json?.status === 'PICKUP_ASSIGNED' && !r.json?.pickup?.issue, r.json);
  check('cancel without reason → 400', (await O('POST', `/sell-orders/shop-purchases/${st.fail.so}/cancel`, shopA, { reason: '' })).status === 400);
  r = await O('POST', `/sell-orders/shop-purchases/${st.fail.so}/cancel`, shopA, { reason: 'Customer wants to keep the phone' });
  check('shop cancels', r.status === 200 && r.json?.status === 'CANCELLED' && r.json?.cancelledBy === 'SHOP' && custN(st, st.fail.so, 'SELL_CANCELLED'), r.json);
}

console.log(`${PHASE}: ${pass} passed, ${fails.length} failed`);
fails.forEach((f) => console.log(`FAIL: ${f}`));
process.exit(fails.length ? 1 : 0);
