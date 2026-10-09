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
    "spring.datasource.url=jdbc:h2:mem:registry-product-test;DB_CLOSE_DELAY=-1"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void createCategoryForProduct() throws Exception {
        mockMvc.perform(post("/api/v1.0.0/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Grocery",
                      "enabled": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    @Order(2)
    void createProduct() throws Exception {
        mockMvc.perform(post("/api/v1.0.0/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "barcode": "123",
                      "description": "Test Product",
                      "unitPrice": 9.99,
                      "discount": 0.00,
                      "taxRate": 0.10,
                      "unit": "ea",
                      "categoryId": 1,
                      "enabled": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.description", is("Test Product")))
            .andExpect(jsonPath("$.categoryId", is(1)))
            .andExpect(jsonPath("$.enabled", is(true)))
            .andExpect(jsonPath("$.unitPrice", is(9.99)))
            .andExpect(jsonPath("$.taxRate", is(0.10)));
    }

    @Test
    @Order(3)
    void listProducts() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.total", is(1)));
    }

    @Test
    @Order(4)
    void readProduct() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/products/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.barcode", is("123")))
            .andExpect(jsonPath("$.categoryId", is(1)));
    }

    @Test
    @Order(5)
    void searchProducts() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/products/search").param("q", "Test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @Order(6)
    void updateProduct() throws Exception {
        mockMvc.perform(put("/api/v1.0.0/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "barcode": "123",
                      "description": "Updated Product",
                      "unitPrice": 10.99,
                      "discount": 1.50,
                      "taxRate": 0.10,
                      "unit": "ea",
                      "categoryId": 1,
                      "enabled": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description", is("Updated Product")))
            .andExpect(jsonPath("$.enabled", is(false)))
            .andExpect(jsonPath("$.discount", is(1.50)));
    }

    @Test
    @Order(7)
    void deleteProduct() throws Exception {
        mockMvc.perform(delete("/api/v1.0.0/products/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("deleted")));

        mockMvc.perform(get("/api/v1.0.0/products/1"))
            .andExpect(status().isNotFound());
    }
}
