import { act, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { openAs, renderApp } from '@/test/renderApp';

/** Código entregue no balcão, usado no teste seguinte para ativar a conta no site. */
let activation: { cpf: string; code: string } | undefined;

type User = Awaited<ReturnType<typeof openAs>>['user'];

/** Portaria: abre o posto (Festival Synthwave, Portão 01) e vai para a aba Vender. */
async function openSaleAsPortaria() {
  const app = await openAs('Bilheteria', '/operator/setup');
  await app.user.click(await screen.findByText('Festival Synthwave 2026'));
  await app.user.click(screen.getByRole('button', { name: /Portão 01/ }));
  await app.user.click(screen.getByRole('button', { name: /Confirmar posto/ }));
  await screen.findByRole('button', { name: /Digitar código/ });
  await app.user.click(screen.getByRole('link', { name: /Vender/ }));
  await screen.findByLabelText(/CPF do cliente/);
  return app;
}

async function findCustomer(user: User, cpf: string) {
  await user.type(screen.getByLabelText(/CPF do cliente/), cpf);
  await user.click(screen.getByRole('button', { name: /Buscar cliente/ }));
}

async function pickSeatsAndReserve(user: User, n = 1) {
  const free = await screen.findAllByRole('button', { name: /, vago$/ });
  for (let i = 0; i < n; i++) await user.click(free[i]!);
  await user.click(screen.getByRole('button', { name: /Reservar/ }));
  await screen.findByRole('radiogroup', { name: 'Forma de pagamento' });
}

/** Venda no balcão: CPF → nome e telefone → lugares vagos → maquininha (crédito, PIX ou dinheiro) → comprovante. */
describe('venda no balcão da bilheteria', () => {
  it('terminal de venda só vende: escolhe o evento, vende com PIX e imprime o comprovante', async () => {
    const { user, router } = await openAs('Venda', '/operator/scan');
    // Sem leitor de QR: vai para a escolha do evento (sem portão).
    expect(await screen.findByRole('button', { name: /Vender para este evento/ })).toBeInTheDocument();
    expect(screen.queryByText('Portão de acesso')).toBeNull();
    expect(screen.queryByRole('link', { name: /Leitor QR/ })).toBeNull();
    await user.click(await screen.findByText('Festival Synthwave 2026'));
    await user.click(screen.getByRole('button', { name: /Vender para este evento/ }));
    await screen.findByLabelText(/CPF do cliente/);
    expect(router.state.location.pathname).toBe('/operator/sell');

    await findCustomer(user, '52998224725');
    expect(await screen.findByText('Sarah Jensen Oliveira')).toBeInTheDocument();
    expect(screen.getByText('(11) 98844-3210')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Confirmar cliente/ }));

    await pickSeatsAndReserve(user);
    expect(screen.getByLabelText(/Nome completo/)).toHaveValue('Sarah Jensen Oliveira');
    await user.click(screen.getByRole('radio', { name: /PIX/ }));
    expect(screen.getByLabelText('QR Code PIX')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Simular PIX recebido/ }));

    const receipt = await screen.findByLabelText('Comprovante de venda');
    expect(within(receipt).getByText(/CPF \*\*\*\.982\.247-\*\*/)).toBeInTheDocument();
    expect(within(receipt).getByText('PIX')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Nova venda' }));
    expect(await screen.findByLabelText(/CPF do cliente/)).toHaveValue('');
  });

  it('troca de evento direto na venda e volta da etapa de lugares', async () => {
    const { user } = await openAs('Venda', '/operator/setup');
    await user.click(await screen.findByText('Festival Synthwave 2026'));
    await user.click(screen.getByRole('button', { name: /Vender para este evento/ }));
    await screen.findByLabelText(/CPF do cliente/);

    await user.click(screen.getByRole('button', { name: /Trocar evento/ }));
    const picker = await screen.findByRole('dialog', { name: 'Escolher evento para vender' });
    await user.click(await within(picker).findByText('Neon Odyssey World Tour'));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByText('Neon Odyssey World Tour')).toBeInTheDocument();

    await findCustomer(user, '52998224725');
    await user.click(await screen.findByRole('button', { name: /Confirmar cliente/ }));
    await screen.findAllByRole('button', { name: /, vago$/ });
    await user.click(screen.getByRole('button', { name: 'Voltar para o cliente' }));
    expect(await screen.findByLabelText(/CPF do cliente/)).toBeInTheDocument();
  });

  it('dinheiro: calcula o troco e só confirma com o valor suficiente', async () => {
    const { user } = await openSaleAsPortaria();
    await findCustomer(user, '52998224725');
    await user.click(await screen.findByRole('button', { name: /Confirmar cliente/ }));
    await pickSeatsAndReserve(user);
    await user.click(screen.getByRole('radio', { name: /Dinheiro/ }));

    const confirm = screen.getByRole('button', { name: /Confirmar recebimento/ });
    await user.type(screen.getByLabelText(/Valor recebido/), '10,00');
    expect(screen.getByText(/Faltam/)).toBeInTheDocument();
    expect(confirm).toBeDisabled();
    await user.clear(screen.getByLabelText(/Valor recebido/));
    await user.type(screen.getByLabelText(/Valor recebido/), '2000,00');
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    const receipt = await screen.findByLabelText('Comprovante de venda');
    expect(within(receipt).getByText('TROCO')).toBeInTheDocument();
    expect(within(receipt).getByText('DINHEIRO')).toBeInTheDocument();
  });

  it('CPF inválido não busca', async () => {
    const { user } = await openSaleAsPortaria();
    await user.type(screen.getByLabelText(/CPF do cliente/), '12345678900');
    expect(screen.getByRole('button', { name: /Buscar cliente/ })).toBeDisabled();
    expect(screen.getByText(/CPF inválido/)).toBeInTheDocument();
  });

  it('CPF sem cadastro: cadastro rápido, cartão recusado na maquininha e desistência', async () => {
    const { user } = await openSaleAsPortaria();
    await findCustomer(user, '15350946056');
    expect(await screen.findByText('CPF sem cadastro')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Nome completo/), 'Rita de Cássia Moura');
    await user.type(screen.getByLabelText(/Telefone/), '51998761234');
    await user.click(screen.getByRole('button', { name: /Cadastrar e continuar/ }));
    // O operador vê o código de ativação para entregar ao cliente antes de seguir.
    const card = await screen.findByLabelText('Código de ativação');
    const code = within(card).getByText(/^\d{6}$/).textContent!;
    activation = { cpf: '15350946056', code };
    await user.click(screen.getByRole('button', { name: /Continuar e escolher lugares/ }));

    await pickSeatsAndReserve(user);
    await user.click(screen.getByRole('button', { name: /Simular cartão sem saldo/ }));
    expect(await screen.findByText(/Tente outro cartão/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Desistir da compra/ }));
    expect(await screen.findByRole('button', { name: /Reservar/ })).toBeInTheDocument();
  });

  it('cliente do balcão ativa a conta no site só com o código entregue', async () => {
    expect(activation).toBeDefined();
    const { user, router } = renderApp('/auth/register');
    await user.type(await screen.findByLabelText(/^Nome completo/), 'Rita de Cássia Moura');
    await user.type(screen.getByLabelText(/^E-mail/), 'rita@teste.io');
    await user.type(screen.getByLabelText(/^CPF/), activation!.cpf);
    await user.type(screen.getByLabelText(/^Telefone/), '51998761234');
    await user.type(screen.getByLabelText(/^Senha/), 'senhaforte');
    await user.type(screen.getByLabelText(/^Confirmar senha/), 'senhaforte');
    await user.click(screen.getByRole('checkbox'));
    // Só o CPF não basta.
    await user.click(screen.getByRole('button', { name: /Criar conta/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/código de ativação/);

    await user.type(screen.getByLabelText(/^Código de ativação/), activation!.code);
    await user.click(screen.getByRole('button', { name: /Criar conta/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });

  it('admin não vê nem acessa a bilheteria', async () => {
    const { router } = await openAs('Admin', '/organizer/dashboard');
    await screen.findByRole('heading', { name: 'Painel geral do produtor' });
    expect(screen.queryByRole('link', { name: /Bilheteria/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Abrir bilheteria/ })).toBeNull();
    // Mesmo pela URL, a bilheteria é barrada (403).
    await act(() => router.navigate('/operator/sell'));
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument();
  });
});
