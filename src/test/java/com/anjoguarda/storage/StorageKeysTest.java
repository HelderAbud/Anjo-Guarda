package com.anjoguarda.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageKeysTest {

    @Test
    void usesSaoPauloDateAndTheRecordId() {
        Instant instant = Instant.parse("2026-10-01T02:30:00Z");
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(StorageKeys.key(instant, id, ".pdf"))
                .isEqualTo("2026/09/11111111-1111-1111-1111-111111111111.pdf");
    }
}
