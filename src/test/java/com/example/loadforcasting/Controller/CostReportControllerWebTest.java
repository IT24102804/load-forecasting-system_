package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.FullChainReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FullChainReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class CostReportControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FullChainReportService fullChainReportService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void exportPdf_MissingId_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/full-chain/export"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("costRunId is required."));
    }

    @Test
    void exportPdf_WithValidId_ReturnsPdfAttachment() throws Exception {
        byte[] pdf = "%PDF-1.4\n%stub".getBytes();
        when(fullChainReportService.exportFullChainReportPdf(5L)).thenReturn(pdf);

        mockMvc.perform(get("/api/reports/full-chain/export")
                        .param("costRunId", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"full-chain-report-5.pdf\""))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf));
    }
}
