import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { openAs, renderApp } from '@/test/renderApp';

/** Jornada do comprador de ponta a ponta, na aplicação real com a API simulada. */
describe('jornada do cliente', () => {
  it('visitante vê o catálogo, busca e filtra eventos', async () => {
    const { user } = renderApp('/');
    expect(await screen.findAllByText('Neon Odyssey World Tour')).not.toHaveLength(0);
    await user.type(screen.getByPlaceholderText(/Buscar por show/), 'Metal{Enter}');
    await waitFor(() => expect(screen.queryAllByText('Neon Odyssey World Tour')).toHaveLength(0));
    expect(screen.getAllByText('Cyber Metal Fest 2026').length).toBeGreaterThan(0);
  });

  it('visitante precisa entrar para escolher assentos', async () => {
    renderApp('/events/evt-neon/seats');
    expect(await screen.findByRole('heading', { name: 'Entrar na sua conta' })).toBeInTheDocument();
  });

  it('compra um assento com PIX e recebe o ingresso com QR dinâmico', async () => {
    // Detalhe do evento é público; escolher assento exige login e volta ao mapa depois.
    const { user, router } = renderApp('/events/evt-neon');
    await user.click(await screen.findByRole('button', { name: /Entrar na seleção de assentos/ }));
    await user.click(await screen.findByRole('button', { name: /Cliente/ }));
    const free = await screen.findAllByRole('button', { name: /disponível$/ });
    await user.click(free[0]!);
    await user.click(screen.getByRole('button', { name: /Garantir reserva/ }));

    // Checkout: cronômetro de 10 min, titular e forma de pagamento.
    await screen.findByRole('radiogroup', { name: 'Forma de pagamento' });
    expect(router.state.location.pathname).toMatch(/^\/checkout\//);
    const name = screen.getByLabelText(/Nome completo/);
    await user.clear(name);
    await user.type(name, 'Joana Prado Lima');
    const cpf = screen.getByLabelText(/^CPF/);
    await user.clear(cpf);
    await user.type(cpf, '11144477735');
    await user.click(screen.getByRole('radio', { name: /PIX/ }));
    await user.click(screen.getByRole('button', { name: /Confirmar e pagar/ }));

    // Ingresso emitido, com QR.
    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/tickets\//), { timeout: 4000 });
    expect(await screen.findAllByText(/Joana Prado Lima/)).not.toHaveLength(0);
    expect(document.querySelector('svg')).not.toBeNull();
  });

  it('cartão final 0000 é recusado e o comprador pode desistir', async () => {
    const { user } = await openAs('Cliente', '/events/evt-metal/seats');
    const free = await screen.findAllByRole('button', { name: /disponível$/ });
    await user.click(free[1]!);
    await user.click(screen.getByRole('button', { name: /Garantir reserva/ }));
    await screen.findByRole('radiogroup', { name: 'Forma de pagamento' });

    const name = screen.getByLabelText(/Nome completo/);
    await user.clear(name);
    await user.type(name, 'Pedro Alves Souza');
    const cpf = screen.getByLabelText(/^CPF/);
    await user.clear(cpf);
    await user.type(cpf, '39053344705');
    await user.type(screen.getByLabelText(/Número do cartão/), '4111111111110000');
    await user.type(screen.getByLabelText(/Validade/), '1230');
    await user.type(screen.getByLabelText(/CVV/), '123');
    await user.type(screen.getByLabelText(/Nome impresso/), 'PEDRO A SOUZA');
    await user.click(screen.getByRole('button', { name: /Confirmar e pagar/ }));

    const giveUp = await screen.findByRole('button', { name: /Desistir/ }, { timeout: 4000 });
    await user.click(giveUp);
    await waitFor(() => expect(screen.queryByRole('button', { name: /Desistir/ })).toBeNull(), { timeout: 4000 });
  });

  it('mostra a carteira e os pedidos do cliente', async () => {
    const { user } = await openAs('Cliente', '/wallet');
    expect(await screen.findAllByText(/Festival Synthwave/)).not.toHaveLength(0);
    await user.click(screen.getAllByRole('link', { name: /Meus Pedidos/ })[0]!);
    const main = await screen.findByRole('main');
    expect(await within(main).findAllByText(/PLS-/)).not.toHaveLength(0);
  });
});
