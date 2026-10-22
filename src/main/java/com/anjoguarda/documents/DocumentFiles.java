package com.anjoguarda.documents;

import com.anjoguarda.storage.BlobStore;
import com.anjoguarda.storage.LocalBlobStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "anjo.storage", havingValue = "local", matchIfMissing = true)
@Qualifier("documentStore")
public class DocumentFiles implements BlobStore {

    private final LocalBlobStore store;

    public DocumentFiles(@Value("${anjo.documents.dir:documentos}") String dir) {
        this.store = new LocalBlobStore(dir, "Documento não encontrado");
    }

    @Override
    public void write(String storageKey, byte[] bytes) {
        store.write(storageKey, bytes);
    }

    @Override
    public byte[] read(String storageKey) {
        return store.read(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        store.delete(storageKey);
    }
}
