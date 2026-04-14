import React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Bell, MessageSquare, LogOut, User, Menu } from 'lucide-react';
import { logout } from '../../store/slices/authSlice';
import { setUnreadCount } from '../../store/slices/notificationSlice';
import { toggleSidebar } from '../../store/slices/sidebarSlice';
import { getUnreadNotifications } from '../../core/api/notificationApi';
import useAuth from '../hooks/useAuth';
import useNotificationSocket from '../hooks/useNotificationSocket';
import type { RootState } from '../../types';
import type { AppDispatch } from '../../store/store';

const NAVBAR_H = 53;

const Navbar: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const { user, userId, isFounder, isInvestor, isCoFounder } = useAuth();
  const unreadCount = useSelector((s: RootState) => s.notifications.unreadCount);
  const [showLogoutModal, setShowLogoutModal] = useState(false);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const modalRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!userId) return;
    getUnreadNotifications(userId)
      .then((res) => {
        const payload = res.data as { data?: Array<unknown> } | Array<unknown>;
        const list = Array.isArray(payload) ? payload : payload.data || [];
        dispatch(setUnreadCount(list.length));
      })
      .catch(() => {});
  }, [userId, dispatch]);

  const handleNewNotification = useCallback(() => {
    dispatch(setUnreadCount(unreadCount + 1));
  }, [dispatch, unreadCount]);

  useNotificationSocket(userId, handleNewNotification);

  useEffect(() => {
    if (!showLogoutModal) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { setShowLogoutModal(false); return; }
      if (e.key === 'Tab') {
        const focusable = modalRef.current?.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        if (!focusable || focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    setTimeout(() => cancelRef.current?.focus(), 10);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [showLogoutModal]);

  const confirmLogout = () => {
    setShowLogoutModal(false);
    dispatch(logout());
    navigate('/login');
  };

  let dashboardLink = '/admin/dashboard';
  if (isFounder) dashboardLink = '/founder/dashboard';
  else if (isInvestor) dashboardLink = '/investor/dashboard';
  else if (isCoFounder) dashboardLink = '/cofounder/dashboard';

  const iconCls = 'p-2 rounded-[8px] transition-all duration-150 hover:[background:var(--surface-2)]';

  const iconBtnStyle: React.CSSProperties = {
    width: 32,
    height: 32,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    background: 'var(--surface-2)',
    color: 'var(--text-muted)',
    border: 'none',
    cursor: 'pointer',
    transition: 'background 150ms',
    flexShrink: 0,
  };

  return (
    <>
      <nav
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          height: NAVBAR_H,
          zIndex: 50,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 12px 0 12px',
          background: 'var(--surface)',
          borderBottom: '1px solid var(--border)',
          boxSizing: 'border-box',
          gap: 8,
          minWidth: 0,
        }}
      >
        {/* LEFT: Hamburger + Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, flexShrink: 1, overflow: 'hidden' }}>
          <button
            onClick={() => dispatch(toggleSidebar())}
            aria-label="Toggle sidebar"
            style={{ ...iconBtnStyle, flexShrink: 0 }}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--surface-3)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'var(--surface-2)')}
          >
            <Menu size={17} />
          </button>

          <Link
            to={dashboardLink}
            style={{ display: 'flex', alignItems: 'center', gap: 8, textDecoration: 'none', minWidth: 0, flexShrink: 1 }}
          >
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 8,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fff',
                fontSize: 10,
                fontWeight: 700,
                background: 'var(--brand)',
                boxShadow: '0 1px 4px rgba(5,150,105,0.4)',
                flexShrink: 0,
              }}
            >
              FL
            </div>
            <span
              style={{
                fontFamily: "'Syne', system-ui, sans-serif",
                fontSize: 15,
                fontWeight: 700,
                letterSpacing: '-0.02em',
                color: 'var(--text-primary)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              FounderLink
            </span>
          </Link>
        </div>

        {/* RIGHT: Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 2, flexShrink: 0 }}>
          {(isFounder || isInvestor || isCoFounder) && (
            <>
              <Link
                to="/notifications"
                aria-label="Notifications"
                className={`relative ${iconCls}`}
                style={{ color: 'var(--text-muted)' }}
              >
                <Bell size={17} />
                {unreadCount > 0 && (
                  <span
                    className="absolute top-1.5 right-1.5 w-3.5 h-3.5 text-white text-[9px] rounded-full flex items-center justify-center font-semibold"
                    style={{ background: 'var(--red)' }}
                  >
                    {unreadCount > 9 ? '9+' : unreadCount}
                  </span>
                )}
              </Link>
              <Link
                to="/messages"
                aria-label="Messages"
                className={iconCls}
                style={{ color: 'var(--text-muted)' }}
              >
                <MessageSquare size={17} />
              </Link>
            </>
          )}

          <Link to="/profile" aria-label="Profile" className={iconCls} style={{ color: 'var(--text-muted)' }}>
            <User size={17} />
          </Link>

          {user?.email && (
            <span
              className="text-xs hidden md:block mx-2 pl-3 font-medium"
              style={{ color: 'var(--text-muted)', borderLeft: '1px solid var(--border-medium)', whiteSpace: 'nowrap' }}
            >
              {user.email}
            </span>
          )}

          <button
            onClick={() => setShowLogoutModal(true)}
            aria-label="Sign out"
            title="Sign out"
            className={`${iconCls} hover:![color:var(--red)] hover:![background:var(--red-bg)]`}
            style={{ color: 'var(--text-muted)' }}
          >
            <LogOut size={17} />
          </button>
        </div>
      </nav>

      {showLogoutModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="logout-title"
          onClick={(e) => { if (e.target === e.currentTarget) setShowLogoutModal(false); }}
          style={{
            position: 'fixed', inset: 0, zIndex: 9999,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'rgba(0,0,0,0.4)',
            backdropFilter: 'blur(4px)', WebkitBackdropFilter: 'blur(4px)',
            padding: '1rem',
          }}
        >
          <div
            ref={modalRef}
            style={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 16,
              padding: '28px 32px',
              boxShadow: '0 16px 48px rgba(0,0,0,0.18)',
              width: '100%', maxWidth: 400,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 38, height: 38, borderRadius: 10,
                background: 'var(--red-bg)',
                display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
              }}>
                <LogOut size={17} style={{ color: 'var(--red)' }} />
              </div>
              <h2
                id="logout-title"
                style={{
                  fontFamily: "'Syne', system-ui, sans-serif",
                  fontSize: 18, fontWeight: 600,
                  color: 'var(--text-primary)', margin: 0,
                }}
              >
                Sign out
              </h2>
            </div>
            <p style={{
              color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6,
              marginBottom: 24, fontFamily: "'DM Sans', system-ui, sans-serif",
            }}>
              You'll be signed out of your account. Any unsaved changes will be lost.
            </p>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button ref={cancelRef} onClick={() => setShowLogoutModal(false)} className="btn-secondary">
                Cancel
              </button>
              <button onClick={confirmLogout} className="btn-danger">
                Sign out
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default Navbar;
