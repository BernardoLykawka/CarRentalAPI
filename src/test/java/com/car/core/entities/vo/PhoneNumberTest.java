package com.car.core.entities.vo;

import com.car.core.entities.exceptions.InvalidPhoneNumberException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes unitários do Value Object {@link PhoneNumber}.
 *
 * Regra do domínio (regex): ^\+?[1-9]\d{1,14}$ (formato E.164, sem espaços).
 *
 * Técnicas aplicadas:
 *  - Partição de equivalência: números aceitos x rejeitados.
 *  - Análise de valor limite: comprimento mínimo (2 dígitos) e primeiro dígito.
 *
 * Observação: o valor "+55 11 98765-4321" (usado como exemplo nos DTOs) é
 * REJEITADO por conter espaços — inconsistência entre a documentação e a regra.
 */
@DisplayName("Value Object PhoneNumber")
class PhoneNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"11999999999", "+5511999999999", "5511999999999", "12"})
    @DisplayName("Aceita números no formato E.164")
    void shouldAcceptValidPhone(String value) {
        assertDoesNotThrow(() -> new PhoneNumber(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0119999", "+0119999", "abc", "+55 11 98765-4321", "1"})
    @DisplayName("Rejeita números fora do formato (começando com 0, com espaços ou curtos demais)")
    void shouldRejectInvalidPhone(String value) {
        assertThrows(InvalidPhoneNumberException.class, () -> new PhoneNumber(value));
    }

    @Test
    @DisplayName("Rejeita número nulo")
    void shouldRejectNullPhone() {
        assertThrows(InvalidPhoneNumberException.class, () -> new PhoneNumber(null));
    }
}
