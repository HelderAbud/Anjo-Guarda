package com.anjoguarda.imports;

import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final StoredImportRepository imports;
    private final ImportFiles files;

    public ImportService(
            ChildRepository children,
            FamilyAccessRepository access,
            StoredImportRepository imports,
            ImportFiles files) {
        this.children = children;
        this.access = access;
        this.imports = imports;
        this.files = files;
    }

    @Transactional
    public ImportResponse start(UUID userId, UUID childId, MultipartFile file) {
        requireChild(userId, childId);
        String filename = filename(file == null ? null : file.getOriginalFilename());
        byte[] bytes = bytes(file);
        String sourceType = ImportText.sourceType(file.getContentType(), bytes);
        String extracted = ImportText.extract(sourceType, bytes);
        String storageKey = UUID.randomUUID() + extension(sourceType);
        files.write(storageKey, bytes);
        try {
            StoredImport stored = imports.save(new StoredImport(
                    UUID.randomUUID(), childId, filename, storageKey, sourceType, extracted, Instant.now()));
            return toResponse(stored);
        } catch (RuntimeException error) {
            files.delete(storageKey);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public ImportResponse get(UUID userId, UUID importId) {
        StoredImport stored = imports.findById(importId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Importação não encontrada"));
        requireChild(userId, stored.getChildId());
        return toResponse(stored);
    }

    private void requireChild(UUID userId, UUID childId) {
        children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }

    private String filename(String original) {
        String name = original == null ? "" : original.replace('\\', '/').trim();
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1).trim();
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.length() > 255 || name.indexOf('/') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do arquivo inválido");
        }
        return name;
    }

    private byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
            }
            return bytes;
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    private String extension(String sourceType) {
        return "PDF".equals(sourceType) ? ".pdf" : ".docx";
    }

    private ImportResponse toResponse(StoredImport stored) {
        return new ImportResponse(
                stored.getId(),
                stored.getChildId(),
                stored.getFilename(),
                stored.getSourceType(),
                stored.getStatus(),
                stored.getExtractedText(),
                stored.getCreatedAt());
    }
}
