// @ts-ignore - Vite env types
const API_BASE_URL = import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080';

export interface LoginRequest {
  email: string;
  password: string;
  deviceInfo?: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  name: string;
  roles: string[];
}

class ApiService {
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const token = localStorage.getItem('accessToken');
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...((options.headers as Record<string, string>) || {}),
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      // Если 401 и есть refresh token, попробуем обновить токен
      if (response.status === 401 && endpoint !== '/api/v1/auth/refresh' && endpoint !== '/api/v1/auth/login') {
        const refreshToken = localStorage.getItem('refreshToken');
        if (refreshToken) {
          try {
            const newTokens = await this.refreshToken(refreshToken);

            // Сохраняем новые данные в localStorage
            localStorage.setItem('accessToken', newTokens.accessToken);
            localStorage.setItem('refreshToken', newTokens.refreshToken);

            const existingUser = JSON.parse(localStorage.getItem('user') || '{}');
            localStorage.setItem('user', JSON.stringify({
              ...existingUser,
              userId: newTokens.userId,
              email: newTokens.email,
              name: newTokens.name,
              roles: newTokens.roles
            }));

            // Повторяем запрос с новым токеном
            headers['Authorization'] = `Bearer ${newTokens.accessToken}`;
            const retryResponse = await fetch(`${this.baseUrl}${endpoint}`, {
              ...options,
              headers,
            });

            if (!retryResponse.ok) {
              const retryText = await retryResponse.text();
              const error = retryText ? JSON.parse(retryText) : { message: retryResponse.statusText };
              throw new Error(error.message || `HTTP error! status: ${retryResponse.status}`);
            }

            const retryText = await retryResponse.text();
            const retryData = retryText ? JSON.parse(retryText) : {};
            return this.fixMinioUrls(retryData);
          } catch (refreshError: any) {
            // Если обновление не удалось, очищаем токены и выбрасываем ошибку
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('user');
            throw new Error(refreshError.message || 'Session expired. Please login again.');
          }
        }
      }

