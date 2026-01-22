import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Cloud } from 'lucide-react';
import { CloudSyncButton } from '../components/CloudSyncButton';
import { CloudSyncInput } from '../components/CloudSyncInput';
import { cn } from '../components/ui/utils';
import { useAuth } from '../context/AuthContext';

export function LoginPage() {
  const navigate = useNavigate();
  const { login, register, isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/files');
    }
  }, [isAuthenticated, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      if (activeTab === 'login') {
        await login(email, password);
        navigate('/files');
      } else {
        if (!name.trim() || name.length < 3) {
          setError('Имя должно быть не менее 3 символов');
          setIsLoading(false);
          return;
        }
        if (password.length < 6) {
          setError('Пароль должен быть не менее 6 символов');
          setIsLoading(false);
          return;
        }
        if (password !== confirmPassword) {
          setError('Пароли не совпадают');
          setIsLoading(false);
          return;
        }
        await register(email, password, name);
        navigate('/files');
      }
    } catch (err: any) {
      const message = err.message || '';
      if (message.includes('Invalid credentials')) {
        setError('Неверный логин или пароль');
      } else if (message.includes('PERMISSION_DENIED') || message.includes('User is blocked')) {
        // Extract reason if present (format: "PERMISSION_DENIED - reason")
        const parts = message.split(' - ');
        const reason = parts.length > 1 ? parts[1] : '';
        setError(reason ? `Ваша учетная запись заблокирована. Причина: ${reason}` : 'Ваша учетная запись заблокирована');
      } else if (message.includes('already registered')) {
        setError('Пользователь с таким email уже существует');
      } else {
        setError('Произошла ошибка при входе. Проверьте данные и попробуйте снова.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-bg-0 to-bg-1 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="rounded-[20px] bg-surface-2 border border-border-0 p-8">
          {/* Logo */}
          <div className="flex flex-col items-center mb-8">
            <Cloud className="w-16 h-16 text-accent-primary mb-4" />
            <h2 className="text-text-primary">CloudSync</h2>
            <p className="text-sm text-text-muted mt-1">Синхронизация файлов в облаке</p>
          </div>

          {/* Tabs */}
          <div className="flex gap-2 mb-6">
            <button
              onClick={() => setActiveTab('login')}
              className={cn(
                'flex-1 py-2.5 rounded-xl transition-colors',
                activeTab === 'login'
                  ? 'bg-accent-dark/40 text-text-primary'
                  : 'bg-surface-1 text-text-secondary'
              )}
            >
              Вход
            </button>
            <button
              onClick={() => setActiveTab('register')}
              className={cn(
                'flex-1 py-2.5 rounded-xl transition-colors',
                activeTab === 'register'
                  ? 'bg-accent-dark/40 text-text-primary'
                  : 'bg-surface-1 text-text-secondary'
              )}
            >
              Регистрация
            </button>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="bg-red-500/10 border border-red-500/20 text-red-400 text-sm p-3 rounded-lg">
                {error}
              </div>
            )}

            {activeTab === 'register' && (
              <CloudSyncInput
                label="Имя"
                type="text"
                placeholder="Введите ваше имя"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            )}

            <CloudSyncInput
              label="Email"
              type="email"
              placeholder="Введите email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={isLoading}
            />
            <CloudSyncInput
              label="Пароль"
              type="password"
              placeholder="Введите пароль"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={isLoading}
            />

            {activeTab === 'register' && (
              <CloudSyncInput
                label="Подтверждение пароля"
                type="password"
                placeholder="Повторите пароль"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                disabled={isLoading}
              />
            )}

            <CloudSyncButton
              type="submit"
              className="w-full mt-6"
              disabled={isLoading}
            >
              {isLoading
                ? 'Обработка...'
                : activeTab === 'login'
                  ? 'Войти'
                  : 'Зарегистрироваться'
              }
            </CloudSyncButton>

            {activeTab === 'login' && (
              <button
                type="button"
                className="w-full text-sm text-accent-primary hover:text-accent-hover transition-colors mt-4"
              >
                Забыли пароль?
              </button>
            )}
          </form>
        </div>
      </div>
    </div>
  );
}
