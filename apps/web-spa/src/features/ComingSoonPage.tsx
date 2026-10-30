import { Construction } from 'lucide-react';
import { Card } from '@/components/ui';

const TEXT = {
  organizer: {
    title: 'Painel do organizador',
    body: 'Dashboard, lista de eventos, criação de eventos e lotes. Entra na próxima parte.',
  },
  operator: {
    title: 'App da portaria',
    body: 'Leitor de QR, histórico de leituras e modo offline. Entra numa próxima parte.',
  },
};

export default function ComingSoonPage({ area }: { area: keyof typeof TEXT }) {
  return (
    <div className="grid min-h-[60vh] place-items-center px-4">
      <Card className="max-w-md p-8 text-center">
        <Construction className="mx-auto size-10 text-brand-600" aria-hidden />
        <h1 className="mt-3 text-xl font-bold">{TEXT[area].title}</h1>
        <p className="mt-2 text-sm text-muted">{TEXT[area].body}</p>
      </Card>
    </div>
  );
}
