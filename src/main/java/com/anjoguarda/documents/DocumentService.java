package com.anjoguarda.documents;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> MIME_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final StoredDocumentRepository documents;
    private final AuditLogRepository audits;
    private final DocumentFiles files;
    private final ObjectMapper objectMapper;

    public DocumentService(
            ChildRepository children,
            FamilyAccessRepository access,
            StoredDocumentRepository documents,
            AuditLogRepository audits,
            DocumentFiles files,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.documents = documents;
        this.audits = audits;
        this.files = files;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentResponse store(UUID userId, UUID childId, String category, MultipartFile file) {
        requireChild(userId, childId);
        String storedCategory = category(category);
        String filename = filename(file == null ? null : file.getOriginalFilename());
        byte[] bytes = bytes(file);
        String mimeType = mimeType(file, bytes);
        String storageKey = UUID.randomUUID() + extension(mimeType);
        files.write(storageKey, bytes);
        try {
            StoredDocument document = documents.save(new StoredDocument(
                    UUID.randomUUID(),
                    childId,
                    storedCategory,
                    filename,
                    storageKey,
                    mimeType,
                    bytes.length,
                    userId));
            audits.save(new AuditLog(
                    UUID.randomUUID(),
                    userId,
                    "DOCUMENT_UPLOADED",
                    "document",
                    document.getId(),
                    metadata(childId)));
            return toResponse(document);
        } catch (RuntimeException error) {
            files.delete(storageKey);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID userId, UUID childId) {
        requireChild(userId, childId);
        return documents.findByChildIdOrderByFilenameAscIdAsc(childId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(UUID userId, UUID documentId) {
        StoredDocument document = documents.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado"));
        requireChild(userId, document.getChildId());
        return new DocumentDownload(
                document.getFilename(), document.getMimeType(), files.read(document.getStorageKey()));
    }

    private void requireChild(UUID userId, UUID childId) {
        children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }

    private String category(String value) {
        String category = value == null ? "" : value.trim();
        if (category.isEmpty() || category.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida");
        }
        return category;
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

    private String mimeType(MultipartFile file, byte[] bytes) {
        String mime = file.getContentType();
        if (mime == null || !MIME_TYPES.contains(mime) || !signatureMatches(mime, bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de arquivo inválido");
        }
        return mime;
    }

    private boolean signatureMatches(String mime, byte[] bytes) {
        return switch (mime) {
            case "application/pdf" -> startsWith(bytes, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case "image/jpeg" -> startsWith(bytes, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(bytes, new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });
            case "image/webp" -> startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && rangeEquals(bytes, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        return rangeEquals(bytes, 0, prefix);
    }

    private boolean rangeEquals(byte[] bytes, int offset, byte[] expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de arquivo inválido");
        };
    }

    private String metadata(UUID childId) {
        try {
            return objectMapper.writeValueAsString(Map.of("childId", childId.toString()));
        } catch (JsonProcessingException error) {
            return "{}";
        }
    }

    private DocumentResponse toResponse(StoredDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getChildId(),
                document.getCategory(),
                document.getFilename(),
                document.getMimeType(),
                document.getSize());
    }
}
