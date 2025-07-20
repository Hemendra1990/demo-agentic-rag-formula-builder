package info.hemendra.demoaiopenrouter.controller;

import info.hemendra.demoaiopenrouter.service.OpenAiService;
import info.hemendra.demoaiopenrouter.service.approach2.FormulaAiService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;

@Controller
public class ChatController {
    private final OpenAiService openAiService;
    private final FormulaAiService formulaAiService;

    public ChatController(OpenAiService openAiService, FormulaAiService formulaAiService) {
        this.openAiService = openAiService;
        this.formulaAiService = formulaAiService;
    }

    @GetMapping("/")
    public String index() {
        return "chat";
    }

    @PostMapping("/chat")
    @ResponseBody
    public String chat(@RequestParam String message) {
        return openAiService.chat(message);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<String> chatStream(@RequestParam String message) {
        return formulaAiService.chatStream(message);
    }
}