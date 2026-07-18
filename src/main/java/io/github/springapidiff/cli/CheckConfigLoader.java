package io.github.springapidiff.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class CheckConfigLoader {
    private static final String CONFIG_FILE = "spring-api-diff.yml";

    CheckConfig load(Path repoRoot) throws IOException {
        Path configPath = repoRoot.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(configPath)) {
            return new CheckConfig();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            CheckConfig config = mapper.readValue(configPath.toFile(), CheckConfig.class);
            return config == null ? new CheckConfig() : config;
        } catch (JsonProcessingException e) {
            throw new UserFacingException("Invalid " + CONFIG_FILE + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UserFacingException("Failed to read " + configPath.toAbsolutePath().normalize() + ": " + e.getMessage(), e);
        }
    }
}
