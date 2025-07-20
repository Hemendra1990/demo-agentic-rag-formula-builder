package info.hemendra.demoaiopenrouter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 2: Function Mapping & Availability Check Service
 * <p>
 * Purpose: Map Salesforce functions to your CRM equivalents
 * <p>
 * Responsibilities:
 * - Check function availability in your CRM system
 * - Map Salesforce functions to your custom functions
 * - Identify missing functions and suggest alternatives
 * - Validate function compatibility and limitations
 */
@Service
public class FunctionMappingAgent {

    private static final Logger log = LoggerFactory.getLogger(FunctionMappingAgent.class);

    @Qualifier("openAiChatClient")
    @Autowired
    private ChatClient chatClient;

    private final ObjectMapper objectMapper;

    @Autowired
    private ChatMemory chatMemory;

    // Function mapping knowledge base
    private final Map<String, FunctionMapping> functionMappings;
    private final Map<String, FunctionAvailability> availabilityMatrix;

    public FunctionMappingAgent() {
        this.objectMapper = new ObjectMapper();
        this.functionMappings = initializeFunctionMappings();
        this.availabilityMatrix = initializeAvailabilityMatrix();
    }

    /**
     * Main method to map functions and check availability
     */
    public FunctionMappingResult mapFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult, String sessionId) {
        log.info("[FUNCTION_AGENT] Mapping functions for categories: {}", analysisResult.getFunctionCategories());

        try {
            FunctionMappingResult result = new FunctionMappingResult();

            // Process each function category
            for (String category : analysisResult.getFunctionCategories()) {
                processFunctionCategory(category, analysisResult, result);
            }

            // Check for missing functions and suggest alternatives
            identifyMissingFunctions(analysisResult, result);

            // Validate compatibility
            validateCompatibility(result);

            // Use AI for complex mapping scenarios
            if (result.getConfidenceScore() < 0.8) {
                enhanceWithAIMapping(analysisResult, result, sessionId);
            }

            log.info("[FUNCTION_AGENT] Mapping complete - Available: {}, Missing: {}",
                    result.getAvailableFunctions().size(),
                    result.getMissingFunctions().size());

            return result;

        } catch (Exception e) {
            log.error("[FUNCTION_AGENT] Error during function mapping: {}", e.getMessage(), e);
            return createFallbackMapping(analysisResult);
        }
    }

    /**
     * Process individual function category
     */
    private void processFunctionCategory(String category,
                                         QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                         FunctionMappingResult result) {

        log.debug("[FUNCTION_AGENT] Processing category: {}", category);

        switch (category.toUpperCase()) {
            case "MATH":
                processMathFunctions(analysisResult, result);
                break;
            case "DATE_TIME":
                processDateTimeFunctions(analysisResult, result);
                break;
            case "LOGICAL":
                processLogicalFunctions(analysisResult, result);
                break;
            case "TEXT":
                processTextFunctions(analysisResult, result);
                break;
            case "LOOKUP":
                processLookupFunctions(analysisResult, result);
                break;
            case "VALIDATION":
                processValidationFunctions(analysisResult, result);
                break;
            case "CONVERSION":
                processConversionFunctions(analysisResult, result);
                break;
            case "AGGREGATION":
                processAggregationFunctions(analysisResult, result);
                break;
            default:
                log.warn("[FUNCTION_AGENT] Unknown function category: {}", category);
                addGenericFunctions(result);
        }
    }

    /**
     * Process MATH category functions
     */
    private void processMathFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                      FunctionMappingResult result) {

        List<String> mathOperations = analysisResult.getMathOperations();

        if (mathOperations != null && mathOperations.contains("PERCENTAGE")) {
            addAvailableFunction(result, "PERCENTAGE", "MULTIPLY", "value * 0.01", "Basic percentage calculation", 1.0);
        }

        // Standard math functions
        addAvailableFunction(result, "ADD", "ADD", "value1 + value2", "Addition operation", 1.0);
        addAvailableFunction(result, "SUBTRACT", "SUBTRACT", "value1 - value2", "Subtraction operation", 1.0);
        addAvailableFunction(result, "MULTIPLY", "MULTIPLY", "value1 * value2", "Multiplication operation", 1.0);
        addAvailableFunction(result, "DIVIDE", "DIVIDE", "value1 / value2", "Division operation", 1.0);
        addAvailableFunction(result, "ROUND", "ROUND", "ROUND(value, decimals)", "Round to specified decimals", 1.0);
        addAvailableFunction(result, "ABS", "ABS", "ABS(value)", "Absolute value", 1.0);
        addAvailableFunction(result, "CEILING", "CEIL", "CEIL(value)", "Round up to nearest integer", 1.0);
        addAvailableFunction(result, "FLOOR", "FLOOR", "FLOOR(value)", "Round down to nearest integer", 1.0);
    }

    /**
     * Process DATE_TIME category functions
     */
    private void processDateTimeFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                          FunctionMappingResult result) {

        addAvailableFunction(result, "TODAY", "CURRENT_DATE", "CURRENT_DATE()", "Current date", 1.0);
        addAvailableFunction(result, "NOW", "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP()", "Current date and time", 1.0);
        addAvailableFunction(result, "YEAR", "EXTRACT_YEAR", "EXTRACT(YEAR FROM date)", "Extract year from date", 1.0);
        addAvailableFunction(result, "MONTH", "EXTRACT_MONTH", "EXTRACT(MONTH FROM date)", "Extract month from date", 1.0);
        addAvailableFunction(result, "DAY", "EXTRACT_DAY", "EXTRACT(DAY FROM date)", "Extract day from date", 1.0);
        addAvailableFunction(result, "ADDMONTHS", "DATE_ADD_MONTHS", "DATE_ADD(date, INTERVAL months MONTH)", "Add months to date", 0.9);
        addAvailableFunction(result, "DATEVALUE", "PARSE_DATE", "STR_TO_DATE(text, format)", "Convert text to date", 0.8);

        // Functions with limited support
        addPartialFunction(result, "WEEKDAY", "DAYOFWEEK", "DAYOFWEEK(date)", "Day of week (1=Sunday)", 0.7, "Different numbering system");
    }

    /**
     * Process LOGICAL category functions
     */
    private void processLogicalFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                         FunctionMappingResult result) {

        addAvailableFunction(result, "IF", "IF", "IF(condition, true_value, false_value)", "Conditional logic", 1.0);
        addAvailableFunction(result, "AND", "AND", "condition1 AND condition2", "Logical AND", 1.0);
        addAvailableFunction(result, "OR", "OR", "condition1 OR condition2", "Logical OR", 1.0);
        addAvailableFunction(result, "NOT", "NOT", "NOT condition", "Logical NOT", 1.0);
        addAvailableFunction(result, "ISNULL", "IS_NULL", "field IS NULL", "Check for null values", 1.0);
        addAvailableFunction(result, "ISBLANK", "IS_EMPTY", "field = '' OR field IS NULL", "Check for empty values", 0.9);

        // Complex conditional patterns
        List<String> conditionalPatterns = analysisResult.getConditionalPatterns();
        if (conditionalPatterns != null) {
            if (conditionalPatterns.contains("NESTED_IF")) {
                addAvailableFunction(result, "NESTED_IF", "CASE_WHEN", "CASE WHEN condition1 THEN value1 WHEN condition2 THEN value2 ELSE default END", "Complex conditional logic", 0.9);
            }
            if (conditionalPatterns.contains("CASE_WHEN")) {
                addAvailableFunction(result, "CASE", "CASE_WHEN", "CASE WHEN condition THEN value ELSE default END", "Switch-like logic", 1.0);
            }
        }
    }

    /**
     * Process TEXT category functions
     */
    private void processTextFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                      FunctionMappingResult result) {

        addAvailableFunction(result, "CONCATENATE", "CONCAT", "CONCAT(text1, text2, ...)", "Concatenate strings", 1.0);
        addAvailableFunction(result, "LEN", "LENGTH", "LENGTH(text)", "String length", 1.0);
        addAvailableFunction(result, "LEFT", "LEFT", "LEFT(text, length)", "Left substring", 1.0);
        addAvailableFunction(result, "RIGHT", "RIGHT", "RIGHT(text, length)", "Right substring", 1.0);
        addAvailableFunction(result, "MID", "SUBSTRING", "SUBSTRING(text, start, length)", "Middle substring", 1.0);
        addAvailableFunction(result, "UPPER", "UPPER", "UPPER(text)", "Convert to uppercase", 1.0);
        addAvailableFunction(result, "LOWER", "LOWER", "LOWER(text)", "Convert to lowercase", 1.0);
        addAvailableFunction(result, "TRIM", "TRIM", "TRIM(text)", "Remove leading/trailing spaces", 1.0);
        addAvailableFunction(result, "FIND", "LOCATE", "LOCATE(substring, text)", "Find substring position", 0.9);
        addAvailableFunction(result, "SUBSTITUTE", "REPLACE", "REPLACE(text, old_text, new_text)", "Replace text", 1.0);
    }

    /**
     * Process LOOKUP category functions
     */
    private void processLookupFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                        FunctionMappingResult result) {

        // Limited LOOKUP support - these would need custom implementation
        addPartialFunction(result, "VLOOKUP", "JOIN_LOOKUP", "LEFT JOIN table ON condition", "Table join operation", 0.6, "Requires JOIN syntax");
        addPartialFunction(result, "LOOKUP", "SUBQUERY", "(SELECT value FROM table WHERE condition)", "Subquery lookup", 0.7, "Requires subquery");

        addMissingFunction(result, "HLOOKUP", "No direct equivalent", "Horizontal lookup not supported");
        addMissingFunction(result, "INDEX", "Array access syntax needed", "Index-based lookup requires custom logic");
    }

    /**
     * Process VALIDATION category functions
     */
    private void processValidationFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                            FunctionMappingResult result) {

        addAvailableFunction(result, "ISNUMBER", "IS_NUMERIC", "field REGEXP '^[0-9]+$'", "Check if numeric", 0.8);
        addAvailableFunction(result, "ISTEXT", "IS_TEXT", "field REGEXP '^[A-Za-z ]+$'", "Check if text", 0.7);
        addPartialFunction(result, "ISERROR", "TRY_CATCH", "Try-catch block needed", "Error handling", 0.5, "Requires procedural logic");
    }

    /**
     * Process CONVERSION category functions
     */
    private void processConversionFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                            FunctionMappingResult result) {

        addAvailableFunction(result, "VALUE", "CAST_NUMERIC", "CAST(text AS DECIMAL)", "Convert text to number", 0.9);
        addAvailableFunction(result, "TEXT", "CAST_TEXT", "CAST(value AS CHAR)", "Convert value to text", 0.9);
        addAvailableFunction(result, "CURRENCY", "FORMAT_CURRENCY", "FORMAT(value, 2)", "Format as currency", 0.8);
        addPartialFunction(result, "PERCENT", "FORMAT_PERCENT", "CONCAT(value * 100, '%')", "Format as percentage", 0.7, "Manual percentage formatting");
    }

    /**
     * Process AGGREGATION category functions
     */
    private void processAggregationFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                             FunctionMappingResult result) {

        addAvailableFunction(result, "SUM", "SUM", "SUM(field)", "Sum aggregation", 1.0);
        addAvailableFunction(result, "COUNT", "COUNT", "COUNT(field)", "Count aggregation", 1.0);
        addAvailableFunction(result, "AVERAGE", "AVG", "AVG(field)", "Average aggregation", 1.0);
        addAvailableFunction(result, "MIN", "MIN", "MIN(field)", "Minimum value", 1.0);
        addAvailableFunction(result, "MAX", "MAX", "MAX(field)", "Maximum value", 1.0);

        addPartialFunction(result, "MEDIAN", "MEDIAN_CALC", "Complex subquery needed", "Median calculation", 0.6, "No built-in MEDIAN function");
    }

    /**
     * Add generic functions for unknown categories
     */
    private void addGenericFunctions(FunctionMappingResult result) {
        addAvailableFunction(result, "GENERIC", "CUSTOM", "Custom logic required", "Generic function support", 0.5);
    }

    /**
     * Identify missing functions and suggest alternatives
     */
    private void identifyMissingFunctions(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                          FunctionMappingResult result) {

        // Check for commonly requested but unsupported functions
        List<String> allOperations = new ArrayList<>();
        if (analysisResult.getMathOperations() != null) allOperations.addAll(analysisResult.getMathOperations());
        if (analysisResult.getDateTimeOperations() != null)
            allOperations.addAll(analysisResult.getDateTimeOperations());
        if (analysisResult.getTextOperations() != null) allOperations.addAll(analysisResult.getTextOperations());
        if (analysisResult.getLogicalOperations() != null) allOperations.addAll(analysisResult.getLogicalOperations());

        for (String operation : allOperations) {
            if (!isOperationSupported(operation, result)) {
                suggestAlternative(operation, result);
            }
        }
    }

    /**
     * Validate function compatibility
     */
    private void validateCompatibility(FunctionMappingResult result) {
        result.setOverallCompatibility(calculateOverallCompatibility(result));

        // Add compatibility warnings
        for (AvailableFunction func : result.getAvailableFunctions()) {
            if (func.getCompatibilityScore() < 0.8) {
                result.getCompatibilityWarnings().add(
                        String.format("Function %s has limited compatibility: %s",
                                func.getCrmFunction(), func.getLimitations())
                );
            }
        }
    }

    /**
     * Enhance mapping with AI for complex scenarios
     */
    private void enhanceWithAIMapping(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                      FunctionMappingResult result,
                                      String sessionId) {

        try {
            String enhancementPrompt = buildEnhancementPrompt(analysisResult, result);

            String response = chatClient.prompt()
                    .advisors(createMemoryAdvisor(sessionId))
                    .user(enhancementPrompt)
                    .call()
                    .content();

            parseEnhancementResponse(response, result);

        } catch (Exception e) {
            log.warn("[FUNCTION_AGENT] AI enhancement failed: {}", e.getMessage());
        }
    }

    // Helper methods for adding functions
    private void addAvailableFunction(FunctionMappingResult result, String salesforceFunc, String crmFunc,
                                      String syntax, String description, double compatibilityScore) {
        AvailableFunction func = new AvailableFunction();
        func.setSalesforceFunction(salesforceFunc);
        func.setCrmFunction(crmFunc);
        func.setSyntax(syntax);
        func.setDescription(description);
        func.setCompatibilityScore(compatibilityScore);
        func.setFullySupported(compatibilityScore >= 0.9);
        result.getAvailableFunctions().add(func);
    }

    private void addPartialFunction(FunctionMappingResult result, String salesforceFunc, String crmFunc,
                                    String syntax, String description, double compatibilityScore, String limitations) {
        AvailableFunction func = new AvailableFunction();
        func.setSalesforceFunction(salesforceFunc);
        func.setCrmFunction(crmFunc);
        func.setSyntax(syntax);
        func.setDescription(description);
        func.setCompatibilityScore(compatibilityScore);
        func.setFullySupported(false);
        func.setLimitations(limitations);
        result.getAvailableFunctions().add(func);
    }

    private void addMissingFunction(FunctionMappingResult result, String salesforceFunc, String reason, String alternative) {
        MissingFunction missing = new MissingFunction();
        missing.setSalesforceFunction(salesforceFunc);
        missing.setReason(reason);
        missing.setSuggestedAlternative(alternative);
        result.getMissingFunctions().add(missing);
    }

    // Utility methods
    private boolean isOperationSupported(String operation, FunctionMappingResult result) {
        return result.getAvailableFunctions().stream()
                .anyMatch(func -> func.getSalesforceFunction().equalsIgnoreCase(operation));
    }

    private void suggestAlternative(String operation, FunctionMappingResult result) {
        // Suggest alternatives for unsupported operations
        String alternative = switch (operation.toUpperCase()) {
            case "POWER" -> "Use repeated multiplication: value * value";
            case "LOG" -> "Use custom calculation or external function";
            case "REGEX" -> "Use LIKE patterns or substring functions";
            default -> "Consider custom implementation or breaking down into simpler operations";
        };

        addMissingFunction(result, operation, "Not directly supported", alternative);
    }

    private double calculateOverallCompatibility(FunctionMappingResult result) {
        if (result.getAvailableFunctions().isEmpty()) return 0.0;

        return result.getAvailableFunctions().stream()
                .mapToDouble(AvailableFunction::getCompatibilityScore)
                .average()
                .orElse(0.0);
    }

    // Initialize knowledge base
    private Map<String, FunctionMapping> initializeFunctionMappings() {
        // Initialize with predefined mappings - could be loaded from database
        Map<String, FunctionMapping> mappings = new HashMap<>();

        try {
            ClassPathResource resource = new ClassPathResource("formula/formula-functions-metadata.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            JsonNode functionsNode = rootNode.get("functions");

            if (functionsNode != null) {
                functionsNode.fields().forEachRemaining(entry -> {
                    String functionName = entry.getKey();
                    JsonNode functionData = entry.getValue();

                    // Create function mapping from metadata
                    FunctionMapping mapping = new FunctionMapping();
                    mapping.setSalesforceFunction(functionName);
                    mapping.setCrmFunction(functionName); // Default to same name
                    mapping.setCompatibilityScore(1.0); // Default full compatibility
                    mapping.setLimitations("");

                    mappings.put(functionName, mapping);
                });

                log.info("[FUNCTION_AGENT] Loaded {} function mappings from metadata", mappings.size());
            }
        } catch (IOException e) {
            log.error("[FUNCTION_AGENT] Failed to load function mappings: {}", e.getMessage());
        }

        return mappings;
    }

    private Map<String, FunctionAvailability> initializeAvailabilityMatrix() {
        // Initialize availability matrix - could be loaded from configuration
        Map<String, FunctionAvailability> matrix = new HashMap<>();
        // Add availability information here
        return matrix;
    }

    // Fallback and utility methods
    private FunctionMappingResult createFallbackMapping(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        FunctionMappingResult result = new FunctionMappingResult();
        result.setOverallCompatibility(0.3);
        result.setConfidenceScore(0.2);

        // Add basic functions as fallback
        addAvailableFunction(result, "BASIC", "ARITHMETIC", "Basic operations available", "Fallback functions", 0.5);

        return result;
    }

    private String buildEnhancementPrompt(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                          FunctionMappingResult result) {
        return String.format("""
                        Analyze the following function mapping scenario and suggest improvements:
                        
                        Original Requirements: %s
                        Current Mappings: %d functions mapped
                        Missing Functions: %d functions missing
                        
                        Suggest alternative approaches or custom implementations for missing functionality.
                        """,
                analysisResult.getBusinessLogic(),
                result.getAvailableFunctions().size(),
                result.getMissingFunctions().size());
    }

    private void parseEnhancementResponse(String response, FunctionMappingResult result) {
        // Parse AI suggestions and enhance the result
        result.setAiSuggestions(response);
        result.setConfidenceScore(Math.min(1.0, result.getConfidenceScore() + 0.1));
    }

    private MessageChatMemoryAdvisor createMemoryAdvisor(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId != null ? sessionId : "function-mapping-session")
                .build();
    }

    // Data classes
    public static class FunctionMappingResult {
        private List<AvailableFunction> availableFunctions = new ArrayList<>();
        private List<MissingFunction> missingFunctions = new ArrayList<>();
        private List<String> compatibilityWarnings = new ArrayList<>();
        private double overallCompatibility;
        private double confidenceScore = 0.8;
        private String aiSuggestions;

        // Getters and setters
        public List<AvailableFunction> getAvailableFunctions() {
            return availableFunctions;
        }

        public void setAvailableFunctions(List<AvailableFunction> availableFunctions) {
            this.availableFunctions = availableFunctions;
        }

        public List<MissingFunction> getMissingFunctions() {
            return missingFunctions;
        }

        public void setMissingFunctions(List<MissingFunction> missingFunctions) {
            this.missingFunctions = missingFunctions;
        }

        public List<String> getCompatibilityWarnings() {
            return compatibilityWarnings;
        }

        public void setCompatibilityWarnings(List<String> compatibilityWarnings) {
            this.compatibilityWarnings = compatibilityWarnings;
        }

        public double getOverallCompatibility() {
            return overallCompatibility;
        }

        public void setOverallCompatibility(double overallCompatibility) {
            this.overallCompatibility = overallCompatibility;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }

        public void setConfidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
        }

        public String getAiSuggestions() {
            return aiSuggestions;
        }

        public void setAiSuggestions(String aiSuggestions) {
            this.aiSuggestions = aiSuggestions;
        }
    }

    public static class AvailableFunction {
        private String salesforceFunction;
        private String crmFunction;
        private String syntax;
        private String description;
        private double compatibilityScore;
        private boolean fullySupported;
        private String limitations;

        // Getters and setters
        public String getSalesforceFunction() {
            return salesforceFunction;
        }

        public void setSalesforceFunction(String salesforceFunction) {
            this.salesforceFunction = salesforceFunction;
        }

        public String getCrmFunction() {
            return crmFunction;
        }

        public void setCrmFunction(String crmFunction) {
            this.crmFunction = crmFunction;
        }

        public String getSyntax() {
            return syntax;
        }

        public void setSyntax(String syntax) {
            this.syntax = syntax;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public double getCompatibilityScore() {
            return compatibilityScore;
        }

        public void setCompatibilityScore(double compatibilityScore) {
            this.compatibilityScore = compatibilityScore;
        }

        public boolean isFullySupported() {
            return fullySupported;
        }

        public void setFullySupported(boolean fullySupported) {
            this.fullySupported = fullySupported;
        }

        public String getLimitations() {
            return limitations;
        }

        public void setLimitations(String limitations) {
            this.limitations = limitations;
        }
    }

    public static class MissingFunction {
        private String salesforceFunction;
        private String reason;
        private String suggestedAlternative;

        // Getters and setters
        public String getSalesforceFunction() {
            return salesforceFunction;
        }

        public void setSalesforceFunction(String salesforceFunction) {
            this.salesforceFunction = salesforceFunction;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getSuggestedAlternative() {
            return suggestedAlternative;
        }

        public void setSuggestedAlternative(String suggestedAlternative) {
            this.suggestedAlternative = suggestedAlternative;
        }
    }

    // Knowledge base classes
    private static class FunctionMapping {
        private String salesforceFunction;
        private String crmFunction;
        private double compatibilityScore;
        private String limitations;

        // Constructor
        public FunctionMapping() {
        }

        // Getters and setters
        public String getSalesforceFunction() {
            return salesforceFunction;
        }

        public void setSalesforceFunction(String salesforceFunction) {
            this.salesforceFunction = salesforceFunction;
        }

        public String getCrmFunction() {
            return crmFunction;
        }

        public void setCrmFunction(String crmFunction) {
            this.crmFunction = crmFunction;
        }

        public double getCompatibilityScore() {
            return compatibilityScore;
        }

        public void setCompatibilityScore(double compatibilityScore) {
            this.compatibilityScore = compatibilityScore;
        }

        public String getLimitations() {
            return limitations;
        }

        public void setLimitations(String limitations) {
            this.limitations = limitations;
        }
    }

    private static class FunctionAvailability {
        private String functionName;
        private boolean available;
        private String version;
        private List<String> dependencies;

        // Constructor
        public FunctionAvailability() {
        }

        // Getters and setters
        public String getFunctionName() {
            return functionName;
        }

        public void setFunctionName(String functionName) {
            this.functionName = functionName;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies;
        }
    }
}