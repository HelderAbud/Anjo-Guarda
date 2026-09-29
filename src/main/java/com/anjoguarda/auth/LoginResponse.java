package com.anjoguarda.auth;

public record LoginResponse(String accessToken, String refreshToken, long expiresInSeconds) {}
