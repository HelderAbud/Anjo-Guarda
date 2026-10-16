package com.anjoguarda.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordResetRunnerTest {

    @Test
    void readsThePasswordFromTheEnvironment() {
        assertThat(PasswordResetRunner.passwordFrom("senha-nova")).isEqualTo("senha-nova");
    }

    @Test
    void refusesABlankEnvironmentPassword() {
        assertThatThrownBy(() -> PasswordResetRunner.passwordFrom("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Defina ANJO_NEW_PASSWORD");
    }
}
