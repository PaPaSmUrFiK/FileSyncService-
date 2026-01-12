import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { FileText, Download, RotateCcw, ChevronLeft, Loader2 } from 'lucide-react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncButton } from '../components/CloudSyncButton';
import { CloudSyncBadge } from '../components/CloudSyncBadge';
import { apiService } from '../services/api';

interface FileVersion {
  version: number;
  size: number;
  hash: string;
  createdAt: string;
  createdBy: string;
}

interface FileMetadata {
  id: string;
  name: string;
  path: string;
  version: number; // Current version
  size: number;
  hash: string;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export function HistoryPage() {
  const { fileId } = useParams<{ fileId: string }>();
  const navigate = useNavigate();

  const [file, setFile] = useState<FileMetadata | null>(null);
  const [versions, setVersions] = useState<FileVersion[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);

  useEffect(() => {
    if (!fileId) return;

    const fetchData = async () => {
      setIsLoading(true);
      try {
        const [fileData, versionsData] = await Promise.all([
          apiService.getFile(fileId),
          apiService.getFileVersions(fileId)
        ]);
        setFile(fileData);

        // Мапим текущую версию как элемент списка (самая свежая)
        const currentVersion: FileVersion = {
          version: fileData.version,
          size: fileData.size,
          hash: fileData.hash,
          createdAt: fileData.updatedAt || fileData.createdAt,
          createdBy: fileData.createdBy || ''
        };

        // Собираем всё вместе и убираем дубликаты
        const allVersions = [currentVersion, ...(versionsData.versions || [])];
        const uniqueVersionsMap = new Map<number, FileVersion>();
        allVersions.forEach(v => {
          uniqueVersionsMap.set(v.version, v);
        });

        const sortedVersions = Array.from(uniqueVersionsMap.values())
          .sort((a, b) => b.version - a.version);

        setVersions(sortedVersions);
      } catch (error) {
        console.error('Error fetching history:', error);
        alert('Не удалось загрузить историю версий');
        navigate('/files');
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, [fileId, navigate]);

  const handleDownload = async (version: number) => {
    if (!fileId) return;
    try {
      const { url } = await apiService.getDownloadUrl(fileId, version);
      window.open(url, '_blank');
    } catch (error) {
      console.error('Error downloading version:', error);
      alert('Ошибка при получении ссылки на скачивание');
    }
  };

  const handleRestore = async (version: number) => {
    if (!fileId || !confirm(`Восстановить версию ${version}? Это создаст новую версию файла.`)) return;

    setIsProcessing(true);
    try {
      await apiService.restoreVersion(fileId, version);

      // Refresh data
      const [fileData, versionsData] = await Promise.all([
        apiService.getFile(fileId),
        apiService.getFileVersions(fileId)
      ]);
      setFile(fileData);

      const currentV: FileVersion = {
        version: fileData.version,
        size: fileData.size,
        hash: fileData.hash,
        createdAt: fileData.updatedAt || fileData.createdAt,
        createdBy: fileData.createdBy || ''
      };

      const allV = [currentV, ...(versionsData.versions || [])];
      const uniqueVMap = new Map<number, FileVersion>();
      allV.forEach(v => uniqueVMap.set(v.version, v));

      setVersions(Array.from(uniqueVMap.values()).sort((a, b) => b.version - a.version));

      alert(`Версия ${version} успешно восстановлена`);
    } catch (error) {
      console.error('Error restoring version:', error);
      alert('Ошибка при восстановлении версии');
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

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <Loader2 className="w-8 h-8 text-accent-primary animate-spin" />
      </div>
    );
  }

  if (!file) {
    return (
      <div className="p-8 text-center text-text-muted">
        Файл не найден
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="mb-6">
        <button
          onClick={() => navigate('/files')}
          className="flex items-center gap-2 text-text-muted hover:text-text-primary transition-colors mb-4"
        >
          <ChevronLeft className="w-4 h-4" /> Назад к файлам
        </button>
        <h1 className="text-text-primary mb-2">История версий</h1>
        <p className="text-sm text-text-secondary">
          Все версии файлов с возможностью восстановления
        </p>
      </div>

      {/* Selected File */}
      <CloudSyncCard className="mb-8">
        <div className="p-6 flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-accent-dark/20 flex items-center justify-center">
            <FileText className="w-6 h-6 text-accent-primary" />
          </div>
          <div>
            <h3 className="text-text-primary text-lg font-medium">{file.name}</h3>
            <p className="text-sm text-text-muted">{file.path}</p>
          </div>
          <div className="ml-auto">
            <CloudSyncBadge variant="synced">Текущая версия: v{file.version}</CloudSyncBadge>
          </div>
        </div>
      </CloudSyncCard>

      {/* Timeline */}
      <div>
        <h3 className="text-text-secondary mb-4">Временная шкала версий</h3>
        <div className="relative">
          {/* Vertical Line */}
          <div className="absolute left-6 top-6 bottom-6 w-px bg-border-0" />

          <div className="space-y-4">
            {versions.map((version) => {
              const isCurrent = version.version === file.version;
              return (
                <CloudSyncCard key={version.version}>
                  <div className="p-6 flex items-center gap-4">
                    <div className="relative z-10">
                      <div className={`w-12 h-12 rounded-full flex items-center justify-center border-4 border-surface-0 ${isCurrent ? 'bg-accent-primary text-text-primary' : 'bg-surface-2 border-border-0 text-text-muted'
                        }`}>
                        <span className="font-bold">
                          {version.version}
                        </span>
                      </div>
                    </div>

                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="text-text-primary font-medium">Версия {version.version}</h3>
                        {isCurrent && <CloudSyncBadge variant="synced">Текущая</CloudSyncBadge>}
                      </div>
                      <p className="text-sm text-text-muted flex gap-2">
                        <span>{formatDate(version.createdAt)}</span>
                        <span>•</span>
                        <span>{formatSize(version.size)}</span>
                      </p>
                    </div>

                    <div className="flex items-center gap-2">
                      {!isCurrent && (
                        <CloudSyncButton
                          variant="secondary"
                          onClick={() => handleRestore(version.version)}
                          disabled={isProcessing}
                        >
                          <RotateCcw className="w-4 h-4 mr-2" /> Восстановить
                        </CloudSyncButton>
                      )}
                      <button
                        onClick={() => handleDownload(version.version)}
                        className="p-2 hover:bg-surface-1 rounded-lg transition-colors text-text-muted hover:text-text-primary"
                        title="Скачать эту версию"
                      >
                        <Download className="w-5 h-5" />
                      </button>
                    </div>
                  </div>
                </CloudSyncCard>
              );
            })}

            {versions.length === 0 && (
              <div className="text-center text-text-muted py-8 bg-surface-2 rounded-xl">
                История версий пуста
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
