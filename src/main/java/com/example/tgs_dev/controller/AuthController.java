package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.LoginRequest;
import com.example.tgs_dev.controller.request.RefreshRequest;
import com.example.tgs_dev.controller.request.RegisterRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.TokenResponse;
import com.example.tgs_dev.controller.response.UserContextDTO;
import com.example.tgs_dev.controller.response.UserDTO;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.mapper.UserContextMapper;
import com.example.tgs_dev.mapper.UserMapper;
import com.example.tgs_dev.security.JwtService;
import com.example.tgs_dev.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService            jwtService;
    private final UserDetailsService    userDetailsService;
    private final UserService           userService;
    private final UserMapper            userMapper;
    private final UserContextMapper     userContextMapper;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody @Valid LoginRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.userName(), request.password()));

        UserDetails user = userDetailsService.loadUserByUsername(request.userName());

        TokenResponse tokens = new TokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user)
        );
        return ResponseEntity.ok(ApiResponse.ok(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody @Valid RefreshRequest request) {
        try {
            Claims claims = jwtService.validateAndExtract(request.refreshToken());

            if (!"refresh".equals(claims.get("type", String.class))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid token type"));
            }

            UserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());

            TokenResponse tokens = new TokenResponse(
                    jwtService.generateAccessToken(user),
                    jwtService.generateRefreshToken(user)
            );
            return ResponseEntity.ok(ApiResponse.ok(tokens));

        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDTO>> register(@RequestBody @Valid RegisterRequest request) {
        User newUser = userService.signUpUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created successfully", userMapper.toDTO(newUser)));
    }

    /**
     * Devuelve el contexto completo del usuario autenticado (roles, permisos, empresa).
     *
     * <p>El frontend llama este endpoint inmediatamente después del login para
     * inicializar su capa RBAC y el contexto de empresa activa (topbar, filtros, etc.).
     * No requiere parámetros — el usuario se extrae del {@link Authentication} del contexto.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserContextDTO>> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(userContextMapper.toDTO(user)));
    }
}
