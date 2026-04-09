import React from 'react';
import { useRouteError, Link } from 'react-router-dom';
import { AlertTriangle, RefreshCw, Home, ArrowLeft } from 'lucide-react';

export const PageLoader: React.FC = () => (
  <div
    style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--bg)',
    }}
  >
    <style>{`
      @keyframes fl-pulse {
        0%, 100% { opacity: 1; transform: scale(1); }
        50% { opacity: 0.4; transform: scale(0.92); }
      }
    `}</style>
    <div
      style={{
        width: 48, height: 48,
        borderRadius: 14,
        background: 'var(--brand)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: '#fff',
        fontSize: 16, fontWeight: 700,
        fontFamily: "'Syne', system-ui, sans-serif",
        boxShadow: '0 4px 20px rgba(5,150,105,0.35)',
        animation: 'fl-pulse 1.4s ease-in-out infinite',
      }}
    >
      FL
    </div>
  </div>
);

export const ErrorPage: React.FC = () => {
  const error = useRouteError() as Error | undefined;
  return (
    <div
      style={{
        minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: 'var(--bg)', padding: '1.5rem',
      }}
    >
      <div style={{ textAlign: 'center', maxWidth: 420 }}>
        <div
          style={{
            width: 56, height: 56, borderRadius: 16,
            background: 'var(--red-bg)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            margin: '0 auto 20px',
          }}
        >
          <AlertTriangle size={28} style={{ color: 'var(--red)' }} />
        </div>
        <h1
          style={{
            fontFamily: "'Syne', system-ui, sans-serif",
            fontSize: 22, fontWeight: 700,
            color: 'var(--text-primary)', marginBottom: 8,
          }}
        >
          Something went wrong
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 24, lineHeight: 1.6 }}>
          {error?.message || 'An unexpected error occurred. Please try again.'}
        </p>
        <button
          onClick={() => window.location.reload()}
          className="btn-primary"
          style={{ gap: 8 }}
        >
          <RefreshCw size={14} /> Reload page
        </button>
      </div>
    </div>
  );
};

export const NotFound: React.FC = () => (
  <div
    style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg)', padding: '1.5rem',
    }}
  >
    <div style={{ textAlign: 'center', maxWidth: 420 }}>
      <h1
        style={{
          fontFamily: "'Syne', system-ui, sans-serif",
          fontSize: 96, fontWeight: 700,
          color: 'var(--text-primary)', lineHeight: 1, marginBottom: 12,
        }}
      >
        404
      </h1>
      <p style={{ color: 'var(--text-secondary)', fontSize: 18, fontWeight: 500, marginBottom: 8 }}>
        This page doesn't exist.
      </p>
      <p style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 28 }}>
        The URL you requested could not be found on this server.
      </p>
      <Link to="/" className="btn-primary" style={{ gap: 8, textDecoration: 'none' }}>
        <Home size={14} /> Go home
      </Link>
    </div>
  </div>
);

export const Unauthorized: React.FC = () => (
  <div
    style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg)', padding: '1.5rem',
    }}
  >
    <div style={{ textAlign: 'center', maxWidth: 420 }}>
      <h1
        style={{
          fontFamily: "'Syne', system-ui, sans-serif",
          fontSize: 96, fontWeight: 700,
          color: 'var(--text-primary)', lineHeight: 1, marginBottom: 12,
        }}
      >
        403
      </h1>
      <p style={{ color: 'var(--text-secondary)', fontSize: 18, fontWeight: 500, marginBottom: 8 }}>
        You don't have access to this page.
      </p>
      <p style={{ color: 'var(--text-muted)', fontSize: 14, marginBottom: 28 }}>
        Your current role doesn't have permission to view this content.
      </p>
      <Link to="/founder/dashboard" className="btn-primary" style={{ gap: 8, textDecoration: 'none' }}>
        <ArrowLeft size={14} /> Go to dashboard
      </Link>
    </div>
  </div>
);
