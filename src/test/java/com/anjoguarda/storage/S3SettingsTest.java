package com.anjoguarda.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class S3SettingsTest {

    @Test
    void refusesToStartWhenARequiredValueIsMissing() {
        assertThatThrownBy(() -> new S3Settings("https://s3.example", "anjo", "auto", "", "segredo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Storage S3 incompleto");
    }

    @Test
    void keepsCompleteSettings() {
        S3Settings settings = new S3Settings("https://s3.example", "anjo", "auto", "chave", "segredo");

        assertThat(settings.bucket()).isEqualTo("anjo");
        assertThat(settings.endpoint()).isEqualTo("https://s3.example");
    }
}
