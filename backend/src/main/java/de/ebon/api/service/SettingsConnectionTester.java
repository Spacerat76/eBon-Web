package de.ebon.api.service;

import de.ebon.api.dto.SettingsConnectionTestResponse;

public interface SettingsConnectionTester {

    SettingsConnectionTestResponse testPaperless(String baseUrl, String apiToken);

    SettingsConnectionTestResponse testOpenRouter(String baseUrl, String apiKey);
}
