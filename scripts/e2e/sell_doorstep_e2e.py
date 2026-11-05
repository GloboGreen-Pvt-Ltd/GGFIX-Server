"""End-to-end check of the doorstep device-purchase flow (feat/sell-doorstep-purchase).

ISOLATED TEST DATABASE ONLY — it inserts test shops / customers / orders and shifts a PIN timestamp.
Run order-service and ticket-service (built from this branch) against a throwaway PostgreSQL, then:

    E2E_ISOLATED_DB=yes TEST_DB_PORT=55432 ORDER_URL=http://localhost:18092 TICKET_URL=http://localhost:18082         python scripts/e2e/sell_doorstep_e2e.py

JWTs are signed with JWT_SECRET (default: the dev default from application.yml). Prints PASS/FAIL per check.
"""
import os
import sys

if os.environ.get('E2E_ISOLATED_DB') != 'yes':
    sys.exit('Refusing to run: set E2E_ISOLATED_DB=yes and point TEST_DB_* at a throwaway database.')

import base64
import hashlib
import hmac
import json
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

import psycopg2
import psycopg2.extras
psycopg2.extras.register_uuid()

ORDER, TICKET = os.environ.get('ORDER_URL', 'http://localhost:18092'), os.environ.get('TICKET_URL', 'http://localhost:18082')
SECRET = os.environ.get('JWT_SECRET', 'your-256-bit-secret-key-for-repair-shop-saas-jwt-signing').encode()  # dev default (application.yml)
db = psycopg2.connect(host=os.environ.get('TEST_DB_HOST', 'localhost'), port=int(os.environ.get('TEST_DB_PORT', '55432')),
                      user=os.environ.get('TEST_DB_USER', 'postgres'), password=os.environ.get('TEST_DB_PASSWORD', ''), dbname=os.environ.get('TEST_DB_NAME', 'repairshop'))
db.autocommit = True
results = []


def q(sql, *args):
    with db.cursor() as c:
        c.execute(sql, args)
        try:
            return c.fetchall()
        except psycopg2.ProgrammingError:
            return None


def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b'=').decode()


def token(sub, roles, shop=None):
    h = b64(json.dumps({'alg': 'HS256', 'typ': 'JWT'}).encode())
    claims = {'sub': str(sub), 'roles': roles, 'iat': int(time.time()), 'exp': int(time.time()) + 3600}
    if shop:
        claims['shopId'] = str(shop)
    p = b64(json.dumps(claims).encode())
    sig = b64(hmac.new(SECRET, f'{h}.{p}'.encode(), hashlib.sha256).digest())
    return f'{h}.{p}.{sig}'


def call(method, url, tok, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers={'Authorization': f'Bearer {tok}', 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode() or 'null'
            return r.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {'raw': raw[:200]}


def check(name, cond, detail=''):
    results.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'   -> {detail}'))


def code(resp):
    return (resp[1] or {}).get('code') if isinstance(resp[1], dict) else None


# ── seed (isolated test DB only) ──────────────────────────────────────────────
shopA, shopB = uuid.uuid4(), uuid.uuid4()
ownerA, ownerB, uT1, uT2, cust = (uuid.uuid4() for _ in range(5))
t1, t2 = uuid.uuid4(), uuid.uuid4()
addr = uuid.uuid4()
q("INSERT INTO shops (id, name, slug) VALUES (%s, 'Test Shop A', %s), (%s, 'Test Shop B', %s)", shopA, f'a-{shopA}', shopB, f'b-{shopB}')
q("INSERT INTO users (id, email, role, shop_id) VALUES (%s, %s, 'TECHNICIAN', %s), (%s, %s, 'TECHNICIAN', %s)", uT1, f"t1-{uT1}@test.local", shopA, uT2, f"t2-{uT2}@test.local", shopA)
q("INSERT INTO technicians (id, shop_id, name, phone, user_id) VALUES (%s, %s, 'Ravi K', '9000000001', %s), (%s, %s, 'Vignesh S', '9000000002', %s)", t1, shopA, uT1, t2, shopA, uT2)
q("INSERT INTO customer_users (id, full_name, mobile) VALUES (%s, 'Arun Customer', %s)", cust, '9' + str(cust.int)[:9])
q("INSERT INTO customer_addresses (id, customer_user_id, address_line, city, latitude, longitude) VALUES (%s, %s, '12 Main Rd', 'Cuddalore', 11.75, 79.75)", addr, cust)
IMEI = '356938035643809'
now = datetime.now(timezone(timedelta(hours=5, minutes=30)))


