package app.fluxer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayInfo(String url, Integer shards, GatewaySessionStartLimit sessionStartLimit) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GatewaySessionStartLimit(Integer total, Integer remaining, Integer resetAfter,
                                           Integer maxConcurrency) {
    }
}
