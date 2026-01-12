import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncButton } from '../components/CloudSyncButton';

interface Conflict {
  id: string;
  fileName: string;
  path: string;
  description: string;
  timestamp: string;
  device: string;
}

const conflicts: Conflict[] = [
  {
    id: '1',
    fileName: 'Отчет_Q4.xlsx',
    path: '/Документы/Отчеты/',
    description: 'Файл был изменен на двух устройствах одновременно',
    timestamp: '10 минут назад',
    device: 'Windows Desktop',
  },
  {
    id: '2',
    fileName: 'Презентация.pptx',
    path: '/Документы/Проекты/',
    description: 'Обнаружены различные версии файла',
    timestamp: '1 час назад',
    device: 'MacBook Pro',
  },
];

export function ConflictsPage() {
  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-text-primary mb-2">Конфликты</h1>
        <p className="text-sm text-text-secondary">
          Обнаружено {conflicts.length} конфликта, требующих решения
        </p>
      </div>

      <div className="space-y-4">
        {conflicts.map((conflict) => (
          <CloudSyncCard key={conflict.id}>
            <div className="p-6">
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl bg-warning-base/20 flex items-center justify-center flex-shrink-0">
                  <AlertTriangle className="w-6 h-6 text-warning-base" />
                </div>
                <div className="flex-1">
                  <h3 className="text-text-primary mb-1">{conflict.fileName}</h3>
                  <p className="text-sm text-text-muted mb-1">{conflict.path}</p>
                  <p className="text-sm text-text-secondary mb-3">{conflict.description}</p>
                  <p className="text-xs text-text-muted">
                    {conflict.timestamp} • {conflict.device}
                  </p>
                </div>
                <CloudSyncButton>Решить</CloudSyncButton>
              </div>
            </div>
          </CloudSyncCard>
        ))}
      </div>
    </div>
  );
}
