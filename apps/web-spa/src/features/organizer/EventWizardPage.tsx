import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Accessibility,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Ban,
  Check,
  CheckCircle2,
  EyeOff,
  Lock,
  Plus,
  Timer,
  Trash2,
} from 'lucide-react';
import { useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { CreateEventInput, EventCategory, MarkedSeatKind } from '@/api/types';
import { Badge, Button, Card, Field, inputClass } from '@/components/ui';
import { lotAllocation, validateLots } from '@/domain/lots';
import { SERVICE_FEE_RATE } from '@/domain/pricing';
import { cx, formatMoney } from '@/lib/format';
import { CATEGORY_LABEL } from '../client/labels';

interface LotForm {
  name: string;
  price: string;
  quantity: number;
  limitPerCpf: number;
  maxInstallments: number;
}
interface SectorForm {
  name: string;
  capacity: number;
  seatsPerRow: number;
  gate: string;
  lots: LotForm[];
}
type Tool = MarkedSeatKind | 'LIMPAR';

const STEPS = ['Dados gerais', 'Local & capacidade', 'Setores & lotes', 'Mapa & assentos', 'Revisão final'];
const cents = (price: string) => Math.round(Number(price.replace(/\./g, '').replace(',', '.')) * 100) || 0;
const newLot = (n: number, quantity: number): LotForm => ({ name: `${n}º Lote`, price: '', quantity, limitPerCpf: 4, maxInstallments: 10 });
const MARK_STYLE: Record<MarkedSeatKind, string> = {
  PCD: 'bg-indigo-100 text-indigo-700',
  BLOQUEADO_PRODUCAO: 'bg-brand-100 text-brand-700',
  OBSTRUCAO: 'bg-surface-strong text-muted line-through',
};

function Step({ n, title, children }: { n: string; title: string; children: ReactNode }) {
  return (
    <Card className="p-6">
      <p className="text-xs font-semibold tracking-wider text-muted uppercase">Etapa {n}</p>
      <h2 className="mb-4 text-xl font-bold">{title}</h2>
      {children}
    </Card>
  );
}

export default function EventWizardPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [step, setStep] = useState(0);
  const [info, setInfo] = useState({
    title: '',
    category: 'FESTIVAIS' as EventCategory,
    date: '',
    time: '20:00',
    description: '',
    ageRating: '18+',
    hasVirtualQueue: false,
    venue: '',
    city: '',
    address: '',
  });
  const [sectors, setSectors] = useState<SectorForm[]>([
    { name: 'Pista Premium', capacity: 40, seatsPerRow: 10, gate: 'Portão 01', lots: [newLot(1, 20), newLot(2, 20)] },
  ]);
  const [active, setActive] = useState(0);
  const [tool, setTool] = useState<Tool>('PCD');
  const [marks, setMarks] = useState<Record<string, MarkedSeatKind>>({});

  const sellable = (i: number) =>
    sectors[i]!.capacity -
    Object.entries(marks).filter(([k, v]) => k.startsWith(`${i}:`) && v !== 'PCD').length;

  const issues = useMemo(() => {
    const out: string[] = [];
    if (info.title.trim().length < 3) out.push('Informe o nome do evento (passo 1).');
    if (!info.date || Date.parse(`${info.date}T${info.time}`) <= Date.now()) out.push('Escolha uma data futura (passo 1).');
    if (!info.venue.trim() || !info.city.trim()) out.push('Informe local e cidade (passo 2).');
    sectors.forEach((s, i) => {
      if (!s.name.trim()) out.push(`Setor ${i + 1} sem nome.`);
      if (s.capacity < 1 || s.capacity > 500) out.push(`${s.name}: capacidade entre 1 e 500 assentos.`);
      const alloc = lotAllocation({
        id: String(i),
        name: s.name,
        capacity: sellable(i),
        lots: s.lots.map((l, j) => ({ id: String(j), name: l.name, priceCents: cents(l.price), quantity: l.quantity, limitPerCpf: l.limitPerCpf, soldCount: 0 })),
      });
      if (!alloc.ok) out.push(`${s.name}: soma dos lotes (${alloc.allocated}) ≠ assentos vendáveis (${alloc.capacity}).`);
      validateLots({
        id: String(i), name: s.name, capacity: s.capacity,
        lots: s.lots.map((l, j) => ({ id: String(j), name: l.name, priceCents: cents(l.price), quantity: l.quantity, limitPerCpf: l.limitPerCpf, soldCount: 0 })),
      }).forEach((issue) => out.push(`${s.name} • ${s.lots[Number(issue.lotId)]?.name}: ${issue.message}.`));
    });
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [info, sectors, marks]);

  const save = useMutation({
    mutationFn: (publish: boolean) => {
      const input: CreateEventInput = {
        title: info.title.trim(),
        category: info.category,
        startsAt: new Date(`${info.date}T${info.time}`).toISOString(),
        venue: info.venue.trim(),
        city: info.city.trim(),
        address: info.address.trim() || undefined,
        description: info.description.trim() || undefined,
        ageRating: info.ageRating,
        hasVirtualQueue: info.hasVirtualQueue,
        sectors: sectors.map((s) => ({
          name: s.name.trim(),
          capacity: s.capacity,
          seatsPerRow: s.seatsPerRow,
          gate: s.gate.trim() || undefined,
          lots: s.lots.map((l) => ({ name: l.name.trim(), priceCents: cents(l.price), quantity: l.quantity, limitPerCpf: l.limitPerCpf, maxInstallments: l.maxInstallments })),
        })),
        blockedSeats: Object.entries(marks).map(([k, kind]) => {
          const [sector, number] = k.split(':').map(Number);
          return { sector: sector!, number: number!, kind };
        }),
        publish,
      };
      return api.organizer.createEvent(input);
    },
    onSuccess: ({ id }) => {
      queryClient.invalidateQueries({ queryKey: ['organizer'] });
      queryClient.invalidateQueries({ queryKey: ['events'] });
      navigate(`/organizer/events/${id}`);
    },
  });

  const updateSector = (i: number, patch: Partial<SectorForm>) =>
    setSectors((prev) => prev.map((s, j) => (j === i ? { ...s, ...patch } : s)));
  const updateLot = (i: number, j: number, patch: Partial<LotForm>) =>
    updateSector(i, { lots: sectors[i]!.lots.map((l, k) => (k === j ? { ...l, ...patch } : l)) });

  function toggleSeat(sector: number, n: number) {
    const key = `${sector}:${n}`;
    setMarks((prev) => {
      const next = { ...prev };
      if (tool === 'LIMPAR' || next[key] === tool) delete next[key];
      else next[key] = tool;
      return next;
    });
  }

  const totalCapacity = sectors.reduce((s, x) => s + x.capacity, 0);
  const totalLots = sectors.reduce((s, x) => s + x.lots.length, 0);
  const sector = sectors[active];

  return (
    <div className="grid gap-6">
      <Card className="p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs font-bold tracking-wider text-brand-600 uppercase">● Novo evento</p>
            <h1 className="text-2xl font-bold">Cadastro do evento e dos ingressos</h1>
          </div>
          <Badge tone="ok" icon={<Lock className="size-3" />}>Venda sem assento duplicado</Badge>
        </div>
        <ol className="mt-4 grid gap-2 sm:grid-cols-5">
          {STEPS.map((label, i) => (
            <li key={label}>
              <button onClick={() => setStep(i)}
                className={cx('flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm', i === step ? 'bg-brand-600 text-white' : 'bg-surface')}>
                <span className={cx('grid size-6 shrink-0 place-items-center rounded-full text-xs font-bold', i === step ? 'bg-white text-brand-600' : i < step ? 'bg-ok text-white' : 'bg-white')}>
                  {i < step ? <Check className="size-3.5" /> : i + 1}
                </span>
                <span className="leading-tight">Passo {i + 1}<br /><b>{label}</b></span>
              </button>
            </li>
          ))}
        </ol>
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="grid content-start gap-6">
          {step === 0 && (
            <Step n="1" title="Dados gerais">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Nome do evento" className="sm:col-span-2"><input className={inputClass} value={info.title} onChange={(e) => setInfo({ ...info, title: e.target.value })} placeholder="Ex.: Festival Synthwave 2027" /></Field>
                <Field label="Categoria">
                  <select className={inputClass} value={info.category} onChange={(e) => setInfo({ ...info, category: e.target.value as EventCategory })}>
                    {(Object.keys(CATEGORY_LABEL) as EventCategory[]).map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
                  </select>
                </Field>
                <Field label="Classificação"><input className={inputClass} value={info.ageRating} onChange={(e) => setInfo({ ...info, ageRating: e.target.value })} /></Field>
                <Field label="Data"><input type="date" className={inputClass} value={info.date} onChange={(e) => setInfo({ ...info, date: e.target.value })} /></Field>
                <Field label="Horário"><input type="time" className={inputClass} value={info.time} onChange={(e) => setInfo({ ...info, time: e.target.value })} /></Field>
                <Field label="Descrição" className="sm:col-span-2">
                  <textarea className={cx(inputClass, 'h-24 py-2')} value={info.description} onChange={(e) => setInfo({ ...info, description: e.target.value })} />
                </Field>
                <label className="flex items-center gap-3 rounded-lg bg-surface p-3 text-sm sm:col-span-2">
                  <input type="checkbox" className="size-4 accent-brand-600" checked={info.hasVirtualQueue} onChange={(e) => setInfo({ ...info, hasVirtualQueue: e.target.checked })} />
                  <span><b>Sala de espera virtual</b><br /><span className="text-xs text-muted">Para aberturas com muita procura: o comprador só entra no mapa quando chega a vez.</span></span>
                </label>
              </div>
            </Step>
          )}

          {step === 1 && (
            <Step n="2" title="Local & capacidade">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Local / arena"><input className={inputClass} value={info.venue} onChange={(e) => setInfo({ ...info, venue: e.target.value })} /></Field>
                <Field label="Cidade"><input className={inputClass} value={info.city} onChange={(e) => setInfo({ ...info, city: e.target.value })} placeholder="São Paulo, SP" /></Field>
                <Field label="Endereço" className="sm:col-span-2"><input className={inputClass} value={info.address} onChange={(e) => setInfo({ ...info, address: e.target.value })} /></Field>
              </div>
              <h3 className="mt-6 mb-2 font-bold">Setores</h3>
              <div className="grid gap-3">
                {sectors.map((s, i) => (
                  <div key={i} className="grid gap-3 rounded-xl bg-surface p-4 sm:grid-cols-[1.5fr_1fr_1fr_1fr_auto] sm:items-end">
                    <Field label="Nome do setor"><input className={inputClass} value={s.name} onChange={(e) => updateSector(i, { name: e.target.value })} /></Field>
                    <Field label="Assentos"><input type="number" min={1} max={500} className={inputClass} value={s.capacity} onChange={(e) => updateSector(i, { capacity: Number(e.target.value) })} /></Field>
                    <Field label="Por fileira"><input type="number" min={1} max={30} className={inputClass} value={s.seatsPerRow} onChange={(e) => updateSector(i, { seatsPerRow: Number(e.target.value) })} /></Field>
                    <Field label="Portão"><input className={inputClass} value={s.gate} onChange={(e) => updateSector(i, { gate: e.target.value })} /></Field>
                    <Button variant="ghost" aria-label={`Remover ${s.name}`} disabled={sectors.length === 1}
                      onClick={() => { setSectors(sectors.filter((_, j) => j !== i)); setMarks({}); setActive(0); }}>
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                ))}
              </div>
              <Button variant="soft" className="mt-3" disabled={sectors.length >= 8}
                onClick={() => setSectors([...sectors, { name: `Setor ${sectors.length + 1}`, capacity: 20, seatsPerRow: 10, gate: '', lots: [newLot(1, 20)] }])}>
                <Plus className="size-4" aria-hidden /> Adicionar setor
              </Button>
              <p className="mt-3 text-xs text-muted">Capacidade total: <b>{totalCapacity}</b> lugares. Cada assento vira um registro com identificador próprio no banco.</p>
            </Step>
          )}

          {step === 2 && (
            <Step n="3" title="Lotes & regras de venda">
              <div className="mb-4 flex flex-wrap gap-2">
                {sectors.map((s, i) => (
                  <button key={i} onClick={() => setActive(i)} className={cx('rounded-lg px-3 py-1.5 text-sm font-semibold', i === active ? 'bg-brand-600 text-white' : 'bg-surface')}>
                    {s.name} <span className="ml-1 rounded-full bg-white/80 px-1.5 text-[10px] text-ink">{s.capacity} un</span>
                  </button>
                ))}
              </div>
              {sector && (
                <>
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[620px] text-sm">
                      <thead className="text-left text-[11px] tracking-wider text-muted uppercase">
                        <tr><th className="py-2">Lote</th><th>Valor (R$)</th><th>Cota</th><th>Limite CPF</th><th>Parcelas</th><th /></tr>
                      </thead>
                      <tbody className="divide-y divide-line">
                        {sector.lots.map((l, j) => (
                          <tr key={j}>
                            <td className="py-2 pr-2"><input className={inputClass} value={l.name} onChange={(e) => updateLot(active, j, { name: e.target.value })} /></td>
                            <td className="pr-2">
                              <input className={inputClass} inputMode="decimal" value={l.price} placeholder="280,00" onChange={(e) => updateLot(active, j, { price: e.target.value })} />
                              {cents(l.price) > 0 && <span className="text-[11px] text-muted">+ {formatMoney(Math.round(cents(l.price) * SERVICE_FEE_RATE))} taxa</span>}
                            </td>
                            <td className="w-24 pr-2"><input type="number" min={1} className={inputClass} value={l.quantity} onChange={(e) => updateLot(active, j, { quantity: Number(e.target.value) })} /></td>
                            <td className="w-24 pr-2"><input type="number" min={1} max={10} className={inputClass} value={l.limitPerCpf} onChange={(e) => updateLot(active, j, { limitPerCpf: Number(e.target.value) })} /></td>
                            <td className="w-24 pr-2"><input type="number" min={1} max={12} className={inputClass} value={l.maxInstallments} onChange={(e) => updateLot(active, j, { maxInstallments: Number(e.target.value) })} /></td>
                            <td><Button variant="ghost" size="sm" aria-label="Remover lote" disabled={sector.lots.length === 1} onClick={() => updateSector(active, { lots: sector.lots.filter((_, k) => k !== j) })}><Trash2 className="size-4" /></Button></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Button variant="soft" size="sm" className="mt-3" disabled={sector.lots.length >= 6}
                    onClick={() => updateSector(active, { lots: [...sector.lots, newLot(sector.lots.length + 1, 1)] })}>
                    <Plus className="size-4" aria-hidden /> Adicionar lote a este setor
                  </Button>
                  {(() => {
                    const allocated = sector.lots.reduce((s, l) => s + l.quantity, 0);
                    const ok = allocated === sellable(active);
                    return (
                      <p className={cx('mt-4 rounded-lg p-3 text-sm', ok ? 'bg-ok-soft text-ok' : 'bg-brand-50 text-brand-700')}>
                        Soma das cotas: <b>{allocated} de {sellable(active)}</b> assentos vendáveis.{' '}
                        {ok ? <><CheckCircle2 className="inline size-4" /> Validação de integridade aprovada.</> : 'A soma precisa fechar exatamente.'}
                      </p>
                    );
                  })()}
                  <p className="mt-2 text-xs text-muted">A virada é automática: quando a cota de um lote esgota, o próximo entra em vigor com o seu preço.</p>
                </>
              )}
            </Step>
          )}

          {step === 3 && sector && (
            <Step n="4" title="Mini editor de assentos & bloqueios">
              <div className="mb-3 flex flex-wrap gap-2">
                {sectors.map((s, i) => (
                  <button key={i} onClick={() => setActive(i)} className={cx('rounded-lg px-3 py-1.5 text-sm font-semibold', i === active ? 'bg-navy text-white' : 'bg-surface')}>{s.name}</button>
                ))}
              </div>
              <div className="mb-4 flex flex-wrap gap-2" role="radiogroup" aria-label="Ferramenta">
                {([
                  ['PCD', Accessibility, 'Cadeira PCD'],
                  ['BLOQUEADO_PRODUCAO', Ban, 'Bloquear / cortesia'],
                  ['OBSTRUCAO', EyeOff, 'Obstrução técnica'],
                  ['LIMPAR', Trash2, 'Limpar marcação'],
                ] as const).map(([value, Icon, label]) => (
                  <button key={value} role="radio" aria-checked={tool === value} onClick={() => setTool(value)}
                    className={cx('flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm', tool === value ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700' : 'border-line bg-white')}>
                    <Icon className="size-4" aria-hidden /> {label}
                  </button>
                ))}
              </div>
              <div className="rounded-xl bg-surface p-4">
                <div className="mx-auto mb-4 max-w-sm rounded-md bg-navy py-2 text-center text-xs font-bold tracking-[0.2em] text-white">PALCO</div>
                <div className="flex flex-col items-center gap-1.5">
                  {Array.from({ length: Math.ceil(sector.capacity / sector.seatsPerRow) }, (_, r) => (
                    <div key={r} className="flex flex-wrap justify-center gap-1">
                      {Array.from({ length: Math.min(sector.seatsPerRow, sector.capacity - r * sector.seatsPerRow) }, (_, c) => {
                        const n = r * sector.seatsPerRow + c + 1;
                        const mark = marks[`${active}:${n}`];
                        return (
                          <button key={n} onClick={() => toggleSeat(active, n)} aria-label={`Assento ${n}${mark ? `, ${mark}` : ''}`}
                            className={cx('grid size-7 place-items-center rounded text-[10px] font-bold', mark ? MARK_STYLE[mark] : 'bg-emerald-100 text-emerald-800')}>
                            {mark === 'PCD' ? <Accessibility className="size-3.5" /> : mark === 'BLOQUEADO_PRODUCAO' ? <Lock className="size-3" /> : n}
                          </button>
                        );
                      })}
                    </div>
                  ))}
                </div>
                <div className="mt-4 flex flex-wrap justify-center gap-4 text-[11px] font-semibold">
                  <span className="flex items-center gap-1"><span className="size-3 rounded bg-emerald-100" /> Disponível para venda</span>
                  <span className="flex items-center gap-1"><span className="size-3 rounded bg-indigo-100" /> PCD (vendável)</span>
                  <span className="flex items-center gap-1"><span className="size-3 rounded bg-brand-100" /> Bloqueado (cortesia/imprensa)</span>
                  <span className="flex items-center gap-1"><span className="size-3 rounded bg-surface-strong" /> Obstrução</span>
                </div>
              </div>
              <p className="mt-3 text-xs text-muted">Bloqueios e obstruções não são vendidos: as cotas dos lotes precisam fechar com <b>{sellable(active)}</b> assentos vendáveis neste setor.</p>
            </Step>
          )}

          {step === 4 && (
            <Step n="5" title="Revisão final">
              <dl className="grid gap-2 text-sm sm:grid-cols-2">
                <div className="rounded-lg bg-surface p-3"><dt className="text-xs text-muted">Evento</dt><dd className="font-bold">{info.title || '—'}</dd></div>
                <div className="rounded-lg bg-surface p-3"><dt className="text-xs text-muted">Quando / onde</dt><dd className="font-bold">{info.date || '—'} {info.time} • {info.venue || '—'}</dd></div>
              </dl>
              <div className="mt-4 grid gap-3">
                {sectors.map((s, i) => (
                  <div key={i} className="rounded-xl border border-line p-4">
                    <p className="font-bold">{s.name} <span className="text-sm font-normal text-muted">• {s.capacity} assentos ({sellable(i)} vendáveis) • {s.gate || 'Portão principal'}</span></p>
                    <ul className="mt-2 flex flex-wrap gap-2 text-xs">
                      {s.lots.map((l, j) => <li key={j} className="rounded-full bg-surface px-3 py-1">{l.name}: <b>{formatMoney(cents(l.price))}</b> × {l.quantity}</li>)}
                    </ul>
                  </div>
                ))}
              </div>
              {issues.length > 0 ? (
                <ul className="mt-4 grid gap-1 rounded-xl bg-brand-50 p-4 text-sm text-brand-700" role="alert">
                  {issues.map((m) => <li key={m} className="flex gap-2"><AlertTriangle className="mt-0.5 size-4 shrink-0" /> {m}</li>)}
                </ul>
              ) : (
                <p className="mt-4 flex items-center gap-2 rounded-xl bg-ok-soft p-4 text-sm font-semibold text-ok"><CheckCircle2 className="size-5" /> Tudo certo para publicar.</p>
              )}
              {save.error && <p role="alert" className="mt-3 rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700">{errorMessage(save.error)}</p>}
            </Step>
          )}

          <Card className="flex flex-wrap items-center justify-between gap-3 p-4">
            <Button variant="soft" disabled={step === 0} onClick={() => setStep(step - 1)}><ArrowLeft className="size-4" aria-hidden /> {step === 0 ? 'Voltar' : `Voltar para ${STEPS[step - 1]}`}</Button>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="ghost" disabled={issues.length > 0} loading={save.isPending && save.variables === false} onClick={() => save.mutate(false)}
                title={issues.length ? 'Resolva as pendências do resumo para salvar' : 'Salva sem abrir as vendas'}>
                Salvar como rascunho
              </Button>
              {step === 4 ? (
                <Button disabled={issues.length > 0} loading={save.isPending && save.variables === true} onClick={() => save.mutate(true)}>Publicar e abrir vendas <ArrowRight className="size-4" aria-hidden /></Button>
              ) : (
                <Button onClick={() => setStep(step + 1)}>Avançar para {STEPS[step + 1]} <ArrowRight className="size-4" aria-hidden /></Button>
              )}
            </div>
          </Card>
        </div>

        <aside className="grid content-start gap-4">
          <Card className="p-5">
            <h2 className="flex items-center gap-2 font-bold"><Timer className="size-5 text-brand-600" aria-hidden /> Tempo de reserva no carrinho</h2>
            <p className="mt-3 rounded-xl bg-surface p-3 text-sm">Prazo para pagar: <b className="rounded bg-brand-600 px-2 py-0.5 text-white">10:00 min</b></p>
            <p className="mt-3 text-xs text-muted">Fixo pela plataforma para todos os eventos.</p>
            <ul className="mt-3 grid gap-2 text-xs text-muted"><li className="flex gap-2"><CheckCircle2 className="size-4 shrink-0 text-ok" aria-hidden /> Cada assento fica reservado para um único comprador: nunca é vendido duas vezes.</li><li className="flex gap-2"><CheckCircle2 className="size-4 shrink-0 text-ok" aria-hidden /> Se o prazo acabar sem pagamento, o assento volta à venda sozinho.</li></ul>
          </Card>
          <Card className="p-5">
            <p className="text-xs font-semibold tracking-wider text-muted uppercase">Resumo da publicação</p>
            <dl className="mt-3 grid gap-2 text-sm">
              <div className="flex justify-between"><dt className="text-muted">Setores</dt><dd className="font-bold">{sectors.length}</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Capacidade</dt><dd className="font-bold">{totalCapacity} lugares</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Lotes programados</dt><dd className="font-bold">{totalLots}</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Taxa de serviço</dt><dd className="font-bold">10% padrão</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Pendências</dt><dd className={cx('font-bold', issues.length ? 'text-brand-600' : 'text-ok')}>{issues.length}</dd></div>
            </dl>
          </Card>
          <Card className="border-brand-200 bg-brand-50 p-5 text-sm text-brand-800">
            <p className="flex items-center gap-2 font-bold"><AlertTriangle className="size-4" aria-hidden /> Atenção legal e regulatória</p>
            <p className="mt-2 text-xs">Depois de publicado, o preço de lotes que já tiveram vendas <b>não pode ser alterado retroativamente</b> (Código de Defesa do Consumidor).</p>
          </Card>
        </aside>
      </div>
    </div>
  );
}
