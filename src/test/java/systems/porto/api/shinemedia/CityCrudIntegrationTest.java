package systems.porto.api.shinemedia;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "porto.api.application=shine-media",
    "porto.api.home-directory=${user.dir}/src/test/resources/runtime-fixtures/shine-media",
    "porto.api.config-path=${user.dir}/src/test/resources/runtime-fixtures/shine-media/config",
    "porto.api.application-path=${user.dir}/src/test/resources/runtime-fixtures/shine-media/config/shine-media",
    "porto.api.plugins-path=${user.dir}/src/test/resources/runtime-fixtures/shine-media/plugins",
    "spring.datasource.url=jdbc:h2:mem:shine-media-city-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=shine-media",
    "spring.datasource.password=shine-media",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "porto.api.auth.required=true",
    "porto.api.auth.identity-provider=local-idp",
    "porto.api.auth.jwt.issuer=shine-media",
    "porto.api.auth.jwt.secret=shine-media-local-jwt-secret-32b-min"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CityCrudIntegrationTest {

    private static final String UNIQUE_NAME = "Testville-CRUD";

    @Autowired
    private MockMvc mockMvc;

    private static int createdId;
    private static String token;

    @Test
    @Order(1)
    void createCity() throws Exception {
        token = ShineMediaAuthSupport.issueUsableToken(mockMvc);
        String body = mockMvc.perform(post("/api/v1.0.0/cities")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Testville-CRUD",
                      "state": "VIC",
                      "country": "AU"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is(UNIQUE_NAME)))
            .andExpect(jsonPath("$.state", is("VIC")))
            .andExpect(jsonPath("$.country", is("AU")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        createdId = JsonPath.read(body, "$.id");
    }

    @Test
    @Order(2)
    void listCities() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/cities").param("size", "100")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(3)
    void readCity() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/cities/" + createdId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is(UNIQUE_NAME)));
    }

    @Test
    @Order(4)
    void searchCities() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/cities/search").param("q", UNIQUE_NAME)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @Order(5)
    void updateCity() throws Exception {
        mockMvc.perform(put("/api/v1.0.0/cities/" + createdId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Testville-CRUD-CBD",
                      "state": "VIC",
                      "country": "AU",
                      "iconUrl": "https://example.com/tv.png"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Testville-CRUD-CBD")))
            .andExpect(jsonPath("$.iconUrl", is("https://example.com/tv.png")));
    }

    @Test
    @Order(6)
    void deleteCity() throws Exception {
        mockMvc.perform(delete("/api/v1.0.0/cities/" + createdId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("deleted")));

        mockMvc.perform(get("/api/v1.0.0/cities/" + createdId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

}
