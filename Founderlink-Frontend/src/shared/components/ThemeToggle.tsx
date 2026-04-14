import React from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Sun, Moon } from 'lucide-react';
import { toggleTheme, selectTheme } from '../../store/slices/themeSlice';
import type { AppDispatch } from '../../store/store';

const ThemeToggle: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const theme = useSelector(selectTheme);

  return (
    <button
      onClick={() => dispatch(toggleTheme())}
      aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      style={{
        position: 'fixed',
        bottom: 24,
        right: 24,
        zIndex: 9998,
        width: 44,
        height: 44,
        borderRadius: 12,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--surface)',
        color: 'var(--text-muted)',
        border: '1px solid var(--border-medium)',
        cursor: 'pointer',
        boxShadow: '0 4px 16px rgba(0,0,0,0.12), 0 1px 4px rgba(0,0,0,0.08)',
        transition: 'background 150ms, box-shadow 150ms, transform 150ms',
        flexShrink: 0,
      }}
      onMouseEnter={e => {
        e.currentTarget.style.background = 'var(--surface-2)';
        e.currentTarget.style.boxShadow = '0 6px 20px rgba(0,0,0,0.16), 0 2px 6px rgba(0,0,0,0.1)';
        e.currentTarget.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={e => {
        e.currentTarget.style.background = 'var(--surface)';
        e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.12), 0 1px 4px rgba(0,0,0,0.08)';
        e.currentTarget.style.transform = 'translateY(0)';
      }}
    >
      {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
    </button>
  );
};

export default ThemeToggle;
