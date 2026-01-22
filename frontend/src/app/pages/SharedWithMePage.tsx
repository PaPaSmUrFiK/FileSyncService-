import React, { useState, useEffect } from 'react';
import {
    Folder,
    FileText,
    Share2,
    Loader2,
    Download,
    Calendar,
    User,
    HardDrive,
    Search,
    Trash2
} from 'lucide-react';
import { apiService } from '../services/api';
import { CloudSyncButton } from '../components/CloudSyncButton';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { cn } from '../components/ui/utils';

export const SharedWithMePage: React.FC = () => {
    const [files, setFiles] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const fetchSharedFiles = async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await apiService.listSharedWithMe();
            // Map backend response to ensure all fields are present
            const mappedFiles = response.shares.map(share => ({
                ...share,
                id: share.shareId, // Use shareId as primary key for list
                name: share.fileName || share.name, // Handle backend using fileName
                originalFileId: share.fileId
            }));
            setFiles(mappedFiles);
        } catch (err: any) {
            console.error('Failed to fetch shared files:', err);
            setError('Failed to load shared files. Please try again later.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchSharedFiles();
    }, []);

    const filteredFiles = files.filter(file =>
        (file.name && file.name.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (file.ownerEmail && file.ownerEmail.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (file.ownerName && file.ownerName.toLowerCase().includes(searchQuery.toLowerCase()))
    );

    const formatSize = (bytes: number) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    const formatDate = (dateString: string) => {
        if (!dateString) return 'Unknown';
        return new Date(dateString).toLocaleString();
    };

    const handleDownload = (file: any) => {
        if (file.downloadUrl) {
            window.open(file.downloadUrl, '_blank');
        }
    };

    const handleRemoveShare = async (file: any) => {
        if (!confirm(`Are you sure you want to remove "${file.name}" from your shared files?`)) {
            return;
        }

        try {
            await apiService.revokeShare(file.shareId);
            // Refresh list
            fetchSharedFiles();
        } catch (err) {
            console.error('Failed to remove share:', err);
            alert('Failed to remove shared file');
        }
    };

    return (
        <div className="p-8 max-w-7xl mx-auto space-y-8 animate-in fade-in duration-500">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-400 to-indigo-500 bg-clip-text text-transparent">
                        Shared With Me
                    </h1>
                    <p className="text-slate-400 mt-2">
                        Files and folders shared with you by other users.
                    </p>
                </div>

                <div className="flex gap-4 flex-1 justify-end items-center">
                    {/* Search Input */}
                    <div className="relative max-w-md w-full">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                        <input
                            type="text"
                            placeholder="Search shared files..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full h-10 pl-10 pr-4 rounded-xl bg-slate-900/50 border border-slate-700 text-slate-200 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500/50"
                        />
                    </div>

                    <CloudSyncButton
                        variant="secondary"
                        onClick={fetchSharedFiles}
                        disabled={loading}
                    >
                        <div className="flex items-center gap-2">
                            {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Share2 className="w-4 h-4" />}
                            Refresh
                        </div>
                    </CloudSyncButton>
                </div>
            </div>

            {error && (
                <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
                    {error}
                </div>
            )}

            {loading ? (
                <div className="flex flex-col items-center justify-center py-20 space-y-4">
                    <Loader2 className="w-12 h-12 text-blue-500 animate-spin" />
                    <p className="text-slate-400 animate-pulse">Loading shared content...</p>
                </div>
            ) : filteredFiles.length === 0 ? (
                <CloudSyncCard className="flex flex-col items-center justify-center py-20 text-center space-y-4 bg-slate-900/50 border-slate-800">
                    <div className="w-20 h-20 rounded-full bg-slate-800 flex items-center justify-center">
                        <Share2 className="w-10 h-10 text-slate-500" />
                    </div>
                    <div>
                        <h3 className="text-xl font-medium text-slate-200">
                            {searchQuery ? 'No matches found' : 'No shared files found'}
                        </h3>
                        <p className="text-slate-500 max-w-xs mx-auto mt-2">
                            {searchQuery ? 'Try adjusting your search terms.' : 'Any files shared with you by others will appear here.'}
                        </p>
                    </div>
                </CloudSyncCard>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {filteredFiles.map((file) => (
                        <CloudSyncCard
                            key={file.id}
                            className="group relative flex flex-col p-5 bg-slate-900/40 border-slate-800 hover:border-blue-500/50 transition-all duration-300 hover:shadow-lg hover:shadow-blue-500/10"
                        >
                            <div className="flex items-start justify-between mb-4">
                                <div className={cn(
                                    "p-3 rounded-xl",
                                    file.isFolder ? "bg-blue-500/10 text-blue-400" : "bg-indigo-500/10 text-indigo-400"
                                )}>
                                    {file.isFolder ? <Folder className="w-6 h-6" /> : <FileText className="w-6 h-6" />}
                                </div>
                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={() => handleRemoveShare(file)}
                                        className="p-2 rounded-lg bg-slate-800 text-red-500 hover:text-red-400 hover:bg-red-500/20 transition-colors"
                                        title="Remove from Shared"
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </button>
                                    {!file.isFolder && (
                                        <button
                                            onClick={() => handleDownload(file)}
                                            className="p-2 rounded-lg bg-slate-800 text-slate-400 hover:text-white hover:bg-slate-700 transition-colors"
                                            title="Download"
                                        >
                                            <Download className="w-4 h-4" />
                                        </button>
                                    )}
                                </div>
                            </div>

                            <div className="space-y-1 mb-4">
                                <h3 className="font-semibold text-slate-100 truncate text-lg group-hover:text-blue-400 transition-colors">
                                    {file.name}
                                </h3>
                                <p className="text-xs text-slate-500 font-mono truncate">
                                    {file.path || '/'}
                                </p>
                            </div>

                            <div className="mt-auto pt-4 border-t border-slate-800/50 grid grid-cols-2 gap-y-3">
                                <div className="flex items-center gap-2 text-xs text-slate-400">
                                    <HardDrive className="w-3.5 h-3.5 text-slate-500" />
                                    <span>{file.isFolder ? '--' : formatSize(file.size)}</span>
                                </div>
                                <div className="flex items-center gap-2 text-xs text-slate-400">
                                    <User className="w-3.5 h-3.5 text-slate-500" />
                                    <span className="truncate" title={file.ownerEmail || file.ownerName || ''}>
                                        {file.ownerEmail || file.ownerName}
                                    </span>
                                </div>
                                <div className="flex items-center gap-2 text-xs text-slate-400 col-span-2">
                                    <Calendar className="w-3.5 h-3.5 text-slate-500" />
                                    <span>Shared: {formatDate(file.createdAt)}</span>
                                </div>
                            </div>
                        </CloudSyncCard>
                    ))}
                </div>
            )}
        </div>
    );
};
