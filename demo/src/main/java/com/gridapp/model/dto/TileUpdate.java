package com.gridapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Single tile delta broadcast to /topic/grid after a successful claim.
 * This is the ONLY data sent over WebSocket — never the full grid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TileUpdate {

    private String tileId;
    private String userId;
    private String username;
    private String color;
    private Instant claimedAt;
}
