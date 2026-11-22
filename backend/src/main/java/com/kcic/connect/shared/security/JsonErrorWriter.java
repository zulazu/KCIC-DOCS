package com.kcic.connect.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcic.connect.shared.api.ApiError;
import com.kcic.connect.shared.api.ErrorCode;
import com.kcic.connect.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class JsonErrorWriter {

    private final ObjectMapper objectMapper;

    public JsonErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(code.name(), message, List.of(), RequestIdFilter.currentRequestId(request)));
    }
}
