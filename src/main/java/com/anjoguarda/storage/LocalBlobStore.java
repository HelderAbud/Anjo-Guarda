package com.anjoguarda.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class LocalBlobStore implements BlobStore {

    private final Path root;
    private final String missingMessage;

    public LocalBlobStore(String dir, String missingMessage) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.missingMessage = missingMessage;
    }

    @Override
    public void write(String storageKey, byte[] bytes) {
        Path path = resolve(storageKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, missingMessage);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\\') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
        Path path = root.resolve(storageKey).normalize();
        if (path.equals(root) || !path.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
        return path;
    }
}
