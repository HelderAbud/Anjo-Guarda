package com.anjoguarda.imports;

import com.anjoguarda.storage.BlobStore;
import com.anjoguarda.storage.LocalBlobStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "anjo.storage", havingValue = "local", matchIfMissing = true)
@Qualifier("importStore")
public class ImportFiles implements BlobStore {

    private final LocalBlobStore store;

    public ImportFiles(@Value("${anjo.imports.dir:imports-originais}") String dir) {
        this.store = new LocalBlobStore(dir, "Arquivo inválido");
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
