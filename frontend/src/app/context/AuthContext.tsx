import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { apiService, TokenResponse } from '../services/api';

interface AuthContextType {
  user: TokenResponse | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;
  logout: () => Promise<void>;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<TokenResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Восстанавливаем пользователя из localStorage
    const storedUser = localStorage.getItem('user');
    const accessToken = localStorage.getItem('accessToken');

    if (storedUser && accessToken) {
      try {
        setUser(JSON.parse(storedUser));
      } catch (error) {
        console.error('Error parsing stored user:', error);
        localStorage.removeItem('user');
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (email: string, password: string) => {
    const response = await apiService.login({ email, password });

    localStorage.setItem('accessToken', response.accessToken);
    localStorage.setItem('refreshToken', response.refreshToken);
    localStorage.setItem('user', JSON.stringify(response));

    setUser(response);
  };

  const register = async (email: string, password: string, name: string) => {
    const response = await apiService.register({ email, password, name });

    localStorage.setItem('accessToken', response.accessToken);
    localStorage.setItem('refreshToken', response.refreshToken);
    localStorage.setItem('user', JSON.stringify(response));

    setUser(response);
  };

  const logout = async () => {
    const refreshToken = localStorage.getItem('refreshToken');

    // Всегда очищаем локальное хранилище, даже если запрос на сервер не удался
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    setUser(null);

    // Попытка уведомить сервер о выходе (не критично, если не получится)
    if (refreshToken) {
      try {
        await apiService.logout(refreshToken);
      } catch (error) {
        // Игнорируем ошибки при logout - токены уже удалены локально
        console.warn('Logout server notification failed (tokens already cleared locally):', error);
      }
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        login,
        register,
        logout,
        isLoading,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