def sell_order(n, start):
    so = uuid.uuid4()
    q("""INSERT INTO sell_orders (id, sell_number, customer_user_id, shop_id, status, final_price, imei, address_id, color,
         pickup_date, pickup_slot_start, pickup_slot_end, created_at, updated_at)
         VALUES (%s, %s, %s, %s, 'PICKUP_SCHEDULED', 50000, %s, %s, 'Blue', %s, %s, %s, now(), now())""",
      so, n, cust, shopA, IMEI, addr, start.date(), start.time().replace(microsecond=0), (start + timedelta(hours=1)).time().replace(microsecond=0))
    q("INSERT INTO sell_order_quotations (id, sell_order_id, shop_id, quotation_price, status, note, created_at, updated_at) VALUES (%s, %s, %s, 50000, 'ACCEPTED', 'Free pickup', now(), now())", uuid.uuid4(), so, shopA)
    q("INSERT INTO marketplace_listings (id, seller_type, seller_id, product_name, expected_price, sell_order_id, status) VALUES (%s, 'CUSTOMER', %s, 'iPhone 13 128GB', 52000, %s, 'AVAILABLE')", uuid.uuid4(), cust, so)
    q("INSERT INTO customer_orders (id, order_number, customer_user_id, order_type, status) VALUES (%s, %s, %s, 'SELL', 'SELL_CONFIRMED')", uuid.uuid4(), n, cust)
    return so


SO1 = sell_order(f'SO-T1-{shopA.hex[:6]}', now + timedelta(minutes=30))
SO2 = sell_order(f'SO-T2-{shopA.hex[:6]}', now + timedelta(hours=3))

OW, OWB = token(ownerA, ['SHOP_OWNER'], shopA), token(ownerB, ['SHOP_OWNER'], shopB)
E1, E2 = token(uT1, ['TECHNICIAN'], shopA), token(uT2, ['TECHNICIAN'], shopA)
CU = token(cust, ['CUSTOMER'])
P = f'{ORDER}/sell-orders/shop-purchases'
E = f'{TICKET}/technicians/me/sell-pickups'
C = f'{ORDER}/sell-orders'

# ── permissions & assignment ──────────────────────────────────────────────────
r = call('GET', f'{P}/{SO1}', OW)
check('Partner reads purchase', r[0] == 200 and 'ASSIGN' in r[1].get('allowedActions', []), r)
check('Other shop owner blocked (403)', call('GET', f'{P}/{SO1}', OWB)[0] == 403)
check('Customer token blocked on Partner API (403)', call('GET', f'{P}/{SO1}', CU)[0] == 403)
check('Assign employee', call('POST', f'{P}/{SO1}/assign', OW, {'technicianId': str(t1)})[1].get('status') == 'PICKUP_ASSIGNED')
r = call('POST', f'{E}/{SO1}/accept', E2)
check('Other employee cannot accept (NOT_ASSIGNED)', r[0] == 409 and code(r) == 'NOT_ASSIGNED', r)
check('Employee accepts', call('POST', f'{E}/{SO1}/accept', E1)[1].get('accepted') is True)
call('POST', f'{P}/{SO2}/assign', OW, {'technicianId': str(t1)})
call('POST', f'{E}/{SO2}/accept', E1)
r = call('PATCH', f'{E}/{SO2}/status', E1, {'status': 'PICKUP_ON_THE_WAY'})
check('Journey blocked >1 h before slot (TOO_EARLY)', r[0] == 409 and code(r) == 'TOO_EARLY', r)

