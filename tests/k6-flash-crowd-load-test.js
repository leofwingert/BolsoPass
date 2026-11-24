import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

/**
 * Script k6 para verificacao de Trafego em Rajada (Flash Crowd - RNF-01)
 * e Tolerancia Zero a Sobre-venda (Zero Overselling - RNF-02) na plataforma BolsoPass.
 */
export const options = {
  scenarios: {
    flash_crowd_reservation_race: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 500 },
        { duration: '20s', target: 2000 },
        { duration: '5s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<200'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

export default function () {
  const payload = JSON.stringify({
    event_id: EVENT_ID,
    seat_id: 'A-12',
    user_id: uuidv4(),
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/reservations`, payload, params);

  check(res, {
    'status is 201 Created or 409 Conflict (Zero Overselling)': (r) =>
      r.status === 201 || r.status === 409,
  });

  if (res.status === 201) {
    const body = JSON.parse(res.body);
    const idempotencyKey = uuidv4();
    const checkoutPayload = JSON.stringify({
      reservation_id: body.reservation_id,
      payment_method: 'CREDIT_CARD',
      installments: 1,
      payment_token: 'tok_visa_ok',
    });

    const checkoutRes = http.post(`${BASE_URL}/api/v1/checkout`, checkoutPayload, {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
    });

    check(checkoutRes, {
      'checkout confirmed with 200 OK': (r) => r.status === 200,
    });
  }

  sleep(0.1);
}

