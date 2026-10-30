/** Aviso sonoro curto (reserva entrando nos 2 minutos finais). Falha em silêncio se o áudio estiver bloqueado. */
export function beep(durationMs = 180, frequency = 880): void {
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;
    const ctx = new Ctx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.frequency.value = frequency;
    gain.gain.value = 0.08;
    osc.connect(gain).connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + durationMs / 1000);
    osc.onended = () => void ctx.close();
  } catch {
    /* sem áudio */
  }
}
