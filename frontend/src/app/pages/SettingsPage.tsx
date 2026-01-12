import React from 'react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncInput } from '../components/CloudSyncInput';
import { CloudSyncButton } from '../components/CloudSyncButton';

export function SettingsPage() {
  return (
    <div className="p-8">
      <h1 className="text-text-primary mb-6">Настройки</h1>

      <div className="space-y-6 max-w-2xl">
        {/* Profile Settings */}
        <CloudSyncCard>
          <div className="p-6">
            <h3 className="text-text-primary mb-4">Профиль</h3>
            <div className="space-y-4">
              <CloudSyncInput
                label="Email"
                type="email"
                defaultValue="admin@cloudsync.ru"
              />
              <CloudSyncInput
                label="Имя"
                type="text"
                defaultValue="Администратор"
              />
              <CloudSyncButton>Сохранить изменения</CloudSyncButton>
            </div>
          </div>
        </CloudSyncCard>

        {/* Password Change */}
        <CloudSyncCard>
          <div className="p-6">
            <h3 className="text-text-primary mb-4">Смена пароля</h3>
            <div className="space-y-4">
              <CloudSyncInput
                label="Текущий пароль"
                type="password"
                placeholder="Введите текущий пароль"
              />
              <CloudSyncInput
                label="Новый пароль"
                type="password"
                placeholder="Введите новый пароль"
              />
              <CloudSyncInput
                label="Подтверждение пароля"
                type="password"
                placeholder="Повторите новый пароль"
              />
              <CloudSyncButton>Изменить пароль</CloudSyncButton>
            </div>
          </div>
        </CloudSyncCard>

        {/* Storage Info */}
        <CloudSyncCard>
          <div className="p-6">
            <h3 className="text-text-primary mb-4">Хранилище</h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-text-muted">Использовано</span>
                <span className="text-text-secondary">4.5 ГБ</span>
              </div>
              <div className="flex justify-between">
                <span className="text-text-muted">Доступно</span>
                <span className="text-text-secondary">5.5 ГБ</span>
              </div>
              <div className="flex justify-between">
                <span className="text-text-muted">Всего</span>
                <span className="text-text-secondary">10 ГБ</span>
              </div>
            </div>
          </div>
        </CloudSyncCard>
      </div>
    </div>
  );
}
