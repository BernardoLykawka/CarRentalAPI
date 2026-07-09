package com.car.core.entities.vo;

import com.car.core.entities.exceptions.InvalidEmailAdressException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes unitários do Value Object {@link Email}.
 *
 * Técnicas aplicadas:
 *  - Partição de equivalência: endereços bem formados x mal formados.
 *  - Análise de valor limite: TLD de 2 caracteres ("a@b.co").
 *  - Teste de caminho: ausência de '@', ausência de domínio, nulo.
 */
@DisplayName("Value Object Email")
class EmailTest {

    @ParameterizedTest
    @ValueSource(strings = {"john.doe@example.com", "a@b.co", "user-name@sub.domain.org"})
    @DisplayName("Aceita e-mails bem formados")
    void shouldAcceptValidEmail(String value) {
        assertDoesNotThrow(() -> new Email(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "a@b", "@example.com", "john@", "john@@example.com"})
    @DisplayName("Rejeita e-mails mal formados")
    void shouldRejectInvalidEmail(String value) {
        assertThrows(InvalidEmailAdressException.class, () -> new Email(value));
    }

    @Test
    @DisplayName("Rejeita e-mail nulo")
    void shouldRejectNullEmail() {
        assertThrows(InvalidEmailAdressException.class, () -> new Email(null));
    }
}
