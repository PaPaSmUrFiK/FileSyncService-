import React, { useState, useEffect } from 'react';
import { Folder, FileText, Trash2, RotateCcw, Loader2, Info } from 'lucide-react';
import { CloudSyncButton } from '../components/CloudSyncButton';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { cn } from '../components/ui/utils';
import { apiService } from '../services/api';

interface TrashItem {
    id: string;
    name: string;
    size: number;
    mimeType: string;
    isFolder: boolean;
    updatedAt: string;
}

export function TrashPage() {
    const [files, setFiles] = useState<TrashItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isProcessing, setIsProcessing] = useState(false);

    const fetchTrash = async () => {
        setIsLoading(true);
        try {
            const response = await apiService.listTrash();
            setFiles(response.files);
        } catch (error) {
            console.error('Error fetching trash:', error);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchTrash();
    }, []);

    const handleRestore = async (fileId: string) => {
        setIsProcessing(true);
        try {
            await apiService.restoreFile(fileId);
            fetchTrash();
            alert('Файл восстановлен');
        } catch (error) {
            console.error('Error restoring file:', error);
            alert('Ошибка восстановления файла');
        } finally {
            setIsProcessing(false);
        }
    };

    const handleEmptyTrash = async () => {
        if (!confirm('Вы уверены, что хотите очистить корзину? Это действие нельзя отменить.')) return;

        setIsProcessing(true);
        try {
            await apiService.emptyTrash();
            setFiles([]);
            alert('Корзина очищена');
        } catch (error) {
            console.error('Error emptying trash:', error);
            alert('Ошибка очистки корзины');
        } finally {
            setIsProcessing(false);
        }
    };

    const handleDelete = async (fileId: string) => {
        if (!confirm('Вы уверены, что хотите удалить этот файл навсегда?')) return;

        setIsProcessing(true);
        try {
            await apiService.deleteFile(fileId);
            fetchTrash(); // Refresh list to remove deleted item
        } catch (error) {
            console.error('Error deleting file:', error);
            alert('Ошибка удаления файла');
        } finally {
            setIsProcessing(false);
        }
    };

    const formatSize = (bytes: number) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    const formatDate = (dateStr: string) => {
        try {
            return new Date(dateStr).toLocaleString('ru-RU', {
                day: '2-digit',
                month: 'short',
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
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-text-primary font-bold text-2xl flex items-center gap-3">
                    <Trash2 className="w-8 h-8 text-danger-bright" />
                    Корзина
                </h1>
                {files.length > 0 && (
                    <CloudSyncButton
                        variant="secondary"
                        onClick={handleEmptyTrash}
                        disabled={isProcessing}
                        className="text-danger-bright hover:bg-danger-bright/10 border-danger-bright/50"
                    >
                        <div className="flex items-center gap-2">
                            <Trash2 className="w-4 h-4" />
                            Очистить корзину
                        </div>
                    </CloudSyncButton>
                )}
            </div>

            <div className="bg-surface-2 border border-border-0 rounded-lg p-4 mb-6 flex items-start gap-3">
                <Info className="w-5 h-5 text-accent-primary shrink-0 mt-0.5" />
                <p className="text-sm text-text-secondary">
                    Файлы в корзине по-прежнему учитываются в вашей квоте хранилища.
                    Они будут удалены навсегда при очистке корзины.
                </p>
            </div>

            <CloudSyncCard className="relative min-h-[400px]">
                {isLoading ? (
                    <div className="absolute inset-0 flex items-center justify-center bg-surface-2/50 z-10">
                        <Loader2 className="w-8 h-8 text-accent-primary animate-spin" />
                    </div>
                ) : files.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-20 text-text-muted">
                        <Trash2 className="w-16 h-16 mb-4 opacity-20" />
                        <p>Корзина пуста</p>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead>
                                <tr className="bg-surface-2 border-b border-border-0">
                                    <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Название</th>
                                    <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Размер</th>
                                    <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Удалено</th>
                                    <th className="w-12"></th>
                                </tr>
                            </thead>
                            <tbody>
                                {files.map((file) => (
                                    <tr
                                        key={file.id}
                                        className="border-b border-border-0 hover:bg-accent-dark/5 transition-colors group"
                                    >
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-3">
                                                {file.isFolder ? (
                                                    <Folder className="w-5 h-5 text-accent-primary fill-accent-primary/20" />
                                                ) : (
                                                    <FileText className="w-5 h-5 text-text-muted" />
                                                )}
                                                <span className="text-text-primary">
                                                    {file.name}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-text-secondary">
                                            {file.isFolder ? '—' : formatSize(file.size)}
                                        </td>
                                        <td className="px-6 py-4 text-sm text-text-secondary">
                                            {formatDate(file.updatedAt)}
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex items-center justify-end gap-2">
                                                <button
                                                    onClick={() => handleRestore(file.id)}
                                                    disabled={isProcessing}
                                                    className="p-2 hover:bg-accent-primary/10 rounded-lg transition-colors text-accent-primary group/btn"
                                                    title="Восстановить"
                                                >
                                                    <RotateCcw className="w-5 h-5 group-hover/btn:rotate-[-45deg] transition-transform" />
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(file.id)}
                                                    disabled={isProcessing}
                                                    className="p-2 hover:bg-danger-bright/10 rounded-lg transition-colors text-danger-bright"
                                                    title="Удалить навсегда"
                                                >
                                                    <Trash2 className="w-5 h-5" />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </CloudSyncCard>
        </div>
    );
}
