package systems.porto.api.shinemedia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    "spring.datasource.url=jdbc:h2:mem:shine-media-category-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=shine-media",
    "spring.datasource.password=shine-media",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "porto.api.auth.required=true",
    "porto.api.auth.identity-provider=local-idp",
    "porto.api.auth.jwt.issuer=shine-media",
    "porto.api.auth.jwt.secret=shine-media-local-jwt-secret-32b-min"
})
class MediaCategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsSeededMediaCategories() throws Exception {
        String token = ShineMediaAuthSupport.issueUsableToken(mockMvc);
        mockMvc.perform(get("/api/v1.0.0/media-categories").param("size", "20")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", greaterThanOrEqualTo(5)))
            .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(5))))
            .andExpect(jsonPath("$.items[0].name", is("Audio")));
    }

    @Test
    void listsSeededRoles() throws Exception {
        String token = ShineMediaAuthSupport.issueUsableToken(mockMvc);
        mockMvc.perform(get("/api/v1.0.0/roles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", greaterThanOrEqualTo(5)));
    }

}
