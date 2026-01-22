const WS_URL = 'ws://localhost:8084/ws';

export interface WebSocketNotification {
    userId: string;
    notificationId: string;
    type: string;
    title: string;
    message: string;
    priority: string;
    resourceId?: string;
    resourceType?: string;
    createdAt: string;
}

type MessageHandler = (notification: WebSocketNotification) => void;

class WebSocketService {
    private ws: WebSocket | null = null;
    private handlers: Set<MessageHandler> = new Set();
    private reconnectTimeout: number | null = null;
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectDelay = 3000;
    private isIntentionallyClosed = false;

    connect(token: string) {
        if (this.ws?.readyState === WebSocket.OPEN) {
            console.log('WebSocket already connected');
            return;
        }

        this.isIntentionallyClosed = false;
        const wsUrl = `${WS_URL}?token=${encodeURIComponent(token)}`;

        try {
            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                console.log('WebSocket connected');
                this.reconnectAttempts = 0;
            };

            this.ws.onmessage = (event) => {
                try {
                    const notification: WebSocketNotification = JSON.parse(event.data);
                    console.log('Received WebSocket notification:', notification);
                    this.handlers.forEach(handler => handler(notification));
                } catch (error) {
                    console.error('Error parsing WebSocket message:', error);
                }
            };

            this.ws.onerror = (error) => {
                console.error('WebSocket error:', error);
            };

            this.ws.onclose = () => {
                console.log('WebSocket disconnected');
                this.ws = null;

                if (!this.isIntentionallyClosed && this.reconnectAttempts < this.maxReconnectAttempts) {
                    this.reconnectAttempts++;
                    console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);

                    this.reconnectTimeout = setTimeout(() => {
                        this.connect(token);
                    }, this.reconnectDelay * this.reconnectAttempts);
                }
            };
        } catch (error) {
            console.error('Error creating WebSocket:', error);
        }
    }

    disconnect() {
        this.isIntentionallyClosed = true;

        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }

        // Clear all handlers to prevent duplicates on reconnect
        this.handlers.clear();
        this.reconnectAttempts = 0;
    }

    subscribe(handler: MessageHandler) {
        this.handlers.add(handler);
        return () => {
            this.handlers.delete(handler);
        };
    }

    isConnected(): boolean {
        return this.ws?.readyState === WebSocket.OPEN;
    }
}

export const webSocketService = new WebSocketService();
