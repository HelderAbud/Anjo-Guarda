package com.anjoguarda.imports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ImportFiles {

    private final Path root;

    public ImportFiles(@Value("${anjo.imports.dir:imports-originais}") String dir) {
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
