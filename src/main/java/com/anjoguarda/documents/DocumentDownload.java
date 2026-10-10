package com.anjoguarda.documents;

public record DocumentDownload(String filename, String mimeType, byte[] bytes) {}
