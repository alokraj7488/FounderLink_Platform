import React from 'react';
import { NavLink } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import {
  LayoutDashboard, Rocket, Search, DollarSign,
  ShieldCheck, TrendingUp, Mail, CreditCard, X,
} from 'lucide-react';
import useAuth from '../hooks/useAuth';
import { setSidebar } from '../../store/slices/sidebarSlice';
import type { AppDispatch } from '../../store/store';

interface SidebarLink {
  to: string;
  icon: React.ReactNode;
  label: string;
}

const Sidebar: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { isFounder, isInvestor, isCoFounder } = useAuth();

  const founderLinks: SidebarLink[] = [
    { to: '/founder/dashboard',   icon: <LayoutDashboard size={16} />, label: 'Dashboard' },
    { to: '/founder/startups',    icon: <Rocket size={16} />,          label: 'My Startups' },
    { to: '/founder/investments', icon: <DollarSign size={16} />,      label: 'Investment Requests' },
    { to: '/founder/payments',    icon: <CreditCard size={16} />,      label: 'Received Payments' },
  ];
  const coFounderLinks: SidebarLink[] = [
    { to: '/cofounder/dashboard',   icon: <LayoutDashboard size={16} />, label: 'Dashboard' },
    { to: '/cofounder/startups',    icon: <Search size={16} />,          label: 'Browse Startups' },
    { to: '/cofounder/invitations', icon: <Mail size={16} />,            label: 'My Invitations' },
  ];
  const investorLinks: SidebarLink[] = [
    { to: '/investor/dashboard',   icon: <LayoutDashboard size={16} />, label: 'Dashboard' },
    { to: '/investor/startups',    icon: <Search size={16} />,          label: 'Browse Startups' },
    { to: '/investor/investments', icon: <TrendingUp size={16} />,      label: 'My Investments' },
    { to: '/investor/payments',    icon: <CreditCard size={16} />,      label: 'Payment History' },
  ];
  const adminLinks: SidebarLink[] = [
    { to: '/admin/dashboard', icon: <ShieldCheck size={16} />, label: 'Approvals' },
  ];

  type RoleConfig = { links: SidebarLink[]; label: string; color: string; dot: string };
  const roleConfig: RoleConfig = isFounder
    ? { links: founderLinks,   label: 'Founder',    color: 'var(--brand)',   dot: 'var(--brand)' }
    : isCoFounder
    ? { links: coFounderLinks, label: 'Co-Founder', color: 'var(--purple)',  dot: 'var(--purple)' }
    : isInvestor
    ? { links: investorLinks,  label: 'Investor',   color: 'var(--green)',   dot: 'var(--green)' }
    : { links: adminLinks,     label: 'Admin',      color: 'var(--amber)',   dot: 'var(--amber)' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>

      {/* Role label row + mobile close button */}
      <div style={{
        padding: '12px 16px',
        borderBottom: '1px solid var(--border)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ width: 6, height: 6, borderRadius: '50%', background: roleConfig.dot, flexShrink: 0 }} />
          <span
            style={{
              fontSize: 11, fontWeight: 600,
              textTransform: 'uppercase', letterSpacing: '0.08em',
              color: roleConfig.color,
              fontFamily: "'DM Sans', system-ui, sans-serif",
            }}
          >
            {roleConfig.label}
          </span>
        </div>

        {/* Close button — visible on mobile via CSS */}
        <button
          onClick={() => dispatch(setSidebar(false))}
          aria-label="Close sidebar"
          className="md:hidden"
          style={{
            width: 28, height: 28,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            borderRadius: 6,
            background: 'var(--surface-2)',
            color: 'var(--text-muted)',
            border: 'none',
            cursor: 'pointer',
            flexShrink: 0,
          }}
        >
          <X size={14} />
        </button>
      </div>

      {/* Nav links */}
      <nav style={{ display: 'flex', flexDirection: 'column', gap: 2, padding: 12, flex: 1 }}>
        {roleConfig.links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            onClick={() => {
              // Close sidebar on mobile when a link is clicked
              if (window.innerWidth < 768) dispatch(setSidebar(false));
            }}
            className={({ isActive }) => isActive ? 'sidebar-link-active' : 'sidebar-link'}
          >
            {link.icon}
            {link.label}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div style={{ padding: 12, borderTop: '1px solid var(--border)' }}>
        <div
          style={{
            padding: '8px 12px',
            fontSize: 10, fontWeight: 500, letterSpacing: '0.05em',
            color: 'var(--text-faint)',
            fontFamily: "'DM Sans', system-ui, sans-serif",
          }}
        >
          FounderLink v2.0
        </div>
      </div>
    </div>
  );
};

export default Sidebar;
