import React, { useState, useEffect } from 'react';
import {
    Users,
    Shield,
    Database,
    Activity,
    Search,
    Filter,
    MoreVertical,
    Ban,
    CheckCircle,
    UserPlus,
    Loader2,
    HardDrive,
    Trash2,
    ShieldOff
} from 'lucide-react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncButton } from '../components/CloudSyncButton';
import { CloudSyncBadge } from '../components/CloudSyncBadge';
import { CloudSyncInput } from '../components/CloudSyncInput';
import { apiService } from '../services/api';
import { cn } from '../components/ui/utils';
import { toast } from 'sonner';

export function AdminPage() {
    const [users, setUsers] = useState<any[]>([]);
    const [stats, setStats] = useState<any>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'blocked'>('all');
    const [roleFilter, setRoleFilter] = useState<'all' | 'admin' | 'user'>('all');
    const [activeTab, setActiveTab] = useState<'users' | 'stats' | 'storage'>('users');
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const fetchUsers = async () => {
        setIsLoading(true);
        try {
            const response = await apiService.adminListUsers({
                page: currentPage,
                pageSize: 10,
                search: searchTerm
            });
            console.log('Fetched users:', response.users?.length, 'users');
            console.log('Users data:', response.users?.map(u => ({ id: u.id, email: u.email, isBlocked: u.isBlocked, roles: u.roles })));
            console.log('First user full data:', response.users?.[0]);
            setUsers(response.users || []);
            setTotalPages(Math.ceil(response.total / 10));
        } catch (error) {
            console.error('Error fetching admin users:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const fetchStats = async () => {
        try {
            const [systemStats, storageStats, userStats] = await Promise.all([
                apiService.adminGetSystemStats(),
                apiService.adminGetStorageStats(),
                apiService.adminGetUserStats()
            ]);
            console.log('System stats:', systemStats);
            console.log('Storage stats:', storageStats);
            console.log('User stats:', userStats);
            setStats({ system: systemStats, storage: storageStats, user: userStats });
        } catch (error) {
            console.error('Error fetching stats:', error);
        }
    };

    useEffect(() => {
        if (activeTab === 'users') {
            fetchUsers();
        } else {
            fetchStats();
        }
    }, [activeTab, currentPage, searchTerm, statusFilter, roleFilter]);

    // Apply client-side filters (since backend doesn't support these filters yet)
    const filteredUsers = users.filter(user => {
        // Status filter
        if (statusFilter === 'blocked' && !user.isBlocked) return false;
        if (statusFilter === 'active' && user.isBlocked) return false;

        // Role filter
        if (roleFilter === 'admin') {
            const isAdmin = user.roles && user.roles.some((role: string) =>
                role === 'ROLE_ADMIN' || role === 'ADMIN' || role.toUpperCase().includes('ADMIN')
            );
            if (!isAdmin) return false;
        }
        if (roleFilter === 'user') {
            const isAdmin = user.roles && user.roles.some((role: string) =>
                role === 'ROLE_ADMIN' || role === 'ADMIN' || role.toUpperCase().includes('ADMIN')
            );
            if (isAdmin) return false;
        }

        return true;
    });

    const handleToggleAdmin = async (userId: string, isAdmin: boolean) => {
        try {
            console.log('Toggling admin role:', userId, 'Current isAdmin:', isAdmin);

            if (isAdmin) {
                if (!confirm('Вы уверены, что хотите снять права администратора у этого пользователя?')) return;
                await apiService.adminRevokeRole(userId, 'ROLE_ADMIN');
                toast.success('Права администратора успешно сняты');
            } else {
                if (!confirm('Назначить этого пользователя администратором?')) return;
                await apiService.adminAssignRole(userId, 'ROLE_ADMIN');
                toast.success('Пользователь успешно назначен администратором');
            }

            // Refresh list from server immediately
            await fetchUsers();
        } catch (error: any) {
            console.error('Error toggling admin role:', error);
            toast.error(error.response?.data?.message || 'Ошибка при изменении роли пользователя');
        }
    };

    const handleBlockUser = async (userId: string, isBlocked: boolean) => {
        try {
            console.log('Blocking user:', userId, 'Current state:', isBlocked);

            if (isBlocked) {
                if (!confirm('Разблокировать пользователя?')) return;
                await apiService.adminUnblockUser(userId);
                toast.success('Пользователь успешно разблокирован');
            } else {
                const reason = prompt('Укажите причину блокировки:');
                if (!reason) return;
                await apiService.adminBlockUser(userId, reason);
                toast.success('Пользователь успешно заблокирован');
            }

            // Refresh list from server immediately
            await fetchUsers();
        } catch (error: any) {
            console.error('Error blocking/unblocking user:', error);
            toast.error(error.response?.data?.message || 'Ошибка при изменении статуса пользователя');
        }
    };

    const handleDeleteUser = async (userId: string) => {
        if (!confirm('Вы уверены, что хотите полностью удалить этого пользователя? Это действие необратимо и удалит все данные, включая файлы.')) return;

        try {
            await apiService.adminDeleteUser(userId);
            toast.success('Пользователь успешно удален');
            fetchUsers();
            fetchStats();
        } catch (error: any) {
            console.error('Error deleting user:', error);
            toast.error(error.response?.data?.message || 'Ошибка при удалении пользователя');
        }
    };

    const formatSize = (bytes: number) => {
        if (!bytes) return '0 Б';
        const k = 1024;
        const sizes = ['Б', 'КБ', 'МБ', 'ГБ', 'ТБ'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(3)) + ' ' + sizes[i];
    };

    return (
        <div className="p-8">
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-2xl font-bold text-text-primary">Панель администратора</h1>
                    <p className="text-text-muted mt-1">Управление пользователями и мониторинг системы</p>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex gap-4 mb-6 border-b border-border-0">
                <button
                    onClick={() => setActiveTab('users')}
                    className={cn(
                        "pb-3 px-4 text-sm font-medium transition-colors relative",
                        activeTab === 'users' ? "text-accent-primary" : "text-text-muted hover:text-text-secondary"
                    )}
                >
                    <div className="flex items-center gap-2">
                        <Users className="w-4 h-4" />
                        Пользователи
                    </div>
                    {activeTab === 'users' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-accent-primary" />}
                </button>
                {/* Временно отключено - статистика работает нестабильно */}
                {/* <button
                    onClick={() => setActiveTab('stats')}
                    className={cn(
                        "pb-3 px-4 text-sm font-medium transition-colors relative",
                        activeTab === 'stats' ? "text-accent-primary" : "text-text-muted hover:text-text-secondary"
                    )}
                >
                    <div className="flex items-center gap-2">
                        <Activity className="w-4 h-4" />
                        Статистика
                    </div>
                    {activeTab === 'stats' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-accent-primary" />}
                </button> */}
            </div>

            {activeTab === 'users' && (
                <>
                    <div className="flex gap-4 mb-6">
                        <div className="flex-1 relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                            <input
                                type="text"
                                placeholder="Поиск по email или имени..."
                                className="w-full pl-10 pr-4 py-2 bg-surface-2 border border-border-0 rounded-xl text-sm focus:outline-none focus:border-accent-primary"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                            />
                        </div>
                        <div className="flex gap-3">
                            <select
                                value={statusFilter}
                                onChange={(e) => setStatusFilter(e.target.value as any)}
                                className="px-4 py-2 bg-surface-1 border border-border-0 rounded-lg text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-accent-primary"
                            >
                                <option value="all">Все статусы</option>
                                <option value="active">Активные</option>
                                <option value="blocked">Заблокированные</option>
                            </select>

                            <select
                                value={roleFilter}
                                onChange={(e) => setRoleFilter(e.target.value as any)}
                                className="px-4 py-2 bg-surface-1 border border-border-0 rounded-lg text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-accent-primary"
                            >
                                <option value="all">Все роли</option>
                                <option value="admin">Администраторы</option>
                                <option value="user">Пользователи</option>
                            </select>
                        </div>
                    </div>

                    <CloudSyncCard className="min-h-[400px] relative">
                        {isLoading ? (
                            <div className="absolute inset-0 flex items-center justify-center bg-surface-2/50 z-10">
                                <Loader2 className="w-8 h-8 text-accent-primary animate-spin" />
                            </div>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full">
                                    <thead>
                                        <tr className="bg-surface-2 border-b border-border-0">
                                            <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Пользователь</th>
                                            <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Регистрация</th>
                                            <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Использование</th>
                                            <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Статус</th>
                                            <th className="w-24"></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredUsers.map((user) => {
                                            const storedUserStr = localStorage.getItem('user');
                                            const currentUserId = storedUserStr ? JSON.parse(storedUserStr).userId : null;
                                            const isMe = user.id === currentUserId;
                                            // Check for admin role in all possible formats
                                            const isAdmin = user.roles && user.roles.some((role: string) =>
                                                role === 'ROLE_ADMIN' || role === 'ADMIN' || role.toUpperCase().includes('ADMIN')
                                            );

                                            // Debug logging
                                            if (user.roles && user.roles.length > 1) {
                                                console.log(`User ${user.email} roles:`, user.roles, 'isAdmin:', isAdmin);
                                            }

                                            return (
                                                <tr key={user.id} className="border-b border-border-0 hover:bg-accent-dark/5 transition-colors group">
                                                    <td className="px-6 py-4">
                                                        <div className="flex flex-col">
                                                            <div className="flex items-center gap-2">
                                                                <span className="text-sm font-medium text-text-primary">{user.name || 'Без имени'}</span>
                                                                {isAdmin && (
                                                                    <div className="flex items-center gap-1 bg-accent-primary/10 px-1.5 py-0.5 rounded text-accent-primary">
                                                                        <Shield className="w-3 h-3" fill="currentColor" />
                                                                        <span className="text-[10px] font-medium">Админ</span>
                                                                    </div>
                                                                )}
                                                            </div>
                                                            <span className="text-xs text-text-muted">{user.email}</span>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4">
                                                        <span className="text-sm text-text-muted">
                                                            {new Date(user.createdAt).toLocaleDateString()}
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-4">
                                                        <div className="flex flex-col gap-1 w-32">
                                                            <div className="flex justify-between text-[10px] text-text-muted">
                                                                <span>{formatSize(user.storageUsed || 0)}</span>
                                                                <span>{formatSize(parseInt(user.storageQuota || '5368709120'))}</span>
                                                            </div>
                                                            <div className="h-1 bg-surface-1 rounded-full overflow-hidden">
                                                                <div
                                                                    className="h-full bg-accent-primary"
                                                                    style={{ width: `${Math.min(100, ((user.storageUsed || 0) / parseInt(user.storageQuota || '5368709120')) * 100)}%` }}
                                                                />
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4">
                                                        {isMe ? (
                                                            <CloudSyncBadge variant="completed">ВЫ (Администратор)</CloudSyncBadge>
                                                        ) : user.isBlocked ? (
                                                            <CloudSyncBadge variant="error">Заблокирован</CloudSyncBadge>
                                                        ) : (
                                                            <CloudSyncBadge variant="synced">Активен</CloudSyncBadge>
                                                        )}
                                                    </td>
                                                    <td className="px-6 py-4 text-right">
                                                        <div className="flex gap-2 justify-end">
                                                            {/* Toggle Admin Button */}
                                                            <button
                                                                onClick={() => handleToggleAdmin(user.id, isAdmin)}
                                                                disabled={isMe || user.isBlocked} // Disable if me or blocked (optional, but safer)
                                                                className={cn(
                                                                    "p-2 rounded-lg transition-colors",
                                                                    isMe
                                                                        ? "opacity-50 cursor-not-allowed text-text-muted"
                                                                        : isAdmin
                                                                            ? "text-text-muted hover:text-danger-bright hover:bg-danger-bright/10"
                                                                            : "text-text-muted hover:text-accent-primary hover:bg-accent-primary/10"
                                                                )}
                                                                title={isMe ? "Нельзя изменить свою роль" : isAdmin ? "Снять права администратора" : "Назначить администратором"}
                                                            >
                                                                {isAdmin ? <ShieldOff className="w-5 h-5" /> : <Shield className="w-5 h-5" />}
                                                            </button>

                                                            <button
                                                                onClick={() => handleBlockUser(user.id, user.isBlocked)}
                                                                disabled={isMe}
                                                                className={cn(
                                                                    "p-2 rounded-lg transition-colors",
                                                                    isMe ? "opacity-50 cursor-not-allowed text-text-muted" :
                                                                        user.isBlocked ? "text-success-bright hover:bg-success-bright/10" : "text-danger-bright hover:bg-danger-bright/10"
                                                                )}
                                                                title={isMe ? "Нельзя изменить себя" : user.isBlocked ? "Разблокировать" : "Заблокировать"}
                                                            >
                                                                {user.isBlocked ? <CheckCircle className="w-5 h-5" /> : <Ban className="w-5 h-5" />}
                                                            </button>

                                                            {/* Delete Button */}
                                                            <button
                                                                onClick={() => handleDeleteUser(user.id)}
                                                                disabled={isMe}
                                                                className={cn(
                                                                    "p-2 rounded-lg transition-colors text-text-muted hover:text-danger-bright hover:bg-danger-bright/10",
                                                                    isMe && "opacity-50 cursor-not-allowed"
                                                                )}
                                                                title={isMe ? "Нельзя удалить себя" : "Удалить пользователя"}
                                                            >
                                                                <Trash2 className="w-5 h-5" />
                                                            </button>
                                                        </div>
                                                    </td>
                                                </tr>
                                            )
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        {/* Pagination Placeholder */}
                        {totalPages > 1 && (
                            <div className="p-4 border-t border-border-0 flex justify-center gap-2">
                                {Array.from({ length: totalPages }).map((_, i) => (
                                    <button
                                        key={i}
                                        onClick={() => setCurrentPage(i)}
                                        className={cn(
                                            "w-8 h-8 rounded-lg text-sm transition-colors",
                                            currentPage === i ? "bg-accent-primary text-white" : "hover:bg-surface-1 text-text-muted"
                                        )}
                                    >
                                        {i + 1}
                                    </button>
                                ))}
                            </div>
                        )}
                    </CloudSyncCard>
                </>
            )}

            {/* Временно отключено - статистика работает нестабильно */}
            {/* {activeTab === 'stats' && stats && (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    <CloudSyncCard className="p-6">
                        <h3 className="text-sm font-medium text-text-muted mb-4 flex items-center gap-2">
                            <Users className="w-4 h-4" /> Пользователи
                        </h3>
                        <div className="space-y-4">
                            <div className="flex justify-between items-end">
                                <span className="text-3xl font-bold text-text-primary">{stats.user.totalUsers}</span>
                                <span className="text-xs text-success-bright">+{stats.user.newUsersLast24h} за 24ч</span>
                            </div>
                            <p className="text-xs text-text-muted">Активных за час: {stats.user.activeUsersLastHour}</p>
                        </div>
                    </CloudSyncCard>

                    <CloudSyncCard className="p-6">
                        <h3 className="text-sm font-medium text-text-muted mb-4 flex items-center gap-2">
                            <HardDrive className="w-4 h-4" /> Хранилище
                        </h3>
                        <div className="space-y-4">
                            <div className="flex justify-between items-end">
                                <span className="text-2xl font-bold text-text-primary">{formatSize(stats.storage.totalBytesUsed)}</span>
                                <span className="text-xs text-text-muted">из {formatSize(stats.storage.totalBytesAllocated)}</span>
                            </div>
                            <div className="h-2 bg-surface-1 rounded-full overflow-hidden">
                                <div
                                    className="h-full bg-accent-primary"
                                    style={{ width: `${(stats.storage.totalBytesUsed / stats.storage.totalBytesAllocated) * 100}%` }}
                                />
                            </div>
                        </div>
                    </CloudSyncCard>

                    <CloudSyncCard className="p-6">
                        <h3 className="text-sm font-medium text-text-muted mb-4 flex items-center gap-2">
                            <Shield className="w-4 h-4" /> Безопасность
                        </h3>
                        <div className="space-y-4 text-sm">
                            <div className="flex justify-between">
                                <span className="text-text-muted">Заблокировано:</span>
                                <span className="text-danger-bright font-medium">{stats.user.blockedUsers}</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-text-muted">Администраторов:</span>
                                <span className="text-accent-primary font-medium">{stats.user.adminCount || 1}</span>
                            </div>
                        </div>
                    </CloudSyncCard>
                </div>
            )} */}
        </div>
    );
}
