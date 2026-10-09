package systems.porto.api.shinemedia;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class ShineMediaAuthSupport {

    static final String BOOTSTRAP_USERNAME = "admin";
    static final String BOOTSTRAP_PASSWORD = "changeme";
    static final String CHANGED_PASSWORD = "ChangedPass1!";

    private ShineMediaAuthSupport() {
    }

    static String issueUsableToken(final MockMvc mockMvc) throws Exception {
        String body = tryLogin(mockMvc, BOOTSTRAP_PASSWORD);
        String currentPassword = BOOTSTRAP_PASSWORD;
        if (body == null) {
            body = tryLogin(mockMvc, CHANGED_PASSWORD);
            currentPassword = CHANGED_PASSWORD;
        }
        if (body == null) {
            throw new IllegalStateException("Could not authenticate seeded admin");
        }
        String token = JsonPath.read(body, "$.access_token");
        Boolean mustChange = JsonPath.read(body, "$.must_change_password");
        if (!Boolean.TRUE.equals(mustChange)) {
            return token;
        }
        String changed = mockMvc.perform(post("/api/v1.0.0/auth/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "current_password": "%s",
                      "new_password": "%s"
                    }
                    """.formatted(currentPassword, CHANGED_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return JsonPath.read(changed, "$.access_token");
    }

    private static String tryLogin(final MockMvc mockMvc, final String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1.0.0/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "grant_type": "password",
                      "username": "%s",
                      "password": "%s"
                    }
                    """.formatted(BOOTSTRAP_USERNAME, password)))
            .andReturn();
        if (result.getResponse().getStatus() != 200) {
            return null;
        }
        return result.getResponse().getContentAsString();
    }
}
