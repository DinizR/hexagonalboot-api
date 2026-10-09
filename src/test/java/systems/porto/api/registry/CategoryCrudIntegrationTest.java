package systems.porto.api.registry;

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
    "porto.api.application=registry",
    "porto.api.home-directory=${user.dir}/src/test/resources/runtime-fixtures/registry",
    "porto.api.config-path=${user.dir}/src/test/resources/runtime-fixtures/registry/config",
    "porto.api.application-path=${user.dir}/src/test/resources/runtime-fixtures/registry/config/registry",
    "porto.api.plugins-path=${user.dir}/src/test/resources/runtime-fixtures/registry/plugins",
    "spring.datasource.url=jdbc:h2:mem:registry-category-test;DB_CLOSE_DELAY=-1"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CategoryCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void createCategory() throws Exception {
        mockMvc.perform(post("/api/v1.0.0/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Grocery",
                      "enabled": true,
                      "barcodeDigit": "1"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.name", is("Grocery")))
            .andExpect(jsonPath("$.enabled", is(true)))
            .andExpect(jsonPath("$.barcodeDigit", is("1")));
    }

    @Test
    @Order(2)
    void listCategories() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.total", is(1)));
    }

    @Test
    @Order(3)
    void readCategory() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Grocery")));
    }

    @Test
    @Order(4)
    void searchCategories() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/categories/search").param("q", "Gro"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @Order(5)
    void updateCategory() throws Exception {
        mockMvc.perform(put("/api/v1.0.0/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Grocery Updated",
                      "enabled": true,
                      "barcodeDigit": "1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Grocery Updated")));
    }

    @Test
    @Order(6)
    void deleteCategory() throws Exception {
        mockMvc.perform(delete("/api/v1.0.0/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("deleted")));

        mockMvc.perform(get("/api/v1.0.0/categories/1"))
            .andExpect(status().isNotFound());
    }
}
