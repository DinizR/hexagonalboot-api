package systems.porto.api.shinemedia;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    "spring.datasource.url=jdbc:h2:mem:shine-media-auth-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=shine-media",
    "spring.datasource.password=shine-media",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "porto.api.auth.required=true",
    "porto.api.auth.identity-provider=local-idp",
    "porto.api.auth.jwt.issuer=shine-media",
    "porto.api.auth.jwt.secret=shine-media-local-jwt-secret-32b-min"
})
class AuthTokenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void issuesExpiredBootstrapTokenThenRequiresPasswordChange() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/roles"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1.0.0/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "grant_type": "password",
                      "username": "admin",
                      "password": "wrong"
                    }
                    """))
            .andExpect(status().isUnauthorized());

        String bootstrap = mockMvc.perform(post("/api/v1.0.0/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "grant_type": "password",
                      "username": "admin",
                      "password": "changeme"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type", is("Bearer")))
            .andExpect(jsonPath("$.username", is("admin")))
            .andExpect(jsonPath("$.must_change_password", is(true)))
            .andExpect(jsonPath("$.permissions.length()", is(1)))
            .andExpect(jsonPath("$.permissions[0]", is("*")))
            .andExpect(jsonPath("$.access_token", not(emptyString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String expiredToken = JsonPath.read(bootstrap, "$.access_token");

        mockMvc.perform(get("/api/v1.0.0/roles")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1.0.0/auth/password")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "current_password": "wrong",
                      "new_password": "ChangedPass1!"
                    }
                    """))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1.0.0/auth/password")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "current_password": "changeme",
                      "new_password": "changeme"
                    }
                    """))
            .andExpect(status().isBadRequest());

        String changed = mockMvc.perform(post("/api/v1.0.0/auth/password")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "current_password": "changeme",
                      "new_password": "ChangedPass1!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.must_change_password", is(false)))
            .andExpect(jsonPath("$.access_token", not(emptyString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String accessToken = JsonPath.read(changed, "$.access_token");

        mockMvc.perform(get("/api/v1.0.0/roles")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1.0.0/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "grant_type": "password",
                      "username": "admin",
                      "password": "changeme"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }
}
