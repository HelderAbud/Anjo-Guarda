package com.anjoguarda.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LocalBlobStoreTest {

    @TempDir
    Path root;

    @Test
    void writesANestedKeyAndStillReadsAFlatKey() throws Exception {
        LocalBlobStore store = new LocalBlobStore(root.toString(), "Documento não encontrado");
        byte[] pdf = new byte[] {1, 2, 3};

        store.write("2026/10/arquivo.pdf", pdf);
        store.write("antigo.pdf", new byte[] {9});

        assertThat(store.read("2026/10/arquivo.pdf")).isEqualTo(pdf);
        assertThat(Files.readAllBytes(root.resolve("antigo.pdf"))).isEqualTo(new byte[] {9});
    }

    @Test
    void rejectsAKeyThatLeavesTheRoot() {
        LocalBlobStore store = new LocalBlobStore(root.toString(), "Documento não encontrado");

        assertThatThrownBy(() -> store.write("../fora.pdf", new byte[] {1}))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Files.exists(root.getParent().resolve("fora.pdf"))).isFalse();
    }
}
