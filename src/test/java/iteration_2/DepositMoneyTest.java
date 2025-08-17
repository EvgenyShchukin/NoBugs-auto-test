package iteration_2;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DepositMoneyTest {

    private static String authToken;
    private static int accountId;
    private static String randomUsername = "Test-" + UUID.randomUUID().toString().substring(0, 8);

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
    public void createAccount() {
        // Создание счета
        accountId = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .path("id");
    }

    public static Stream<Arguments> depositValidData() {
        return Stream.of(
                Arguments.of(100, 100.0F, "Успешное зачисление денег суммой без дробной части пользователем на счет"),
                Arguments.of(5000, 5000.0F, "Успешное зачисление денег максимальной суммой пользователем на счет"),
                Arguments.of(1111.11, 1111.11F, "Успешное зачисление денег суммой с дробной частью пользователем на счет"),
                Arguments.of(0.01, 0.01F, "Успешное зачисление денег минимальной суммой пользователем на счет"),
                Arguments.of(4999, 4999.00F, "Успешное зачисление денег пред максимальной суммой пользователем на счет")
        );
    }


    public static Stream<Arguments> depositInvalidData() {
        return Stream.of(
                // id, balance failed validation
                Arguments.of(accountId, 0.0, "balance", 400, "Invalid account or amount", "Ошибка зачисление денег суммой равной 0 пользователем на счет"),
                Arguments.of(accountId, null, "balance", 500, "Internal Server Error", "Ошибка зачисление денег суммой равной null пользователем на счет"),
                Arguments.of(accountId, -1.0, "balance", 400, "Invalid account or amount", "Ошибка зачисление денег отрицательной суммой пользователем на счет"),
                Arguments.of(44, 234.00, "id", 403, "Unauthorized access to account", "Ошибка зачисление денег пользователем на несуществующий счет"),
                Arguments.of(null, 234.00, "id", 500, "Internal Server Error", "Ошибка зачисление денег пользователем на счет null"),
                Arguments.of(null, 5000.01, "balance", 500, "Internal Server Error", "Ошибка зачисление денег суммой превышающей максимальную пользователем на счет")
        );
    }


    @ParameterizedTest(name = "Positive test: {2}")
    @MethodSource("depositValidData")
    public void depositPassedWithValidData(double balance, Float expectedBalance, String description) {
        String requestBody = String.format(
                """
                        {
                             "id": %d,
                             "balance": %s
                           }
                        """, accountId, balance);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        //Проверка баланса
        given()
                .header("Authorization", authToken)
                .contentType(ContentType.JSON)
                .when()
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(String.format("find { it.id == %d }.balance", accountId), equalTo(expectedBalance));
    }

    @ParameterizedTest(name = "Negative test: {5}")
    @MethodSource("depositInvalidData")
    public void depositFailedWithInvalidData(Integer id, Double balance, String errorKey, int status, String errorValue, String description) {
        Integer realId = id == null ? null : (id == 0 ? accountId : id);
        String requestBody = String.format(
                """
                        {
                             "id": %d,
                             "balance": %s
                           }
                        """, realId, balance);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .post("http://localhost:4111/api/v1/accounts/deposit")
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
                .body(String.format("find { it.id == %d }.balance", accountId), equalTo(0.0F));
    }
}