# ── journey + OTP ─────────────────────────────────────────────────────────────
r = call('PATCH', f'{E}/{SO1}/status', E1, {'status': 'PICKUP_ON_THE_WAY'})
check('Journey starts within 1 h of slot', r[0] == 200 and r[1].get('status') == 'PICKUP_ON_THE_WAY' and r[1].get('otpSentAt'), r)
check('PIN never sent to Employee App', 'pin' not in json.dumps(r[1]).lower().replace('pinned', ''), r)
pin = call('GET', f'{C}/{SO1}/purchase', CU)[1]['pickup'].get('pin')
check('Customer App shows 4-digit PIN', isinstance(pin, str) and len(pin) == 4, pin)
r = call('POST', f'{E}/{SO1}/inspection', E1, {'inspection': {'photos': ['http://x/1', 'http://x/2', 'http://x/3']}})
check('Inspection blocked before OTP (OTP_REQUIRED)', code(r) == 'OTP_REQUIRED', r)
wrong = '0000' if pin != '0000' else '1111'
codes = [code(call('POST', f'{E}/{SO1}/otp/verify', E1, {'otp': wrong})) for _ in range(5)]
r = call('POST', f'{E}/{SO1}/otp/verify', E1, {'otp': pin})
check('Wrong PIN x5 → OTP_INVALID then OTP_LOCKED', codes == ['OTP_INVALID'] * 5 and code(r) == 'OTP_LOCKED', (codes, r))
r = call('POST', f'{E}/{SO1}/otp/send', E1)
check('PIN resend rate-limited (30 s)', r[0] == 429 and code(r) == 'OTP_WAIT', r)
q("UPDATE sell_orders SET pickup_otp_sent_at = now() - interval '1 minute' WHERE id = %s", SO1)  # test clock shift
check('New PIN after lock', call('POST', f'{E}/{SO1}/otp/send', E1)[0] == 200)
pin2 = call('GET', f'{C}/{SO1}/purchase', CU)[1]['pickup'].get('pin')
r = call('POST', f'{E}/{SO1}/otp/verify', E1, {'otp': pin2})
check('Correct PIN verifies + arrival recorded', r[0] == 200 and r[1].get('otpVerifiedAt') and r[1].get('arrivedAt'), r)
check('PIN hidden from customer after verification', call('GET', f'{C}/{SO1}/purchase', CU)[1]['pickup'].get('pin') is None)

# ── inspection ────────────────────────────────────────────────────────────────
r = call('POST', f'{E}/{SO1}/inspection/start', E1)
check('Inspection started time recorded', r[0] == 200 and r[1].get('inspectionStartedAt'), r)
ev = call('GET', f'{E}/{SO1}/evaluation', E1)
check('Evaluation returns customer answers shape', ev[0] == 200 and 'conditions' in ev[1] and 'screeningAnswers' in ev[1], ev)
r = call('POST', f'{E}/{SO1}/payment', E1, {'method': 'UPI', 'proofUrl': 'http://x/p.jpg'})
check('Payment blocked before inspection (INSPECTION_REQUIRED)', code(r) == 'INSPECTION_REQUIRED', r)
r = call('POST', f'{E}/{SO1}/inspection', E1, {'inspection': {'photos': ['http://x/1', 'http://x/2']}})
check('Inspection needs ≥3 photos', r[0] == 400, r)
INS = {'compare': [{'key': 'model', 'label': 'Model', 'listing': 'iPhone 13', 'actual': 'iPhone 13', 'match': True}],
       'checks': [{'key': 'battery', 'label': 'Battery', 'result': 'FAIL', 'ok': False, 'note': '78%'}],
       'imei': IMEI, 'photos': ['http://x/1', 'http://x/2', 'http://x/3']}
check('Inspection saved', call('POST', f'{E}/{SO1}/inspection', E1, {'inspection': INS})[1].get('inspectedAt'))
d = call('GET', f'{P}/{SO1}', OW)[1]
check('Partner sees inspection + decision actions', d.get('pickupInspection') and {'CONFIRM_PICKUP_QUOTE', 'REVISE_PICKUP_PRICE', 'REQUEST_REINSPECTION'} <= set(d['allowedActions']), d.get('allowedActions'))
r = call('POST', f'{E}/{SO1}/payment', E1, {'method': 'UPI', 'proofUrl': 'http://x/p.jpg'})
check('Payment locked until owner approval (PRICE_NOT_APPROVED)', code(r) == 'PRICE_NOT_APPROVED', r)

