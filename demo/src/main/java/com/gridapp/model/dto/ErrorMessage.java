package com.gridapp.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Error message sent to /user/queue/error for per-user error delivery.
 *
 * Error types:
 *   COOLDOWN      – user is in cooldown, includes remainingMs
 *   ALREADY_OWNED – tile claimed by someone else simultaneously
 *   INVALID_INPUT – bad tileId format or out-of-range coordinates
 *   SERVER_ERROR  – unexpected exception (generic, no stack trace)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorMessage {

    private String type;
    private String message;

    /** The tileId that triggered this error, if applicable (e.g. OCCUPIED errors) */
    private String tileId;

    /** Optional extra data, e.g. { "remainingMs": 3200 } for COOLDOWN */
    private Map<String, Object> data;
}
