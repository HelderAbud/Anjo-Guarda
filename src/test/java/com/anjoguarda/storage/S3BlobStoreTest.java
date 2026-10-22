package com.anjoguarda.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class S3BlobStoreTest {

    @Test
    void writesAndReadsUnderTheLogicalPrefix() {
        MemoryRemote remote = new MemoryRemote();
        S3BlobStore store = new S3BlobStore(remote, "anjo", "documentos", "Documento não encontrado");

        store.write("2026/10/arquivo.pdf", new byte[] {1, 2});

        assertThat(store.read("2026/10/arquivo.pdf")).containsExactly(1, 2);
        assertThat(remote.objects).containsKey("anjo/documentos/2026/10/arquivo.pdf");
    }

    @Test
    void missingObjectStaysMissing() {
        S3BlobStore store = new S3BlobStore(new MemoryRemote(), "anjo", "imports-originais", "Arquivo inválido");

        assertThatThrownBy(() -> store.read("2026/10/ausente.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static final class MemoryRemote implements RemoteBytes {
        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void put(String bucket, String key, byte[] body) {
            objects.put(bucket + "/" + key, body);
        }

        @Override
        public byte[] get(String bucket, String key) {
            byte[] body = objects.get(bucket + "/" + key);
            if (body == null) {
                throw new MissingObjectException();
            }
            return body;
        }

        @Override
        public void remove(String bucket, String key) {
            objects.remove(bucket + "/" + key);
        }
    }
}