# ── partner price approval / re-inspection / customer decisions ───────────────
r = call('POST', f'{P}/{SO1}/pickup-reinspect', OW, {'reason': 'Re-check the battery'})
check('Owner requests re-inspection', r[0] == 200 and r[1]['revision']['status'] == 'REINSPECT' and not r[1]['pickup']['inspectedAt'], r)
r = call('POST', f'{E}/{SO1}/revision', E1, {'amount': 40000, 'reason': 'Battery 78%'})
check('Employee must re-inspect first', code(r) == 'INSPECTION_REQUIRED' and 'Re-check the battery' in (r[1].get('message') or ''), r)
call('POST', f'{E}/{SO1}/inspection/start', E1)
call('POST', f'{E}/{SO1}/inspection', E1, {'inspection': INS})
check('Employee suggests ₹40,000 (SHOP_PENDING)', call('POST', f'{E}/{SO1}/revision', E1, {'amount': 40000, 'reason': 'Battery 78%'})[1]['revision']['status'] == 'SHOP_PENDING')
check('Owner approves suggestion → customer', call('POST', f'{P}/{SO1}/pickup-revision/approve', OW)[1]['revision']['status'] == 'SHOP_APPROVED')
cv = call('GET', f'{C}/{SO1}/purchase', CU)[1]
check('Customer App gets Approve/Reject/Negotiate', {'APPROVE_REVISION', 'REJECT_REVISION', 'NEGOTIATE_REVISION'} <= set(cv['allowedActions']), cv.get('allowedActions'))
r = call('POST', f'{P}/{SO1}/pickup-price/revise', OW, {'amount': 42000, 'reason': 'changing my mind'})
check('Owner cannot change price while with customer (409)', r[0] == 409, r)
check('Customer negotiates', call('POST', f'{C}/{SO1}/revision/negotiate', CU, {'amount': 46000, 'note': 'battery fine'})[1]['revision']['status'] == 'NEGOTIATION')
r = call('POST', f'{E}/{SO1}/payment', E1, {'method': 'UPI', 'proofUrl': 'http://x/p.jpg'})
check('Payment locked during negotiation', code(r) == 'PRICE_NOT_APPROVED', r)
check('Owner offers ₹44,000', call('POST', f'{P}/{SO1}/pickup-price/revise', OW, {'amount': 44000, 'reason': 'Meet halfway'})[1]['revision']['status'] == 'SHOP_APPROVED')
check('Customer rejects offer', call('POST', f'{C}/{SO1}/revision/reject', CU, {'note': 'too low'})[1]['revision']['status'] == 'REJECTED')
r = call('POST', f'{E}/{SO1}/payment', E1, {'method': 'UPI', 'proofUrl': 'http://x/p.jpg'})
check('Payment locked after rejection', code(r) == 'PRICE_NOT_APPROVED', r)
check('Owner offers ₹45,000', call('POST', f'{P}/{SO1}/pickup-price/revise', OW, {'amount': 45000, 'reason': 'Final offer'})[1]['revision']['status'] == 'SHOP_APPROVED')
r = call('POST', f'{C}/{SO1}/revision/approve', CU)
check('Customer accepts → final price ₹45,000', r[1]['revision']['status'] == 'APPROVED' and float(r[1]['finalPrice']) == 45000, r)
r = call('POST', f'{C}/{SO1}/revision/approve', CU)
check('Customer accept is idempotent', r[0] == 200)

