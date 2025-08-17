package iteration_2;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransferMoneyTest {

    private static String authToken;
    private static int senderAccountId;
    private static int receiverAccountId;
    private static String randomUsername = "Test-" + UUID.randomUUID().toString().substring(0, 8);
    private Float currentAmount = 10000.0F;

    @BeforeAll
    public void setup() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));

        //авторизация админа
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "username": "admin",
                          "password": "admin"
                        }
                        """)
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=");

        // создание пользователя
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                          "username": "%s",
                          "password": "!QAZ2wsx",
                          "role": "USER"
                        }
                        """.formatted(randomUsername))
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED);

        //Получение токена пользователя
        authToken = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("""
                        {
                          "username": "%s",
                          "password": "!QAZ2wsx"
                        }
                        """.formatted(randomUsername))
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .header("Authorization", Matchers.notNullValue())
                .extract()
                .header("Authorization");
    }

    @BeforeEach
    public void createAccounts() {
        Double balance = 5000.00;
        // Создание счетов
        senderAccountId = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        receiverAccountId = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");

        //Пополнение счета
        String requestBody = String.format(
                """
                        {
                             "id": %d,
                             "balance": %s
                           }
                        """, senderAccountId, balance);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
    }

    public static Stream<Arguments> transferValidData() {
        return Stream.of(
                Arguments.of(100F, 100.0F, "Успешный перевод денег суммой без дробной части пользователем на другой счет"),
                Arguments.of(5000.0F, 5000.0F, "Успешный перевод денег максимальной суммой пользователем на другой счет"),
                Arguments.of(1111.11F, 1111.11F, "Успешный перевод денег суммой с дробной части пользователем на другой счет"),
                Arguments.of(0.01F, 0.01F, "Успешный перевод денег минимальной суммой пользователем на другой счет"),
                Arguments.of(9999.99F, 4999.00F, "Успешный перевод денег пред максимальной суммой пользователем на другой счет"),
                Arguments.of(10000F, 10000.00F, "Успешный перевод всех денег со счета пользователем на другой счет")
        );
    }


    public static Stream<Arguments> transferInvalidData() {
        return Stream.of(
                // id, balance failed validation
                Arguments.of(senderAccountId, receiverAccountId, 11000.0F, "balance", 400, "Transfer amount cannot exceed 10000", "Ошибка перевода денег пользователем суммы, превышающий лимит на счете (недостаточно средств) на другой счет"),
                Arguments.of(1111111, receiverAccountId, 100F, "id", 400, "Invalid account or amount", "Ошибка перевода денег пользователем с несуществующего счета на другой счет"),
                Arguments.of(senderAccountId, 1111111, 100F, "id", 400, "Invalid account or amount", "Ошибка перевода денег пользователем со счета на несуществующий счет"),
                Arguments.of(senderAccountId, receiverAccountId, 0F, "balance", 400, "Invalid transfer: insufficient funds or invalid accounts", "Ошибка перевода денег пользователем суммой равной 0"),
                Arguments.of(null, receiverAccountId, 234.00F, "id", 500, "Internal Server Error", "Ошибка перевода денег пользователем со счета null"),
                Arguments.of(senderAccountId, null, 234.00F, "id", 500, "Internal Server Error", "Ошибка перевода денег пользователем на счет null"),
                Arguments.of(senderAccountId, receiverAccountId, null, "balance", 500, "Internal Server Error", "Ошибка перевода денег пользователем суммой nul"),
                Arguments.of(senderAccountId, receiverAccountId, -2F, "balance", 400, "Invalid transfer: insufficient funds or invalid accounts", " Ошибка перевода денег пользователем с отрицательной суммой")
        );
    }


    @ParameterizedTest(name = "Positive test: {2}")
    @MethodSource("transferValidData")
    public void transferPassedWithValidData(Float amount, Float expectedBalance, String description) {
        String requestBody = String.format(
                """
                        {
                              "senderAccountId": %d,
                               "receiverAccountId": %d,
                               "amount": %s
                           }
                        """, senderAccountId, receiverAccountId, amount);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("message", Matchers.equalTo("Transfer successful"));

        //Проверка перевода
        given()
                .header("Authorization", authToken)
                .contentType(ContentType.JSON)
                .when()
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(String.format("find { it.id == %d }.balance", senderAccountId), equalTo(currentAmount - amount))
                .body(String.format("find { it.id == %d }.balance", receiverAccountId), equalTo(expectedBalance));
    }

    @ParameterizedTest(name = "Negative test: {6}")
    @MethodSource("transferInvalidData")
    public void depositFailedWithInvalidData(Integer sendId, Integer receiveId, Float amount, String errorKey, int status, String errorValue, String description) {
        Integer realSenderAccountId = sendId == null ? null : (sendId == 0 ? senderAccountId : sendId);
        Integer realReceiverAccountId = receiveId == null ? null : (receiveId == 0 ? receiverAccountId : receiveId);
        String requestBody = String.format(
                """
                        {
                              "senderAccountId": %d,
                               "receiverAccountId": %d,
                               "amount": %s
                           }
                        """, senderAccountId, receiverAccountId, amount);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .statusCode(status)
                .body(containsString(errorValue)).toString();
        ;


        //Проверка баланса
        given()
                .header("Authorization", authToken)
                .contentType(ContentType.JSON)
                .when()
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(String.format("find { it.id == %d }.balance", senderAccountId), equalTo(currentAmount))
                .body(String.format("find { it.id == %d }.balance", receiverAccountId), equalTo(0.0F));
    }
}

