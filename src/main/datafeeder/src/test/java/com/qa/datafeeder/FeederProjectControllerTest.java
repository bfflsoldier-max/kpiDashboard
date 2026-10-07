package com.qa.datafeeder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