# ── payment ───────────────────────────────────────────────────────────────────
r = call('POST', f'{E}/{SO1}/payment', E1, {'method': 'UPI', 'reference': 'UTR123456'})
check('Payment needs proof photo', r[0] == 400, r)
r = call('POST', f'{E}/{SO1}/payment', E1, {'method': 'UPI', 'reference': 'UTR123456', 'proofUrl': 'http://x/p.jpg'})
first_at = r[1].get('payment', {}).get('recordedAt') if r[0] == 200 else None
check('Employee records payment (₹45,000)', r[0] == 200 and float(r[1]['payment']['amount']) == 45000, r)
r = call('POST', f'{E}/{SO1}/payment', E1, {'method': 'CASH', 'proofUrl': 'http://x/q.jpg'})
check('Duplicate payment ignored', r[0] == 200 and r[1]['payment']['recordedAt'] == first_at and r[1]['payment']['method'] == 'UPI', r)
d = call('GET', f'{P}/{SO1}', OW)[1]
check('Partner sees payment awaiting customer confirmation', d['payment']['status'] == 'AWAITING_CONFIRMATION' and d['payment']['proofUrl'], d['payment'])
r = call('POST', f'{P}/{SO1}/assign', OW, {'technicianId': str(t2)})
check('Reassign blocked after payment', r[0] == 409, r)
r = call('POST', f'{C}/{SO1}/cancel', CU)
check('Customer cannot cancel after payment', r[0] == 409, r)
r = call('POST', f'{P}/{SO1}/cancel', OW, {'reason': 'test cancel after pay'})
check('Owner cannot cancel after payment', r[0] == 409, r)

# ── collect, handover, inventory ──────────────────────────────────────────────
check('Device collected', call('PATCH', f'{E}/{SO1}/status', E1, {'status': 'DEVICE_PICKED_UP'})[1].get('status') == 'DEVICE_PICKED_UP')
r = call('PATCH', f'{E}/{SO1}/status', E1, {'status': 'RECEIVED_AT_SHOP'})
check('Employee hand-over recorded, Employee App shows done', r[1].get('status') == 'RECEIVED_AT_SHOP' and r[1].get('handedOverAt'), r)
d = call('GET', f'{P}/{SO1}', OW)[1]
check('Purchase waits for owner verification (still PICKED_UP)', d['status'] == 'PICKED_UP' and d['pickup']['handedOverAt'] and 'RECEIVE' in d['allowedActions'], d['status'])
r = call('POST', f'{P}/{SO1}/receive', OW, {'imei': IMEI})
check('Receipt needs order + condition verification', r[0] == 400, r)
r = call('POST', f'{P}/{SO1}/receive', OW, {'imei': '356938035643800', 'orderVerified': True, 'conditionVerified': True})
check('Wrong IMEI refused', r[0] == 409, r)
r = call('POST', f'{P}/{SO1}/receive', OW, {'imei': IMEI, 'orderVerified': True, 'conditionVerified': True})
check('Verified receipt → PAYMENT_PENDING + stock ref', r[0] == 200 and r[1]['status'] == 'PAYMENT_PENDING' and (r[1].get('inventory') or {}).get('sku'), r)
r = call('POST', f'{P}/{SO1}/receive', OW, {'imei': IMEI, 'orderVerified': True, 'conditionVerified': True})
check('Second receipt is a no-op', r[0] == 200)
n_inv = q('SELECT count(*) FROM inventory_items WHERE shop_id = %s', shopA)[0][0]
check('Exactly one stock entry', n_inv == 1, n_inv)
r = call('POST', f'{P}/{SO1}/complete', OW)
check('Complete blocked before customer confirms payment', r[0] == 409, r)
check('Customer confirms payment', call('POST', f'{C}/{SO1}/payout/confirm', CU)[1].get('status') == 'PAID')
r = call('POST', f'{P}/{SO1}/complete', OW)
check('Purchase completed', r[0] == 200 and r[1]['status'] == 'COMPLETED', r)
check('Complete is idempotent', call('POST', f'{P}/{SO1}/complete', OW)[0] == 200)
n_inv = q('SELECT count(*) FROM inventory_items WHERE shop_id = %s', shopA)[0][0]
check('Still one stock entry after completion', n_inv == 1, n_inv)
inv = call('GET', f'{P}/inventory', OW)
check('Inventory list (owner) shows the device', inv[0] == 200 and len(inv[1]) == 1 and inv[1][0]['sku'].startswith('BUY-'), inv)
check('Inventory list is shop-scoped', call('GET', f'{P}/inventory', OWB)[1] == [])
mq = call('GET', f'{ORDER}/sell-orders/listing-quotations/mine', OW)
check('Quote badges endpoint', mq[0] == 200 and any(x.get('quotationStatus') == 'ACCEPTED' for x in mq[1]), mq)

