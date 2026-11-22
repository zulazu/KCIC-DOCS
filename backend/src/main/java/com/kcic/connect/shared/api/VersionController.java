package com.kcic.connect.shared.api;

import com.kcic.connect.shared.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class VersionController {

    private final AppProperties properties;
    private final Clock clock;

    public VersionController(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/version")
    public VersionResponse version() {
        return new VersionResponse(properties.app().name(), properties.app().version(), "UP",
                Instant.now(clock));
    }

    public record VersionResponse(String name, String version, String status, Instant serverTime) {
    }
}
