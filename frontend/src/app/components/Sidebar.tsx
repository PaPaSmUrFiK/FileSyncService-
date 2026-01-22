import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Cloud, FolderOpen, Share2, Trash2, History, Shield, Settings, LogOut, Bell } from 'lucide-react';
import { cn } from '../components/ui/utils';
import { Progress } from '../components/ui/progress';
import { apiService } from '../services/api';
import { webSocketService, WebSocketNotification } from '../services/websocket';
import { useAuth } from '../context/AuthContext';

const menuItems = [
  { path: '/files', label: 'Файлы', icon: FolderOpen },
  { path: '/shared', label: 'Общий доступ', icon: Share2 },
  { path: '/notifications', label: 'Уведомления', icon: Bell, showBadge: true },
  { path: '/trash', label: 'Корзина', icon: Trash2 },
  { path: '/history', label: 'История версий', icon: History },
  { path: '/settings', label: 'Настройки', icon: Settings },
];

export function Sidebar() {
  const { user, logout } = useAuth();
  const isAdmin = user?.roles?.includes('ADMIN');
  const location = useLocation();
  const navigate = useNavigate();
  const [quota, setQuota] = React.useState<{ storageUsed: number; storageQuota: number } | null>(null);
  const [unreadCount, setUnreadCount] = React.useState(0);

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

  React.useEffect(() => {
    const fetchUnreadCount = async () => {
      try {
        const data = await apiService.getUnreadCount();
        setUnreadCount(data.count || 0);
      } catch (error) {
        console.error('Error fetching unread count:', error);
      }
    };
    fetchUnreadCount();

    // Subscribe to WebSocket for real-time updates
    const unsubscribe = webSocketService.subscribe((notification: WebSocketNotification) => {
      // For new notifications, increment immediately for instant feedback
      if (notification.type.includes('FILE_') ||
        notification.type.includes('VERSION_') ||
        notification.type.includes('SHARE_') ||
        notification.type.includes('USER_') ||
        notification.type.includes('ADMIN_')) {
        setUnreadCount(prev => prev + 1);
      }

      // For read/delete events, fetch accurate count from server
      if (notification.type === 'NOTIFICATION_READ' ||
        notification.type === 'NOTIFICATION_DELETED' ||
        notification.type === 'ALL_NOTIFICATIONS_READ' ||
        notification.type === 'ALL_NOTIFICATIONS_DELETED') {
        fetchUnreadCount();
      }
    });

    // Refresh unread count periodically as backup
    const interval = setInterval(fetchUnreadCount, 60000); // 60s (reduced from 15s)
    return () => {
      clearInterval(interval);
      unsubscribe();
    };
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
        {menuItems.filter(item => item.path !== '/settings').map(({ path, label, icon: Icon, showBadge }) => {
          const isActive = location.pathname === path;
          return (
            <Link
              key={path}
              to={path}
              className={cn(
                'flex items-center gap-3 px-4 py-2.5 rounded-xl transition-colors relative',
                isActive
                  ? 'bg-accent-dark/40 text-text-primary'
                  : 'text-text-secondary hover:bg-surface-1'
              )}
            >
              <Icon className="w-5 h-5" />
              <span>{label}</span>
              {showBadge && unreadCount > 0 && (
                <span className="ml-auto px-2 py-0.5 text-xs bg-accent-primary text-white rounded-full">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </Link>
          );
        })}

        {/* Separator before Settings */}
        <div className="pt-4 mt-2 border-t border-border-0 space-y-1">
          <Link
            to="/settings"
            className={cn(
              'flex items-center gap-3 px-4 py-2.5 rounded-xl transition-colors',
              location.pathname === '/settings'
                ? 'bg-accent-dark/40 text-text-primary'
                : 'text-text-secondary hover:bg-surface-1'
            )}
          >
            <Settings className="w-5 h-5" />
            <span>Настройки</span>
          </Link>

          {isAdmin && (
            <Link
              to="/admin"
              className={cn(
                'flex items-center gap-3 px-4 py-2.5 rounded-xl transition-colors',
                location.pathname === '/admin'
                  ? 'bg-accent-dark/40 text-text-primary'
                  : 'text-text-secondary hover:bg-surface-1'
              )}
            >
              <Shield className="w-5 h-5" />
              <span>Админ панель</span>
            </Link>
          )}

          <button
            onClick={async () => {
              await logout();
              navigate('/');
            }}
            className="flex w-full items-center gap-3 px-4 py-2.5 rounded-xl transition-colors text-danger-bright hover:bg-danger-bright/10"
          >
            <LogOut className="w-5 h-5" />
            <span>Выйти</span>
          </button>
        </div>
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
