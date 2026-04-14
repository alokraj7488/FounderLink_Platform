import React, { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import { selectSidebarOpen, setSidebar } from '../../store/slices/sidebarSlice';
import type { AppDispatch } from '../../store/store';

interface LayoutProps {
  children: React.ReactNode;
}

const NAVBAR_H   = 53;
const SIDEBAR_W  = 224;
const BREAKPOINT = 768;

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const dispatch    = useDispatch<AppDispatch>();
  const sidebarOpen = useSelector(selectSidebarOpen);
  const [isMobile, setIsMobile] = useState(window.innerWidth < BREAKPOINT);

  /* Track viewport width — auto-close on mobile, auto-open on desktop */
  useEffect(() => {
    const handleResize = () => {
      const mobile = window.innerWidth < BREAKPOINT;
      setIsMobile(mobile);
      if (!mobile && !sidebarOpen) dispatch(setSidebar(true));
      if (mobile  &&  sidebarOpen) dispatch(setSidebar(false));
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [sidebarOpen, dispatch]);

  /* Close sidebar on initial mobile load */
  useEffect(() => {
    if (window.innerWidth < BREAKPOINT) dispatch(setSidebar(false));
  }, [dispatch]);

  const mainMarginLeft = !isMobile && sidebarOpen ? SIDEBAR_W : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', background: 'var(--bg)' }}>

      {/* Fixed top Navbar */}
      <Navbar />

      {/* Mobile backdrop — closes sidebar on tap-outside */}
      {isMobile && sidebarOpen && (
        <div
          aria-hidden="true"
          onClick={() => dispatch(setSidebar(false))}
          style={{
            position: 'fixed', inset: 0, zIndex: 39,
            background: 'rgba(0,0,0,0.45)',
            backdropFilter: 'blur(2px)',
            WebkitBackdropFilter: 'blur(2px)',
          }}
        />
      )}

      {/* Fixed sidebar — slides in/out via transform */}
      <aside
        style={{
          position: 'fixed',
          top: NAVBAR_H, left: 0, bottom: 0,
          width: SIDEBAR_W,
          overflowY: 'auto',
          zIndex: 40,
          background: 'var(--surface)',
          borderRight: '1px solid var(--border)',
          transform: sidebarOpen ? 'translateX(0)' : 'translateX(-100%)',
          transition: 'transform 280ms cubic-bezier(0.4, 0, 0.2, 1)',
          willChange: 'transform',
        }}
      >
        <Sidebar />
      </aside>

      {/* Scrollable main content — shifts right when sidebar is open on desktop */}
      <main
        style={{
          marginTop: NAVBAR_H,
          marginLeft: mainMarginLeft,
          flex: 1,
          padding: '32px',
          overflowY: 'auto',
          minWidth: 0,
          transition: 'margin-left 280ms cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      >
        {children}
      </main>
    </div>
  );
};

export default Layout;
