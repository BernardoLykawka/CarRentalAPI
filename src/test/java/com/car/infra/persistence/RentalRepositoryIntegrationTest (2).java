package com.car.infra.persistence;

import com.car.core.entities.enums.RentalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de INTEGRAÇÃO da camada de persistência contra um banco real (H2 em
 * memória), exercitando a query {@code hasConflictingRental} e o round-trip de
 * persistência de {@link RentalEntity}.
 *
 * Técnicas aplicadas:
 *  - Análise de valor limite: períodos que se tocam exatamente na borda.
 *  - Teste baseado em estado: apenas locações OPEN participam do conflito.
 *
 * Flyway é desabilitado; o schema é criado pelo Hibernate a partir das entidades.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Integração: RentalRepository.hasConflictingRental (banco H2)")
class RentalRepositoryIntegrationTest {

    @Autowired
    private RentalRepository repository;

    private static final LocalDateTime BASE = LocalDateTime.of(2027, 1, 10, 10, 0);

    private RentalEntity openRental(LocalDateTime pickup, LocalDateTime expected) {
        return new RentalEntity(null, 1L, 1L, pickup, expected, null,
                new BigDecimal("500.00"), RentalStatus.OPEN);
    }

    @BeforeEach
    void seed() {
        // Locação aberta ocupando o carro 1 no intervalo [BASE, BASE+5d].
        repository.save(openRental(BASE, BASE.plusDays(5)));
    }

    @Test
    @DisplayName("Período sobreposto deve conflitar")
    void overlappingConflicts() {
        assertTrue(repository.hasConflictingRental(1L, BASE.plusDays(2), BASE.plusDays(7)));
    }

    @Test
    @DisplayName("Valor limite: novo pickup == expectedReturn existente NÃO conflita")
    void touchingBordersDoesNotConflict() {
        assertFalse(repository.hasConflictingRental(1L, BASE.plusDays(5), BASE.plusDays(8)));
    }

    @Test
    @DisplayName("Período totalmente separado não conflita")
    void separatedDoesNotConflict() {
        assertFalse(repository.hasConflictingRental(1L, BASE.plusDays(6), BASE.plusDays(8)));
    }

    @Test
    @DisplayName("Conflito é verificado por carro (carro diferente não conflita)")
    void differentCarDoesNotConflict() {
        assertFalse(repository.hasConflictingRental(99L, BASE.plusDays(1), BASE.plusDays(2)));
    }

    @Test
    @DisplayName("Teste de estado: locação FINISHED é ignorada na verificação de conflito")
    void finishedRentalIgnored() {
        RentalEntity finished = openRental(BASE.plusDays(20), BASE.plusDays(25));
        finished.setStatus(RentalStatus.FINISHED);
        repository.save(finished);
        assertFalse(repository.hasConflictingRental(1L, BASE.plusDays(21), BASE.plusDays(24)));
    }

    @Test
    @DisplayName("Round-trip de persistência preserva os campos")
    void persistsAndReadsBack() {
        RentalEntity saved = repository.save(openRental(BASE.plusDays(40), BASE.plusDays(42)));
        RentalEntity found = repository.findById(saved.getId()).orElseThrow();
        assertEquals(RentalStatus.OPEN, found.getStatus());
        assertEquals(0, found.getTotalValue().compareTo(new BigDecimal("500.00")));
        assertEquals(1L, found.getCarId());
    }
}
