import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { openAs } from '@/test/renderApp';

/** Abre o posto (Festival Synthwave, Portão 01) e vai para a aba Vender. */
async function openSale() {
  const app = await openAs('Bilheteria', '/operator/setup');
  await app.user.click(await screen.findByText('Festival Synthwave 2026'));
  await app.user.click(screen.getByRole('button', { name: /Portão 01/ }));
  await app.user.click(screen.getByRole('button', { name: /Confirmar posto/ }));
  await screen.findByRole('button', { name: /Digitar código/ });
  await app.user.click(screen.getByRole('link', { name: /Vender/ }));
  await screen.findByLabelText(/CPF do cliente/);
  return app;
}

async function pickSeatsAndReserve(user: Awaited<ReturnType<typeof openSale>>['user'], n = 1) {
  const free = await screen.findAllByRole('button', { name: /, vago$/ });
  for (let i = 0; i < n; i++) await user.click(free[i]!);
  await user.click(screen.getByRole('button', { name: /Reservar/ }));
  await screen.findByRole('radiogroup', { name: 'Forma de pagamento' });
}

/** Venda no balcão: CPF → nome e telefone → lugares vagos → cartão ou PIX → ingresso emitido. */
describe('venda no balcão da bilheteria', () => {
  it('busca a cliente pelo CPF, vende com PIX e emite o ingresso', async () => {
    const { user } = await openSale();
    await user.type(screen.getByLabelText(/CPF do cliente/), '52998224725');
    await user.click(screen.getByRole('button', { name: /Buscar cliente/ }));
    expect(await screen.findByText('Sarah Jensen Oliveira')).toBeInTheDocument();
    expect(screen.getByText('(11) 98844-3210')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Confirmar cliente/ }));

    await pickSeatsAndReserve(user);
    // Titular já vem com os dados da cliente.
    expect(screen.getByLabelText(/Nome completo/)).toHaveValue('Sarah Jensen Oliveira');
    await user.click(screen.getByRole('button', { name: /Cobrar/ }));

    const done = await screen.findByRole('status');
    expect(within(done).getByText('Venda concluída')).toBeInTheDocument();
    expect(within(done).getByText(/PLS-|BP-|-BR/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Nova venda' }));
    expect(await screen.findByLabelText(/CPF do cliente/)).toHaveValue('');
  });

  it('CPF inválido não busca', async () => {
    const { user } = await openSale();
    await user.type(screen.getByLabelText(/CPF do cliente/), '12345678900');
    expect(screen.getByRole('button', { name: /Buscar cliente/ })).toBeDisabled();
    expect(screen.getByText(/CPF inválido/)).toBeInTheDocument();
  });

  it('CPF sem cadastro: cadastro rápido, cartão recusado e desistência', async () => {
    const { user } = await openSale();
    await user.type(screen.getByLabelText(/CPF do cliente/), '15350946056');
    await user.click(screen.getByRole('button', { name: /Buscar cliente/ }));
    expect(await screen.findByText('CPF sem cadastro')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Nome completo/), 'Rita de Cássia Moura');
    await user.type(screen.getByLabelText(/Telefone/), '51998761234');
    await user.click(screen.getByRole('button', { name: /Cadastrar e continuar/ }));

    await pickSeatsAndReserve(user);
    await user.click(screen.getByRole('radio', { name: /Cartão/ }));
    await user.type(screen.getByLabelText(/Número do cartão/), '4111111111110000');
    await user.type(screen.getByLabelText(/Validade/), '1230');
    await user.type(screen.getByLabelText(/CVV/), '123');
    await user.type(screen.getByLabelText(/Nome no cartão/), 'Rita Moura');
    await user.click(screen.getByRole('button', { name: /Cobrar/ }));

    expect(await screen.findByText(/Tente outro cartão/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Desistir da compra/ }));
    // Volta para a escolha de lugares, com o assento liberado.
    expect(await screen.findByRole('button', { name: /Reservar/ })).toBeInTheDocument();
  });
});
