package com.savemon.shared.interfaces.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.shared.interfaces.logging.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void returnsContractedValidationErrorAndTraceId() throws Exception {
        MvcResult result = mockMvc.perform(post("/validation-probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ValidationRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.fields.name").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("\"traceId\":\"" + result.getResponse().getHeader("X-Trace-Id") + "\"");
    }

    @Test
    void doesNotExposeUnexpectedExceptionDetails() throws Exception {
        MvcResult result = mockMvc.perform(get("/failure-probe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive");
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isNotBlank();
        assertThat(result.getResponse().getContentAsString())
                .contains("\"traceId\":\"" + result.getResponse().getHeader("X-Trace-Id") + "\"");
    }

    @Test
    void returnsDocumentedFinancialBusinessErrorCode() throws Exception {
        mockMvc.perform(get("/business-failure-probe"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message").value("Insufficient account balance."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @RestController
    static class ValidationProbeController {

        @PostMapping("/validation-probe")
        String validate(@Valid @RequestBody ValidationRequest request) {
            return "ok";
        }

        @GetMapping("/failure-probe")
        String fail() {
            throw new IllegalStateException("sensitive implementation detail");
        }

        @GetMapping("/business-failure-probe")
        String businessFailure() {
            throw new BusinessApiException(
                    "INSUFFICIENT_BALANCE", "Insufficient account balance.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    record ValidationRequest(@NotBlank String name) {
    }
}
