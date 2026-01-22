package websocket

import (
	"encoding/base64"
	"fmt"
	"log/slog"
	"net/http"
	"strings"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

type Handler struct {
	hub       *Hub
	jwtSecret string
	logger    *slog.Logger
}

func NewHandler(hub *Hub, jwtSecret string, logger *slog.Logger) *Handler {
	return &Handler{
		hub:       hub,
		jwtSecret: jwtSecret,
		logger:    logger,
	}
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// Extract token from query param (standard for WS) or header
	tokenStr := r.URL.Query().Get("token")
	if tokenStr == "" {
		authHeader := r.Header.Get("Authorization")
		if len(authHeader) > 7 && strings.EqualFold(authHeader[:7], "Bearer ") {
			tokenStr = authHeader[7:]
		}
	}

	if tokenStr == "" {
		http.Error(w, "Unauthorized: missing token", http.StatusUnauthorized)
		return
	}

	claims, err := h.validateToken(tokenStr)
	if err != nil {
		h.logger.Warn("invalid token", slog.Any("error", err))
		http.Error(w, "Unauthorized: invalid token", http.StatusUnauthorized)
		return
	}

	userIDStr, ok := claims["sub"].(string) // Assuming 'sub' holds the user ID
	if !ok {
		// Try custom claim if sub is not used or not string
		if uid, ok := claims["user_id"].(string); ok {
			userIDStr = uid
		} else {
			h.logger.Warn("token missing user_id claim")
			http.Error(w, "Unauthorized: invalid token claims", http.StatusUnauthorized)
			return
		}
	}

	userID, err := uuid.Parse(userIDStr)
	if err != nil {
		h.logger.Warn("invalid user_id in token", slog.String("user_id", userIDStr))
		http.Error(w, "Unauthorized: invalid user_id", http.StatusUnauthorized)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		h.logger.Error("failed to upgrade connection", slog.Any("error", err))
		return
	}

	client := &Client{
		Hub:    h.hub,
		Conn:   conn,
		UserID: userID,
		send:   make(chan []byte, 256),
		logger: h.logger,
	}
	client.Hub.register <- client

	go client.WritePump()
	go client.ReadPump()
}

func (h *Handler) validateToken(token string) (jwt.MapClaims, error) {
	h.logger.Debug("validating token", slog.String("jwt_secret_length", fmt.Sprintf("%d", len(h.jwtSecret))))

	// Decode Base64 secret to match AuthService behavior
	secretBytes, err := base64.StdEncoding.DecodeString(h.jwtSecret)
	if err != nil {
		h.logger.Warn("failed to decode JWT secret as Base64, using raw string", slog.String("error", err.Error()))
		secretBytes = []byte(h.jwtSecret)
	}

	parsedToken, err := jwt.Parse(token, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return secretBytes, nil
	})

	if err != nil {
		h.logger.Warn("token parse error", slog.String("error", err.Error()))
		return nil, err
	}

	if claims, ok := parsedToken.Claims.(jwt.MapClaims); ok && parsedToken.Valid {
		h.logger.Debug("token validated successfully", slog.Any("claims", claims))
		return claims, nil
	}

	h.logger.Warn("token invalid after parsing")
	return nil, fmt.Errorf("invalid token")
}
