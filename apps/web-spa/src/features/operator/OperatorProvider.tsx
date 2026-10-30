import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api } from '@/api';
import type { AssignedEvent, Gate, OfflineTicketCache, ScanRecord } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { validateScan, type ScanOutcome } from '@/domain/ticketQr';
import { importPublicKey, verify } from '@/lib/ecdsa';

/**
 * Estado do posto de leitura (evento + portão + base offline + leituras do turno).
 * Fica salvo no aparelho: se a página recarregar ou a rede cair, nada se perde.
 */
interface Station {
  event: AssignedEvent['event'];
  gate: Gate;
  cache: OfflineTicketCache;
}

export interface ScanResult extends ScanRecord {
  outcome: ScanOutcome;
}

interface OperatorState {
  station?: Station;
  scans: ScanRecord[];
  online: boolean;
  syncing: boolean;
  lastSyncAt?: string;
  lastSyncError?: string;
  nextRetryInS: number;
  pending: number;
  configure(event: AssignedEvent['event'], gate: Gate, cache: OfflineTicketCache): void;
  leave(): void;
  scan(raw: string): Promise<ScanResult>;
  syncNow(): Promise<void>;
}

const Ctx = createContext<OperatorState | null>(null);
const RETRY_S = 10;

function load<T>(key: string): T | undefined {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : undefined;
  } catch {
    return undefined;
  }
}
function save(key: string, value: unknown) {
  try {
    if (value === undefined) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* armazenamento cheio ou bloqueado: segue só em memória */
  }
}

/** CPF parcial para a tela da portaria (LGPD): ***.392.810-** */
export function maskCpf(cpf?: string) {
  const d = (cpf ?? '').replace(/\D/g, '');
  return d.length === 11 ? `***.${d.slice(3, 6)}.${d.slice(6, 9)}-**` : '—';
}

export function OperatorProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const prefix = `bp.operator.${user?.id ?? 'anon'}`;
  const [station, setStation] = useState<Station | undefined>(() => load(`${prefix}.station`));
  const [scans, setScans] = useState<ScanRecord[]>(() => load(`${prefix}.scans`) ?? []);
  const [online, setOnline] = useState(() => navigator.onLine);
  const [syncing, setSyncing] = useState(false);
  const [lastSyncAt, setLastSyncAt] = useState<string>();
  const [lastSyncError, setLastSyncError] = useState<string>();
  const [nextRetryInS, setNextRetryInS] = useState(RETRY_S);
  const keyRef = useRef<{ jwk: string; key: CryptoKey }>();
  const scansRef = useRef(scans);
  scansRef.current = scans;

  useEffect(() => save(`${prefix}.station`, station), [prefix, station]);
  useEffect(() => save(`${prefix}.scans`, scans.slice(0, 500)), [prefix, scans]);

  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    return () => {
      window.removeEventListener('online', up);
      window.removeEventListener('offline', down);
    };
  }, []);

  const pending = scans.filter((s) => !s.synced && s.status === 'VALIDO').length;

  /** Envia entradas pendentes e atualiza a base (para saber de entradas em outros portões). */
  const syncNow = useCallback(async () => {
    if (!station) return;
    setSyncing(true);
    try {
      const queue = scansRef.current.filter((s) => !s.synced && s.status === 'VALIDO');
      if (queue.length) await api.operator.syncScans(station.event.id, queue);
      const sent = new Set(queue.map((q) => q.id));
      setScans((prev) => prev.map((s) => (sent.has(s.id) ? { ...s, synced: true } : s)));
      const cache = await api.operator.downloadCache(station.event.id);
      setStation((prev) => (prev && prev.event.id === cache.eventId ? { ...prev, cache } : prev));
      setOnline(true);
      setLastSyncAt(new Date().toISOString());
      setLastSyncError(undefined);
    } catch (err) {
      setOnline(false);
      setLastSyncError(err instanceof Error ? err.message : 'Sem conexão');
    } finally {
      setSyncing(false);
      setNextRetryInS(RETRY_S);
    }
  }, [station]);

  // Sincroniza a cada 10 s (contagem regressiva visível na tela de contingência).
  useEffect(() => {
    if (!station) return;
    const t = setInterval(() => {
      setNextRetryInS((s) => {
        if (s <= 1) {
          void syncNow();
          return RETRY_S;
        }
        return s - 1;
      });
    }, 1000);
    return () => clearInterval(t);
  }, [station, syncNow]);

  const verifier = useCallback(async () => {
    const jwk = JSON.stringify(station?.cache.publicKeyJwk);
    if (keyRef.current?.jwk !== jwk) keyRef.current = { jwk, key: await importPublicKey(station!.cache.publicKeyJwk) };
    return keyRef.current.key;
  }, [station]);

  const scan = useCallback(
    async (raw: string): Promise<ScanResult> => {
      if (!station) throw new Error('Posto não configurado');
      const key = await verifier();
      const now = Date.now();
      const outcome = await validateScan(raw, {
        eventId: station.event.id,
        nowMs: now,
        verify: (signed, signature) => verify(key, signed, signature),
        // Entrada já feita: base baixada do servidor + leituras válidas deste aparelho.
        findUse: (tid) => {
          const local = scansRef.current.find((s) => s.ticketId === tid && s.status === 'VALIDO');
          return local ? { at: local.at, gate: local.gate } : station.cache.used[tid];
        },
      });
      const tid = outcome.payload?.tid;
      const info = tid ? station.cache.tickets[tid] : undefined;
      const record: ScanResult = {
        id: crypto.randomUUID?.() ?? `${now}-${Math.random()}`,
        at: new Date(now).toISOString(),
        status: outcome.status,
        reason: outcome.reason,
        ticketId: tid,
        holderName: info?.holderName ?? (outcome.status === 'INVALIDO' ? 'Desconhecido' : 'Titular não está na base baixada'),
        holderCpf: info?.holderCpf,
        sectorName: info?.sectorName,
        categoryName: info?.categoryName,
        gate: station.gate.name,
        turnstile: station.gate.turnstiles,
        previousUse: outcome.previousUse,
        synced: outcome.status !== 'VALIDO',
        offline: !online,
        outcome,
      };
      const { outcome: _o, ...stored } = record;
      // Atualiza a referência já: o envio logo abaixo precisa enxergar esta leitura.
      scansRef.current = [stored, ...scansRef.current];
      setScans((prev) => [stored, ...prev]);
      if (record.status === 'VALIDO' && online) void syncNow();
      return record;
    },
    [station, verifier, online, syncNow],
  );

  const value = useMemo<OperatorState>(
    () => ({
      station,
      scans,
      online,
      syncing,
      lastSyncAt,
      lastSyncError,
      nextRetryInS,
      pending,
      configure: (event, gate, cache) => {
        // Trocar de evento começa um turno novo (a tela só permite com a fila vazia).
        if (station && station.event.id !== event.id) setScans([]);
        setStation({ event, gate, cache });
      },
      leave: () => setStation(undefined),
      scan,
      syncNow,
    }),
    [station, scans, online, syncing, lastSyncAt, lastSyncError, nextRetryInS, pending, scan, syncNow],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useOperator() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useOperator fora do OperatorProvider');
  return ctx;
}
