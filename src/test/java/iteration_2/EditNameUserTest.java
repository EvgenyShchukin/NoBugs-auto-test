package iteration_2;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EditNameUserTest {

    private static String authToken;
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

    public static Stream<Arguments> editNameValidData() {
        return Stream.of(
                Arguments.of("Anna Karenina", "Успешное изменение имени пользователем"),
                Arguments.of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrs tuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFG", "Успешное изменение имени пользователем на максимальную длину"),
                Arguments.of("Any Mat ", "Успешное изменение имени пользователем на минимальную длину"),
                Arguments.of("Сергей Иванов", "Успешное изменение имени пользователем русским буквами")
        );
    }


    public static Stream<Arguments> editNameInvalidData() {
        return Stream.of(
                // name failed validation
                Arguments.of("testIteration", "Name must contain two words with letters only", "Ошибка изменения имени пользователем состоящим из одного слова"),
                Arguments.of("Anna Gofman333", "Name must contain two words with letters only", "Ошибка изменения имени пользователем содержащим цифры"),
                Arguments.of("Anna Gofman!@#$%^&*()_=+", "Name must contain two words with letters only", "Ошибка изменения имени пользователем содержащим спецсимволы"),
                Arguments.of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOSD", "Name must contain two words with letters only", "Ошибка изменения имени пользователем превышающим максимальную длину"),
                Arguments.of("", "Name must contain two words with letters only", "Ошибка изменения имени пользователем состоящим из пустой строки")
        );
    }


    @ParameterizedTest(name = "Positive test: {1}")
    @MethodSource("editNameValidData")
    public void editNameUserPassedWithValidData(String name, String description) {
        String requestBody = String.format(
                """
                        {
                         "name": "%s"
                        }
                        """, name);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("name", Matchers.nullValue());

        //Проверка имени
        given()
                .header("Authorization", authToken)
                .contentType(ContentType.JSON)
                .when()
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("name", Matchers.equalTo(name));
        ;
    }

    @ParameterizedTest(name = "Negative test: {2}")
    @MethodSource("editNameInvalidData")
    public void editNameUserFailedWithInvalidData(String name, String error, String description) {
        String requestBody = String.format(
                """
                        {
                         "name": "%s"
                        }
                        """, name);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", authToken)
                .body(requestBody)
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .assertThat()
                .statusCode(400)
                .body(equalTo(error));

        //Проверка имени
        given()
                .header("Authorization", authToken)
                .contentType(ContentType.JSON)
                .when()
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("name", Matchers.equalTo(null));
        ;
    }
}

