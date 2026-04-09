import React from 'react';
import Navbar from './Navbar';
import Sidebar from './Sidebar';

interface LayoutProps {
  children: React.ReactNode;
}

const Layout: React.FC<LayoutProps> = ({ children }) => (
  <div className="min-h-screen flex flex-col" style={{ background: 'var(--bg)' }}>
    <Navbar />
    <div className="flex flex-1 min-h-0">
      <Sidebar />
      <main className="flex-1 p-6 md:p-8 overflow-auto" style={{ minWidth: 0 }}>
        {children}
      </main>
    </div>
  </div>
);

export default Layout;
