package info.hemendra.demoaiopenrouter.controller;

import info.hemendra.demoaiopenrouter.service.OpenAiService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openai")
public class OpenAIController {
    private final OpenAiService openAiService;

    public OpenAIController(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @RequestMapping("/hello")
    public String hello(@RequestParam(required = true) String message) {
        return openAiService.chat( message);
    }
}
