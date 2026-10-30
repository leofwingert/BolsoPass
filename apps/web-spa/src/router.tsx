import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom';
import { RequireArea } from '@/auth/RequireArea';
import type { Area } from '@/auth/roles';
import { Spinner } from '@/components/ui';
import { ClientLayout } from '@/layouts/ClientLayout';

const LoginPage = lazy(() => import('@/features/auth/LoginPage'));
const HomePage = lazy(() => import('@/features/client/HomePage'));
const EventDetailPage = lazy(() => import('@/features/client/EventDetailPage'));
const SeatMapPage = lazy(() => import('@/features/client/SeatMapPage'));
const CheckoutPage = lazy(() => import('@/features/client/CheckoutPage'));
const TicketPage = lazy(() => import('@/features/client/TicketPage'));
const WalletPage = lazy(() => import('@/features/client/WalletPage'));
const MyOrdersPage = lazy(() => import('@/features/client/MyOrdersPage'));
const OrganizerLayout = lazy(() => import('@/features/organizer/OrganizerLayout'));
const OrganizerDashboardPage = lazy(() => import('@/features/organizer/OrganizerDashboardPage'));
const OrganizerEventsPage = lazy(() => import('@/features/organizer/OrganizerEventsPage'));
const OrganizerEventPage = lazy(() => import('@/features/organizer/OrganizerEventPage'));
const EventWizardPage = lazy(() => import('@/features/organizer/EventWizardPage'));
const OperatorLayout = lazy(() => import('@/features/operator/OperatorLayout'));
const OperatorSetupPage = lazy(() => import('@/features/operator/OperatorSetupPage'));
const OperatorScanPage = lazy(() => import('@/features/operator/OperatorScanPage'));
const OperatorHistoryPage = lazy(() => import('@/features/operator/OperatorHistoryPage'));
const OperatorOfflinePage = lazy(() => import('@/features/operator/OperatorOfflinePage'));
const NotFoundPage = lazy(() => import('@/features/NotFoundPage'));

const page = (node: ReactNode) => <Suspense fallback={<Spinner />}>{node}</Suspense>;
const guarded = (area: Area, node: ReactNode) => page(<RequireArea area={area}>{node}</RequireArea>);

/** Rotas do doc 07 ("Tabela de Rotas, Perfis e Políticas de Acesso"). */
/** Árvore de rotas (exportada para os testes montarem a aplicação com MemoryRouter). */
export const routes: RouteObject[] = [
  {
    element: <ClientLayout />,
    children: [
      { path: '/', element: page(<HomePage />) },
      { path: '/auth/login', element: page(<LoginPage />) },
      { path: '/events/:id', element: page(<EventDetailPage />) },
      { path: '/events/:id/seats', element: guarded('client', <SeatMapPage />) },
      { path: '/checkout/:reservationId', element: guarded('client', <CheckoutPage />) },
      { path: '/tickets/:ticketId', element: guarded('client', <TicketPage />) },
      { path: '/wallet', element: guarded('client', <WalletPage />) },
      { path: '/my-orders', element: guarded('client', <MyOrdersPage />) },
      // Bilheteria / portaria (ROLE_OPERATOR).
      { path: '/operator/login', element: <Navigate to="/auth/login" replace /> },
      { path: '*', element: page(<NotFoundPage />) },
    ],
  },
  // Administração (ROLE_ORGANIZER / ROLE_ADMIN): cabeçalho próprio, fora do layout do cliente.
  {
    path: '/organizer',
    element: guarded('organizer', <OrganizerLayout />),
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      { path: 'dashboard', element: page(<OrganizerDashboardPage />) },
      { path: 'events', element: page(<OrganizerEventsPage />) },
      { path: 'events/new', element: page(<EventWizardPage />) },
      { path: 'events/:id', element: page(<OrganizerEventPage />) },
    ],
  },
  // Bilheteria / portaria (ROLE_OPERATOR): app de celular com navegação inferior.
  {
    path: '/operator',
    element: guarded('operator', <OperatorLayout />),
    children: [
      { index: true, element: <Navigate to="scan" replace /> },
      { path: 'setup', element: page(<OperatorSetupPage />) },
      { path: 'scan', element: page(<OperatorScanPage />) },
      { path: 'history', element: page(<OperatorHistoryPage />) },
      { path: 'offline', element: page(<OperatorOfflinePage />) },
    ],
  },
];

export const router = createBrowserRouter(routes);
