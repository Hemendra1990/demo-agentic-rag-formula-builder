package info.hemendra.demoaiopenrouter.service;

import info.hemendra.demoaiopenrouter.tools.DateTimeTools;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OpenAiService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    @Qualifier("openAiChatClient")
    @Autowired
    private ChatClient chatClient;

    @Autowired
    JdbcChatMemoryRepository chatMemoryRepository;

    @Autowired
    private PgVectorStore vectorStore;

    private ChatMemory chatMemory;
    private static final String DEFAULT_SESSION_ID = "default-session";
    
    // Industry standard memory management constants
    private static final int MAX_MEMORY_MESSAGES = 20;
    private static final String CONTEXT_SEPARATOR = "\n---CONTEXT---\n";
    private static final String RETRY_CONTEXT_PREFIX = "[RETRY ATTEMPT] Previous attempt failed. ";
    private static final String FORMULA_CONTEXT_PREFIX = "[FORMULA REQUEST] ";
    private static final String GENERAL_CONTEXT_PREFIX = "[GENERAL CHAT] ";
    @Autowired
    private QueryUnderstandingAgent queryUnderstandingAgent;
    
    @Autowired
    private FunctionMappingAgent functionMappingAgent;
    
    @Autowired
    private FunctionSelectionAgent functionSelectionAgent;
    
    @Autowired
    private FormulaSynthesisAgent formulaSynthesisAgent;
    
    @Autowired
    private FormulaTestingAgent formulaTestingAgent;

    @PostConstruct
    public void init() {
        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
    }
    
    private MessageChatMemoryAdvisor createMemoryAdvisor(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId != null ? sessionId : DEFAULT_SESSION_ID)
                .build();
    }
    
    /**
     * Industry standard: Preserve conversation context for retry scenarios
     * This method ensures that when a user says "try again", the system remembers:
     * 1. The original question that failed
     * 2. The failure reason
     * 3. The context of the conversation
     */
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
    
    /**
     * Industry standard: Detect retry patterns in user messages
     */
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
    
    /**
     * Industry standard: Add conversation metadata for better tracking
     */
    private void logConversationContext(String sessionId, String messageType, String userMessage) {
        log.info("[CONVERSATION] Session: {}, Type: {}, Timestamp: {}, Message: {}", 
                sessionId != null ? sessionId : DEFAULT_SESSION_ID, 
                messageType, 
                LocalDateTime.now(), 
                userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage);
    }

    public String chat(String message) {
        return chat(message, null);
    }
    
    public String chat(String message, String sessionId) {
        logConversationContext(sessionId, "DIRECT_CHAT", message);
        
        String contextualPrompt = buildContextAwarePrompt(message, sessionId, GENERAL_CONTEXT_PREFIX);
        
        String response = this.chatClient
                .prompt()
                .tools(new DateTimeTools())
                .advisors(createMemoryAdvisor(sessionId))
                .user(contextualPrompt)
                .call().content();
        
        // Store the actual user message in memory for context preservation
        if (response == null || response.trim().isEmpty()) {
            log.warn("[MEMORY] Empty response for session: {}, message: {}", 
                    sessionId != null ? sessionId : DEFAULT_SESSION_ID, message);
            response = "I apologize, but I couldn't process your request. Please try rephrasing your question.";
        }
        
        return response;
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

        switch (classifier != null ? classifier.toUpperCase() : "GENERAL") {
            case "FORMULA":
                generateFormulaWithAgents(message, sessionId);
                return handleFormulaRequest(message, sessionId);
            case "GENERAL":
                return handleGeneralRequest(message, sessionId);
            default:
                log.warn("[CLASSIFICATION] Unknown classification '{}' for message: {}", classifier, message);
                return handleGeneralRequest(message, sessionId);
        }
    }

    private void generateFormulaWithAgents(String message, String sessionId) {
        try {
            log.info("[FORMULA_AGENTS] Starting multi-agent formula generation for: {}", message);
            
            // Step 1: Analyze the query
            QueryUnderstandingAgent.QueryAnalysisResult queryAnalysisResult = queryUnderstandingAgent.analyzeQuery(message, sessionId);
            log.info("[FORMULA_AGENTS] Step 1 - Query analysis complete: {}", queryAnalysisResult.getBusinessLogic());
            
            // Step 2: Map functions
            FunctionMappingAgent.FunctionMappingResult mappingResult = functionMappingAgent.mapFunctions(queryAnalysisResult, sessionId);
            log.info("[FORMULA_AGENTS] Step 2 - Function mapping complete - Available: {}, Missing: {}, Compatibility: {}", 
                    mappingResult.getAvailableFunctions().size(),
                    mappingResult.getMissingFunctions().size(),
                    String.format("%.2f", mappingResult.getOverallCompatibility()));
            
            // Step 3: Select functions and map parameters
            FunctionSelectionAgent.FunctionSelectionResult selectionResult = functionSelectionAgent.selectFunctions(queryAnalysisResult, mappingResult, sessionId);
            log.info("[FORMULA_AGENTS] Step 3 - Function selection complete - Selected: {}, Validation: {}", 
                    selectionResult.getSelectedFunctions().size(),
                    selectionResult.isValidationPassed());
            
            // Step 4: Synthesize formula
            FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult = formulaSynthesisAgent.synthesizeFormula(queryAnalysisResult, mappingResult, selectionResult, sessionId);
            log.info("[FORMULA_AGENTS] Step 4 - Formula synthesis complete - Primary: {}, Alternatives: {}", 
                    synthesisResult.getPrimaryFormula(),
                    synthesisResult.getAlternativeFormulas() != null ? synthesisResult.getAlternativeFormulas().size() : 0);
            
            // Step 5: Test and optimize
            FormulaTestingAgent.FormulaTestingResult testingResult = formulaTestingAgent.testAndOptimizeFormulas(queryAnalysisResult, mappingResult, selectionResult, synthesisResult, sessionId);
            log.info("[FORMULA_AGENTS] Step 5 - Testing complete - Overall score: {}, Passed: {}", 
                    testingResult.getOverallScore(),
                    testingResult.getPrimaryFormulaTest() != null ? testingResult.getPrimaryFormulaTest().isPassed() : false);
            
            // Log comprehensive results
            log.info("[FORMULA_AGENTS] Multi-agent formula generation complete:");
            log.info("  - Business Logic: {}", queryAnalysisResult.getBusinessLogic());
            log.info("  - Output Type: {}", queryAnalysisResult.getOutputDataType());
            log.info("  - Function Categories: {}", queryAnalysisResult.getFunctionCategories());
            log.info("  - Available Functions: {}", mappingResult.getAvailableFunctions().size());
            log.info("  - Selected Functions: {}", selectionResult.getSelectedFunctions().size());
            log.info("  - Primary Formula: {}", synthesisResult.getPrimaryFormula());
            log.info("  - Alternative Formulas: {}", synthesisResult.getAlternativeFormulas() != null ? synthesisResult.getAlternativeFormulas().size() : 0);
            log.info("  - All Formulas Valid: {}", synthesisResult.isAllFormulasValid());
            log.info("  - Test Score: {}", testingResult.getOverallScore());
            log.info("  - Recommendations: {}", testingResult.getRecommendations() != null ? testingResult.getRecommendations().size() : 0);
            
        } catch (Exception e) {
            log.error("[FORMULA_AGENTS] Error in multi-agent formula generation: {}", e.getMessage(), e);
        }
    }

    /**
     * Industry standard: Handle retry requests with full conversation context
     */
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
    
    /**
     * Industry standard: Handle formula requests with enhanced context
     */
    private Flux<String> handleFormulaRequest(String message, String sessionId) {
        String contextualPrompt = buildContextAwarePrompt(message, sessionId, FORMULA_CONTEXT_PREFIX);
        
        String formulaPrompt = """
                # You are a CRM formula expert with access to conversation history.
                
                # Do not use your prior knowledge of Salesforce syntax or functions.
                # Instead, use only the information and field definitions provided in the context to generate a formula.
                
                Based on the following context and our conversation, help with this formula question:
                Context: %s
                
                User Question: %s
                
                ## Instructions:
                ### - If this is a retry attempt, provide a different approach
                ### - Provide the exact formula with clear explanation
                ### - If you cannot find relevant context, clearly state what information you need
                
                # Important:
                ## - Please provide the exact formula without any additional context or clarification
                ## - Do not explain anything in detail, just provide the formula
                ## - You Should not use your own words, just use the words from the original question
                ## - You should use the context provided by the system
                """;
        
        return generateFormula(formulaPrompt, contextualPrompt, sessionId);
    }
    
    /**
     * Industry standard: Handle general requests with conversation awareness
     */
    private Flux<String> handleGeneralRequest(String message, String sessionId) {
        String contextualPrompt = buildContextAwarePrompt(message, sessionId, GENERAL_CONTEXT_PREFIX);
        
        String content = this.chatClient
                .prompt()
                .user(contextualPrompt)
                .tools(new DateTimeTools())
                .advisors(createMemoryAdvisor(sessionId))
                .call()
                .content();

        if (content != null) {
            content = content.replaceAll("(?s)<think>.*?</think>\\s*", "");
        }
        
        return Flux.just(content != null ? content : "I apologize, but I couldn't process your request. Please try rephrasing your question or provide more context.");
    }

    /**
     * Industry standard: Enhanced formula generation with retry handling and context awareness
     */
    private Flux<String> generateFormula(String formulaPrompt, String userMessage, String sessionId) {
        logConversationContext(sessionId, "FORMULA_GENERATION", userMessage);
        
        // Enhanced vector search with conversation context
        List<Document> documents = vectorStore.similaritySearch(userMessage);
        
        // If no documents found, try with conversation context
        if (documents == null || documents.isEmpty()) {
            var conversationHistory = chatMemory.get(sessionId != null ? sessionId : DEFAULT_SESSION_ID);
            if (!conversationHistory.isEmpty()) {
                // Limit to last 3 messages for context and extract previous user messages
                var recentHistory = conversationHistory.size() > 3 ? 
                    conversationHistory.subList(Math.max(0, conversationHistory.size() - 3), conversationHistory.size()) : 
                    conversationHistory;
                
                String conversationContext = recentHistory.stream()
                        .filter(msg -> msg instanceof UserMessage)
                        .map(msg -> ((UserMessage) msg).getText())
                        .collect(Collectors.joining(" "));
                
                if (!conversationContext.trim().isEmpty()) {
                    documents = vectorStore.similaritySearch(conversationContext + " " + userMessage);
                    log.info("[FORMULA] Retry search with conversation context found {} documents", 
                            documents != null ? documents.size() : 0);
                }
            }
        }
        
        if (documents == null || documents.isEmpty()) {
            String noContextResponse = "I couldn't find relevant formula information for your request. " +
                    "Could you please provide more specific details about the CRM formula you need? " +
                    "For example, mention the fields, objects, or specific calculation you're trying to achieve.";
            
            log.warn("[FORMULA] No vector documents found for session: {}, message: {}", 
                    sessionId != null ? sessionId : DEFAULT_SESSION_ID, userMessage);
            return Flux.just(noContextResponse);
        }
        
        String formulaContext = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining(CONTEXT_SEPARATOR));
        
        log.info("[FORMULA] Found {} relevant documents for formula generation", documents.size());
        
        return this.chatClient.prompt()
                .advisors(createMemoryAdvisor(sessionId))
                .tools(new DateTimeTools())
                .user(String.format(formulaPrompt, formulaContext, userMessage))
                .stream().content();
        
        /*// Ensure we have a meaningful response
        if (response == null || response.trim().isEmpty()) {
            response = "I encountered an issue generating the formula. Please try rephrasing your question or provide more specific requirements.";
        }
        
        return response;*/
    }
    
    /**
     * Industry standard: Clear conversation memory for a specific session
     * Useful for starting fresh conversations or handling session cleanup
     */
    public void clearSessionMemory(String sessionId) {
        String effectiveSessionId = sessionId != null ? sessionId : DEFAULT_SESSION_ID;
        chatMemory.clear(effectiveSessionId);
        log.info("[MEMORY] Cleared conversation memory for session: {}", effectiveSessionId);
    }
    
    /**
     * Industry standard: Get conversation history for debugging or context display
     */
    public List<org.springframework.ai.chat.messages.Message> getConversationHistory(String sessionId) {
        String effectiveSessionId = sessionId != null ? sessionId : DEFAULT_SESSION_ID;
        return chatMemory.get(effectiveSessionId);
    }
}