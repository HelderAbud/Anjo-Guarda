package com.anjoguarda.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.family.Child;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.storage.BlobStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceStorageTest {

    @Test
    void storesTheDocumentUnderYearMonthAndItsOwnId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        ChildRepository children = mock(ChildRepository.class);
        FamilyAccessRepository access = mock(FamilyAccessRepository.class);
        StoredDocumentRepository documents = mock(StoredDocumentRepository.class);
        BlobStore files = mock(BlobStore.class);
        when(children.findById(childId))
                .thenReturn(Optional.of(new Child(childId, familyId, "Ana", LocalDate.of(2020, 1, 1), Instant.now())));
        when(access.existsByFamilyIdAndUserId(familyId, userId)).thenReturn(true);
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentService service = new DocumentService(
                children, access, documents, mock(AuditLogRepository.class), files, new ObjectMapper());
        MockMultipartFile file = new MockMultipartFile(
                "arquivo",
                "acordo.pdf",
                "application/pdf",
                "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));

        DocumentResponse response = service.store(userId, childId, "escola", file);

        ArgumentCaptor<StoredDocument> saved = ArgumentCaptor.forClass(StoredDocument.class);
        verify(documents).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(response.id());
        assertThat(saved.getValue().getStorageKey())
                .matches("\\d{4}/\\d{2}/" + response.id() + "\\.pdf")
                .doesNotContain("acordo");
        verify(files).write(saved.getValue().getStorageKey(), file.getBytes());
    }
}
