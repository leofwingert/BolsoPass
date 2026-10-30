import type { Seat, SeatKind, SeatStatus } from '@/domain/seat';
import type { EventDetail, Gate, Lot, Sector, User } from '@/api/types';

/** Dados de exemplo baseados nos mockups do Figma (Mocks-User). Datas relativas a "agora". */
const DAY = 24 * 60 * 60 * 1000;
const at = (days: number, hour = 21) => {
  const d = new Date(Date.now() + days * DAY);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
};

export const DEMO_PASSWORD = 'bolsopass';
export const DEMO_OPERATOR_PIN = '123456';

export const users: (User & { password?: string; pin?: string })[] = [
  {
    id: 'u-sarah',
    name: 'Sarah Jensen Oliveira',
    email: 'sarah@bolsopass.io',
    roles: ['ROLE_CLIENT'],
    cpf: '529.982.247-25',
    phone: '(11) 98844-3210',
    password: DEMO_PASSWORD,
  },
  {
    id: 'u-admin',
    name: 'Beatriz Lima',
    email: 'admin@bolsopass.io',
    roles: ['ROLE_ORGANIZER', 'ROLE_ADMIN'],
    password: DEMO_PASSWORD,
  },
  {
    id: 'u-operator',
    name: 'Carlos Silva',
    email: 'carlos.silva@bolsopass.io',
    roles: ['ROLE_OPERATOR'],
    badge: 'OP-88219',
    pin: DEMO_OPERATOR_PIN,
  },
];

/** Layout físico padrão: quantidade de assentos por setor e por fileira. */
const SECTOR_LAYOUT: Record<string, { seats: number; perRow: number }> = {
  A: { seats: 24, perRow: 12 },
  V: { seats: 6, perRow: 6 },
  B: { seats: 6, perRow: 6 },
  T: { seats: 20, perRow: 10 },
};

interface SectorSpec {
  id: keyof typeof SECTOR_LAYOUT;
  name: string;
  priceCents: number;
  originalPriceCents?: number;
  lotName: string;
  description: string;
  perks: string[];
  gate: string;
  limitPerCpf: number;
  maxInstallments: number;
  status?: Lot['status'];
  color: string;
}

function buildSectors(eventId: string, specs: SectorSpec[]): { sectors: Sector[]; lots: Lot[] } {
  const sectors: Sector[] = specs.map((s) => ({
    id: s.id,
    name: s.name,
    kind: 'NUMERADO',
    capacity: SECTOR_LAYOUT[s.id]!.seats,
    priceCents: s.priceCents,
    color: s.color,
  }));
  const lots: Lot[] = specs.map((s) => ({
    id: `${eventId}-lot-${s.id}`,
    sectorId: s.id,
    name: s.lotName,
    description: s.description,
    priceCents: s.priceCents,
    originalPriceCents: s.originalPriceCents,
    quantity: SECTOR_LAYOUT[s.id]!.seats,
    sold: 0,
    limitPerCpf: s.limitPerCpf,
    maxInstallments: s.maxInstallments,
    status: s.status ?? 'ATIVO',
    perks: s.perks,
    gate: s.gate,
  }));
  return { sectors, lots };
}

const defaultSpecs = (base: number): SectorSpec[] => [
  {
    id: 'A',
    name: 'Setor A • Gold Premium',
    priceCents: base,
    lotName: 'Lote 03 de 05',
    description: 'Visão frontal privilegiada, colado ao palco principal.',
    perks: ['Entrada dedicada Portão B', 'Pulseira RFID inclusa'],
    gate: 'Portão 04 - VIP',
    limitPerCpf: 4,
    maxInstallments: 10,
    color: '#e11d48',
  },
  {
    id: 'V',
    name: 'Camarote VIP Lounge',
    priceCents: Math.round(base * 1.41),
    lotName: 'Lote 02 de 03',
    description: 'Deck elevado, open bar, gastronomia autoral e after party exclusivo.',
    perks: ['Entrada VIP Portão VIP-1', 'Fast pass para atrações'],
    gate: 'Portão VIP Sul',
    limitPerCpf: 2,
    maxInstallments: 12,
    color: '#1e293b',
  },
  {
    id: 'B',
    name: 'Setor B • Lateral',
    priceCents: Math.round(base * 0.59),
    lotName: 'Lote 02 de 04',
    description: 'Visão lateral com assentos numerados.',
    perks: ['Entrada Portão 02'],
    gate: 'Portão 02 - Leste',
    limitPerCpf: 4,
    maxInstallments: 6,
    color: '#64748b',
  },
  {
    id: 'T',
    name: 'Tribuna Superior',
    priceCents: Math.round(base * 0.45),
    lotName: 'Lote 01 de 02',
    description: 'Visão panorâmica com cobertura completa.',
    perks: ['Entrada Portão 01'],
    gate: 'Portão 01 - Pista Comum',
    limitPerCpf: 4,
    maxInstallments: 6,
    color: '#94a3b8',
  },
];

