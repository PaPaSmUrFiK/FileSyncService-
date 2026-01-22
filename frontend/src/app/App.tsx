import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'sonner';
import { LoginPage } from './pages/LoginPage';
import { FilesPage } from './pages/FilesPage';
import { SharedPage } from './pages/SharedPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { TrashPage } from './pages/TrashPage';
import { HistoryPage } from './pages/HistoryPage';
import { AdminPage } from './pages/AdminPage';
import { SettingsPage } from './pages/SettingsPage';
import { Sidebar } from './components/Sidebar';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const isAdmin = user?.roles?.includes('ADMIN');

  if (!isAdmin) {
    return <Navigate to="/files" replace />;
  }

  return <>{children}</>;
}

function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg-0 via-bg-1 to-bg-2">
      <Sidebar />
      <div className="ml-[260px]">
        <main className="min-h-screen">
          {children}
        </main>
      </div>
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <Toaster richColors position="top-right" />
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LoginPage />} />
          <Route
            path="/files"
            element={
              <ProtectedRoute>
                <AppLayout><FilesPage /></AppLayout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/shared"
            element={
              <ProtectedRoute>
                <AppLayout><SharedPage /></AppLayout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/notifications"
            element={
              <ProtectedRoute>
                <AppLayout><NotificationsPage /></AppLayout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/trash"
            element={
              <ProtectedRoute>
                <AppLayout><TrashPage /></AppLayout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/history/:fileId"
            element={
              <ProtectedRoute>
                <AppLayout><HistoryPage /></AppLayout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <ProtectedRoute>
                <AdminRoute>
                  <AppLayout><AdminPage /></AppLayout>
                </AdminRoute>
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <AppLayout><SettingsPage /></AppLayout>
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
