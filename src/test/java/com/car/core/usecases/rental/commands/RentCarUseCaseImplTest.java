package com.car.core.usecases.rental.commands;

import com.car.core.entities.Car;
import com.car.core.entities.Customer;
import com.car.core.entities.Rental;
import com.car.core.entities.enums.CarClass;
import com.car.core.entities.enums.Category;
import com.car.core.entities.enums.RentalStatus;
import com.car.core.entities.vo.Cpf;
import com.car.core.entities.vo.Email;
import com.car.core.entities.vo.PhoneNumber;
import com.car.core.gateway.CarGateway;
import com.car.core.gateway.CustomerGateway;
import com.car.core.gateway.RentalGateway;
import com.car.core.usecases.exception.BusinessRuleException;
import com.car.core.usecases.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class RentCarUseCaseImplTest {
    @Mock
    private CarGateway carGateway;
    @Mock
    private RentalGateway rentalGateway;
    @Mock
    private CustomerGateway customerGateway;

    @InjectMocks
    private RentCarUseCaseImpl rentCarUseCase;

    @Test
    void shouldRentCarSuccessfully() {
        Long carId = 1L;
        Long customerId = 1L;
        LocalDateTime pickup = LocalDateTime.now().plusDays(1);
        LocalDateTime expectedReturn = pickup.plusDays(5);

        Car car = new Car(carId, "Toyota", "Corolla", Category.SEDAN, CarClass.ECONOMY, "ABC-1234", 2024, "Silver", BigDecimal.valueOf(100.0), true);
        Customer customer = new Customer(customerId, "John Doe", new Email("john@test.com"), "pass", new Cpf("03519450089"), new PhoneNumber("11999999999"), "12345", LocalDate.of(1990, 1, 1), "USER");
        Rental rentalRequest = new Rental(null, carId, customerId, pickup, expectedReturn, null, null, null);
        Rental expectedSavedRental = new Rental(1L, carId, customerId, pickup, expectedReturn, null, BigDecimal.valueOf(500.0), RentalStatus.OPEN);

        Mockito.when(carGateway.findById(carId)).thenReturn(Optional.of(car));
        Mockito.when(customerGateway.findById(customerId)).thenReturn(Optional.of(customer));
        Mockito.when(rentalGateway.hasConflictingRental(carId, pickup, expectedReturn)).thenReturn(false);
        Mockito.when(rentalGateway.createRental(Mockito.any(Rental.class))).thenReturn(expectedSavedRental);

        Rental actualRental = rentCarUseCase.execute(rentalRequest);

        assertNotNull(actualRental);
        assertEquals(expectedSavedRental.totalValue(), actualRental.totalValue());
    }

    @Test
    void shouldThrowExceptionWhenCarNotFound(){
        Long carId = 1L;
        Long customerId = 1L;
        LocalDateTime pickup = LocalDateTime.now().plusDays(1);
        LocalDateTime expectedReturn = pickup.plusDays(5);

        Rental rentalRequest = new Rental(null, carId, customerId, pickup, expectedReturn, null, null, null);

        Mockito.when(carGateway.findById(carId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(NotFoundException.class, () -> rentCarUseCase.execute(rentalRequest));
        assertEquals("Car not found", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCustomerNotFound(){
        Long carId = 1L;
        Long customerId = 1L;
        LocalDateTime pickup = LocalDateTime.now().plusDays(1);
        LocalDateTime expectedReturn = pickup.plusDays(5);

        Car car = new Car(carId, "Toyota", "Corolla", Category.SEDAN, CarClass.ECONOMY, "ABC-1234", 2024, "Silver", BigDecimal.valueOf(100.0), true);
        Rental rentalRequest = new Rental(null, carId, customerId, pickup, expectedReturn, null, null, null);

        Mockito.when(carGateway.findById(carId)).thenReturn(Optional.of(car));
        Mockito.when(customerGateway.findById(customerId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(NotFoundException.class, () -> rentCarUseCase.execute(rentalRequest));
        assertEquals("Customer not exists!", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCarIsUnavailableOrHasConflict(){
        Long carId = 1L;
        Long customerId = 1L;
        LocalDateTime pickup = LocalDateTime.now().plusDays(1);
        LocalDateTime expectedReturn = pickup.plusDays(5);

        Car car = new Car(carId, "Toyota", "Corolla", Category.SEDAN, CarClass.ECONOMY, "ABC-1234", 2024, "Silver", BigDecimal.valueOf(100.0), true);
        Customer customer = new Customer(customerId, "John Doe", new Email("john@test.com"), "pass", new Cpf("08442524096"), new PhoneNumber("11999999999"), "12345", LocalDate.of(1990, 1, 1), "USER");
        Rental rentalRequest = new Rental(null, carId, customerId, pickup, expectedReturn, null, null, null);

        Mockito.when(carGateway.findById(carId)).thenReturn(Optional.of(car));
        Mockito.when(customerGateway.findById(customerId)).thenReturn(Optional.of(customer));
        Mockito.when(rentalGateway.hasConflictingRental(carId, pickup, expectedReturn)).thenReturn(true);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> rentCarUseCase.execute(rentalRequest));
        assertEquals("Car already has rental", exception.getMessage());
        Mockito.verify(rentalGateway, Mockito.never()).createRental(Mockito.any(Rental.class));
    }

    @Test
    void shouldThrowExceptionWhenRentalDaysIsZeroOrLess(){
        Long carId = 1L;
        Long customerId = 1L;
        LocalDateTime pickup = LocalDateTime.now().plusDays(1);

        Car car = new Car(carId, "Toyota", "Corolla", Category.SEDAN, CarClass.ECONOMY, "ABC-1234", 2024, "Silver", BigDecimal.valueOf(100.0), true);
        Customer customer = new Customer(customerId, "John Doe", new Email("john@test.com"), "pass", new Cpf("08442524096"), new PhoneNumber("11999999999"), "12345", LocalDate.of(1990, 1, 1), "USER");
        Rental rentalRequest = new Rental(null, carId, customerId, pickup, pickup, null, null, null);

        Mockito.when(carGateway.findById(carId)).thenReturn(Optional.of(car));
        Mockito.when(customerGateway.findById(customerId)).thenReturn(Optional.of(customer));
        Mockito.when(rentalGateway.hasConflictingRental(carId, pickup, pickup)).thenReturn(false);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> rentCarUseCase.execute(rentalRequest));
        assertEquals("Days must be greater than 0", exception.getMessage());
        Mockito.verify(rentalGateway, Mockito.never()).createRental(Mockito.any(Rental.class));
    }
}