function event(
  e: Omit<EventDetail, 'sectors' | 'lots'> & { specs?: SectorSpec[] },
): EventDetail {
  const { specs, ...rest } = e;
  const built = buildSectors(e.id, specs ?? defaultSpecs(e.fromPriceCents));
  return { ...rest, ...built };
}

export const events: EventDetail[] = [
  event({
    id: 'evt-synthwave',
    title: 'Festival Synthwave 2026',
    venue: 'Arena Pulse Cyber Dome',
    city: 'São Paulo, SP',
    startsAt: at(12, 18),
    endsAt: at(14, 23),
    category: 'FESTIVAIS',
    status: 'EM_VENDAS',
    fromPriceCents: 58_000,
    capacity: 48_000,
    sold: 47_040,
    hue: 330,
    highlight: 'Últimos ingressos',
    hasVirtualQueue: true,
    numberedSeats: true,
    description:
      'A maior imersão audiovisual retrofuturista da América Latina. 4 palcos simultâneos, som espacial 360° e controle de acesso com QR dinâmico.',
    address: 'Av. das Nações Unidas, 17.000 - São Paulo, SP',
    ageRating: '18+',
    nextLotAt: new Date(Date.now() + 5 * 60 * 60 * 1000 + 22 * 60 * 1000).toISOString(),
    lineup: [
      {
        stage: 'Palco Neon Horizon',
        artists: 'Gunship & The Midnight',
        description: 'Show sinfônico com orquestra cibernética e projeção holográfica.',
      },
      {
        stage: 'Palco Cyber Grid',
        artists: 'Carpenter Brut + Kavinsky',
        description: 'Sets contínuos de darksynth com pirotecnia sincronizada.',
      },
      {
        stage: 'Palco Sunset Drive',
        artists: 'FM-84 & Timecop1983',
        description: 'Sessão nostálgica dos anos 80 durante o pôr do sol.',
      },
    ],
    specs: [
      { ...defaultSpecs(58_000)[0]!, name: 'Pista Premium Front Stage', originalPriceCents: 89_000 },
      { ...defaultSpecs(58_000)[1]!, name: 'Camarote Synth Lounge Open Bar' },
      { ...defaultSpecs(58_000)[2]!, name: 'Setor B • Lateral' },
      { ...defaultSpecs(58_000)[3]!, name: 'Cadeira Superior Coberta', status: 'ESGOTADO' },
    ],
  }),
  event({
    id: 'evt-neon',
    title: 'Neon Odyssey World Tour',
    venue: 'Allianz Dome',
    city: 'São Paulo, SP',
    startsAt: at(30, 21),
    category: 'ELETRONICA',
    status: 'EM_VENDAS',
    fromPriceCents: 34_000,
    capacity: 50_000,
    sold: 38_000,
    hue: 200,
    highlight: 'Assentos 360° disponíveis',
    hasVirtualQueue: false,
    numberedSeats: true,
    description: 'Turnê mundial com palco central 360° e plateia ao redor.',
    address: 'Av. Francisco Matarazzo, 1705 - São Paulo, SP',
    ageRating: '16+',
    lineup: [
      { stage: 'Palco Central 360°', artists: 'Neon Odyssey', description: 'Show completo da turnê.' },
    ],
  }),
  event({
    id: 'evt-metal',
    title: 'Cyber Metal Fest 2026',
    venue: 'Arena da Baixada',
    city: 'Curitiba, PR',
    startsAt: at(14, 19),
    category: 'ROCK',
    status: 'EM_VENDAS',
    fromPriceCents: 19_000,
    capacity: 25_000,
    sold: 22_000,
    hue: 18,
    highlight: 'Lote promocional 01',
    hasVirtualQueue: false,
    numberedSeats: true,
    description: 'Dois dias de metal industrial com pirotecnia e telões 8K.',
    address: 'R. Buenos Aires, 1260 - Curitiba, PR',
    ageRating: '16+',
    lineup: [{ stage: 'Main Stage', artists: 'Line-up em breve', description: '' }],
  }),
  event({
    id: 'evt-symphonic',
    title: 'Pulse Symphonic Experience',
    venue: 'Qualistage',
    city: 'Rio de Janeiro, RJ',
    startsAt: at(37, 20),
    category: 'TEATRO',
    status: 'EM_VENDAS',
    fromPriceCents: 15_000,
    capacity: 8_500,
    sold: 5_270,
    hue: 265,
    highlight: 'Meia-entrada disponível',
    hasVirtualQueue: false,
    numberedSeats: true,
    description: 'Orquestra ao vivo interpretando trilhas eletrônicas clássicas.',
    address: 'Av. Ayrton Senna, 3000 - Rio de Janeiro, RJ',
    ageRating: 'Livre',
    lineup: [{ stage: 'Plateia', artists: 'Orquestra Pulse', description: '' }],
  }),
  event({
    id: 'evt-techno',
    title: 'Techno Rave Horizon 2027',
    venue: 'Autódromo de Interlagos',
    city: 'São Paulo, SP',
    startsAt: at(85, 22),
    category: 'ELETRONICA',
    status: 'AGENDADO',
    fromPriceCents: 24_000,
    capacity: 60_000,
    sold: 0,
    hue: 160,
    hasVirtualQueue: true,
    numberedSeats: false,
    description: 'Abertura de vendas programada.',
    address: 'Av. Sen. Teotônio Vilela, 261 - São Paulo, SP',
    ageRating: '18+',
    lineup: [],
  }),
  event({
    id: 'evt-acoustic',
    title: 'Acoustic Nights Session',
    venue: 'Auditório Ibirapuera',
    city: 'São Paulo, SP',
    startsAt: at(104, 19),
    category: 'TEATRO',
    status: 'RASCUNHO',
    fromPriceCents: 0,
    capacity: 1_500,
    sold: 0,
    hue: 40,
    hasVirtualQueue: false,
    numberedSeats: true,
    description: 'Mapa de assentos pendente.',
    address: 'Av. Pedro Álvares Cabral - São Paulo, SP',
    ageRating: 'Livre',
    lineup: [],
  }),
  event({
    id: 'evt-retro',
    title: 'Synth Retro Classic 2025',
    venue: 'Arena Anhembi',
    city: 'São Paulo, SP',
    startsAt: at(-120, 17),
    category: 'ELETRONICA',
    status: 'ENCERRADO',
    fromPriceCents: 15_000,
    capacity: 32_000,
    sold: 32_000,
    hue: 290,
    hasVirtualQueue: false,
    numberedSeats: true,
    description: 'Evento encerrado. Check-in concluído.',
    address: 'Av. Olavo Fontoura, 1209 - São Paulo, SP',
    ageRating: '18+',
    lineup: [],
  }),
];

