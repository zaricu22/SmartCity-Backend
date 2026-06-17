package com.example.smartcityback.auth.webapi.response;

public record LoginResponse(String token, String role, long expiresInMs, String refreshToken) {}
