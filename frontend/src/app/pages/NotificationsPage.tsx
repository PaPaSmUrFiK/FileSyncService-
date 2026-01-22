import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, Check, Trash2, Loader2, FileText, User, Shield, Lock, Share2, Upload, Edit, AlertCircle } from 'lucide-react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { cn } from '../components/ui/utils';
import { apiService } from '../services/api';
import { webSocketService, WebSocketNotification } from '../services/websocket';

interface Notification {
    id: string;
    notificationType: string;
    title: string;
    message: string;
    priority: string;
    isRead: boolean;
    data: Record<string, string>;
    createdAt: string;
    readAt?: string;
}

export function NotificationsPage() {
    const navigate = useNavigate();
    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [filter, setFilter] = useState<'all' | 'unread'>('all');
    const [unreadCount, setUnreadCount] = useState(0);
    const [showDeleteAllConfirm, setShowDeleteAllConfirm] = useState(false);

    const fetchNotifications = async () => {
        setIsLoading(true);
        try {
            const response = await apiService.getNotifications({
                unreadOnly: filter === 'unread',
                limit: 50,
                offset: 0
            });
            setNotifications(response.notifications || []);
            setUnreadCount(response.unread_count || 0);
        } catch (error) {
            console.error('Error fetching notifications:', error);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchNotifications();
    }, [filter]);

    // WebSocket subscription for real-time notifications
    useEffect(() => {
        const token = localStorage.getItem('accessToken');
        if (!token) return;

        // Connect to WebSocket
        webSocketService.connect(token);

        // Subscribe to notifications
        const unsubscribe = webSocketService.subscribe((wsNotification: WebSocketNotification) => {
            // Handle status updates
            if (wsNotification.type === 'NOTIFICATION_READ') {
                setNotifications(prev => prev.map(n =>
                    n.id === wsNotification.notificationId ? { ...n, isRead: true } : n
                ));
                setUnreadCount(prev => Math.max(0, prev - 1));
                return;
            }

            if (wsNotification.type === 'NOTIFICATION_DELETED') {
                // Check if it was unread before removing to update count
                setNotifications(prev => {
                    const target = prev.find(n => n.id === wsNotification.notificationId);
                    if (target && !target.isRead) {
                        setUnreadCount(p => Math.max(0, p - 1));
                    }
                    return prev.filter(n => n.id !== wsNotification.notificationId);
                });
                return;
            }

            if (wsNotification.type === 'ALL_NOTIFICATIONS_READ') {
                setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
                setUnreadCount(0);
                return;
            }

            if (wsNotification.type === 'ALL_NOTIFICATIONS_DELETED') {
                setNotifications([]);
                setUnreadCount(0);
                return;
            }

            // Normal new notification
            // Convert WebSocket notification to our Notification format
            const newNotification: Notification = {
                id: wsNotification.notificationId,
                notificationType: wsNotification.type,
                title: wsNotification.title,
                message: wsNotification.message,
                priority: wsNotification.priority,
                isRead: false,
                data: {
                    resourceId: wsNotification.resourceId || '',
                    resourceType: wsNotification.resourceType || ''
                },
                createdAt: wsNotification.createdAt
            };

            // Add to notifications list if not already present
            setNotifications(prev => {
                const exists = prev.some(n => n.id === newNotification.id);
                if (exists) return prev;
                return [newNotification, ...prev];
            });

            // Increment unread count
            setUnreadCount(prev => prev + 1);
        });

        return () => {
            unsubscribe();
            webSocketService.disconnect();
        };
    }, []);

    const handleMarkAsRead = async (notificationId: string) => {
        // Optimistic update for instant UI feedback
        setNotifications(prev => prev.map(n =>
            n.id === notificationId ? { ...n, isRead: true } : n
        ));

        try {
            await apiService.markAsRead(notificationId);
            // WebSocket will update the counter
        } catch (error) {
            console.error('Error marking as read:', error);
            // Revert optimistic update on error
            setNotifications(prev => prev.map(n =>
                n.id === notificationId ? { ...n, isRead: false } : n
            ));
        }
    };

    const handleMarkAllAsRead = async () => {
        // Optimistic update
        const previousNotifications = notifications;
        setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));

        try {
            await apiService.markAllAsRead();
            // WebSocket will update the counter
        } catch (error) {
            console.error('Error marking all as read:', error);
            // Revert on error
            setNotifications(previousNotifications);
        }
    };

    const handleDelete = async (notificationId: string) => {
        // Optimistic update
        const deletedNotification = notifications.find(n => n.id === notificationId);
        setNotifications(prev => prev.filter(n => n.id !== notificationId));

        try {
            await apiService.deleteNotification(notificationId);
            // WebSocket will update the counter
        } catch (error) {
            console.error('Error deleting notification:', error);
            // Revert on error
            if (deletedNotification) {
                setNotifications(prev => [deletedNotification, ...prev]);
            }
        }
    };

    const handleDeleteAll = () => {
        setShowDeleteAllConfirm(true);
    };

    const confirmDeleteAll = async () => {
        setShowDeleteAllConfirm(false);

        // Optimistic update
        const previousNotifications = notifications;
        setNotifications([]);

        try {
            await apiService.deleteAllNotifications();
            // WebSocket will update the counter
        } catch (error) {
            console.error('Error deleting all notifications:', error);
            // Revert on error
            setNotifications(previousNotifications);
        }
    };

    const handleNotificationClick = (notification: Notification) => {
        if (!notification.isRead) {
            handleMarkAsRead(notification.id);
        }

        // Navigate based on notification type
        const { notificationType, data } = notification;
        if (data?.fileId) {
            if (notificationType.startsWith('FILE_')) {
                navigate(`/files?fileId=${data.fileId}`);
            } else if (notificationType === 'VERSION_UPLOADED') {
                navigate(`/history/${data.fileId}`);
            }
        } else if (notificationType.startsWith('USER_') || notificationType === 'ROLE_CHANGED' || notificationType === 'PASSWORD_CHANGED') {
            navigate('/settings');
        }
    };

    const getNotificationIcon = (type: string) => {
        switch (type) {
            case 'FILE_CREATED':
            case 'FILE_UPDATED':
            case 'FILE_RENAMED':
            case 'FILE_DELETED':
                return <FileText className="w-5 h-5" />;
            case 'FILE_SHARED':
                return <Share2 className="w-5 h-5" />;
            case 'VERSION_UPLOADED':
                return <Upload className="w-5 h-5" />;
            case 'ROLE_CHANGED':
                return <Shield className="w-5 h-5" />;
            case 'USER_BLOCKED':
            case 'USER_UNBLOCKED':
                return <AlertCircle className="w-5 h-5" />;
            case 'PASSWORD_CHANGED':
                return <Lock className="w-5 h-5" />;
            default:
                return <Bell className="w-5 h-5" />;
        }
    };

    const getNotificationColor = (type: string, priority: string) => {
        if (priority === 'urgent' || type === 'USER_BLOCKED') return 'text-danger-bright';
        if (type.startsWith('FILE_')) return 'text-accent-primary';
        if (type.startsWith('USER_') || type === 'ROLE_CHANGED') return 'text-warning-bright';
        return 'text-text-muted';
    };

    const formatDate = (dateStr: string) => {
        try {
            const date = new Date(dateStr);
            const now = new Date();
            const diffMs = now.getTime() - date.getTime();
            const diffMins = Math.floor(diffMs / 60000);
            const diffHours = Math.floor(diffMs / 3600000);
            const diffDays = Math.floor(diffMs / 86400000);

            if (diffMins < 1) return 'Только что';
            if (diffMins < 60) return `${diffMins} мин назад`;
            if (diffHours < 24) return `${diffHours} ч назад`;
            if (diffDays < 7) return `${diffDays} дн назад`;

            return date.toLocaleString('ru-RU', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch {
            return dateStr;
        }
    };

    return (
        <div className="p-8">
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-2xl font-bold text-text-primary">Уведомления</h1>
                    <p className="text-text-muted mt-1">
                        {unreadCount > 0 ? `${unreadCount} непрочитанных` : 'Все прочитано'}
                    </p>
                </div>

                <div className="flex gap-3">
                    <button
                        onClick={handleMarkAllAsRead}
                        disabled={unreadCount === 0}
                        className={`px-4 py-2 text-sm rounded-lg transition-colors flex items-center gap-2 ${unreadCount > 0
                            ? 'text-accent-primary hover:bg-accent-primary/10'
                            : 'text-text-muted cursor-not-allowed opacity-50'
                            }`}
                    >
                        <Check className="w-4 h-4" />
                        Прочитать все
                    </button>
                    {notifications.length > 0 && (
                        <button
                            onClick={handleDeleteAll}
                            className="px-4 py-2 text-sm text-danger-bright hover:bg-danger-bright/10 rounded-lg transition-colors flex items-center gap-2"
                        >
                            <Trash2 className="w-4 h-4" />
                            Удалить все
                        </button>
                    )}
                </div>
            </div>

            {/* Filter Tabs */}
            <div className="flex gap-4 mb-6 border-b border-border-0">
                <button
                    onClick={() => setFilter('all')}
                    className={cn(
                        "pb-3 px-4 text-sm font-medium transition-colors relative",
                        filter === 'all' ? "text-accent-primary" : "text-text-muted hover:text-text-secondary"
                    )}
                >
                    Все
                    {filter === 'all' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-accent-primary" />}
                </button>
                <button
                    onClick={() => setFilter('unread')}
                    className={cn(
                        "pb-3 px-4 text-sm font-medium transition-colors relative",
                        filter === 'unread' ? "text-accent-primary" : "text-text-muted hover:text-text-secondary"
                    )}
                >
                    Непрочитанные
                    {unreadCount > 0 && (
                        <span className="ml-2 px-2 py-0.5 text-xs bg-accent-primary text-white rounded-full">
                            {unreadCount}
                        </span>
                    )}
                    {filter === 'unread' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-accent-primary" />}
                </button>
            </div>

            <CloudSyncCard className="min-h-[400px] relative">
                {isLoading ? (
                    <div className="absolute inset-0 flex items-center justify-center bg-surface-2/50 z-10">
                        <Loader2 className="w-8 h-8 text-accent-primary animate-spin" />
                    </div>
                ) : notifications.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-20 text-text-muted">
                        <Bell className="w-16 h-16 mb-4 opacity-20" />
                        <p>{filter === 'unread' ? 'Нет непрочитанных уведомлений' : 'Нет уведомлений'}</p>
                    </div>
                ) : (
                    <div className="divide-y divide-border-0">
                        {notifications.map((notification) => (
                            <div
                                key={notification.id}
                                onClick={() => handleNotificationClick(notification)}
                                className={cn(
                                    "p-4 hover:bg-accent-dark/5 transition-colors cursor-pointer group",
                                    !notification.isRead && "bg-accent-primary/5"
                                )}
                            >
                                <div className="flex items-start gap-4">
                                    <div className={cn(
                                        "w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0",
                                        !notification.isRead ? "bg-accent-primary/20" : "bg-surface-1"
                                    )}>
                                        <div className={getNotificationColor(notification.notificationType, notification.priority)}>
                                            {getNotificationIcon(notification.notificationType)}
                                        </div>
                                    </div>

                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-start justify-between gap-2">
                                            <h3 className={cn(
                                                "text-sm font-medium",
                                                !notification.isRead ? "text-text-primary" : "text-text-secondary"
                                            )}>
                                                {notification.title}
                                            </h3>
                                            <span className="text-xs text-text-muted flex-shrink-0">
                                                {formatDate(notification.createdAt)}
                                            </span>
                                        </div>
                                        <p className="text-sm text-text-muted mt-1">{notification.message}</p>
                                    </div>

                                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                        {!notification.isRead && (
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    handleMarkAsRead(notification.id);
                                                }}
                                                className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                                                title="Отметить как прочитанное"
                                            >
                                                <Check className="w-4 h-4" />
                                            </button>
                                        )}
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handleDelete(notification.id);
                                            }}
                                            className="p-2 hover:bg-danger-bright/10 rounded-lg transition-colors text-text-secondary hover:text-danger-bright"
                                            title="Удалить"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </CloudSyncCard>

            {/* Delete All Confirmation Dialog */}
            {showDeleteAllConfirm && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <CloudSyncCard className="max-w-md w-full mx-4">
                        <div className="p-6">
                            <div className="flex items-center gap-3 mb-4">
                                <div className="w-12 h-12 rounded-full bg-danger-bright/20 flex items-center justify-center">
                                    <AlertCircle className="w-6 h-6 text-danger-bright" />
                                </div>
                                <h2 className="text-xl font-bold text-text-primary">Удалить все уведомления?</h2>
                            </div>
                            <p className="text-text-muted mb-6">
                                Это действие нельзя отменить. Все уведомления будут удалены безвозвратно.
                            </p>
                            <div className="flex gap-3 justify-end">
                                <button
                                    onClick={() => setShowDeleteAllConfirm(false)}
                                    className="px-4 py-2 text-sm text-text-secondary hover:bg-surface-1 rounded-lg transition-colors"
                                >
                                    Отмена
                                </button>
                                <button
                                    onClick={confirmDeleteAll}
                                    className="px-4 py-2 text-sm bg-danger-bright text-white hover:bg-danger-bright/90 rounded-lg transition-colors"
                                >
                                    Удалить все
                                </button>
                            </div>
                        </div>
                    </CloudSyncCard>
                </div>
            )}
        </div>
    );
}