/** Pseudo-aleatório determinístico para o estado inicial do mapa. */
function rand(seed: string): number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 16777619);
  return ((h >>> 0) % 1000) / 1000;
}

export function buildSeats(ev: EventDetail): Seat[] {
  const seats: Seat[] = [];
  for (const sector of ev.sectors) {
    const layout = SECTOR_LAYOUT[sector.id]!;
    const lot = ev.lots.find((l) => l.sectorId === sector.id);
    for (let n = 1; n <= layout.seats; n++) {
      const id = `${ev.id}:${sector.id}${n}`;
      const r = rand(id);
      let status: SeatStatus = 'DISPONIVEL';
      if (lot?.status === 'ESGOTADO' || ev.status === 'ENCERRADO') status = 'VENDIDO';
      else if (r < 0.28) status = 'VENDIDO';
      let kind: SeatKind = 'PADRAO';
      if (sector.id === 'A' && (n === 3 || n === 4)) kind = 'PCD';
      seats.push({
        id,
        sectorId: sector.id,
        row: `${sector.id}${Math.ceil(n / layout.perRow)}`,
        number: n,
        label: `${sector.id}${String(n).padStart(2, '0')}`,
        kind,
        status,
      });
    }
  }
  return seats;
}

export const gates: Gate[] = [
  {
    id: 'g-01',
    name: 'Portão 01 — Pista Comum',
    turnstiles: 'Catracas 1 a 6',
    description: 'Fluxo alto • Validador óptico ativo',
    icon: 'door',
  },
  {
    id: 'g-02',
    name: 'Portão 02 — Pista Geral Leste',
    turnstiles: 'Catracas 7 a 11',
    description: 'Fluxo moderado • Acesso padrão',
    icon: 'door',
  },
  {
    id: 'g-vip',
    name: 'Portão VIP Sul',
    turnstiles: 'Catracas 12 a 14',
    description: 'Pulseiras NFC + Camarote Backstage',
    icon: 'star',
  },
  {
    id: 'g-pcd',
    name: 'Portão PCD / Acessibilidade',
    turnstiles: 'Catraca Larga A1',
    description: 'Rampa NBR 9050 • Acompanhantes',
    icon: 'accessible',
  },
];

export const holderNames = [
  'Mariana Duarte Souza',
  'Rodrigo Matos',
  'Lucas F. Andrade',
  'Camila Valença',
  'Thiago Nogueira',
  'Gabriel S. Silveira',
  'Ana Beatriz Rocha',
  'Pedro Henrique Lima',
  'Juliana Prado',
  'Rafael Costa',
];

/** CPFs válidos fictícios para os titulares gerados. */
export const holderCpfs = [
  '714.392.810-08',
  '384.921.048-22',
  '412.008.971-89',
  '853.513.468-93',
  '111.444.777-35',
  '935.411.347-80',
  '276.581.394-03',
  '529.982.247-25',
  '168.995.350-09',
  '461.807.523-91',
];
