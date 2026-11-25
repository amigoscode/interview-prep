package com.amigoscode.constraints.settings;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/settings")
public class SettingsController {

    public record ThemeRequest(String theme) {
    }

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** "Make sure this user has a settings row and give it to me." Safe to repeat, so it is a PUT. */
    @PutMapping
    public Settings getOrCreate(@PathVariable long userId,
                                @RequestParam(defaultValue = "on-conflict") String strategy) {
        return settingsService.getOrCreate(Strategy.from(strategy), userId);
    }

    @PutMapping("/theme")
    public Settings saveTheme(@PathVariable long userId, @RequestBody ThemeRequest request) {
        if (request.theme() == null || request.theme().isBlank()) {
            throw new IllegalArgumentException("theme is required");
        }
        return settingsService.saveTheme(userId, request.theme());
    }
}
