package com.car.infra.gateway;

import com.car.core.entities.Rental;
import com.car.core.entities.enums.RentalStatus;
import com.car.infra.mapper.RentalEntityMapper;
import com.car.infra.persistence.RentalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de INTEGRAÇÃO do fluxo caso de uso -> gateway -> mapper -> repositório ->
 * banco (H2). Verifica que o {@link RentalRepositoryGateway} realmente persiste
 * o domínio e o recupera corretamente através do {@link RentalEntityMapper}.
 *
 * O {@code @Import} adiciona o gateway e o mapper ao contexto reduzido do
 * {@code @DataJpaTest} (que por padrão só carrega componentes JPA).
 */
@DataJpaTest
@Import({RentalRepositoryGateway.class, RentalEntityMapper.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Integração: RentalRepositoryGateway sobre banco H2")
class RentalGatewayIntegrationTest {

    @Autowired
    private RentalRepositoryGateway gateway;

    @Autowired
    private RentalRepository repository;

    @Test
    @DisplayName("createRental persiste e findRentalById recupera o domínio")
    void createAndFind() {
        LocalDateTime pickup = LocalDateTime.of(2027, 3, 1, 9, 0);
        Rental toSave = new Rental(null, 5L, 7L, pickup, pickup.plusDays(4),
                null, new BigDecimal("800.00"), RentalStatus.OPEN);

        Rental saved = gateway.createRental(toSave);

        assertNotNull(saved.id());

        Optional<Rental> found = gateway.findRentalById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(5L, found.get().carId());
        assertEquals(7L, found.get().customerId());
        assertEquals(RentalStatus.OPEN, found.get().rentalStatus());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    @DisplayName("hasConflictingRental via gateway reflete a locação aberta persistida")
    void gatewayDetectsConflict() {
        LocalDateTime pickup = LocalDateTime.of(2027, 4, 1, 9, 0);
        gateway.createRental(new Rental(null, 3L, 1L, pickup, pickup.plusDays(3),
                null, new BigDecimal("300.00"), RentalStatus.OPEN));

        assertTrue(gateway.hasConflictingRental(3L, pickup.plusDays(1), pickup.plusDays(2)));
    }
}
