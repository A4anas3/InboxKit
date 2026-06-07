package com.gridapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/users/join
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRequest {
    private String username;
    private String token;
}
