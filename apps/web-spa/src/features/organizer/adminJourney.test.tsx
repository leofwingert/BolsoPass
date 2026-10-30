import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { openAs } from '@/test/renderApp';

/** Área administrativa de ponta a ponta, na aplicação real com a API simulada. */
describe('jornada do admin', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(window, 'alert').mockImplementation(() => undefined);
    // O jsdom não gera arquivos: o download do CSV só precisa não quebrar.
    URL.createObjectURL = vi.fn(() => 'blob:csv');
    URL.revokeObjectURL = vi.fn();
  });
  afterEach(() => vi.restoreAllMocks());

  it('cliente não entra na administração (403)', async () => {
    await openAs('Cliente', '/organizer/dashboard');
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument();
  });

  it('painel mostra indicadores, próximos eventos e exporta o relatório', async () => {
    const { user } = await openAs('Admin', '/organizer/dashboard');
    expect(await screen.findByRole('heading', { name: 'Painel geral do produtor' })).toBeInTheDocument();
    expect(screen.getByText('Receita bruta total')).toBeInTheDocument();
    expect(screen.getByText('Saúde das vendas')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Em vendas' }));
    await user.click(screen.getByRole('button', { name: 'Esgotados' }));
    await user.click(screen.getByRole('button', { name: 'Todos' }));
    await user.click(screen.getByRole('button', { name: /Relatório consolidado/ }));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('lista eventos, busca, filtra, pausa em massa e retoma', async () => {
    const { user } = await openAs('Admin', '/organizer/events');
    expect(await screen.findByRole('heading', { name: /Meus eventos/ })).toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: 'Buscar eventos' }), 'Metal');
    const table = screen.getByRole('table');
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(2));
    await user.clear(screen.getByRole('textbox', { name: 'Buscar eventos' }));

    await user.click(screen.getByRole('checkbox', { name: 'Selecionar Cyber Metal Fest 2026' }));
    await user.click(screen.getByRole('button', { name: /Pausar selecionados \(1\)/ }));
    await user.click(await screen.findByRole('button', { name: /Pausados 1/ }));
    expect(await screen.findByText('Cyber Metal Fest 2026')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Retomar vendas/ }));
    await user.click(await screen.findByRole('button', { name: /Em vendas/ }));
    expect(await screen.findByText('Cyber Metal Fest 2026')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Exportar dados/ }));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('cria um evento pelo assistente de 5 passos e publica', async () => {
    const { user, router } = await openAs('Admin', '/organizer/events/new');
    expect(await screen.findByText('Cadastro do evento e dos ingressos')).toBeInTheDocument();

    // Passo 1: dados gerais.
    await user.type(screen.getByLabelText('Nome do evento'), 'Festival de Testes 2027');
    fireEvent.change(screen.getByLabelText('Data'), { target: { value: '2027-12-20' } });
    await user.click(screen.getByRole('button', { name: /Avançar para Local/ }));

    // Passo 2: local.
    await user.type(await screen.findByLabelText('Local / arena'), 'Arena Teste');
    await user.type(screen.getByLabelText('Cidade'), 'Porto Alegre, RS');
    await user.click(screen.getByRole('button', { name: /Avançar para Setores/ }));

    // Passo 3: preço dos dois lotes (20 + 20 = 40 assentos).
    const prices = await screen.findAllByPlaceholderText('280,00');
    await user.type(prices[0]!, '100,00');
    await user.type(prices[1]!, '150,00');
    await user.click(screen.getByRole('button', { name: /Avançar para Mapa/ }));

    // Passo 4: marca um assento PCD (continua vendável).
    await user.click(await screen.findByRole('button', { name: 'Assento 1' }));
    expect(screen.getByRole('button', { name: 'Assento 1, PCD' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Avançar para Revisão/ }));

    // Passo 5: revisão sem pendências e publicação.
    expect(await screen.findByText('Tudo certo para publicar.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Publicar e abrir vendas/ }));
    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/organizer\/events\/(?!new)/));
    expect(await screen.findByRole('heading', { name: 'Festival de Testes 2027' })).toBeInTheDocument();
  });

  it('assistente aponta pendências quando faltam dados', async () => {
    const { user } = await openAs('Admin', '/organizer/events/new');
    await user.click(await screen.findByRole('button', { name: /Passo 5/ }));
    expect(await screen.findByText(/Informe o nome do evento/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Publicar e abrir vendas/ })).toBeDisabled();
  });

  it('abre o setor, vê os assentos e os dados de quem comprou', async () => {
    const { user } = await openAs('Admin', '/organizer/events/evt-synthwave');
    expect(await screen.findByRole('heading', { name: 'Festival Synthwave 2026' })).toBeInTheDocument();

    await user.click(screen.getAllByTitle('Ver assentos e compradores')[0]!);
    const dialog = await screen.findByRole('dialog', { name: 'Assentos do setor' });
    const sold = await within(dialog).findAllByRole('button', { name: /Vendido/ });
    await user.click(sold[0]!);
    expect(await within(dialog).findByText('Comprador (conta que pagou)')).toBeInTheDocument();
    expect(within(dialog).getByText('Telefone para contato')).toBeInTheDocument();
    expect(within(dialog).getByText('CPF vinculado ao ingresso')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Fechar' }));
    expect(screen.queryByRole('dialog')).toBeNull();

    for (const tab of ['Setores e lotes', 'Portaria', 'Auditoria', 'Resumo e métricas']) {
      await user.click(screen.getByRole('button', { name: tab }));
    }
    expect(screen.getByText('Público que já entrou')).toBeInTheDocument();
  });

  it('pausar todas as vendas pelo painel', async () => {
    const { user } = await openAs('Admin', '/organizer/dashboard');
    await user.click(await screen.findByRole('button', { name: /Pausar todas as vendas/ }));
    await waitFor(() => expect(window.confirm).toHaveBeenCalled());
    await user.click(screen.getAllByRole('link', { name: /Meus eventos/ })[0]!);
    expect(await screen.findByRole('button', { name: /Pausados [1-9]/ })).toBeInTheDocument();
  });
});
