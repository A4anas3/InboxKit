package com.gridapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for POST /api/users/join
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinResponse {
    private String userId;
    private String username;
    private String color;
}
