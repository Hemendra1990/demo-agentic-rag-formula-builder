package info.hemendra.demoaiopenrouter.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Agent 1: Query Understanding & Intent Classification Service
 * <p>
 * Purpose: Parse user requirements and classify the formula type
 * <p>
 * Responsibilities:
 * - Extract business logic requirements
 * - Identify required function categories (Math, Date/Time, Logical, Text, etc.)
 * - Determine output data type (Number, Text, Boolean, Date)
 * - Extract field references and relationships
 * - Identify conditional logic patterns
 */
@Service
public class QueryUnderstandingAgent {

    private static final Logger log = LoggerFactory.getLogger(QueryUnderstandingAgent.class);

    @Qualifier("openAiChatClient")
    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    private final ObjectMapper objectMapper;

    public QueryUnderstandingAgent() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Main method to analyze user query and extract structured requirements
     */
    public QueryAnalysisResult analyzeQuery(String userQuery, String sessionId) {
        log.info("[QUERY_AGENT] Analyzing user query: {}",
                userQuery.length() > 100 ? userQuery.substring(0, 100) + "..." : userQuery);

        try {
            String analysisPrompt = buildAnalysisPrompt(userQuery);

            String response = chatClient.prompt()
                    .advisors(createMemoryAdvisor(sessionId))
                    .user(analysisPrompt)
                    .call()
                    .content();

            log.debug("[QUERY_AGENT] Raw AI response: {}", response);

            QueryAnalysisResult result = parseResponse(response);

            log.info("[QUERY_AGENT] Analysis complete - Functions: {}, Data Type: {}, Fields: {}",
                    result.getFunctionCategories(),
                    result.getOutputDataType(),
                    result.getFieldReferences().size());

            return result;

        } catch (Exception e) {
            log.error("[QUERY_AGENT] Error analyzing query: {}", e.getMessage(), e);
            return createFallbackResult(userQuery);
        }
    }

    /**
     * Build comprehensive analysis prompt for AI processing
     */
    private String buildAnalysisPrompt(String userQuery) {
        return String.format("""
                # Query Understanding & Intent Classification Task
                
                You are a CRM formula requirements analyst. Analyze the following user query and extract structured information.
                
                ## User Query:
                %s
                
                ## Analysis Requirements:
                Extract and classify the following information in JSON format:
                
                ```json
                {
                    "businessLogic": "Clear description of what the user wants to achieve",
                    "functionCategories": ["category1", "category2"],
                    "outputDataType": "Number|Text|Boolean|Date|Currency|Percent",
                    "fieldReferences": ["field1", "field2"],
                    "conditionalPatterns": ["pattern1", "pattern2"],
                    "mathOperations": ["operation1", "operation2"],
                    "dateTimeOperations": ["operation1", "operation2"],
                    "textOperations": ["operation1", "operation2"],
                    "logicalOperations": ["operation1", "operation2"],
                    "complexityLevel": "Simple|Medium|Complex",
                    "confidenceScore": 0.85
                }
                ```
                
                ## Function Categories (choose relevant ones):
                - MATH: Basic arithmetic, calculations, rounding
                - DATE_TIME: Date calculations, comparisons, formatting
                - LOGICAL: IF statements, AND/OR conditions, comparisons
                - TEXT: String manipulation, concatenation, formatting
                - LOOKUP: VLOOKUP, reference functions
                - VALIDATION: Data validation, error checking
                - CONVERSION: Type conversions, formatting
                - AGGREGATION: SUM, COUNT, AVERAGE operations
                
                ## Output Data Types:
                - Number: Numeric calculations, counts, amounts
                - Text: String results, formatted text
                - Boolean: True/false results, validation checks
                - Date: Date calculations, date formatting
                - Currency: Money amounts, financial calculations
                - Percent: Percentage calculations
                
                ## Conditional Patterns (identify if present):
                - IF_THEN_ELSE: Basic conditional logic
                - NESTED_IF: Multiple condition levels
                - AND_OR_LOGIC: Complex boolean conditions
                - CASE_WHEN: Switch-like logic
                - RANGE_CHECK: Value within range validation
                - NULL_CHECK: Handling empty/null values
                
                ## Field Reference Patterns:
                Look for field names, object references, related record fields
                
                ## Instructions:
                1. Provide ONLY the JSON response
                2. Be specific about function categories needed
                3. Identify ALL field references mentioned
                4. Set appropriate complexity level
                5. Provide confidence score (0.0 to 1.0)
                
                JSON Response:
                """, userQuery);
    }

