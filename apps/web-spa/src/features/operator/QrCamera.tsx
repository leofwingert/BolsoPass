import { CameraOff, Flashlight, SwitchCamera } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { cx } from '@/lib/format';

type Facing = 'environment' | 'user';

/**
 * Câmera com leitura contínua de QR (jsQR, carregado só quando a câmera abre).
 * Precisa de HTTPS ou localhost: é exigência do navegador para usar a câmera.
 */
export function QrCamera({ paused, onCode }: { paused: boolean; onCode: (raw: string) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream>();
  const onCodeRef = useRef(onCode);
  onCodeRef.current = onCode;
  const pausedRef = useRef(paused);
  pausedRef.current = paused;
  const [facing, setFacing] = useState<Facing>('environment');
  const [error, setError] = useState<string>();
  const [torch, setTorch] = useState(false);
  const [torchSupported, setTorchSupported] = useState(false);

  useEffect(() => {
    let stop = false;
    let timer: ReturnType<typeof setTimeout>;
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });

    async function run() {
      if (!navigator.mediaDevices?.getUserMedia) {
        setError('Este navegador não libera a câmera aqui. Use HTTPS (ou localhost) ou digite o código.');
        return;
      }
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing, width: { ideal: 1280 } }, audio: false });
        if (stop) return stream.getTracks().forEach((t) => t.stop());
        streamRef.current = stream;
        const track = stream.getVideoTracks()[0];
        const caps = (track?.getCapabilities?.() ?? {}) as MediaTrackCapabilities & { torch?: boolean };
        setTorchSupported(!!caps.torch);
        setError(undefined);
        const video = videoRef.current!;
        video.srcObject = stream;
        await video.play().catch(() => undefined);
        const { default: jsQR } = await import('jsqr');
        const tick = () => {
          if (stop) return;
          if (!pausedRef.current && video.readyState >= 2 && ctx) {
            const w = video.videoWidth;
            const h = video.videoHeight;
            // Lê só o quadrado central (onde fica a moldura): mais rápido e evita códigos ao fundo.
            const side = Math.min(w, h) * 0.8;
            canvas.width = canvas.height = Math.round(Math.min(side, 640));
            ctx.drawImage(video, (w - side) / 2, (h - side) / 2, side, side, 0, 0, canvas.width, canvas.height);
            const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
            const code = jsQR(img.data, img.width, img.height, { inversionAttempts: 'dontInvert' });
            if (code?.data) onCodeRef.current(code.data);
          }
          timer = setTimeout(tick, 200);
        };
        tick();
      } catch (err) {
        setError(err instanceof DOMException && err.name === 'NotAllowedError'
          ? 'Permissão da câmera negada. Libere nas configurações do navegador ou digite o código.'
          : 'Nenhuma câmera disponível neste aparelho. Use "Digitar código".');
      }
    }
    void run();
    return () => {
      stop = true;
      clearTimeout(timer);
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = undefined;
      setTorch(false);
    };
  }, [facing]);

  async function toggleTorch() {
    const track = streamRef.current?.getVideoTracks()[0];
    if (!track) return;
    try {
      await track.applyConstraints({ advanced: [{ torch: !torch } as MediaTrackConstraintSet] });
      setTorch(!torch);
    } catch {
      setTorchSupported(false);
    }
  }

  return (
    <div className="relative aspect-[3/4] max-h-[60vh] w-full overflow-hidden rounded-2xl bg-navy sm:aspect-square">
      <video ref={videoRef} className="absolute inset-0 size-full object-cover" playsInline muted />
      {error ? (
        <div className="absolute inset-0 grid place-items-center p-6 text-center text-sm text-white/80">
          <div><CameraOff className="mx-auto mb-2 size-8" aria-hidden />{error}</div>
        </div>
      ) : (
        <>
          {/* Moldura de leitura */}
          <div className="pointer-events-none absolute inset-[12%]">
            {['top-0 left-0 border-t-4 border-l-4', 'top-0 right-0 border-t-4 border-r-4', 'bottom-0 left-0 border-b-4 border-l-4', 'bottom-0 right-0 border-b-4 border-r-4'].map((c) => (
              <span key={c} className={cx('absolute size-10 rounded-sm border-brand-500', c)} />
            ))}
            {!paused && <span className="absolute inset-x-2 top-1/2 h-0.5 animate-pulse bg-brand-500 shadow-[0_0_12px_2px] shadow-brand-500/60" />}
          </div>
          <p className="absolute inset-x-0 bottom-20 text-center">
            <span className="rounded-full bg-black/50 px-3 py-1 text-xs font-semibold text-white">{paused ? 'Leitura pausada' : 'Alinhe o QR Code dentro da moldura'}</span>
          </p>
        </>
      )}
      <div className="absolute inset-x-0 bottom-3 flex justify-center gap-6">
        <button type="button" onClick={toggleTorch} disabled={!torchSupported}
          className={cx('flex flex-col items-center gap-1 text-[11px] font-semibold text-white disabled:opacity-40')} aria-pressed={torch}>
          <span className={cx('grid size-11 place-items-center rounded-xl', torch ? 'bg-amber-400 text-navy' : 'bg-white/15')}><Flashlight className="size-5" aria-hidden /></span>
          Lanterna
        </button>
        <button type="button" onClick={() => setFacing(facing === 'environment' ? 'user' : 'environment')}
          className="flex flex-col items-center gap-1 text-[11px] font-semibold text-white">
          <span className="grid size-11 place-items-center rounded-xl bg-white/15"><SwitchCamera className="size-5" aria-hidden /></span>
          Alternar câmera
        </button>
      </div>
    </div>
  );
}
