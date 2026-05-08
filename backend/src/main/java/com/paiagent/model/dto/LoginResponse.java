package com.paiagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private UserDTO user;

    @Data
    @AllArgsConstructor
    public static class UserDTO {
        private Long id;
        private String username;
        private String role;
    }
}
