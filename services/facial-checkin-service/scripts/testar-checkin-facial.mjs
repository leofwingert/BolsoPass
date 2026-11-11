#!/usr/bin/env node
/**
 * Teste manual do check-in facial, de ponta a ponta, contra o ambiente do docker compose.
 *
 *   node services/facial-checkin-service/scripts/testar-checkin-facial.mjs <selfie> [foto-portaria] [url]
 *
 *   selfie         foto usada no cadastro do rosto (JPEG ou PNG)
 *   foto-portaria  foto tirada "na catraca" (padrão: a mesma selfie)
 *   url            padrão http://localhost:3000 (nginx); pelo celular/rede: https://<IP>:3443
 *
 * O que ele faz: cria um cliente novo, vende 1 ingresso no balcão para ele (titular), aceita o
 * termo LGPD, cadastra o rosto, faz duas leituras na portaria, revoga o consentimento e mostra
 * cada resposta. No modo simulado (sem AWS) só a MESMA imagem é reconhecida; com a AWS ligada,
 * fotos diferentes da mesma pessoa também.
 */
import { readFileSync } from 'node:fs';
import { basename, extname } from 'node:path';

const [selfiePath, gatePath = selfiePath, base = 'http://localhost:3000'] = process.argv.slice(2);
if (!selfiePath) {
  console.error('Uso: node testar-checkin-facial.mjs <selfie> [foto-portaria] [url]');
  process.exit(1);
}
if (base.startsWith('https')) process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'; // certificado de desenvolvimento
const API = `${base}/api/v1`;
const EVENT = process.env.EVENTO ?? 'evt-neon';

const type = (p) => (extname(p).toLowerCase() === '.png' ? 'image/png' : 'image/jpeg');
const photo = (p) => new Blob([readFileSync(p)], { type: type(p) });
const form = (fields, path) => {
  const f = new FormData();
  for (const [k, v] of Object.entries(fields)) f.append(k, v);
  f.append('image', photo(path), basename(path));
  return f;
};
const cpf = () => {
  const d = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  for (const p of [9, 10]) {
    let s = 0;
    for (let i = 0; i < p; i++) s += d[i] * (p + 1 - i);
    const r = (s * 10) % 11;
    d.push(r === 10 ? 0 : r);
  }
  return d.join('');
};

async function call(method, path, token, body, headers = {}) {
  const init = { method, headers: { ...headers } };
  if (token) init.headers.Authorization = `Bearer ${token}`;
  if (body instanceof FormData) init.body = body;
  else if (body) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const r = await fetch(API + path, init);
  const text = await r.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: r.status, json };
}

function show(label, r) {
  const j = r.json ?? {};
  const detail = j.status
    ? `${j.status}${j.reason ? ` (${j.reason})` : ''}${j.holderName ? ` — ${j.holderName}, ${j.ticketId}` : ''}${j.similarity ? `, similaridade ${j.similarity.toFixed(1)}%` : ''}`
    : (j.message ?? JSON.stringify(j));
  console.log(`${label.padEnd(34)} HTTP ${r.status}  ${detail}`);
}

// 1. Cliente novo + ingresso vendido no balcão (ele é o titular).
const doc = cpf();
const reg = await call('POST', '/auth/register', null, {
  name: 'Cliente Teste Facial', email: `facial.${Date.now()}@teste.io`, cpf: doc, phone: '(51) 99999-0000',
  password: 'senhaforte', acceptTerms: true,
});
if (reg.status !== 201) throw new Error(`Cadastro falhou: ${JSON.stringify(reg.json)}`);
const client = reg.json.accessToken;
const op = (await call('POST', '/auth/operator/login', null, { badge: 'OP-88219', pin: '123456' })).json.accessToken;
const seats = (await call('GET', `/operator/box-office/events/${EVENT}/seats`, op)).json.seats;
const seat = seats.find((s) => s.status === 'DISPONIVEL' && s.kind === 'PADRAO')?.id;
if (!seat) throw new Error(`Sem lugar vago em ${EVENT} (defina EVENTO=outro-evento)`);
const res = (await call('POST', `/operator/box-office/events/${EVENT}/reservations`, op, { customerId: reg.json.user.id, seatIds: [seat] })).json;
const order = (await call('POST', '/operator/box-office/checkout', op,
  { customerId: reg.json.user.id, payment: { reservationId: res.reservation.id, method: 'PIX', installments: 1, holders: [{ seatId: seat, name: 'Cliente Teste Facial', cpf: doc }] } },
  { 'Idempotency-Key': crypto.randomUUID() })).json;
const ticketId = order.items[0].ticketId;
console.log(`\nIngresso ${ticketId} (evento ${EVENT}) vendido para ${reg.json.user.email}\n`);

// 2. Cadastro do rosto.
show('Cadastrar rosto SEM aceitar termo', await call('POST', '/facial/enrollments', client, form({ ticketId }, selfiePath)));
const consent = await call('POST', '/facial/consent', client, { termVersion: 'biometria-v1', accepted: true });
console.log(`${'Aceitar termo LGPD'.padEnd(34)} HTTP ${consent.status}  ativo=${consent.json.active}`);
show('Cadastrar rosto (selfie)', await call('POST', '/facial/enrollments', client, form({ ticketId }, selfiePath)));

// 3. Portaria.
show('Portaria: 1ª leitura', await call('POST', `/facial/events/${EVENT}/checkins`, op, form({ gate: 'Portão 01' }, gatePath)));
show('Portaria: 2ª leitura (mesma pessoa)', await call('POST', `/facial/events/${EVENT}/checkins`, op, form({ gate: 'Portão 02' }, gatePath)));

// 4. Revogação.
const rv = await call('DELETE', '/facial/consent', client);
console.log(`${'Revogar consentimento'.padEnd(34)} HTTP ${rv.status}  ativo=${rv.json.active}`);
console.log(`\nVeja o evento no AKHQ: ${base.replace(/:\d+$/, '')}:8089 → Topics → checkin.realizado (procure ${ticketId})`);
