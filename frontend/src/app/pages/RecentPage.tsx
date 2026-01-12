import React from 'react';
import { Upload, AlertTriangle, Check, Download, Trash } from 'lucide-react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncBadge } from '../components/CloudSyncBadge';

interface Activity {
  id: string;
  type: 'upload' | 'conflict' | 'sync' | 'download' | 'delete';
  fileName: string;
  status: 'completed' | 'error';
  timestamp: string;
  device: string;
}

const activities: Activity[] = [
  { id: '1', type: 'upload', fileName: 'Проект_2024.pdf', status: 'completed', timestamp: '10 минут назад', device: 'MacBook Pro' },
  { id: '2', type: 'conflict', fileName: 'Отчет_Q4.xlsx', status: 'error', timestamp: '25 минут назад', device: 'Windows Desktop' },
  { id: '3', type: 'sync', fileName: 'Презентация.pptx', status: 'completed', timestamp: '1 час назад', device: 'iPhone 14' },
  { id: '4', type: 'download', fileName: 'Договор.docx', status: 'completed', timestamp: '2 часа назад', device: 'iPad Air' },
  { id: '5', type: 'delete', fileName: 'Старый_файл.txt', status: 'completed', timestamp: '3 часа назад', device: 'MacBook Pro' },
  { id: '6', type: 'upload', fileName: 'Видео_инструкция.mp4', status: 'error', timestamp: '4 часа назад', device: 'MacBook Pro' },
];

const getActivityIcon = (type: string) => {
  switch (type) {
    case 'upload': return Upload;
    case 'conflict': return AlertTriangle;
    case 'sync': return Check;
    case 'download': return Download;
    case 'delete': return Trash;
    default: return Check;
  }
};

const getActivityText = (type: string, fileName: string) => {
  switch (type) {
    case 'upload': return `Загружен: ${fileName}`;
    case 'conflict': return `Конфликт: ${fileName}`;
    case 'sync': return `Синхронизирован: ${fileName}`;
    case 'download': return `Скачан: ${fileName}`;
    case 'delete': return `Удалён: ${fileName}`;
    default: return fileName;
  }
};

export function RecentPage() {
  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-text-primary mb-2">Недавняя активность</h1>
        <p className="text-sm text-text-secondary">Последние действия с файлами на всех устройствах</p>
      </div>

      <div className="space-y-4">
        {activities.map((activity) => {
          const Icon = getActivityIcon(activity.type);
          const isError = activity.status === 'error' || activity.type === 'conflict';

          return (
            <CloudSyncCard key={activity.id}>
              <div className="p-6 flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                    isError ? 'bg-danger-base/20' : 'bg-success-base/20'
                  }`}>
                    <Icon className={`w-6 h-6 ${isError ? 'text-danger-bright' : 'text-success-bright'}`} />
                  </div>
                  <div>
                    <p className="text-text-primary">{getActivityText(activity.type, activity.fileName)}</p>
                    <p className="text-sm text-text-muted mt-1">{activity.timestamp} • {activity.device}</p>
                  </div>
                </div>
                <CloudSyncBadge variant={isError ? 'error' : 'completed'}>
                  {isError ? 'Ошибка' : 'Завершено'}
                </CloudSyncBadge>
              </div>
            </CloudSyncCard>
          );
        })}
      </div>
    </div>
  );
}