# ── regression: shop-visit path (unchanged flow, modified receive/complete) ──
SO3 = sell_order(f'SO-T3-{shopA.hex[:6]}', now + timedelta(days=1))
q("UPDATE sell_order_quotations SET note = 'Customer will visit the shop' WHERE sell_order_id = %s", SO3)
d = call('GET', f'{P}/{SO3}', OW)[1]
check('Visit: fulfilment SHOP_VISIT, no ASSIGN, RECEIVE allowed', d['fulfilment'] == 'SHOP_VISIT' and 'ASSIGN' not in d['allowedActions'] and 'RECEIVE' in d['allowedActions'], d.get('allowedActions'))
r = call('POST', f'{P}/{SO3}/receive', OW)
check('Visit: receive without body → DEVICE_RECEIVED', r[0] == 200 and r[1]['status'] == 'DEVICE_RECEIVED', r)
r = call('POST', f'{P}/{SO3}/inspection', OW, {'imei': IMEI, 'modelVerified': True, 'condition': 'Good', 'checks': {'display': True}, 'photos': ['http://x/a', 'http://x/b']})
check('Visit: shop inspection', r[0] == 200 and r[1]['status'] == 'INSPECTED', r)
check('Visit: confirm price', call('POST', f'{P}/{SO3}/price/confirm', OW)[1].get('status') == 'PRICE_CONFIRMED')
check('Visit: record payout', call('POST', f'{P}/{SO3}/payout', OW, {'method': 'CASH'})[1].get('status') == 'PAYMENT_PENDING')
check('Visit: customer confirms', call('POST', f'{C}/{SO3}/payout/confirm', CU)[1].get('status') == 'PAID')
r = call('POST', f'{P}/{SO3}/complete', OW)
check('Visit: completed with stock ref', r[0] == 200 and r[1]['status'] == 'COMPLETED' and (r[1].get('inventory') or {}).get('sku'), r)
n_inv = q('SELECT count(*) FROM inventory_items WHERE shop_id = %s', shopA)[0][0]
check('Two purchases → two stock entries', n_inv == 2, n_inv)

# ── stricter stock table (as production might have): the purchase must still complete ──
def visit_purchase(tag):
    so = sell_order(f'SO-{tag}-{shopA.hex[:6]}', now + timedelta(days=1))
    q("UPDATE sell_order_quotations SET note = 'Customer will visit the shop' WHERE sell_order_id = %s", so)
    call('POST', f'{P}/{so}/receive', OW)
    call('POST', f'{P}/{so}/inspection', OW, {'imei': IMEI, 'modelVerified': True, 'condition': 'Good', 'checks': {'display': True}, 'photos': ['http://x/a', 'http://x/b']})
    call('POST', f'{P}/{so}/price/confirm', OW)
    call('POST', f'{P}/{so}/payout', OW, {'method': 'CASH'})
    call('POST', f'{C}/{so}/payout/confirm', CU)
    return so, call('POST', f'{P}/{so}/complete', OW)


q("ALTER TABLE inventory_items ADD CONSTRAINT e2e_category_check CHECK (category IN ('PART', 'ACCESSORY', 'CONSUMABLE')) NOT VALID")
so4, r = visit_purchase('T4')
cat = q('SELECT i.category FROM inventory_items i JOIN sell_orders s ON s.inventory_item_id = i.id WHERE s.id = %s', so4)
check('Category CHECK without DEVICE: completes, stock entry with empty category', r[0] == 200 and r[1]['status'] == 'COMPLETED' and cat == [(None,)], (r[0], cat))
q('ALTER TABLE inventory_items DROP CONSTRAINT e2e_category_check')
q("ALTER TABLE inventory_items ADD COLUMN e2e_required TEXT NOT NULL DEFAULT 'x'")
q('ALTER TABLE inventory_items ALTER COLUMN e2e_required DROP DEFAULT')
so5, r = visit_purchase('T5')
check('Extra required stock column: completes, stock entry skipped', r[0] == 200 and r[1]['status'] == 'COMPLETED' and r[1].get('inventory') is None, r[0])
q('ALTER TABLE inventory_items DROP COLUMN e2e_required')

passed = sum(1 for _, ok in results if ok)
print(f'\nRESULT: {passed}/{len(results)} passed')
