import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Check, ChevronDown } from 'lucide-react';
import { cn } from '../components/ui/utils';
import { useAuth } from '../context/AuthContext';

export function Topbar() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [showDropdown, setShowDropdown] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  const getUserInitials = () => {
    if (user?.name) {
      return user.name
        .split(' ')
        .map(n => n[0])
        .join('')
        .toUpperCase()
        .slice(0, 2);
    }
    if (user?.email) {
      return user.email[0].toUpperCase();
    }
    return 'U';
  };

  return (
    <header className="h-20 flex items-center justify-between px-8 bg-bg-1/50 border-b border-border-0">
      {/* Search */}
      <div className="flex-1 max-w-md">
        <div className="relative">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-text-muted" />
          <input
            type="text"
            placeholder="Поиск файлов..."
            className="w-full h-10 pl-12 pr-4 rounded-xl bg-surface-1 border border-border-0 text-text-secondary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent-primary"
          />
        </div>
      </div>

      {/* Status & Profile */}
      <div className="flex items-center gap-4">
        {/* Sync Status */}
        <div className="flex items-center gap-2 px-4 py-2 rounded-full bg-surface-1 border border-border-0">
          <Check className="w-4 h-4 text-accent-primary" />
          <span className="text-sm text-text-secondary">Синхронизировано</span>
        </div>

        {/* Profile Dropdown */}
        <div className="relative">
          <button
            onClick={() => setShowDropdown(!showDropdown)}
            className="flex items-center gap-2 hover:opacity-80 transition-opacity"
          >
            <div className="w-10 h-10 rounded-full bg-accent-dark flex items-center justify-center">
              <span className="text-text-primary font-medium">{getUserInitials()}</span>
            </div>
            <ChevronDown className="w-4 h-4 text-text-secondary" />
          </button>

          {showDropdown && (
            <div className="absolute right-0 mt-2 w-48 rounded-xl bg-surface-2 border border-border-0 shadow-lg overflow-hidden z-50">
              <div className="p-3 border-b border-border-0">
                <p className="text-sm font-medium text-text-primary">{user?.name || 'Пользователь'}</p>
                <p className="text-xs text-text-muted mt-1">{user?.email}</p>
              </div>
              <button 
                onClick={() => {
                  navigate('/settings');
                  setShowDropdown(false);
                }}
                className="w-full px-4 py-2.5 text-left text-sm text-text-secondary hover:bg-surface-1 transition-colors"
              >
                Настройки
              </button>
              <button 
                onClick={handleLogout}
                className="w-full px-4 py-2.5 text-left text-sm text-danger-bright hover:bg-surface-1 transition-colors"
              >
                Выйти
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
