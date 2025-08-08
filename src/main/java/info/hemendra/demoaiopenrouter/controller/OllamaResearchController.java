package info.hemendra.demoaiopenrouter.controller;

import info.hemendra.demoaiopenrouter.service.ResearchService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ollama/research")
public class OllamaResearchController {

    private final ResearchService researchService;

    public OllamaResearchController(ResearchService researchService) {
        this.researchService = researchService;
    }

    @GetMapping
    public String getResearch(@RequestParam String topic) {
        return researchService.generateResearchReport(topic);
    }
}
