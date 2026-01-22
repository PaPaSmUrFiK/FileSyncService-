import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Folder, FileText, Download, Edit, Trash, History, ChevronRight, Loader2, Plus, Upload, Share2, Check, X, Search } from 'lucide-react';
import { CloudSyncButton } from '../components/CloudSyncButton';

import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncInput } from '../components/CloudSyncInput';
import { cn } from '../components/ui/utils';
import { apiService } from '../services/api';

interface FileItem {
  id: string;
  name: string;
  path: string;
  size: number;
  mimeType: string;
  isFolder: boolean;
  version: number;
  updatedAt: string;
  status: 'synced' | 'uploading' | 'pending' | 'conflict' | 'error';
  uploadProgress?: number;
}

export function FilesPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchQuery, setSearchQuery] = useState('');

  const [files, setFiles] = useState<FileItem[]>([]);
  const [currentFolderId, setCurrentFolderId] = useState<string | undefined>(undefined);
  const [breadcrumbs, setBreadcrumbs] = useState<{ id?: string; name: string }[]>([{ name: 'Мои файлы' }]);
  const [isLoading, setIsLoading] = useState(true);


  // Modals
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [showCreateFolderModal, setShowCreateFolderModal] = useState(false);
  const [showShareModal, setShowShareModal] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [shareWithUserId, setShareWithUserId] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

  // File upload refs
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);

  // Update version refs
  const updateFileInputRef = useRef<HTMLInputElement>(null);
  const [fileToUpdate, setFileToUpdate] = useState<FileItem | null>(null);
  const [fileToShare, setFileToShare] = useState<FileItem | null>(null);

  // Renaming state
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');

  const handleRenameClick = (file: FileItem) => {
    setRenamingId(file.id);
    setRenameValue(file.name);
  };

  const handleRenameSubmit = async () => {
    if (!renamingId || !renameValue.trim()) return;

    const file = files.find(f => f.id === renamingId);
    if (!file) return;

    if (file.name === renameValue) {
      setRenamingId(null);
      return;
    }

    try {
      if (file.isFolder) {
        await apiService.updateFolder(file.id, { name: renameValue });
      } else {
        await apiService.updateFile(file.id, { name: renameValue });
      }
      fetchFiles();
    } catch (error) {
      console.error('Error renaming:', error);
      alert('Ошибка при переименовании');
    } finally {
      setRenamingId(null);
    }
  };

  const handleShare = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fileToShare || !shareWithUserId.trim()) return;

    setIsProcessing(true);
    try {
      await apiService.shareFile(fileToShare.id, shareWithUserId);
      setShowShareModal(false);
      setShareWithUserId('');
      setFileToShare(null);
      alert('Файл успешно расшарен!');
    } catch (error: any) {
      console.error('Error sharing file:', error);
      alert('Ошибка при предоставлении доступа: ' + error.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const fetchFiles = async () => {
    setIsLoading(true);
    try {
      const response = await apiService.listFiles({
        parentFolderId: currentFolderId,
        limit: 100
      });

      const mappedFiles: FileItem[] = response.files.map((f: any) => ({
        ...f,
        status: 'synced' // По умолчанию считаем синхронизированными
      }));

      setFiles(mappedFiles);
    } catch (error) {
      console.error('Error fetching files:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchFiles();
  }, [currentFolderId]);

  const handleFolderClick = (folder: FileItem) => {
    setCurrentFolderId(folder.id);
    setBreadcrumbs([...breadcrumbs, { id: folder.id, name: folder.name }]);
  };

  const handleBreadcrumbClick = (index: number) => {
    const item = breadcrumbs[index];
    setCurrentFolderId(item.id);
    setBreadcrumbs(breadcrumbs.slice(0, index + 1));
  };

  const handleCreateFolder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;

    setIsProcessing(true);
    try {
      await apiService.createFolder({
        name: newFolderName,
        path: breadcrumbs.map(b => b.name).join('/') + '/' + newFolderName,
        parentFolderId: currentFolderId
      });
      setShowCreateFolderModal(false);
      setNewFolderName('');
      fetchFiles();
    } catch (error) {
      console.error('Error creating folder:', error);
      alert('Ошибка при создании папки');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleDelete = async (fileId: string, isFolder: boolean) => {
    if (!confirm(`Вы уверены, что хотите удалить ${isFolder ? 'папку' : 'файл'}?`)) return;

    try {
      if (isFolder) {
        await apiService.deleteFolder(fileId);
      } else {
        await apiService.deleteFile(fileId);
      }
      fetchFiles();
    } catch (error) {
      console.error('Error deleting:', error);
      alert('Ошибка при удалении');
    }
  };

  const handleDownload = async (file: FileItem) => {
    try {
      const { url } = await apiService.getDownloadUrl(file.id);
      window.open(url, '_blank');
    } catch (error) {
      console.error('Error getting download URL:', error);
      alert('Ошибка при получении ссылки на скачивание');
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      setSelectedFiles(Array.from(e.target.files));
    }
  };

  const handleUpload = async () => {
    if (selectedFiles.length === 0) return;

    setIsProcessing(true);
    try {
      for (const file of selectedFiles) {
        const fileMetadata = await apiService.createFile({
          name: file.name,
          path: breadcrumbs.map(b => b.name).join('/') + '/' + file.name,
          size: file.size,
          mimeType: file.type || 'application/octet-stream',
          parentFolderId: currentFolderId
        });

        let uploadInfo = fileMetadata.uploadUrl ? { url: fileMetadata.uploadUrl, method: 'PUT', headers: {} } : null;
        if (!uploadInfo) {
          uploadInfo = await apiService.getUploadUrl({
            fileId: fileMetadata.id,
            fileName: file.name,
            fileSize: file.size,
            mimeType: file.type,
            version: fileMetadata.version
          });
        }

        const response = await fetch(uploadInfo.url, {
          method: uploadInfo.method,
          body: file,
          headers: {
            ...uploadInfo.headers || {},
            'Content-Type': '',
          }
        });

        if (!response.ok) throw new Error('Upload failed');

        await apiService.confirmUpload(fileMetadata.id, fileMetadata.version, "");
      }

      setShowUploadModal(false);
      setSelectedFiles([]);
      fetchFiles();
    } catch (error) {
      console.error('Upload error:', error);
      alert('Ошибка при загрузке файлов');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleUpdateVersionClick = (file: FileItem) => {
    setFileToUpdate(file);
    updateFileInputRef.current?.click();
  };

  const handleFileUpdateSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files || !e.target.files[0] || !fileToUpdate) return;
    const file = e.target.files[0];

    // Reset inputs
    e.target.value = '';

    setIsProcessing(true);
    try {
      // 1. Обновляем метаданные и получаем (возможно) новый uploadUrl
      const updatedMetadata = await apiService.updateFile(fileToUpdate.id, {
        size: file.size,
        // Если нужно хеш, его тоже тут (обычно считается на клиенте, если есть силы)
      });

      // 2. Если сервис вернул uploadUrl, значит нужно грузить
      // Примечание: apiService.updateFile может не возвращать uploadUrl напрямую в типе, 
      // но мы знаем, что бэкенд возвращает FileMetadata, где он есть.
      // Используем приведение типа или проверку.
      // В текущей реализации api.ts updateFile возвращает Promise<any> (или FileMetadata).

      let uploadInfo = updatedMetadata.uploadUrl ? { url: updatedMetadata.uploadUrl, method: 'PUT', headers: {} } : null;

      // Если URL не пришел, запросим явно для НОВОЙ версии
      if (!uploadInfo) {
        uploadInfo = await apiService.getUploadUrl({
          fileId: fileToUpdate.id,
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type || 'application/octet-stream',
          version: updatedMetadata.version
        });
      }

      // 3. Загружаем в MinIO
      const response = await fetch(uploadInfo.url, {
        method: uploadInfo.method,
        body: file,
        headers: {
          ...uploadInfo.headers || {},
          'Content-Type': '',
        }
      });

      if (!response.ok) throw new Error('Update upload failed');

      // 4. Подтверждаем
      await apiService.confirmUpload(fileToUpdate.id, updatedMetadata.version, "");

      setFileToUpdate(null);
      fetchFiles();
      alert('Версия файла обновлена!');

    } catch (error) {
      console.error('Update version error:', error);
      alert('Ошибка при обновлении версии файла');
    } finally {
      setIsProcessing(false);
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 Б';
    const k = 1024;
    const sizes = ['Б', 'КБ', 'МБ', 'ГБ'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
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

  // Filter files locally based on search query
  const filteredFiles = files.filter(file =>
    file.name && file.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="p-8">
      {/* Hidden input for version updates */}
      <input
        type="file"
        className="hidden"
        ref={updateFileInputRef}
        onChange={handleFileUpdateSelect}
      />

      {/* Breadcrumbs */}
      <div className="flex items-center gap-2 text-sm text-text-muted mb-4 overflow-x-auto pb-2">
        {breadcrumbs.map((crumb, index) => (
          <React.Fragment key={index}>
            {index > 0 && <ChevronRight className="w-4 h-4 flex-shrink-0" />}
            <button
              onClick={() => handleBreadcrumbClick(index)}
              className={cn(
                "hover:text-accent-primary transition-colors whitespace-nowrap",
                index === breadcrumbs.length - 1 && "text-text-primary font-medium"
              )}
            >
              {crumb.name}
            </button>
          </React.Fragment>
        ))}
      </div>

      {/* Header */}
      <div className="flex items-center justify-between mb-6 gap-4">
        <h1 className="text-text-primary whitespace-nowrap">Файлы</h1>

        {/* Search Input */}
        <div className="flex-1 max-w-md relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
          <input
            type="text"
            placeholder="Поиск файлов..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full h-10 pl-10 pr-4 rounded-xl bg-surface-1 border border-border-0 text-text-secondary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent-primary"
          />
        </div>

        <div className="flex gap-3">
          <CloudSyncButton onClick={() => setShowUploadModal(true)}>
            <div className="flex items-center gap-2">
              <Upload className="w-4 h-4" />
              Загрузить
            </div>
          </CloudSyncButton>
          <CloudSyncButton variant="secondary" onClick={() => setShowCreateFolderModal(true)}>
            <div className="flex items-center gap-2">
              <Plus className="w-4 h-4" />
              Создать папку
            </div>
          </CloudSyncButton>
        </div>
      </div>

      {/* Files Table */}
      <CloudSyncCard className="relative min-h-[400px]">
        {isLoading ? (
          <div className="absolute inset-0 flex items-center justify-center bg-surface-2/50 z-10">
            <Loader2 className="w-8 h-8 text-accent-primary animate-spin" />
          </div>
        ) : filteredFiles.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-text-muted">
            <Folder className="w-16 h-16 mb-4 opacity-20" />
            <p>{searchQuery ? 'Ничего не найдено' : 'В этой папке пока ничего нет'}</p>
            {searchQuery && (
              <p className="text-sm text-text-muted mt-2">Попробуйте изменить поисковый запрос</p>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-surface-2 border-b border-border-0">
                  <th className="w-10"></th>
                  <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Имя</th>
                  <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Размер</th>
                  <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Изменён</th>
                  <th className="text-left px-6 py-4 text-xs text-text-muted font-medium uppercase tracking-wider">Версия</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {breadcrumbs.length > 1 && (
                  <tr
                    className="border-b border-border-0 hover:bg-accent-dark/5 transition-colors cursor-pointer"
                    onClick={() => handleBreadcrumbClick(breadcrumbs.length - 2)}
                  >
                    <td className="px-6 py-4 w-10 text-center">
                      <Folder className="w-5 h-5 text-accent-primary/70" />
                    </td>
                    <td className="px-6 py-4 text-text-primary font-medium">
                      ..
                    </td>
                    <td className="px-6 py-4"></td>
                    <td className="px-6 py-4"></td>
                    <td className="px-6 py-4"></td>
                    <td className="px-6 py-4"></td>
                  </tr>
                )}
                {filteredFiles.map((file) => (
                  <tr
                    key={file.id}
                    className="border-b border-border-0 hover:bg-accent-dark/5 transition-colors group"
                  >
                    <td className="px-6 py-4 w-10 text-center">
                      {file.isFolder ? (
                        <Folder className="w-5 h-5 text-accent-primary fill-accent-primary/20" />
                      ) : (
                        <FileText className="w-5 h-5 text-text-muted" />
                      )}
                    </td>
                    <td className="px-6 py-4">
                      {renamingId === file.id ? (
                        <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                          <input
                            type="text"
                            value={renameValue}
                            onChange={(e) => setRenameValue(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') handleRenameSubmit();
                              if (e.key === 'Escape') setRenamingId(null);
                            }}
                            autoFocus
                            className="bg-surface-1 border border-border-0 rounded px-2 py-1 text-text-primary text-sm w-full outline-none focus:ring-2 focus:ring-accent-primary"
                          />
                          <button onClick={handleRenameSubmit} className="p-1 hover:text-accent-primary"><Check className="w-4 h-4" /></button>
                          <button onClick={() => setRenamingId(null)} className="p-1 hover:text-danger-bright"><X className="w-4 h-4" /></button>
                        </div>
                      ) : (
                        <div className="flex items-center gap-3 cursor-pointer" onClick={() => file.isFolder && handleFolderClick(file)}>
                          {file.isFolder ? (
                            <Folder className="w-5 h-5 text-accent-primary fill-accent-primary/20" />
                          ) : (
                            <FileText className="w-5 h-5 text-text-muted" />
                          )}
                          <span className="text-text-primary group-hover:text-accent-primary transition-colors">
                            {file.name}
                          </span>
                        </div>
                      )}
                    </td>
                    <td className="px-6 py-4 text-sm text-text-secondary">
                      {file.isFolder ? '—' : formatSize(file.size)}
                    </td>
                    <td className="px-6 py-4 text-sm text-text-secondary">
                      {formatDate(file.updatedAt)}
                    </td>
                    <td className="px-6 py-4 text-sm text-text-secondary">
                      {file.isFolder ? '—' : `v${file.version}`}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        {!file.isFolder && (
                          <>
                            <button
                              onClick={(e) => { e.stopPropagation(); navigate(`/history/${file.id}`); }}
                              className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                              title="История версий"
                            >
                              <History className="w-4 h-4" />
                            </button>
                            <button
                              onClick={(e) => { e.stopPropagation(); handleDownload(file); }}
                              className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                              title="Скачать"
                            >
                              <Download className="w-4 h-4" />
                            </button>
                            <button
                              onClick={(e) => { e.stopPropagation(); handleUpdateVersionClick(file); }}
                              className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                              title="Загрузить новую версию"
                            >
                              <Upload className="w-4 h-4" />
                            </button>
                          </>
                        )}
                        <button
                          onClick={(e) => { e.stopPropagation(); setFileToShare(file); setShowShareModal(true); }}
                          className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                          title="Поделиться"
                        >
                          <Share2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={(e) => { e.stopPropagation(); handleRenameClick(file); }}
                          className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-secondary hover:text-accent-primary"
                          title="Переименовать"
                        >
                          <Edit className="w-4 h-4" />
                        </button>
                        <button
                          onClick={(e) => { e.stopPropagation(); handleDelete(file.id, file.isFolder); }}
                          className="p-2 hover:bg-danger-bright/10 rounded-lg transition-colors text-text-secondary hover:text-danger-bright"
                          title="Удалить"
                        >
                          <Trash className="w-4 h-4" />
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

      {/* Upload Modal */}
      {showUploadModal && (
        <div className="fixed inset-0 bg-bg-0/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-[24px] bg-surface-2 border border-border-0 p-8 shadow-2xl">
            <h3 className="text-xl font-semibold text-text-primary mb-6">Загрузить файлы</h3>

            <div className="mb-8">
              <input
                type="file"
                multiple
                className="hidden"
                ref={fileInputRef}
                onChange={handleFileSelect}
              />
              <div
                onClick={() => fileInputRef.current?.click()}
                className="border-2 border-dashed border-border-0 rounded-2xl p-10 flex flex-col items-center justify-center cursor-pointer hover:border-accent-primary hover:bg-accent-primary/5 transition-all group"
              >
                <Upload className="w-12 h-12 text-text-muted group-hover:text-accent-primary mb-4 transition-colors" />
                <p className="text-text-primary font-medium">Нажмите для выбора</p>
                <p className="text-sm text-text-muted mt-1">или перетащите файлы сюда</p>
              </div>

              {selectedFiles.length > 0 && (
                <div className="mt-4 max-h-32 overflow-y-auto space-y-2">
                  {selectedFiles.map((f, i) => (
                    <div key={i} className="flex justify-between items-center text-sm p-2 bg-surface-1 rounded-lg">
                      <span className="truncate flex-1 pr-4">{f.name}</span>
                      <span className="text-text-muted shrink-0">{formatSize(f.size)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex gap-3">
              <CloudSyncButton
                variant="secondary"
                className="flex-1"
                onClick={() => { setShowUploadModal(false); setSelectedFiles([]); }}
                disabled={isProcessing}
              >
                Отмена
              </CloudSyncButton>
              <CloudSyncButton
                className="flex-1"
                onClick={handleUpload}
                disabled={selectedFiles.length === 0 || isProcessing}
              >
                {isProcessing ? <Loader2 className="w-5 h-5 animate-spin mx-auto" /> : 'Загрузить'}
              </CloudSyncButton>
            </div>
          </div>
        </div>
      )}

      {/* Create Folder Modal */}
      {showCreateFolderModal && (
        <div className="fixed inset-0 bg-bg-0/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
          <form onSubmit={handleCreateFolder} className="w-full max-w-md rounded-[24px] bg-surface-2 border border-border-0 p-8 shadow-2xl">
            <h3 className="text-xl font-semibold text-text-primary mb-6">Новая папка</h3>

            <div className="mb-8">
              <CloudSyncInput
                label="Имя папки"
                placeholder="Введите название"
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                autoFocus
                required
              />
            </div>

            <div className="flex gap-3">
              <CloudSyncButton
                type="button"
                variant="secondary"
                className="flex-1"
                onClick={() => { setShowCreateFolderModal(false); setNewFolderName(''); }}
                disabled={isProcessing}
              >
                Отмена
              </CloudSyncButton>
              <CloudSyncButton
                type="submit"
                className="flex-1"
                disabled={!newFolderName.trim() || isProcessing}
              >
                {isProcessing ? <Loader2 className="w-5 h-5 animate-spin mx-auto" /> : 'Создать'}
              </CloudSyncButton>
            </div>
          </form>
        </div>
      )}
      {/* Share Modal */}
      {showShareModal && (
        <div className="fixed inset-0 bg-bg-0/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
          <form onSubmit={handleShare} className="w-full max-w-md rounded-[24px] bg-surface-2 border border-border-0 p-8 shadow-2xl">
            <h3 className="text-xl font-semibold text-text-primary mb-2">Поделиться</h3>
            <p className="text-sm text-text-muted mb-6">Предоставить доступ к файлу "{fileToShare?.name}"</p>

            <div className="mb-8">
              <CloudSyncInput
                label="ID пользователя или Email"
                placeholder="Введите ID пользователя"
                value={shareWithUserId}
                onChange={(e) => setShareWithUserId(e.target.value)}
                autoFocus
                required
              />
              <p className="text-[10px] text-text-muted mt-2 px-1">
                Для предоставления доступа введите UUID пользователя, с которым хотите поделиться файлом.
              </p>
            </div>

            <div className="flex gap-3">
              <CloudSyncButton
                type="button"
                variant="secondary"
                className="flex-1"
                onClick={() => { setShowShareModal(false); setShareWithUserId(''); }}
                disabled={isProcessing}
              >
                Отмена
              </CloudSyncButton>
              <CloudSyncButton
                type="submit"
                className="flex-1"
                disabled={!shareWithUserId.trim() || isProcessing}
              >
                {isProcessing ? <Loader2 className="w-5 h-5 animate-spin mx-auto" /> : 'Поделиться'}
              </CloudSyncButton>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
