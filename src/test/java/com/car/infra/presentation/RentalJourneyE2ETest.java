package com.car.infra.presentation;

import com.car.core.entities.Customer;
import com.car.core.security.TokenService;
import com.car.core.security.PasswordEncryptor;
import com.car.infra.persistence.CustomerEntity;
import com.car.infra.persistence.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Teste de SISTEMA / END-TO-END da jornada de usuário descrita na seção
 * "Objetivos" do relatório: registrar, autenticar (JWT), cadastrar carro (admin),
 * alugar, rejeitar aluguel conflitante e devolver.
 *
 * Percorre a API real por HTTP (MockMvc), passando pelos filtros de segurança e
 * pelo token JWT emitido no login. Usa H2 em memória (perfil "test").
 *
 * Observações de comportamento confirmadas na leitura do código:
 *  - Aluguel conflitante lança BusinessRuleException -> HTTP 400 (e não 409).
 *  - Requisição sem token a endpoint protegido -> HTTP 403 (o
 *    CustomAuthenticationEntryPoint responde com SC_FORBIDDEN).
 *  - Como a validação @FutureOrPresent impede datas passadas na criação, a
 *    devolução ocorre no prazo; o cálculo de multa por atraso é coberto nos
 *    testes unitários de valor limite.
 */
@SpringBootTest(properties = "SECRET_KEY=test-secret-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Sistema/E2E: jornada de aluguel e devolução com JWT")
class RentalJourneyE2ETest {

        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
                registry.add("SECRET_KEY", () -> "test-secret-key");
        }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @MockBean
    private TokenService tokenService;

    private static final String USER_PASSWORD = "SenhaSegura123";

    @BeforeEach
    void configureTokenService() {
        when(tokenService.generateToken(any(Customer.class)))
                .thenAnswer(invocation -> ((Customer) invocation.getArgument(0)).email().address());
        when(tokenService.validateToken(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String registerJson(String name, String email, String cpf) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "%s",
                  "cpf": "%s",
                  "phoneNumber": "11999999999",
                  "driverLicense": "1234567890",
                  "birthDate": "1990-05-15"
                }
                """.formatted(name, email, USER_PASSWORD, cpf);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        MvcResult res = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    /** Cria um cliente com papel ADMIN diretamente no banco (a API sempre cria USER). */
    private void seedAdmin(String email, String rawPassword) {
        CustomerEntity admin = new CustomerEntity(
                null, "Admin", email, passwordEncryptor.encrypt(rawPassword),
                "39053344705", "11988888888", "9999999999",
                LocalDate.of(1985, 1, 1), "ADMIN");
        customerRepository.save(admin);
    }

    private String rentalBody(long carId, LocalDateTime pickup, LocalDateTime expected) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("carId", carId);
        body.put("pickupDate", pickup.toString());
        body.put("expectedReturnDate", expected.toString());
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("Fluxo principal completo da jornada")
    void fullJourney() throws Exception {
        // 1. Registrar usuário comum
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Cliente Teste", "user@teste.com", "08442524096")))
                .andExpect(status().isCreated());

        // 2. Login do usuário -> token JWT
        String userToken = loginAndGetToken("user@teste.com", USER_PASSWORD);

        // 3. Admin cadastra um carro disponível
        seedAdmin("admin@teste.com", "AdminPass123");
        String adminToken = loginAndGetToken("admin@teste.com", "AdminPass123");

        String carBody = """
                {
                  "brand":"Toyota","model":"Corolla","category":"SEDAN","carClass":"ECONOMY",
                  "licensePlate":"ABC-1234","year":2024,"color":"Silver","dailyRate":100.00,"available":true
                }
                """;
        MvcResult carRes = mockMvc.perform(post("/car")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(carBody))
                .andExpect(status().isCreated())
                .andReturn();
        long carId = objectMapper.readTree(carRes.getResponse().getContentAsString()).get("carId").asLong();

        // 4. Usuário aluga o carro (datas futuras por causa de @FutureOrPresent)
        LocalDateTime pickup = LocalDateTime.now().plusDays(1).withNano(0);
        LocalDateTime expected = pickup.plusDays(5);
        MvcResult rentRes = mockMvc.perform(post("/rental")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rentalBody(carId, pickup, expected)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalStatus").value("OPEN"))
                .andExpect(jsonPath("$.totalValue").value(500.00))
                .andReturn();
        long rentalId = objectMapper.readTree(rentRes.getResponse().getContentAsString()).get("id").asLong();

        // 5. Tentar alugar o MESMO carro em datas sobrepostas -> conflito -> 400
        mockMvc.perform(post("/rental")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rentalBody(carId, pickup, expected)))
                .andExpect(status().isBadRequest());

        // 6. Devolver a locação -> 201 e status FINISHED
        mockMvc.perform(post("/rental/" + rentalId + "/return")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rentalStatus").value("FINISHED"));
    }

    @Test
    @DisplayName("Endpoint protegido sem token retorna 403")
    void unauthenticatedIsForbidden() throws Exception {
        String body = """
                {"carId":1,"pickupDate":"2999-01-01T10:00:00","expectedReturnDate":"2999-01-05T10:00:00"}
                """;
        mockMvc.perform(post("/rental")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test 
    @DisplayName("Autorização por papel: usuário comum não cadastra carro (403)")
    void normalUserCannotCreateCar() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Cliente Dois", "user2@teste.com", "11144477735")))
                .andExpect(status().isCreated());
        String token = loginAndGetToken("user2@teste.com", USER_PASSWORD);

        String carBody = """
                {"brand":"Ford","model":"Ka","category":"HATCH","carClass":"ECONOMY",
                 "licensePlate":"XYZ-9999","year":2022,"color":"Red","dailyRate":80.00,"available":true}
                """;
        mockMvc.perform(post("/car")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(carBody))
                .andExpect(status().isForbidden());
    }
}
