import React, { useEffect, useState } from 'react';
import { CloudSyncCard } from '../components/CloudSyncCard';
import { CloudSyncInput } from '../components/CloudSyncInput';
import { CloudSyncButton } from '../components/CloudSyncButton';
import { apiService } from '../services/api';
import { toast } from 'sonner';

export function SettingsPage() {
  const [profile, setProfile] = useState<{ email: string; name: string } | null>(null);
  const [storage, setStorage] = useState<{ used: number; total: number } | null>(null);
  const [loading, setLoading] = useState(true);

  // Profile Form State
  const [name, setName] = useState('');

  // Password Form State
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [userData, quotaData] = await Promise.all([
        apiService.getCurrentUser(),
        apiService.checkQuota()
      ]);

      setProfile({
        email: userData.email,
        name: userData.name
      });
      setName(userData.name || '');

      setStorage({
        used: quotaData.storageUsed || 0,
        total: quotaData.storageQuota || 0
      });
    } catch (error) {
      console.error('Failed to fetch settings:', error);
      toast.error('Не удалось загрузить настройки');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateProfile = async () => {
    if (name.length < 3) {
      toast.error('Имя должно быть не менее 3 символов');
      return;
    }

    try {
      await apiService.updateCurrentUser({ name });
      toast.success('Данные профиля успешно обновлены');
    } catch (error) {
      console.error('Failed to update profile:', error);
      toast.error('Ошибка при обновлении профиля');
    }
  };

  const handleChangePassword = async () => {
    if (newPassword !== confirmPassword) {
      toast.error('Пароли не совпадают');
      return;
    }

    if (newPassword.length < 6) {
      toast.error('Пароль должен быть не менее 6 символов');
      return;
    }

    try {
      await apiService.changePassword({ oldPassword, newPassword });
      toast.success('Пароль успешно изменен');
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error: any) {
      console.error('Failed to change password:', error);
      toast.error(error.message || 'Ошибка при смене пароля');
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  if (loading) {
    return <div className="p-8">Загрузка...</div>;
  }

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
                value={profile?.email || ''}
                readOnly
                className="opacity-60 cursor-not-allowed"
              />
              <CloudSyncInput
                label="Имя"
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <CloudSyncButton onClick={handleUpdateProfile}>Сохранить изменения</CloudSyncButton>
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
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
              />
              <CloudSyncInput
                label="Новый пароль"
                type="password"
                placeholder="Введите новый пароль"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
              <CloudSyncInput
                label="Подтверждение пароля"
                type="password"
                placeholder="Повторите новый пароль"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
              <CloudSyncButton onClick={handleChangePassword}>Изменить пароль</CloudSyncButton>
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
                <span className="text-text-secondary">{storage ? formatSize(storage.used) : '0 B'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-text-muted">Всего</span>
                <span className="text-text-secondary">{storage ? formatSize(storage.total) : '0 B'}</span>
              </div>
              {storage && (
                <div className="w-full bg-surface-1 rounded-full h-2 mt-2 overflow-hidden">
                  <div
                    className="bg-accent-primary h-2 rounded-full transition-all duration-300"
                    style={{ width: `${Math.min((storage.used / storage.total) * 100, 100)}%` }}
                  ></div>
                </div>
              )}
            </div>
          </div>
        </CloudSyncCard>
      </div>
    </div>
  );
}
