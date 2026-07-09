package com.car.core.usecases.rental.commands;

import com.car.core.entities.Rental;
import com.car.core.entities.enums.RentalStatus;
import com.car.core.gateway.RentalGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários complementares para o cálculo da multa por atraso em
 * {@link ReturnRentUseCaseImpl}, focando em ANÁLISE DE VALOR LIMITE.
 *
 * A multa usa {@code Duration.between(expected, now).toDays()}, que TRUNCA para
 * dias inteiros. Este conjunto exercita explicitamente as fronteiras dessa
 * fórmula, inclusive o caso do atraso de horas (< 1 dia) que resulta em multa
 * ZERO — comportamento reportado como defeito na seção "Resultado dos testes".
 *
 * O valor de multa por dia usado aqui (400,00) é o mesmo configurado em produção
 * ({@code carrental.penalty.amount}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnRent - valor limite da multa por atraso")
class ReturnRentPenaltyBoundaryTest {

    @Mock
    private RentalGateway rentalGateway;

    private ReturnRentUseCaseImpl useCase;

    private static final BigDecimal PENALTY_PER_DAY = new BigDecimal("400.00");
    private static final BigDecimal BASE_TOTAL = new BigDecimal("500.00");

    @BeforeEach
    void setUp() {
        useCase = new ReturnRentUseCaseImpl(rentalGateway, PENALTY_PER_DAY);
    }

    private Rental openRentalExpectedAt(LocalDateTime expectedReturn) {
        return new Rental(1L, 10L, 20L,
                expectedReturn.minusDays(3), expectedReturn,
                null, BASE_TOTAL, RentalStatus.OPEN);
    }

    private Rental captureSaved() {
        ArgumentCaptor<Rental> captor = ArgumentCaptor.forClass(Rental.class);
        verify(rentalGateway).createRental(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Devolução antes da data prevista: sem multa (limite inferior)")
    void noPenaltyWhenReturnedBeforeExpected() {
        Rental rental = openRentalExpectedAt(LocalDateTime.now().plusDays(2));
        when(rentalGateway.findRentalById(1L)).thenReturn(Optional.of(rental));
        when(rentalGateway.createRental(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(1L);

        assertEquals(0, captureSaved().totalValue().compareTo(BASE_TOTAL));
    }

    @Test
    @DisplayName("DEFEITO: atraso de horas (< 1 dia) => multa ZERO por truncamento de toDays()")
    void noPenaltyWhenLateByHoursOnly() {
        // Atraso de 5 horas: pela regra de negócio esperaríamos alguma multa,
        // mas toDays() trunca para 0 dias -> multa zero.
        Rental rental = openRentalExpectedAt(LocalDateTime.now().minusHours(5));
        when(rentalGateway.findRentalById(1L)).thenReturn(Optional.of(rental));
        when(rentalGateway.createRental(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(1L);

        // Documenta o comportamento ATUAL (defeituoso): total permanece inalterado.
        assertEquals(0, captureSaved().totalValue().compareTo(BASE_TOTAL));
    }

    @Test
    @DisplayName("Atraso de exatamente 1 dia: multa de 1x (primeiro valor positivo)")
    void oneDayPenalty() {
        Rental rental = openRentalExpectedAt(LocalDateTime.now().minusDays(1).minusMinutes(1));
        when(rentalGateway.findRentalById(1L)).thenReturn(Optional.of(rental));
        when(rentalGateway.createRental(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(1L);

        assertEquals(0, captureSaved().totalValue().compareTo(BASE_TOTAL.add(PENALTY_PER_DAY)));
    }

    @Test
    @DisplayName("Atraso de 10 dias: multa de 10x (partição de equivalência 'muito atrasado')")
    void manyDaysPenalty() {
        Rental rental = openRentalExpectedAt(LocalDateTime.now().minusDays(10));
        when(rentalGateway.findRentalById(1L)).thenReturn(Optional.of(rental));
        when(rentalGateway.createRental(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(1L);

        BigDecimal expected = BASE_TOTAL.add(PENALTY_PER_DAY.multiply(BigDecimal.valueOf(10)));
        assertEquals(0, captureSaved().totalValue().compareTo(expected));
    }
}
