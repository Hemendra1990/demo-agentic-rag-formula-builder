package info.hemendra.demoaiopenrouter.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class ValidateFormula {

    private static final Logger log = LoggerFactory.getLogger(ValidateFormula.class);

    private final ChatClient chatClient;
    public ValidateFormula(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Tool(description = "Validate a MVEL formula string")
    public String validateFormula(@ToolParam(description = "formula string") String formula) {
        log.info("Validating formula {}", formula);
        if(formula == null || formula.trim().isEmpty()) {
            return "Invalid: Empty Formula";
        }

        return this.chatClient.prompt("""
                You are given a MVEL formula string.
                Your task is to validate it.
                please return the formula if it is valid, otherwise return an error message.
                
                Formula: %s
                
                """.formatted(formula))
                .call()
                .content();
    }
}
