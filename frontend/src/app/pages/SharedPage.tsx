import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Share2, FileText, Folder, MoreVertical, Trash2, User, Loader2, Search, History, Download, Upload } from 'lucide-react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncBadge } from '../components/CloudSyncBadge';
import { apiService } from '../services/api';
import { cn } from '../components/ui/utils';

export function SharedPage() {
    const navigate = useNavigate();
    const [sharesWithMe, setSharesWithMe] = useState<any[]>([]);
    const [myShares, setMyShares] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<'with-me' | 'by-me'>('with-me');
    const [openMenuId, setOpenMenuId] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const fetchData = async () => {
        setIsLoading(true);
        try {
            if (activeTab === 'with-me') {
                const response = await apiService.listSharedWithMe();
                setSharesWithMe(response.shares || []);
            } else {
                const response = await apiService.listMyShares();
                setMyShares(response.shares || []);
            }
        } catch (error) {
            console.error('Error fetching shares:', error);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, [activeTab]);

    const handleRevokeShare = async (shareId: string) => {
        if (!confirm('Вы уверены, что хотите отозвать доступ?')) return;
        try {
            await apiService.revokeShare(shareId);
            fetchData();
        } catch (error) {
            console.error('Error revoking share:', error);
            alert('Ошибка при отзыве доступа');
        }
    };

    const formatDate = (dateStr: string) => {
        try {
            return new Date(dateStr).toLocaleString('ru-RU', {
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

    const handleDownload = async (fileId: string) => {
        try {
            const { url } = await apiService.getDownloadUrl(fileId);
            window.open(url, '_blank');
        } catch (error) {
            console.error('Error downloading file:', error);
            alert('Ошибка скачивания файла');
        }
    };

    const handleUploadNewVersion = async (e: React.ChangeEvent<HTMLInputElement>, share: any) => {
        const file = e.target.files?.[0];
        if (!file) return;

        if (!confirm(`Загрузить новую версию файла "${share.fileName}"?`)) {
            e.target.value = '';
            return;
        }

        try {
            // 1. Notify backend about file update (triggers version increment in FileService)
            // We pass size so FileService knows content changed and increments version
            const updatedMetadata = await apiService.updateFile(share.fileId, {
                size: file.size
            });

            // 2. Get upload URL for the new version
            // FileService might have returned one, or we request it explicitly
            let uploadUrl = updatedMetadata.uploadUrl;
            let method = 'PUT';
            let headers = {};

            if (!uploadUrl) {
                const uploadParams = await apiService.getUploadUrl({
                    fileId: share.fileId,
                    fileName: file.name,
                    fileSize: file.size,
                    mimeType: file.type || 'application/octet-stream',
                    version: updatedMetadata.version
                });
                uploadUrl = uploadParams.url;
                method = uploadParams.method;
                headers = uploadParams.headers;
            }

            // 3. Upload file
            const uploadResponse = await fetch(uploadUrl, {
                method: method,
                body: file,
                headers: headers || {}
            });

            if (!uploadResponse.ok) throw new Error('Upload failed');

            // 4. Confirm upload
            const etag = uploadResponse.headers.get('ETag')?.replace(/"/g, '') || '';
            await apiService.confirmUpload(share.fileId, updatedMetadata.version, etag);

            alert('Новая версия загружена успешно');
            fetchData();
        } catch (error: any) {
            console.error('Error uploading version:', error);
            alert('Ошибка загрузки новой версии: ' + (error.message || 'Unknown error'));
        } finally {
            e.target.value = '';
        }
    };

    const currentList = activeTab === 'with-me' ? sharesWithMe : myShares;
    const filteredList = currentList.filter(share =>
        (share.fileName && share.fileName.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (share.ownerName && share.ownerName.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (share.ownerEmail && share.ownerEmail.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (share.sharedWithEmail && share.sharedWithEmail.toLowerCase().includes(searchQuery.toLowerCase()))
    );

    return (
        <div className="p-8">
            <div className="flex items-center justify-between mb-8 gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-text-primary">Общий доступ</h1>
                    <p className="text-text-muted mt-1">Управление общими файлами и папками</p>
                </div>

                {/* Search Input */}
                <div className="flex-1 max-w-md relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                    <input
                        type="text"
                        placeholder="Поиск..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full h-10 pl-10 pr-4 rounded-xl bg-surface-1 border border-border-0 text-text-secondary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent-primary"
                    />
                </div>
            </div>

            {/* Tabs */}
            <div className="flex gap-4 mb-6 border-b border-border-0">
                <button
                    onClick={() => setActiveTab('with-me')}
                    className={cn(
                        "pb-3 px-4 text-sm font-medium transition-colors relative",
                        activeTab === 'with-me' ? "text-accent-primary" : "text-text-muted hover:text-text-secondary"
                    )}
                >
                    Доступные мне
                    {activeTab === 'with-me' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-accent-primary" />}
                </button>
                <button
                    onClick={() => setActiveTab('by-me')}
                    className={cn(
                        "pb-3 px-4 text-sm font-medium transition-colors relative",
                        activeTab === 'by-me' ? "text-accent-primary" : "text-text-muted hover:text-text-secondary"
                    )}
                >
                    Общие со мной
                    {activeTab === 'by-me' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-accent-primary" />}
                </button>
            </div>

            <CloudSyncCard className="min-h-[400px] relative">
                {isLoading ? (
                    <div className="absolute inset-0 flex items-center justify-center bg-surface-2/50 z-10">
                        <Loader2 className="w-8 h-8 text-accent-primary animate-spin" />
                    </div>
                ) : filteredList.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-20 text-text-muted">
                        <Share2 className="w-16 h-16 mb-4 opacity-20" />
                        <p>{searchQuery ? 'Ничего не найдено' : 'Нет файлов в общем доступе'}</p>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead>
                                <tr className="bg-surface-2 border-b border-border-0">
                                    <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Имя</th>
                                    <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">
                                        {activeTab === 'with-me' ? 'Владелец' : 'Общий с'}
                                    </th>
                                    <th className="text-right px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Действия</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredList.map((share) => (
                                    <tr key={share.shareId} className="border-b border-border-0 hover:bg-accent-dark/5 transition-colors group">
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-3">
                                                <FileText className="w-5 h-5 text-text-muted" />
                                                <div className="flex flex-col">
                                                    <span className="text-text-primary">{share.fileName || 'Файл'}</span>
                                                    <span className="text-xs text-text-muted">
                                                        {formatDate(share.createdAt)}
                                                    </span>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-2">
                                                <div className="w-8 h-8 rounded-full bg-accent-primary/10 flex items-center justify-center">
                                                    <User className="w-4 h-4 text-accent-primary" />
                                                </div>
                                                <div className="flex flex-col">
                                                    <span className="text-sm text-text-primary">
                                                        {activeTab === 'with-me'
                                                            ? (share.ownerEmail || share.ownerName)
                                                            : (share.sharedWithEmail || share.sharedWithName)}
                                                    </span>
                                                    {activeTab === 'with-me' && share.ownerName && share.ownerEmail && (
                                                        <span className="text-xs text-text-muted">{share.ownerName}</span>
                                                    )}
                                                    {activeTab === 'by-me' && share.sharedWithName && share.sharedWithEmail && (
                                                        <span className="text-xs text-text-muted">{share.sharedWithName}</span>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex gap-2 justify-end">
                                                {activeTab === 'with-me' && (
                                                    <>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); navigate(`/history/${share.fileId}`); }}
                                                            className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                                                            title="История версий"
                                                        >
                                                            <History className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); handleDownload(share.fileId); }}
                                                            className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                                                            title="Скачать"
                                                        >
                                                            <Download className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); document.getElementById(`upload-${share.shareId}`)?.click(); }}
                                                            className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                                                            title="Загрузить новую версию"
                                                        >
                                                            <Upload className="w-4 h-4" />
                                                            <input
                                                                id={`upload-${share.shareId}`}
                                                                type="file"
                                                                className="hidden"
                                                                onChange={(e) => handleUploadNewVersion(e, share)}
                                                            />
                                                        </button>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); handleRevokeShare(share.shareId); }}
                                                            className="p-2 hover:bg-danger-bright/10 rounded-lg transition-colors text-text-secondary hover:text-danger-bright"
                                                            title="Удалить из доступных"
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
                                                    </>
                                                )}
                                                {activeTab === 'by-me' && (
                                                    <button
                                                        onClick={(e) => { e.stopPropagation(); handleRevokeShare(share.shareId); }}
                                                        className="p-2 hover:bg-danger-bright/10 rounded-lg transition-colors text-text-secondary hover:text-danger-bright"
                                                        title="Отозвать доступ"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                )}
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
