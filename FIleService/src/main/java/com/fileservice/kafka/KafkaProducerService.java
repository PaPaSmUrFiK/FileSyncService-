package com.fileservice.kafka;

import com.fileservice.event.FileEvent;
import com.fileservice.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "file.events";

    public void sendEvent(FileEvent event) {
        log.debug("Sending kafka event: {}", event);
        try {
            // Преобразуем FileEvent в Map для совместимости с consumers
            Map<String, Object> eventMap = convertToEventMap(event);
            
            // Отправляем как Map для совместимости с NotificationService и другими
            kafkaTemplate.send(TOPIC, event.getFileId().toString(), eventMap);
        } catch (Exception e) {
            log.error("Failed to send kafka event", e);
        }
    }

    private Map<String, Object> convertToEventMap(FileEvent event) {
        Map<String, Object> eventMap = new HashMap<>();
        
        // Стандартные поля для всех consumers
        eventMap.put("eventType", event.getEventType());
        eventMap.put("type", event.getEventType().replace("file.", "")); // Для NotificationService
        eventMap.put("eventId", event.getEventId());
        eventMap.put("fileId", event.getFileId() != null ? event.getFileId().toString() : null);
        eventMap.put("userId", event.getUserId() != null ? event.getUserId().toString() : null);
        eventMap.put("timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : null);
        eventMap.put("version", event.getVersion());
        
        // Извлекаем дополнительные поля из payload (если payload это File объект)
        if (event.getPayload() != null) {
            if (event.getPayload() instanceof File) {
                File file = (File) event.getPayload();
                eventMap.put("fileName", file.getName());
                eventMap.put("filePath", file.getPath());
                eventMap.put("size", file.getSize());
                eventMap.put("sizeDelta", calculateSizeDelta(event.getEventType(), file.getSize()));
            } else if (event.getPayload() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = (Map<String, Object>) event.getPayload();
                eventMap.putAll(payloadMap);
            }
        }
        
        return eventMap;
    }

    private long calculateSizeDelta(String eventType, long fileSize) {
        // Для UserService: положительное значение при создании, отрицательное при удалении
        if ("file.created".equals(eventType) || "file.uploaded".equals(eventType)) {
            return fileSize;
        } else if ("file.deleted".equals(eventType)) {
            return -fileSize;
        }
        return 0; // Для обновлений и других событий
    }
}