    /**
     * Parse AI response to extract structured analysis result
     */
    private QueryAnalysisResult parseResponse(String response) throws JsonProcessingException {
        try {
            // Clean response to extract JSON
            String jsonContent = extractJsonFromResponse(response);

            // Parse JSON to result object
            return objectMapper.readValue(jsonContent, QueryAnalysisResult.class);

        } catch (JsonProcessingException e) {
            log.warn("[QUERY_AGENT] Failed to parse JSON response, attempting fallback parsing");
            return parseResponseFallback(response);
        }
    }

    /**
     * Extract JSON content from AI response
     */
    private String extractJsonFromResponse(String response) {
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        // Look for JSON-like content
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        throw new RuntimeException("No valid JSON found in response");
    }

    /**
     * Fallback parsing when JSON parsing fails
     */
    private QueryAnalysisResult parseResponseFallback(String response) {
        log.info("[QUERY_AGENT] Using fallback parsing for response");

        QueryAnalysisResult result = new QueryAnalysisResult();

        // Basic pattern matching for key information
        result.setBusinessLogic(extractBusinessLogic(response));
        result.setFunctionCategories(extractFunctionCategories(response));
        result.setOutputDataType(extractOutputDataType(response));
        result.setFieldReferences(extractFieldReferences(response));
        result.setConditionalPatterns(extractConditionalPatterns(response));
        result.setComplexityLevel("Medium");
        result.setConfidenceScore(0.6); // Lower confidence for fallback

        return result;
    }

    /**
     * Create fallback result when analysis fails
     */
    private QueryAnalysisResult createFallbackResult(String userQuery) {
        log.warn("[QUERY_AGENT] Creating fallback result for failed analysis");

        QueryAnalysisResult result = new QueryAnalysisResult();
        result.setBusinessLogic("User requested formula assistance: " + userQuery);
        result.setFunctionCategories(List.of("MATH", "LOGICAL")); // Default categories
        result.setOutputDataType("Number"); // Default type
        result.setFieldReferences(List.of());
        result.setConditionalPatterns(List.of());
        result.setMathOperations(List.of("CALCULATION"));
        result.setComplexityLevel("Medium");
        result.setConfidenceScore(0.3); // Low confidence

        return result;
    }

    // Helper methods for fallback parsing
    private String extractBusinessLogic(String response) {
        // Extract business logic description
        return "Formula requirement extracted from: " + response.substring(0, Math.min(200, response.length()));
    }

    private List<String> extractFunctionCategories(String response) {
        Set<String> categories = Set.of();
        String lowerResponse = response.toLowerCase();

        if (lowerResponse.contains("math") || lowerResponse.contains("calculate") || lowerResponse.contains("sum")) {
            categories = Set.of("MATH");
        }
        if (lowerResponse.contains("date") || lowerResponse.contains("time")) {
            categories = Set.of("DATE_TIME");
        }
        if (lowerResponse.contains("if") || lowerResponse.contains("condition")) {
            categories = Set.of("LOGICAL");
        }
        if (lowerResponse.contains("text") || lowerResponse.contains("string")) {
            categories = Set.of("TEXT");
        }

        return categories.isEmpty() ? List.of("MATH") : List.copyOf(categories);
    }

    private String extractOutputDataType(String response) {
        String lowerResponse = response.toLowerCase();

        if (lowerResponse.contains("number") || lowerResponse.contains("calculate")) return "Number";
        if (lowerResponse.contains("text") || lowerResponse.contains("string")) return "Text";
        if (lowerResponse.contains("true") || lowerResponse.contains("false") || lowerResponse.contains("boolean"))
            return "Boolean";
        if (lowerResponse.contains("date")) return "Date";
        if (lowerResponse.contains("currency") || lowerResponse.contains("money")) return "Currency";
        if (lowerResponse.contains("percent")) return "Percent";

        return "Number"; // Default
    }

