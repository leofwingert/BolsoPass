import { act, screen, waitFor } from '@testing-library/react';
import { beforeAll, describe, expect, it } from 'vitest';
import { setAccessToken } from '@/api/session';
import { mockApi } from '@/mocks/mockApi';
import { openAs } from '@/test/renderApp';

/** QR atual de um ingresso ativo da cliente Sarah no Festival Synthwave (gerado pela API simulada). */
async function currentQr(): Promise<string> {
  const session = await mockApi.auth.login('sarah@bolsopass.io', 'bolsopass');
  setAccessToken(session.accessToken);
  const tickets = await mockApi.tickets.listMine();
  const ticket = tickets.find((t) => t.event.id === 'evt-synthwave' && t.status === 'ATIVO');
  if (!ticket) throw new Error('Sem ingresso ativo para o teste');
  const { qr } = await mockApi.tickets.getQr(ticket.id);
  setAccessToken(undefined);
  return qr;
}

/** Abre o posto: evento Festival Synthwave, Portão 01, e vai para o leitor. */
async function openStation() {
  const app = await openAs('Bilheteria', '/operator/setup');
  await app.user.click(await screen.findByText('Festival Synthwave 2026'));
  await app.user.click(screen.getByRole('button', { name: /Portão 01/ }));
  await app.user.click(screen.getByRole('button', { name: /Confirmar posto/ }));
  await screen.findByRole('button', { name: /Digitar código/ });
  return app;
}

async function typeCode(user: Awaited<ReturnType<typeof openStation>>['user'], code: string) {
  await user.click(screen.getByRole('button', { name: /Digitar código/ }));
  await user.click(screen.getByLabelText('Código do QR'));
  await user.paste(code);
  await user.click(screen.getByRole('button', { name: 'Validar' }));
  return (await screen.findByRole('status')).textContent ?? '';
}

describe('jornada da bilheteria', () => {
  let qr: string;
  beforeAll(async () => {
    qr = await currentQr();
  });

  it('cliente não entra na portaria (403)', async () => {
    await openAs('Cliente', '/operator/scan');
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument();
  });

  it('sem posto escolhido, o leitor manda para a escolha do portão', async () => {
    await openAs('Bilheteria', '/operator/scan');
    expect(await screen.findByRole('heading', { name: 'Eventos atribuídos' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Escolha o portão/ })).toBeDisabled();
  });

  it('valida o ingresso, barra a segunda entrada e recusa QR falso', async () => {
    const { user } = await openStation();
    expect(screen.getByText(/Nenhuma câmera|não libera a câmera/)).toBeInTheDocument();

    expect(await typeCode(user, qr)).toMatch(/Acesso liberado/);
    expect(screen.getByText(/\*\*\*\./)).toBeInTheDocument(); // CPF parcial (LGPD)
    await user.click(screen.getByRole('button', { name: /Próxima leitura/ }));

    expect(await typeCode(user, qr)).toMatch(/já utilizado/i);
    await user.click(screen.getByRole('button', { name: /Próxima leitura/ }));

    expect(await typeCode(user, 'BP1.eyJ0aWQiOiJUSy0wIn0.falsa')).toMatch(/Acesso negado/);
    await user.click(screen.getByRole('button', { name: /Próxima leitura/ }));

    // Histórico do turno com as 3 leituras.
    await user.click(screen.getByRole('link', { name: /Histórico/ }));
    expect(await screen.findByRole('button', { name: 'Todas (3)' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Válidas (1)' }));
    await user.click(screen.getByRole('button', { name: 'Recusadas (2)' }));
    await user.click(screen.getAllByRole('button', { name: /Detalhes/ })[0]!);
    expect(screen.getByText('CPF do titular')).toBeInTheDocument();
    await user.type(screen.getByRole('textbox', { name: 'Buscar leituras' }), 'zzz');
    expect(await screen.findByText('Nenhuma leitura neste filtro.')).toBeInTheDocument();
  });

  it('sem internet entra em contingência e continua validando', async () => {
    const { user } = await openStation();
    act(() => {
      window.dispatchEvent(new Event('offline'));
    });
    expect(await screen.findByText(/Sem internet: validando pela base do aparelho/)).toBeInTheDocument();

    await user.click(screen.getByTitle('Situação da conexão'));
    expect(await screen.findByText('Sem conexão com o servidor')).toBeInTheDocument();
    expect(screen.getByText('Regras para o modo sem internet')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Tentar reconectar agora/ }));
    await waitFor(() => expect(screen.getByText('Conectado ao servidor')).toBeInTheDocument());
  });
});
