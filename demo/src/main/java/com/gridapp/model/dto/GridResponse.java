package com.gridapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * HTTP response for GET /api/grid.
 * Contains only the claimed tiles — unclaimed tiles are absent.
 * The frontend treats missing keys as empty/unclaimed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridResponse {

    /**
     * Key: tileId (e.g. "12_5")
     * Value: tile data map with userId, username, color, claimedAt
     */
    private Map<String, Object> tiles;
}
