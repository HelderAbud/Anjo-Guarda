package com.anjoguarda.documents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentFiles {

    private final Path root;

    public DocumentFiles(@Value("${anjo.documents.dir:documentos}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public void write(String storageKey, byte[] bytes) {
        Path path = resolve(storageKey);
        try {
            Files.createDirectories(root);
            Files.write(path, bytes);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    public byte[] read(String storageKey) {
        Path path = resolve(storageKey);
        try {
            return Files.readAllBytes(path);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado");
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    private Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
        return path;
    }
}
