package info.hemendra.demoaiopenrouter.service.approach2;

import info.hemendra.demoaiopenrouter.model.FormulaFunctionsMetadata;
import info.hemendra.demoaiopenrouter.tools.DateTimeTools;
import info.hemendra.demoaiopenrouter.tools.ValidateFormula;
import info.hemendra.demoaiopenrouter.util.FormulaMetadataUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FormulaAiService {
    private static final Logger log = LoggerFactory.getLogger(FormulaAiService.class);
    private static final String DEFAULT_SESSION_ID = "default-session";
    private ChatMemory chatMemory;
    private static final int MAX_MEMORY_MESSAGES = 20;
    private static final String RETRY_CONTEXT_PREFIX = "[RETRY ATTEMPT] Previous attempt failed. ";

    @Autowired
    JdbcChatMemoryRepository chatMemoryRepository;

    @Autowired
    @Qualifier("openAiChatClient")
    private ChatClient chatClient;

    @Autowired
    private PgVectorStore vectorStore;

    @Autowired
    private FormulaMetadataUtil formulaMetadataUtil;

    @PostConstruct
    public void init() {
        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
    }

    public Flux<String> chatStream(String message) {
        return chatStream(message, null);
    }

    public Flux<String> chatStream(String message, String sessionId) {
        logConversationContext(sessionId, "STREAM_CHAT", message);

        // Industry standard: For retry attempts, skip classification and use previous context
        if (isRetryRequest(message)) {
            return handleRetryRequest(message, sessionId);
        }

        String classifier = classifyUserMessage(message, sessionId);

        switch (classifier != null ? classifier.toUpperCase() : "GENERAL") {
            case "FORMULA":
                return handleFormulaGenerateRequest(message, sessionId);
            case "GENERAL":
                return handleGeneralRequest(message, sessionId);
            default:
                log.warn("[CLASSIFICATION] Unknown classification '{}' for message: {}", classifier, message);
                return handleGeneralRequest(message, sessionId);
        }
    }

    private Flux<String> handleFormulaGenerateRequest(String message, String sessionId) {
        //Step 1. break the problem into smaller problems
        List<String>  subProblems = breakProblemsIntoSmallerParts(message, sessionId);

        //Step 2. Understand what are the functions the llm need to know
        List<ProblemCategory> problemCategories = getFormulaFunctionsFromSubproblems(subProblems, sessionId);
        List<String> distinctFunctionTypes = problemCategories.stream().flatMap(pc -> pc.functionTypes.stream()).distinct().toList();
        log.info("Problem Categories distinctFunctionTypes: \n {}", distinctFunctionTypes);

        //Step 3. Get the formula syntax and their examples from the distinctFunctionTypes
        List<Document> documents = vectorStore.similaritySearch(String.join("\n", distinctFunctionTypes));
        String documentTextOutput = documents.stream().map(d -> d.getText()).collect(Collectors.joining("\n"));

        List<FormulaFunctionsMetadata.CategoryDefinition> categoryDefinitions = formulaMetadataUtil.getCategoryDefinitions(distinctFunctionTypes);

        List<String> functions = categoryDefinitions.stream().flatMap(fun -> fun.getFunctions().stream()).toList();
        List<FormulaFunctionsMetadata.FunctionDefinition> functionDefinition = formulaMetadataUtil.getFunctionDefinition(functions);

        //log.info("Document Text Output: \n {}", documentTextOutput);

        //Step 4.combine them and send them as the syntax
        return buildFormulaContext(message, documentTextOutput, functionDefinition, sessionId);
    }

    private Flux<String> buildFormulaContext(String message, String documentTextOutput, List<FormulaFunctionsMetadata.FunctionDefinition> functionDefinitions, String sessionId) {
        String functionStringRepresentation = functionDefinitions.stream()
                .map(FormulaFunctionsMetadata.FunctionDefinition::toFunctionString)
                .collect(Collectors.joining("\n"));
        String finalPrompt = """
                You are given:
                   - A user query describing a business rule or requirement
                   - A knowledge context that contains detailed documentation or syntax of formula functions available.
                
                # 🧾 Instructions:
                -	Use only the functions and syntax provided in the context.
                -	Do not use prior knowledge or guess missing logic.
                -	Make sure the formula satisfies all parts of the user query.
                -	If a condition involves multiple functions (e.g., date + logic), combine them according to the described rules in the context.
                -	Output only the final formula.
                ---
                # 🧠 Input Format:
                ### User Query: %s
                
                ### Context:
                --- 
                %s
                
                ---
                # IMPORTANT
                1. You must validate the formula syntax and output the final formula using available tools.
                2. If the formula is invalid, you must try again.
                3. Maximum 3 tries are allowed. After that you can respond unable to generate the formula.
                4. You should not use prior knowledge or guess missing logic.
                5. You must output the final formula no additional text.
                
                """.formatted(message, functionStringRepresentation);

        log.info("Final Prompt: \n {}", finalPrompt);

        String content = this.chatClient.prompt(finalPrompt)
                .advisors(createMemoryAdvisor(sessionId))
                //.tools(new ValidateFormula(chatClient))
                .call().content();
        return Flux.just(content != null ? content : "I apologize, but I couldn't process your request. Please try rephrasing your question or provide more context.");
    }

    record ProblemCategory(String subproblem, List<String> functionTypes) {}

    private List<ProblemCategory> getFormulaFunctionsFromSubproblems(List<String> subProblems, String sessionId) {

        BeanOutputConverter<List<ProblemCategory>> beanOutputConverter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<ProblemCategory>>() {
        });
        String subProblemsStr = String.join("\n", subProblems);

        String identifyFormulaFunctions = """
                You are given a list of subproblems derived from a business rule or requirement.
                Your task is to analyze each subproblem and determine what category of formula functions would be needed to implement it in my application.
                
                Choose from the following formula function categories:
                	•	LOGICAL: IF, CASE, AND, OR, NOT, ISBLANK, ISPICKVAL, etc.
                	•	MATH: ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX, ABS, ROUND, etc.
                	•	DATE: TODAY, NOW, DATEVALUE, ADDMONTHS, YEAR, WEEKDAY, etc.
                	•	HTML: BGCOLOR, TEXTCOLOR, BADGECOLOR, HYPERLINK, BGROWCOLOR, HTML — used for visual or formatting logic
                	•	CONTEXT: CURRENTUSERID(), CURRENTUSEREMAIL(), CURRENTUSERFULLNAME(), etc.
                🔽 Instructions:
                	•	For each subproblem, identify the main function types required.
                	•	Use multiple categories if the condition involves multiple function types (e.g., a date + logical condition).
                	•	Do not guess or hallucinate logic not present in the subproblem.
                	
                🔍 Example Input (Subproblems):
                    1. Is the Amount greater than 100,000? If yes, add 40 points.
                    2. Is the Stage equal to 'Negotiation/Review'? If yes, add 20 points.
                    3. Is the CloseDate within the next 30 days from today? If yes, add 10 points.
                    4. Is the Account's AnnualRevenue greater than 1,000,000? If yes, add 10 points.
                    5. Is the checkbox field Is_Enterprise__c set to true? If yes, add 5 points.
                    6. Is the Probability less than 30? If yes, subtract 20 points.
                    7. Is the total score greater than 100? If yes, cap it at 100.
                    8. Is the CurrentUser’s title equal to ‘Sales Manager’? If yes, return ‘YES’.
                    9. If the Account.Industry = 'Finance', make the row color red.
                    10. Create a link to the Opportunity using the record ID and label it ‘View’.
                    
                ✅ Expected Output Format:
                [
                  { "subproblem": "Is the Amount greater than 100,000?", "functionTypes": ["LOGICAL", "MATH"] },
                  { "subproblem": "Is the Stage equal to 'Negotiation/Review'?", "functionTypes": ["LOGICAL"] },
                  { "subproblem": "Is the CloseDate within the next 30 days from today?", "functionTypes": ["LOGICAL", "DATE"] },
                  { "subproblem": "Is the Account's AnnualRevenue greater than 1,000,000?", "functionTypes": ["LOGICAL", "MATH"] },
                  { "subproblem": "Is the checkbox field Is_Enterprise__c set to true?", "functionTypes": ["LOGICAL"] },
                  { "subproblem": "Is the Probability less than 30?", "functionTypes": ["LOGICAL", "MATH"] },
                  { "subproblem": "Is the total score greater than 100?", "functionTypes": ["LOGICAL", "MATH"] },
                  { "subproblem": "Is the CurrentUser’s title equal to ‘Sales Manager’?", "functionTypes": ["LOGICAL", "CONTEXT"] },
                  { "subproblem": "If the Account.Industry = 'Finance', make the row color red.", "functionTypes": ["LOGICAL", "HTML"] },
                  { "subproblem": "Create a link to the Opportunity using the record ID and label it ‘View’.", "functionTypes": ["HYPERLINK", "HTML"] }
                ]
                
                # user query %s
                
                # formatted as: %s
                """.formatted(subProblemsStr, beanOutputConverter.getFormat());

        List<ProblemCategory> problemCategories = this.chatClient.prompt(identifyFormulaFunctions)
                .advisors(createMemoryAdvisor(sessionId))
                .call().entity(beanOutputConverter);

        log.info("Problem Categories: \n {}", problemCategories);

        return problemCategories;
    }

    private List<String> breakProblemsIntoSmallerParts(String message, String sessionId) {

        BeanOutputConverter<List<String>> listBeanOutputConverter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<String>>() {
        });

        String prompt = """
                # Important 
                    1. Break business logic into subqueries
                
                Given a business rule or requirement, break it down into individual subqueries.
                Each subquery should be a standalone conditional statement that can be evaluated independently.
                Focus only on what is explicitly stated — do not assume or hallucinate logic.
                
                Format the result as a numbered list of subqueries, each capturing a single logical condition or step from the original business requirement.
                
                Example Input:
                
                “Add 40 points if Amount > 100,000, 20 points if Stage is ‘Negotiation/Review’, 10 points if CloseDate is within 30 days, 10 points if AnnualRevenue > 1,000,000, 5 points if Is_Enterprise__c is true; subtract 20 points if Probability < 30; cap the result at 100.”
                
                Expected Output:
                
                1. Is the Amount greater than 100,000? If yes, add 40 points.
                2. Is the Stage equal to 'Negotiation/Review'? If yes, add 20 points.
                3. Is the CloseDate within the next 30 days from today? If yes, add 10 points.
                4. Is the Account's AnnualRevenue greater than 1,000,000? If yes, add 10 points.
                5. Is the checkbox field Is_Enterprise__c set to true? If yes, add 5 points.
                6. Is the Probability less than 30? If yes, subtract 20 points.
                7. Is the total score greater than 100? If yes, cap it at 100.
                
                User query {query}
                
                Please return the result in the format:
                {format}
                """;

        Prompt actualPrompt = PromptTemplate.builder()
                .template(prompt)
                .variables(Map.of(
                        "format", listBeanOutputConverter.getFormat(),
                        "query", message
                        )
                )
                .build().create();

        List<String> subQueries = chatClient.prompt(actualPrompt)
                .advisors(createMemoryAdvisor(sessionId))
                .call().entity(listBeanOutputConverter);

        log.info("Sub Queries: \n {}", subQueries);

        return subQueries;
    }

    private String classifyUserMessage(String message, String sessionId) {
        // Build context-aware classification prompt
        String classificationPrompt = String.format("""
                Based on the conversation history and current request, classify this into 'FORMULA' or 'GENERAL'.
                
                Current request: %s
                
                Classification rules:
                - FORMULA: Requests for CRM formulas, calculations, or formula-related help
                - GENERAL: Everything else including general questions, greetings, follow-ups
                
                Respond with single word: FORMULA or GENERAL
                """, message);

        String classifier = this.chatClient.prompt()
                .advisors(createMemoryAdvisor(sessionId))
                .user(classificationPrompt)
                .call().content();

        if (classifier != null) {
            classifier = classifier.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
        }

        log.info("[CLASSIFICATION] Session: {}, Message: '{}', Classified as: {}",
                sessionId != null ? sessionId : DEFAULT_SESSION_ID,
                message.length() > 50 ? message.substring(0, 50) + "..." : message,
                classifier);
        return classifier;
    }

    private Flux<String> handleGeneralRequest(String message, String sessionId) {
        String content = this.chatClient
                .prompt()
                .user(message)
                .tools(new DateTimeTools())
                .advisors(createMemoryAdvisor(sessionId))
                .call()
                .content();

        if (content != null) {
            content = content.replaceAll("(?s)<think>.*?</think>\\s*", "");
        }

        return Flux.just(content != null ? content : "I apologize, but I couldn't process your request. Please try rephrasing your question or provide more context.");
    }

    private void logConversationContext(String sessionId, String messageType, String userMessage) {
        log.info("[CONVERSATION] Session: {}, Type: {}, Timestamp: {}, Message: {}",
                sessionId != null ? sessionId : DEFAULT_SESSION_ID,
                messageType,
                LocalDateTime.now(),
                userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage);
    }

    private boolean isRetryRequest(String message) {
        String lowerMessage = message.toLowerCase().trim();
        return lowerMessage.equals("try again") ||
               lowerMessage.equals("retry") ||
               lowerMessage.equals("again") ||
               lowerMessage.contains("try again") ||
               lowerMessage.contains("can you try") ||
               lowerMessage.contains("please try") ||
               lowerMessage.contains("attempt again") ||
               lowerMessage.contains("one more time");
    }

    private Flux<String> handleRetryRequest(String message, String sessionId) {
        log.info("[RETRY] Handling retry request for session: {}", sessionId != null ? sessionId : DEFAULT_SESSION_ID);

        String contextualPrompt = buildContextAwarePrompt(message, sessionId, null);

        String response = this.chatClient
                .prompt()
                .user(contextualPrompt)
                .tools(new DateTimeTools())
                .advisors(createMemoryAdvisor(sessionId))
                .call()
                .content();

        if (response != null) {
            response = response.replaceAll("(?s)<think>.*?</think>\\s*", "");
        }

        return Flux.just(response != null ? response : "I apologize, but I'm still having trouble with your request. Could you please provide more specific details?");
    }

    private String buildContextAwarePrompt(String userMessage, String sessionId, String contextType) {
        // Get recent conversation history for context
        var conversationHistory = chatMemory.get(sessionId != null ? sessionId : DEFAULT_SESSION_ID);

        // Check if this might be a retry attempt
        boolean isRetryAttempt = isRetryRequest(userMessage);

        if (isRetryAttempt && !conversationHistory.isEmpty()) {
            // For retry attempts, provide context about the previous interaction
            // Limit to last 5 messages for context
            var recentHistory = conversationHistory.size() > 5 ?
                    conversationHistory.subList(Math.max(0, conversationHistory.size() - 5), conversationHistory.size()) :
                    conversationHistory;

            String contextualPrompt = RETRY_CONTEXT_PREFIX +
                                      "User previously asked and received an unsatisfactory response. " +
                                      "Please reconsider the previous question with additional context or a different approach.\n" +
                                      "Current request: " + userMessage;
            return contextualPrompt;
        }

        // For normal requests, add context prefix based on type
        //return (contextType != null ? contextType : "") + userMessage;
        return userMessage;
    }

    private MessageChatMemoryAdvisor createMemoryAdvisor(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId != null ? sessionId : DEFAULT_SESSION_ID)
                .build();
    }
}