    private List<String> extractFieldReferences(String response) {
        // Simple field extraction - look for capitalized words that might be field names
        return List.of(); // Placeholder - could be enhanced with regex
    }

    private List<String> extractConditionalPatterns(String response) {
        Set<String> patterns = Set.of();
        String lowerResponse = response.toLowerCase();

        if (lowerResponse.contains("if")) {
            patterns = Set.of("IF_THEN_ELSE");
        }
        if (lowerResponse.contains("and") || lowerResponse.contains("or")) {
            patterns = Set.of("AND_OR_LOGIC");
        }

        return List.copyOf(patterns);
    }

    /**
     * Create memory advisor for conversation context
     */
    private MessageChatMemoryAdvisor createMemoryAdvisor(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId != null ? sessionId : "query-analysis-session")
                .build();
    }

    /**
     * Data class representing the structured analysis result
     */
    public static class QueryAnalysisResult {

        @JsonProperty("businessLogic")
        private String businessLogic;

        @JsonProperty("functionCategories")
        private List<String> functionCategories;

        @JsonProperty("outputDataType")
        private String outputDataType;

        @JsonProperty("fieldReferences")
        private List<String> fieldReferences;

        @JsonProperty("conditionalPatterns")
        private List<String> conditionalPatterns;

        @JsonProperty("mathOperations")
        private List<String> mathOperations;

        @JsonProperty("dateTimeOperations")
        private List<String> dateTimeOperations;

        @JsonProperty("textOperations")
        private List<String> textOperations;

        @JsonProperty("logicalOperations")
        private List<String> logicalOperations;

        @JsonProperty("complexityLevel")
        private String complexityLevel;

        @JsonProperty("confidenceScore")
        private Double confidenceScore;

        // Constructors
        public QueryAnalysisResult() {
        }

        // Getters and Setters
        public String getBusinessLogic() {
            return businessLogic;
        }

        public void setBusinessLogic(String businessLogic) {
            this.businessLogic = businessLogic;
        }

        public List<String> getFunctionCategories() {
            return functionCategories;
        }

        public void setFunctionCategories(List<String> functionCategories) {
            this.functionCategories = functionCategories;
        }

        public String getOutputDataType() {
            return outputDataType;
        }

        public void setOutputDataType(String outputDataType) {
            this.outputDataType = outputDataType;
        }

        public List<String> getFieldReferences() {
            return fieldReferences;
        }

        public void setFieldReferences(List<String> fieldReferences) {
            this.fieldReferences = fieldReferences;
        }

        public List<String> getConditionalPatterns() {
            return conditionalPatterns;
        }

        public void setConditionalPatterns(List<String> conditionalPatterns) {
            this.conditionalPatterns = conditionalPatterns;
        }

        public List<String> getMathOperations() {
            return mathOperations;
        }

        public void setMathOperations(List<String> mathOperations) {
            this.mathOperations = mathOperations;
        }

        public List<String> getDateTimeOperations() {
            return dateTimeOperations;
        }

        public void setDateTimeOperations(List<String> dateTimeOperations) {
            this.dateTimeOperations = dateTimeOperations;
        }

        public List<String> getTextOperations() {
            return textOperations;
        }

        public void setTextOperations(List<String> textOperations) {
            this.textOperations = textOperations;
        }

        public List<String> getLogicalOperations() {
            return logicalOperations;
        }

        public void setLogicalOperations(List<String> logicalOperations) {
            this.logicalOperations = logicalOperations;
        }

        public String getComplexityLevel() {
            return complexityLevel;
        }

        public void setComplexityLevel(String complexityLevel) {
            this.complexityLevel = complexityLevel;
        }

        public Double getConfidenceScore() {
            return confidenceScore;
        }

        public void setConfidenceScore(Double confidenceScore) {
            this.confidenceScore = confidenceScore;
        }

        @Override
        public String toString() {
            return String.format("QueryAnalysisResult{businessLogic='%s', functionCategories=%s, outputDataType='%s', " +
                                 "fieldReferences=%s, conditionalPatterns=%s, complexityLevel='%s', confidenceScore=%.2f}",
                    businessLogic, functionCategories, outputDataType, fieldReferences,
                    conditionalPatterns, complexityLevel, confidenceScore != null ? confidenceScore : 0.0);
        }
    }
}