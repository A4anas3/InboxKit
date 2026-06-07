package com.gridapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound WebSocket message from client when claiming a tile.
 * Note: username and color from this message are NOT trusted.
 * Only tileId and userId are used; user data is looked up server-side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimRequest {

    /** Tile identifier in "{row}_{col}" format, e.g. "12_5" */
    private String tileId;

    /** UUID string of the claiming user */
    private String userId;

    // username and color are intentionally ignored — fetched server-side
}
