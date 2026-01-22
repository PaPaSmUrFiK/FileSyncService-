import React, { useState, useEffect } from 'react';
import { Monitor, Tablet, Smartphone, Trash, Loader2, Plus } from 'lucide-react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncButton } from '../components/CloudSyncButton';
import { CloudSyncBadge } from '../components/CloudSyncBadge';
import { apiService } from '../services/api';

interface Device {
    id: string;
    deviceName: string;
    deviceType: string;
    os: string;
    osVersion: string;
    lastSyncAt: string;
    status: 'online' | 'offline';
}

export function DevicesPage() {
    const [devices, setDevices] = useState<Device[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    const fetchDevices = async () => {
        setIsLoading(true);
        try {
            const response = await apiService.getDevices();
            setDevices(response.devices || []);
        } catch (error) {
            console.error('Error fetching devices:', error);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchDevices();
    }, []);

    const handleUnregister = async (deviceId: string) => {
        if (!confirm('Вы уверены, что хотите отключить это устройство?')) return;

        try {
            await apiService.unregisterDevice(deviceId);
            fetchDevices();
        } catch (error) {
            console.error('Error unregistering device:', error);
            alert('Ошибка при отключении устройства');
        }
    };

    const getDeviceIcon = (type: string) => {
        switch (type?.toLowerCase()) {
            case 'desktop':
            case 'laptop':
                return <Monitor className="w-6 h-6" />;
            case 'tablet':
                return <Tablet className="w-6 h-6" />;
            case 'mobile':
            case 'phone':
                return <Smartphone className="w-6 h-6" />;
            default:
                return <Monitor className="w-6 h-6" />;
        }
    };

    return (
        <div className="p-8">
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-text-primary mb-2">Устройства</h1>
                    <p className="text-sm text-text-secondary overflow-hidden text-ellipsis whitespace-nowrap max-w-md">
                        Управляйте подключенными устройствами и сессиями синхронизации
                    </p>
                </div>
                <CloudSyncButton variant="secondary">
                    <Plus className="w-4 h-4 mr-2" /> Добавить устройство
                </CloudSyncButton>
            </div>

            {isLoading ? (
                <div className="flex items-center justify-center py-20">
                    <Loader2 className="w-8 h-8 text-accent-primary animate-spin" />
                </div>
            ) : devices.length === 0 ? (
                <CloudSyncCard className="py-20 text-center text-text-muted">
                    <div className="flex flex-col items-center">
                        <Monitor className="w-16 h-16 mb-4 opacity-20" />
                        <p className="text-lg font-medium mb-1">Нет подключенных устройств</p>
                        <p className="max-w-xs mx-auto">
                            Установите клиент CloudSync на ваш ПК или телефон, чтобы синхронизировать файлы.
                        </p>
                    </div>
                </CloudSyncCard>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {devices.map((device) => (
                        <CloudSyncCard key={device.id} className="p-6 transition-all hover:scale-[1.02] hover:shadow-xl">
                            <div className="flex items-start justify-between mb-6">
                                <div className="w-12 h-12 rounded-xl bg-accent-dark/20 flex items-center justify-center text-accent-primary">
                                    {getDeviceIcon(device.deviceType)}
                                </div>
                                <CloudSyncBadge variant={device.status === 'online' ? 'synced' : 'pending'}>
                                    {device.status === 'online' ? 'В сети' : 'Оффлайн'}
                                </CloudSyncBadge>
                            </div>

                            <div className="mb-6">
                                <h3 className="text-text-primary font-semibold text-lg mb-1">{device.deviceName}</h3>
                                <p className="text-sm text-text-muted">
                                    {device.os} {device.osVersion}
                                </p>
                            </div>

                            <div className="pt-6 border-t border-border-0 flex items-center justify-between">
                                <div className="text-[10px] uppercase tracking-wider text-text-muted">
                                    Последняя синхронизация
                                    <div className="text-sm text-text-secondary mt-0.5 normal-case font-medium">
                                        {device.lastSyncAt ? new Date(device.lastSyncAt).toLocaleString() : 'Никогда'}
                                    </div>
                                </div>
                                <button
                                    onClick={() => handleUnregister(device.id)}
                                    className="p-2 text-text-muted hover:text-danger-bright hover:bg-danger-bright/10 rounded-lg transition-all"
                                    title="Отключить устройство"
                                >
                                    <Trash className="w-5 h-5" />
                                </button>
                            </div>
                        </CloudSyncCard>
                    ))}
                </div>
            )}
        </div>
    );
}
