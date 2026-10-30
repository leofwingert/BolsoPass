/** Regras do wizard de lotes (mockup Admin "Configuração Avançada de Ingressos"). */
export interface LotDraft {
  id: string;
  name: string;
  priceCents: number;
  quantity: number;
  limitPerCpf: number;
  /** Lote que já teve vendas não pode ter o preço alterado (aviso legal do mockup). */
  soldCount: number;
}

export interface SectorDraft {
  id: string;
  name: string;
  capacity: number;
  lots: LotDraft[];
}

export interface LotAllocation {
  allocated: number;
  capacity: number;
  ok: boolean;
}

/** A soma das cotas dos lotes deve ser exatamente a capacidade do setor. */
export function lotAllocation(sector: SectorDraft): LotAllocation {
  const allocated = sector.lots.reduce((sum, l) => sum + l.quantity, 0);
  return { allocated, capacity: sector.capacity, ok: allocated === sector.capacity };
}

export function canEditLotPrice(lot: LotDraft): boolean {
  return lot.soldCount === 0;
}

export type LotIssue = { lotId: string; message: string };

export function validateLots(sector: SectorDraft): LotIssue[] {
  const issues: LotIssue[] = [];
  for (const l of sector.lots) {
    if (l.priceCents <= 0) issues.push({ lotId: l.id, message: 'Preço deve ser maior que zero' });
    if (l.quantity <= 0) issues.push({ lotId: l.id, message: 'Quantidade deve ser maior que zero' });
    if (l.limitPerCpf < 1) issues.push({ lotId: l.id, message: 'Limite por CPF deve ser >= 1' });
  }
  return issues;
}
