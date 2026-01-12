import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Cloud, FolderOpen, Clock, AlertTriangle, History, Monitor, Settings } from 'lucide-react';
import { cn } from '../components/ui/utils';
import { Progress } from '../components/ui/progress';
import { apiService } from '../services/api';

const menuItems = [
  { path: '/files', label: 'Файлы', icon: FolderOpen },
  { path: '/recent', label: 'Недавние', icon: Clock },
  { path: '/conflicts', label: 'Конфликты', icon: AlertTriangle },
  { path: '/history', label: 'История версий', icon: History },
  { path: '/devices', label: 'Устройства', icon: Monitor },
  { path: '/settings', label: 'Настройки', icon: Settings },
];

export function Sidebar() {
  const location = useLocation();
  const [quota, setQuota] = React.useState<{ storageUsed: number; storageQuota: number } | null>(null);

  React.useEffect(() => {
    const fetchQuota = async () => {
      try {
        const data = await apiService.checkQuota();
        setQuota(data);
      } catch (error) {
        console.error('Error fetching quota:', error);
      }
    };
    fetchQuota();

    // Refresh quota periodically or on custom event if needed
    const interval = setInterval(fetchQuota, 30000); // 30s
    return () => clearInterval(interval);
  }, []);

  const formatSize = (bytes: number) => {
    if (!bytes) return '0';
    const gb = bytes / (1024 * 1024 * 1024);
    if (gb < 0.1) return gb.toFixed(3);
    return gb.toFixed(1);
  };

  const storageUsed = quota ? quota.storageUsed : 0;
  const storageTotal = quota ? quota.storageQuota : 10 * 1024 * 1024 * 1024; // Default 10GB if not found
  const storagePercent = (storageUsed / storageTotal) * 100;

  return (
    <aside className="fixed left-0 top-0 h-full w-[260px] bg-bg-1 border-r border-border-0 flex flex-col p-6">
      {/* Logo */}
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-2">
          <Cloud className="w-8 h-8 text-accent-primary" />
          <h3 className="text-text-primary">CloudSync</h3>
        </div>
        <p className="text-xs text-text-muted">Синхронизация файлов</p>
      </div>

      {/* Menu */}
      <nav className="flex-1 space-y-2">
        {menuItems.map(({ path, label, icon: Icon }) => {
          const isActive = location.pathname === path;
          return (
            <Link
              key={path}
              to={path}
              className={cn(
                'flex items-center gap-3 px-4 py-2.5 rounded-xl transition-colors',
                isActive
                  ? 'bg-accent-dark/40 text-text-primary'
                  : 'text-text-secondary hover:bg-surface-1'
              )}
            >
              <Icon className="w-5 h-5" />
              <span>{label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Storage Card */}
      <div className="mt-auto p-4 rounded-2xl bg-surface-0 border border-border-0">
        <p className="text-xs text-text-secondary mb-3">Хранилище</p>
        <Progress value={storagePercent} className="h-2 mb-2" />
        <p className="text-xs text-text-secondary">
          {formatSize(storageUsed)} ГБ из {formatSize(storageTotal)} ГБ
        </p>
      </div>
    </aside>
  );
}
