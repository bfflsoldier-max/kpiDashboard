package com.qa.datafeeder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feeder-test;DB_CLOSE_DELAY=-1",
        "dashboard.cors.allowed-origin=http://localhost:8081,http://127.0.0.1:5500"
})
@AutoConfigureMockMvc
class FeederProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeederProjectRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void savesAndListsProjectForDashboard() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "streamName": "Bellmedia",
                                  "domain": "QA",
                                  "projectName": "Datahub",
                                  "projectId": "RDSDEV",
                                  "boardId": 318
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(jsonPath("$.streamName").value("Bellmedia"));

        mockMvc.perform(get("/api/projects")
                        .header("Origin", "http://localhost:8081"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].domain").value("QA"))
                .andExpect(jsonPath("$[0].projectName").value("Datahub"))
                .andExpect(jsonPath("$[0].projectId").value("RDSDEV"))
                .andExpect(jsonPath("$[0].boardId").value(318));
    }

    @Test
    void rejectsNonPositiveBoardIds() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "streamName": "Bellmedia",
                                  "domain": "QA",
                                  "projectName": "Datahub",
                                  "projectId": "RDSDEV",
                                  "boardId": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsLiveServerToPostProjects() throws Exception {
        mockMvc.perform(options("/api/projects")
                        .header("Origin", "http://127.0.0.1:5500")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", "http://127.0.0.1:5500"))
                .andExpect(header().string("Access-Control-Allow-Methods", "POST"))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type"));
    }

    @Test
    void allowsAdminDeletePreflightFromDashboard() throws Exception {
        mockMvc.perform(options("/api/projects")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(header().string("Access-Control-Allow-Methods", "DELETE"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers", "authorization, content-type"));
    }

    @Test
    void deletesProjectsAndStreamsOnlyWithAdminSession() throws Exception {
        repository.save(new FeederProject("Admin Stream", "Admin Domain", "Remove Me", "ADMIN-REMOVE", 901));
        repository.save(new FeederProject("Admin Stream", "Admin Domain", "Keep Me", "ADMIN-KEEP", 902));

        mockMvc.perform(delete("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": "Admin Domain",
                                  "streamName": "Admin Stream",
                                  "projectId": "ADMIN-REMOVE",
                                  "boardId": 901
                                }
                                """))
                .andExpect(status().isUnauthorized());

        String loginResponse = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode login = objectMapper.readTree(loginResponse);
        String authorization = "Bearer " + login.path("token").asText();

        mockMvc.perform(delete("/api/projects")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": "Admin Domain",
                                  "streamName": "Admin Stream",
                                  "projectId": "ADMIN-REMOVE",
                                  "boardId": 901
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId == 'ADMIN-REMOVE')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.projectId == 'ADMIN-KEEP')]").exists());

        mockMvc.perform(delete("/api/projects/stream")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"Admin Domain","streamName":"Admin Stream"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId == 'ADMIN-KEEP')]").doesNotExist());
    }

    @Test
    void rejectsInvalidAdminCredentials() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
