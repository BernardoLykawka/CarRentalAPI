package com.car.core.entities.vo;

import com.car.core.entities.exceptions.InvalidCpfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes unitários do Value Object {@link Cpf}.
 *
 * Técnicas aplicadas:
 *  - Partição de equivalência: CPFs válidos x inválidos.
 *  - Análise de valor limite: comprimento 10/11/12 dígitos.
 *  - Teste de caminho: ramos de nulo, dígitos repetidos e dígito verificador.
 *
 * Os CPFs válidos abaixo foram confirmados executando o algoritmo de dígitos
 * verificadores presente na própria classe {@code Cpf}.
 */
@DisplayName("Value Object Cpf")
class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"08442524096", "11144477735", "52998224725", "39053344705"})
    @DisplayName("Aceita CPFs com dígitos verificadores válidos")
    void shouldAcceptValidCpf(String value) {
        assertDoesNotThrow(() -> new Cpf(value));
    }

    @Test
    @DisplayName("Aceita CPF válido com máscara (pontos e hífen são removidos)")
    void shouldAcceptFormattedValidCpf() {
        assertDoesNotThrow(() -> new Cpf("084.425.240-96"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678900", "12345678901"})
    @DisplayName("Rejeita CPF com dígito verificador incorreto")
    void shouldRejectWrongCheckDigits(String value) {
        assertThrows(InvalidCpfException.class, () -> new Cpf(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    @DisplayName("Rejeita CPF de dígitos repetidos (conhecidos como inválidos)")
    void shouldRejectRepeatedDigits(String value) {
        assertThrows(InvalidCpfException.class, () -> new Cpf(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "084425240960"}) // valor limite: 10 e 12 dígitos
    @DisplayName("Rejeita CPF com quantidade de dígitos diferente de 11")
    void shouldRejectWrongLength(String value) {
        assertThrows(InvalidCpfException.class, () -> new Cpf(value));
    }

    @Test
    @DisplayName("Rejeita CPF nulo")
    void shouldRejectNullCpf() {
        assertThrows(InvalidCpfException.class, () -> new Cpf(null));
    }
}
