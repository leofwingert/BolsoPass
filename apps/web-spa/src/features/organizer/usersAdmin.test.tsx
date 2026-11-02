import { act, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { openAs, renderApp } from '@/test/renderApp';

/** Credenciais geradas num teste e usadas no seguinte (a API simulada guarda o estado no arquivo). */
let seller: { badge: string; pin: string } | undefined;

describe('cadastro público', () => {
  it('cria sempre um Cliente, entra logado e não acessa a administração', async () => {
    const { user, router } = renderApp('/auth/login');
    await user.click(await screen.findByRole('link', { name: 'Criar conta' }));
    await screen.findByRole('heading', { name: 'Criar sua conta' });
    // Sem campo de perfil: o cadastro público é só de cliente.
    expect(screen.queryByRole('radiogroup', { name: 'Perfil' })).toBeNull();

    await user.click(screen.getByRole('button', { name: /Criar conta/ }));
    expect(screen.getByText('Informe nome e sobrenome')).toBeInTheDocument();
    expect(screen.getByText('Aceite os termos para continuar')).toBeInTheDocument();

    await user.type(screen.getByLabelText(/^Nome completo/), 'Paula Mendes Costa');
    await user.type(screen.getByLabelText(/^E-mail/), 'paula@teste.io');
    await user.type(screen.getByLabelText(/^CPF/), '24681357928');
    await user.type(screen.getByLabelText(/^Telefone/), '51998765432');
    await user.type(screen.getByLabelText(/^Senha/), 'senhaforte');
    await user.type(screen.getByLabelText(/^Confirmar senha/), 'senhaforte');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /Criar conta/ }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    await act(() => router.navigate('/organizer/users'));
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument();
  });

  it('CPF de conta normal já cadastrada é recusado', async () => {
    const { user } = renderApp('/auth/register');
    await user.type(await screen.findByLabelText(/^Nome completo/), 'Outra Pessoa');
    await user.type(screen.getByLabelText(/^E-mail/), 'outra@teste.io');
    await user.type(screen.getByLabelText(/^CPF/), '52998224725'); // CPF da Sarah
    await user.type(screen.getByLabelText(/^Telefone/), '51998765432');
    await user.type(screen.getByLabelText(/^Senha/), 'senhaforte');
    await user.type(screen.getByLabelText(/^Confirmar senha/), 'senhaforte');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /Criar conta/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/CPF já cadastrado/);
  });
});

describe('administração › usuários', () => {
  it('lista todos com filtros e cadastra um usuário de Venda com matrícula e PIN', async () => {
    const { user } = await openAs('Admin', '/organizer/dashboard');
    await user.click((await screen.findAllByRole('link', { name: /Usuários/ }))[0]!);
    await screen.findByRole('heading', { name: 'Usuários e perfis de acesso' });
    const list = await screen.findByRole('list', { name: 'Usuários' });
    expect(within(list).getByText('Sarah Jensen Oliveira')).toBeInTheDocument();
    // O próprio admin não muda o próprio perfil.
    expect(screen.getByRole('button', { name: 'Alterar perfil de Beatriz Lima' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Venda' }));
    await waitFor(() => expect(screen.queryByText('Sarah Jensen Oliveira')).toBeNull());
    expect(await screen.findByText('Juliana Rocha')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Todos' }));
    await user.type(screen.getByLabelText('Buscar usuários'), '982.247');
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Usuários' })).getAllByRole('listitem')).toHaveLength(1));

    await user.click(screen.getByRole('button', { name: /Cadastrar usuário/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Cadastrar usuário' });
    await user.click(within(dialog).getByRole('radio', { name: /Venda/ }));
    expect(within(dialog).queryByLabelText(/Senha inicial/)).toBeNull();
    await user.type(within(dialog).getByLabelText(/^Nome completo/), 'Téo Ramos Dias');
    await user.type(within(dialog).getByLabelText(/^E-mail/), 'teo@bolsopass.io');
    await user.click(within(dialog).getByRole('button', { name: /Cadastrar/ }));

    const creds = await screen.findByLabelText('Credenciais da bilheteria');
    const [badge, pin] = within(creds).getAllByText(/^(VD-\d{5}|\d{6})$/).map((n) => n.textContent!);
    expect(badge).toMatch(/^VD-\d{5}$/);
    expect(pin).toMatch(/^\d{6}$/);
    seller = { badge: badge!, pin: pin! };
    await user.click(within(creds).getByRole('button', { name: 'Concluir' }));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('a conta criada entra na bilheteria com a matrícula e o PIN gerados', async () => {
    expect(seller).toBeDefined();
    const { user } = renderApp('/auth/login');
    await user.type(await screen.findByLabelText(/E-mail ou matrícula/), seller!.badge);
    await user.type(screen.getByLabelText(/Senha ou PIN/), seller!.pin);
    await user.click(screen.getByRole('button', { name: /^Entrar$/ }));
    expect(await screen.findByRole('button', { name: /Vender para este evento/ })).toBeInTheDocument();
  });

  it('promove a cliente para Bilheteria e mostra a nova matrícula uma vez', async () => {
    const { user } = await openAs('Admin', '/organizer/users');
    await user.click(await screen.findByRole('button', { name: 'Alterar perfil de Sarah Jensen Oliveira' }));
    const dialog = await screen.findByRole('dialog', { name: 'Alterar perfil' });
    // O perfil atual não aparece como opção; avisos de um perfil por conta.
    expect(within(dialog).queryByRole('radio', { name: /Cliente/ })).toBeNull();
    expect(within(dialog).getByText(/Deixa de comprar como cliente/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('radio', { name: /Bilheteria/ }));
    await user.click(within(dialog).getByRole('button', { name: /Tornar Bilheteria/ }));

    const creds = await screen.findByLabelText('Credenciais da bilheteria');
    expect(within(creds).getByText(/^OP-\d{5}$/)).toBeInTheDocument();
    await user.click(within(creds).getByRole('button', { name: 'Concluir' }));
    const row = (await screen.findByText('Sarah Jensen Oliveira')).closest('li')!;
    await waitFor(() => expect(within(row).getByText('Bilheteria')).toBeInTheDocument());
  });
});
