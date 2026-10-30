/** Taxa de conveniência transparente de 10% (mockups de checkout e wizard do produtor). */
export const SERVICE_FEE_RATE = 0.1;

/** Valores sempre em centavos para evitar erro de ponto flutuante. */
export interface PriceLine {
  priceCents: number;
}

export interface OrderTotals {
  subtotalCents: number;
  feeCents: number;
  totalCents: number;
}

export function computeTotals(
  lines: readonly PriceLine[],
  feeRate = SERVICE_FEE_RATE,
): OrderTotals {
  const subtotalCents = lines.reduce((sum, l) => sum + l.priceCents, 0);
  const feeCents = Math.round(subtotalCents * feeRate);
  return { subtotalCents, feeCents, totalCents: subtotalCents + feeCents };
}

export function installmentCents(totalCents: number, installments: number): number {
  if (installments < 1) throw new RangeError('Parcelas devem ser >= 1');
  return Math.ceil(totalCents / installments);
}
