package com.gridapp.controller;

import com.gridapp.model.dto.*;
import com.gridapp.service.GridService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GridWebSocketController {

    private final GridService gridService;

    /**
     * Handles client tile claim requests.
     *
     * Client sends to: /app/claim
     * Payload: { "tileId": "12_5", "userId": "uuid" }
     *
     * The controller delegates entirely to GridService for the 8-step claim flow.
     * Any errors are sent to /user/queue/error via GridService.
     */
    @MessageMapping("/claim")
    public void handleClaim(ClaimRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String tileId  = request != null ? request.getTileId() : null;
        String userId  = request != null ? request.getUserId() : null;

        // Guard: reject claims from unauthenticated sessions.
        // The STOMP user principal is set during CONNECT in WebSocketConfig
        // if a valid JWT Authorization header was sent. Without it, there is no auth.
        if (headerAccessor.getUser() == null) {
            log.warn("Rejected unauthenticated claim: tileId={} sessionId={}", tileId, sessionId);
            return;
        }

        log.debug("Claim received: tileId={} userId={} sessionId={}", tileId, userId, sessionId);

        try {
            gridService.processClaim(request, sessionId);
        } catch (Exception e) {
            log.error("Unexpected error handling claim: tileId={} userId={} sessionId={} error={}",
                    tileId, userId, sessionId, e.getMessage(), e);
        }
    }

    /**
     * Handles client heartbeat to maintain online presence.
     *
     * Client sends to: /app/heartbeat every 15 seconds
     * Payload: { "userId": "uuid" }
     *
     * No response is sent — purely a TTL refresh.
     */
    @MessageMapping("/heartbeat")
    public void handleHeartbeat(HeartbeatRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId().isBlank()) {
            log.warn("Received heartbeat with null/blank userId");
            return;
        }
        log.debug("Heartbeat from userId={}", request.getUserId());
        gridService.refreshOnlinePresence(request.getUserId());
    }
}
