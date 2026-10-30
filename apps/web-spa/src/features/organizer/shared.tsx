import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pause, Play, Rocket } from 'lucide-react';
import { api } from '@/api';
import { errorMessage } from '@/api/errors';
import type { EventStatus } from '@/api/types';
import { Badge, Button } from '@/components/ui';

export const STATUS_LABEL: Record<EventStatus, string> = {
  RASCUNHO: 'Rascunho',
  AGENDADO: 'Agendado',
  EM_VENDAS: 'Em vendas',
  PAUSADO: 'Pausado',
  ESGOTADO: 'Esgotado',
  ENCERRADO: 'Encerrado',
  CANCELADO: 'Cancelado',
};

export function StatusBadge({ status }: { status: EventStatus }) {
  const tone =
    status === 'EM_VENDAS' ? 'ok' : status === 'PAUSADO' ? 'warn' : status === 'ESGOTADO' ? 'brand' : 'neutral';
  return <Badge tone={tone}>● {STATUS_LABEL[status]}</Badge>;
}

/** Botão de ação conforme o status: publicar rascunho, pausar ou retomar vendas. */
export function SalesAction({ id, status, size = 'sm' }: { id: string; status: EventStatus; size?: 'sm' | 'md' }) {
  const queryClient = useQueryClient();
  const action = useMutation({
    mutationFn: (kind: 'publish' | 'pause' | 'resume') => api.organizer[kind](id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizer'] });
      queryClient.invalidateQueries({ queryKey: ['events'] });
    },
    onError: (err) => window.alert(errorMessage(err)),
  });

  if (status === 'RASCUNHO' || status === 'AGENDADO') {
    return (
      <Button size={size} loading={action.isPending} onClick={() => action.mutate('publish')}>
        <Rocket className="size-4" aria-hidden /> Publicar
      </Button>
    );
  }
  if (status === 'EM_VENDAS') {
    return (
      <Button size={size} variant="danger" loading={action.isPending} onClick={() => {
        if (window.confirm('Pausar as vendas agora? Novas reservas ficam bloqueadas.')) action.mutate('pause');
      }}>
        <Pause className="size-4" aria-hidden /> Pausar vendas
      </Button>
    );
  }
  if (status === 'PAUSADO') {
    return (
      <Button size={size} variant="success" loading={action.isPending} onClick={() => action.mutate('resume')}>
        <Play className="size-4" aria-hidden /> Retomar vendas
      </Button>
    );
  }
  return null;
}

/** Exporta linhas para CSV (abre no Excel/Sheets). */
export function downloadCsv(filename: string, rows: (string | number)[][]) {
  const csv = rows
    .map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(';'))
    .join('\n');
  const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
