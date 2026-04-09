import React, { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import ProtectedRoute from '../shared/components/ProtectedRoute';

import { PageLoader, ErrorPage, NotFound, Unauthorized } from '../shared/components/RouteFallbacks';

// ─── Lazy Page Imports ────────────────────────────────────────────────────────

const LandingPage        = lazy(() => import('../features/common/LandingPage'));
const Login              = lazy(() => import('../features/auth/Login'));
const Register           = lazy(() => import('../features/auth/Register'));

const FounderDashboard      = lazy(() => import('../features/founder/FounderDashboard'));
const FounderStartupDetail  = lazy(() => import('../features/founder/FounderStartupDetail'));
const MyStartups            = lazy(() => import('../features/founder/MyStartups'));
const CreateStartup      = lazy(() => import('../features/founder/CreateStartup'));
const EditStartup        = lazy(() => import('../features/founder/EditStartup'));
const TeamManagement     = lazy(() => import('../features/founder/TeamManagement'));
const FounderInvestments = lazy(() => import('../features/founder/FounderInvestments'));
const ReceivedPayments   = lazy(() => import('../features/founder/ReceivedPayments'));

const CoFounderDashboard = lazy(() => import('../features/cofounder/CoFounderDashboard'));
const MyInvitations      = lazy(() => import('../features/founder/MyInvitations'));

const InvestorDashboard  = lazy(() => import('../features/investor/InvestorDashboard'));
const BrowseStartups     = lazy(() => import('../features/investor/BrowseStartups'));
const StartupDetail      = lazy(() => import('../features/investor/StartupDetail'));
const MyInvestments      = lazy(() => import('../features/investor/MyInvestments'));
const PaymentHistory     = lazy(() => import('../features/investor/PaymentHistory'));

const AdminDashboard     = lazy(() => import('../features/admin/AdminDashboard'));

const Notifications      = lazy(() => import('../features/common/Notifications'));
const Messages           = lazy(() => import('../features/common/Messages'));
const Chat               = lazy(() => import('../features/common/Chat'));
const Profile            = lazy(() => import('../features/common/Profile'));

const s = (Component: React.LazyExoticComponent<React.ComponentType<any>>) => (
  <Suspense fallback={<PageLoader />}>
    <Component />
  </Suspense>
);

// ─── Router ───────────────────────────────────────────────────────────────────

const router = createBrowserRouter([
  { path: '/',              element: s(LandingPage) },
  { path: '/login',         element: s(Login) },
  { path: '/register',      element: s(Register) },
  { path: '/unauthorized',  element: <Unauthorized /> },
  { path: '*',              element: <NotFound /> },

  {
    element: <ProtectedRoute allowedRoles={['ROLE_FOUNDER']} />,
    errorElement: <ErrorPage />,  
    children: [
      { path: '/founder/dashboard',         element: s(FounderDashboard) },
      { path: '/founder/startups',          element: s(MyStartups) },
      { path: '/founder/startups/create',   element: s(CreateStartup) },
      { path: '/founder/startups/:id',      element: s(FounderStartupDetail) },
      { path: '/founder/startups/:id/edit', element: s(EditStartup) },
      { path: '/founder/team/:startupId',   element: s(TeamManagement) },
      { path: '/founder/investments',       element: s(FounderInvestments) },
      { path: '/founder/payments',          element: s(ReceivedPayments) },
    ],
  },

  {
    element: <ProtectedRoute allowedRoles={['ROLE_COFOUNDER']} />,
    errorElement: <ErrorPage />,
    children: [
      { path: '/cofounder/dashboard',       element: s(CoFounderDashboard) },
      { path: '/cofounder/startups',        element: s(BrowseStartups) },
      { path: '/cofounder/startups/:id',    element: s(StartupDetail) },
      { path: '/cofounder/invitations',     element: s(MyInvitations) },
    ],
  },

  {
    element: <ProtectedRoute allowedRoles={['ROLE_INVESTOR']} />,
    errorElement: <ErrorPage />,
    children: [
      { path: '/investor/dashboard',        element: s(InvestorDashboard) },
      { path: '/investor/startups',         element: s(BrowseStartups) },
      { path: '/investor/startups/:id',     element: s(StartupDetail) },
      { path: '/investor/investments',      element: s(MyInvestments) },
      { path: '/investor/payments',         element: s(PaymentHistory) },
    ],
  },

  {
    element: <ProtectedRoute allowedRoles={['ROLE_ADMIN']} />,
    errorElement: <ErrorPage />,
    children: [
      { path: '/admin/dashboard',           element: s(AdminDashboard) },
    ],
  },

  {
    element: <ProtectedRoute />,
    errorElement: <ErrorPage />,
    children: [
      { path: '/notifications',             element: s(Notifications) },
      { path: '/messages',                  element: s(Messages) },
      { path: '/messages/:conversationId',  element: s(Chat) },
      { path: '/profile',                   element: s(Profile) },
    ],
  },
]);

export default router;
