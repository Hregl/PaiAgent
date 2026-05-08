package com.paiagent.controller;

import com.paiagent.model.dto.ApiResponse;
import com.paiagent.model.dto.LoginRequest;
import com.paiagent.model.dto.LoginResponse;
import com.paiagent.model.entity.User;
import com.paiagent.repository.UserRepository;
import com.paiagent.security.JwtTokenProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResponse.error(401, "Invalid credentials");
        }

        String token = tokenProvider.generateToken(user.getUsername());
        LoginResponse.UserDTO userDTO = new LoginResponse.UserDTO(user.getId(), user.getUsername(), user.getRole());
        return ApiResponse.success(new LoginResponse(token, userDTO));
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserDTO> getMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) auth.getPrincipal();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ApiResponse.error(401, "User not found");
        }
        return ApiResponse.success(new LoginResponse.UserDTO(user.getId(), user.getUsername(), user.getRole()));
    }
}