      const errorText = await response.text();
      const error = errorText ? JSON.parse(errorText) : { message: response.statusText };
      throw new Error(error.message || `HTTP error! status: ${response.status}`);
    }

    const text = await response.text();
    const data = text ? JSON.parse(text) : {};
    return this.fixMinioUrls(data);
  }

  // Метод для замены внутренних адресов Docker (minio:9000) на доступные браузеру (localhost:9000)
  private fixMinioUrls(data: any): any {
    if (!data) return data;

    const fixUrl = (url: string) => {
      if (typeof url === 'string' && url.includes('http://minio:9000')) {
        return url.replace('http://minio:9000', 'http://localhost:9000');
      }
      return url;
    };

    if (typeof data === 'object') {
      // Обрабатываем все возможные поля с URL
      const urlFields = ['url', 'uploadUrl', 'downloadUrl'];
      for (const field of urlFields) {
        if (data[field]) {
          data[field] = fixUrl(data[field]);
        }
      }

      // Если это массив (например, список файлов)
      if (Array.isArray(data)) {
        return data.map(item => this.fixMinioUrls(item));
      }

      // Рекурсивно для вложенных объектов (например, в { files: [...] })
      for (const key in data) {
        if (data[key] && typeof data[key] === 'object') {
          data[key] = this.fixMinioUrls(data[key]);
        }
      }
    }
    return data;
  }

  async login(credentials: LoginRequest): Promise<TokenResponse> {
    return this.request<TokenResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        ...credentials,
        deviceInfo: navigator.userAgent,
      }),
    });
  }

  async register(data: RegisterRequest): Promise<TokenResponse> {
    return this.request<TokenResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async refreshToken(refreshToken: string): Promise<TokenResponse> {
    return this.request<TokenResponse>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    });
  }

  async logout(refreshToken: string): Promise<void> {
    return this.request<void>('/api/v1/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    });
  }

  /* =========================
     FILE & FOLDER ENDPOINTS
     ========================= */

  async listFiles(params: {
    path?: string;
    parentFolderId?: string;
    limit?: number;
    offset?: number;
  }): Promise<{ files: any[]; total: number }> {
    const query = new URLSearchParams();
    if (params.path) query.append('path', params.path);
    if (params.parentFolderId) query.append('parentFolderId', params.parentFolderId);
    if (params.limit) query.append('limit', params.limit.toString());
    if (params.offset) query.append('offset', params.offset.toString());

    return this.request<{ files: any[]; total: number }>(`/api/v1/files?${query.toString()}`);
  }

  async getFile(fileId: string): Promise<any> {
    return this.request<any>(`/api/v1/files/${fileId}`);
  }

  async createFile(data: {
    name: string;
    path: string;
    size: number;
    mimeType?: string;
    hash?: string;
    parentFolderId?: string;
  }): Promise<any> {
    return this.request<any>('/api/v1/files', {
      method: 'POST',
      body: JSON.stringify({ ...data, isFolder: false }),
    });
  }

  async updateFile(fileId: string, data: {
    name?: string;
    size?: number;
    hash?: string;
    version?: number;
  }): Promise<any> {
    return this.request<any>(`/api/v1/files/${fileId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteFile(fileId: string): Promise<void> {
    return this.request<void>(`/api/v1/files/${fileId}`, {
      method: 'DELETE',
    });
  }

  async shareFile(fileId: string, sharedWithUserId: string, permission: 'read' | 'write' = 'read'): Promise<any> {
    return this.request<any>(`/api/v1/files/${fileId}/share`, {
      method: 'POST',
      body: JSON.stringify({ sharedWithUserId, permission }),
    });
  }

  async getFileVersions(fileId: string): Promise<{ versions: any[] }> {
    return this.request<{ versions: any[] }>(`/api/v1/files/${fileId}/versions`);
  }

  async restoreVersion(fileId: string, version: number): Promise<any> {
    return this.request<any>(`/api/v1/files/${fileId}/versions/${version}/restore`, {
      method: 'POST',
    });
  }

  async checkPermission(fileId: string, requiredPermission: string): Promise<{ has_permission: boolean; permission: string }> {
    return this.request<{ has_permission: boolean; permission: string }>(
      `/api/v1/files/${fileId}/permission?requiredPermission=${requiredPermission}`
    );
  }

  async moveFile(fileId: string, newParentFolderId: string): Promise<any> {
    return this.request<any>(`/api/v1/files/${fileId}/move`, {
      method: 'PUT',
      body: JSON.stringify({ newParentFolderId }),
    });
  }

  async createFolder(data: {
    name: string;
    path: string;
    parentFolderId?: string;
  }): Promise<any> {
    return this.request<any>('/api/v1/folders', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getFolder(folderId: string): Promise<any> {
    return this.request<any>(`/api/v1/folders/${folderId}`);
  }

  async updateFolder(folderId: string, data: { name: string }): Promise<any> {
    return this.request<any>(`/api/v1/folders/${folderId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteFolder(folderId: string): Promise<void> {
    return this.request<void>(`/api/v1/folders/${folderId}`, {
      method: 'DELETE',
    });
  }

  /* =========================
     SYNC SERVICE ENDPOINTS
     ========================= */

  async getDevices(): Promise<{ devices: any[] }> {
    return this.request<{ devices: any[] }>('/api/v1/sync/devices');
  }

  async registerDevice(data: {
    deviceName: string;
    deviceType: string;
    os: string;
    osVersion: string;
  }): Promise<any> {
    return this.request<any>('/api/v1/sync/devices', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async unregisterDevice(deviceId: string): Promise<void> {
    return this.request<void>(`/api/v1/sync/devices/${deviceId}`, {
      method: 'DELETE',
    });
  }

  async getSyncStatus(deviceId: string): Promise<any> {
    return this.request<any>(`/api/v1/sync/status?device_id=${deviceId}`);
  }

  async pushChanges(deviceId: string, changes: any[]): Promise<any> {
    return this.request<any>('/api/v1/sync/push', {
      method: 'POST',
      body: JSON.stringify({ deviceId, changes }),
    });
  }

  async pullChanges(deviceId: string, lastSyncCursor?: string): Promise<any> {
    const query = new URLSearchParams({ device_id: deviceId });
    if (lastSyncCursor) query.append('last_sync_cursor', lastSyncCursor);

    return this.request<any>(`/api/v1/sync/pull?${query.toString()}`);
  }

  async resolveConflict(conflictId: string, resolutionType: 'manual' | 'server' | 'client', chosenFileId?: string): Promise<void> {
    return this.request<void>(`/api/v1/sync/conflicts/${conflictId}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ resolutionType, chosenFileId }),
    });
  }

  /* =========================
     STORAGE SERVICE ENDPOINTS
     ========================= */

  async getUploadUrl(data: {
    fileId: string;
    fileName: string;
    fileSize: number;
    mimeType?: string;
    version?: number;
  }): Promise<{ url: string; method: string; expiresIn: number; headers: Record<string, string> }> {
    return this.request<any>('/api/v1/storage/upload-url', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getDownloadUrl(fileId: string, version?: number): Promise<{ url: string; method: string; expiresIn: number; headers: Record<string, string> }> {
    return this.request<any>('/api/v1/storage/download-url', {
      method: 'POST',
      body: JSON.stringify({ fileId, version }),
    });
  }

  async deleteFileFromStorage(fileId: string, version?: number): Promise<void> {
    const query = version ? `?version=${version}` : '';
    return this.request<void>(`/api/v1/storage/files/${fileId}${query}`, {
      method: 'DELETE',
    });
  }

  async copyFileInStorage(sourceFileId: string, destinationFileId: string): Promise<void> {
    return this.request<void>('/api/v1/storage/files/copy', {
      method: 'POST',
      body: JSON.stringify({ sourceFileId, destinationFileId }),
    });
  }

  async confirmUpload(fileId: string, version: number, hash: string): Promise<void> {
    return this.request<void>('/api/v1/storage/upload/confirm', {
      method: 'POST',
      body: JSON.stringify({ fileId, version, hash }),
    });
  }

  /* =========================
     NOTIFICATION SERVICE ENDPOINTS
     ========================= */

  async getNotifications(params: {
    unreadOnly?: boolean;
    notificationType?: string;
    limit?: number;
    offset?: number;
  }): Promise<{ notifications: any[]; total: number; unread_count: number }> {
    const query = new URLSearchParams();
    if (params.unreadOnly !== undefined) query.append('unread_only', params.unreadOnly.toString());
    if (params.notificationType) query.append('notification_type', params.notificationType);
    if (params.limit) query.append('limit', params.limit.toString());
    if (params.offset) query.append('offset', params.offset.toString());

    return this.request<any>(`/api/v1/notifications?${query.toString()}`);
  }

  async getUnreadCount(): Promise<{ count: number }> {
    return this.request<{ count: number }>('/api/v1/notifications/unread-count');
  }

  async markAsRead(notificationId: string): Promise<void> {
    return this.request<void>(`/api/v1/notifications/${notificationId}/read`, {
      method: 'PUT',
    });
  }

  async markAllAsRead(): Promise<void> {
    return this.request<void>('/api/v1/notifications/read-all', {
      method: 'PUT',
    });
  }

  async deleteNotification(notificationId: string): Promise<void> {
    return this.request<void>(`/api/v1/notifications/${notificationId}`, {
      method: 'DELETE',
    });
  }

  async registerPushToken(data: {
    deviceId: string;
    token: string;
    platform: 'fcm' | 'apns';
  }): Promise<void> {
    return this.request<void>('/api/v1/notifications/push-tokens', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async unregisterPushToken(deviceId: string): Promise<void> {
    return this.request<void>(`/api/v1/notifications/push-tokens/${deviceId}`, {
      method: 'DELETE',
    });
  }

  async getNotificationPreferences(): Promise<any> {
    return this.request<any>('/api/v1/notifications/preferences');
  }

  async updateNotificationPreferences(data: any): Promise<any> {
    return this.request<any>('/api/v1/notifications/preferences', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  /* =========================
     USER SERVICE ENDPOINTS
     ========================= */

  async getCurrentUser(): Promise<any> {
    return this.request<any>('/api/v1/users/me');
  }

  async updateCurrentUser(data: { name?: string; avatarUrl?: string }): Promise<any> {
    return this.request<any>('/api/v1/users/me', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteCurrentUser(): Promise<void> {
    return this.request<void>('/api/v1/users/me', {
      method: 'DELETE',
    });
  }

  async getUserSettings(): Promise<any> {
    return this.request<any>('/api/v1/users/me/settings');
  }

  async updateUserSettings(data: any): Promise<any> {
    return this.request<any>('/api/v1/users/me/settings', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async checkQuota(fileSize: number = 0): Promise<{ hasSpace: boolean; availableSpace: number; storageUsed: number; storageQuota: number }> {
    return this.request<any>(`/api/v1/users/me/quota?fileSize=${fileSize}`);
  }

  /* =========================
     ADMIN SERVICE ENDPOINTS
     ========================= */

  async adminListUsers(params: {
    page?: number;
    pageSize?: number;
    search?: string;
    plan?: string;
    sortBy?: string;
    sortOrder?: 'asc' | 'desc';
  }): Promise<{ users: any[]; total: number; page: number; pageSize: number }> {
    const query = new URLSearchParams();
    if (params.page) query.append('page', params.page.toString());
    if (params.pageSize) query.append('pageSize', params.pageSize.toString());
    if (params.search) query.append('search', params.search);
    if (params.plan) query.append('plan', params.plan);
    if (params.sortBy) query.append('sortBy', params.sortBy);
    if (params.sortOrder) query.append('sortOrder', params.sortOrder);

    return this.request<any>(`/api/v1/admin/users?${query.toString()}`);
  }

  async adminGetUserDetails(userId: string): Promise<any> {
    return this.request<any>(`/api/v1/admin/users/${userId}`);
  }

  async adminUpdateUserQuota(userId: string, newQuota: number): Promise<void> {
    return this.request<void>(`/api/v1/admin/users/${userId}/quota`, {
      method: 'PUT',
      body: JSON.stringify({ newQuota }),
    });
  }

  async adminChangeUserPlan(userId: string, newPlan: string): Promise<void> {
    return this.request<void>(`/api/v1/admin/users/${userId}/plan`, {
      method: 'PUT',
      body: JSON.stringify({ newPlan }),
    });
  }

  async adminBlockUser(userId: string, reason: string): Promise<void> {
    return this.request<void>(`/api/v1/admin/users/${userId}/block`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  }

  async adminUnblockUser(userId: string): Promise<void> {
    return this.request<void>(`/api/v1/admin/users/${userId}/unblock`, {
      method: 'POST',
    });
  }

  async adminAssignRole(userId: string, roleName: string): Promise<void> {
    return this.request<void>(`/api/v1/admin/users/${userId}/roles`, {
      method: 'POST',
      body: JSON.stringify({ roleName }),
    });
  }

  async adminRevokeRole(userId: string, roleName: string): Promise<void> {
    return this.request<void>(`/api/v1/admin/users/${userId}/roles?roleName=${roleName}`, {
      method: 'DELETE',
    });
  }

  async adminGetSystemStats(from?: number, to?: number): Promise<any> {
    const query = new URLSearchParams();
    if (from) query.append('fromTimestamp', from.toString());
    if (to) query.append('toTimestamp', to.toString());
    return this.request<any>(`/api/v1/admin/statistics/system?${query.toString()}`);
  }

  async adminGetStorageStats(): Promise<any> {
    return this.request<any>('/api/v1/admin/statistics/storage');
  }

  async adminGetUserStats(): Promise<any> {
    return this.request<any>('/api/v1/admin/statistics/users');
  }

  async adminGetActiveUsers(minutes: number = 60): Promise<any> {
    return this.request<any>(`/api/v1/admin/statistics/active?minutes=${minutes}`);
  }
}

export const apiService = new ApiService(API_BASE_URL);

