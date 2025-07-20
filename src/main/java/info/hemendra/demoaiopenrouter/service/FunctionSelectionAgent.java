package info.hemendra.demoaiopenrouter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.hemendra.demoaiopenrouter.model.FormulaFunctionsMetadata;
import info.hemendra.demoaiopenrouter.util.FormulaMetadataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 3: Function Selection & Parameter Mapping Service
 * <p>
 * Purpose: Select optimal functions and map parameters from user requirements
 * <p>
 * Responsibilities:
 * - Select best functions from available mappings
 * - Map user parameters to function parameters
 * - Resolve parameter types and validation
 * - Handle parameter dependencies and relationships
 * - Optimize function selection for performance
 */
@Service
public class FunctionSelectionAgent {

    private static final Logger log = LoggerFactory.getLogger(FunctionSelectionAgent.class);

    @Qualifier("openAiChatClient")
    @Autowired
    private ChatClient chatClient;

    private final ObjectMapper objectMapper;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private FormulaMetadataUtil formulaMetadataUtil;

    public FunctionSelectionAgent() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Main method to select functions and map parameters
     */
    public FunctionSelectionResult selectFunctions(
            QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
            FunctionMappingAgent.FunctionMappingResult mappingResult,
            String sessionId) {

        log.info("[SELECTION_AGENT] Selecting functions for business logic: {}",
                analysisResult.getBusinessLogic());

        try {
            FunctionSelectionResult result = new FunctionSelectionResult();

            // Step 1: Select optimal functions based on requirements
            List<SelectedFunction> selectedFunctions = selectOptimalFunctions(analysisResult, mappingResult);
            result.setSelectedFunctions(selectedFunctions);

            // Step 2: Map parameters for each selected function
            mapFunctionParameters(selectedFunctions, analysisResult, sessionId);

            // Step 3: Resolve parameter dependencies
            resolveParameterDependencies(selectedFunctions, analysisResult);

            // Step 4: Validate parameter mappings
            validateParameterMappings(selectedFunctions, result);

            // Step 5: Optimize function selection
            optimizeFunctionSelection(selectedFunctions, result);

            // Step 6: Generate execution plan
            generateExecutionPlan(selectedFunctions, result);

            log.info("[SELECTION_AGENT] Function selection complete - Selected: {}, Confidence: {}",
                    result.getSelectedFunctions().size(), result.getConfidenceScore());

            return result;

        } catch (Exception e) {
            log.error("[SELECTION_AGENT] Error during function selection: {}", e.getMessage(), e);
            return createFallbackSelection(analysisResult, mappingResult);
        }
    }

    /**
     * Select optimal functions based on requirements and availability
     */
    private List<SelectedFunction> selectOptimalFunctions(
            QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
            FunctionMappingAgent.FunctionMappingResult mappingResult) {

        List<SelectedFunction> selectedFunctions = new ArrayList<>();

        // Process available functions based on analysis requirements
        for (FunctionMappingAgent.AvailableFunction availableFunc : mappingResult.getAvailableFunctions()) {

            // Check if function is needed based on analysis
            if (isFunctionNeeded(availableFunc, analysisResult)) {

                SelectedFunction selectedFunc = new SelectedFunction();
                selectedFunc.setFunctionName(availableFunc.getCrmFunction());
                selectedFunc.setOriginalSalesforceFunction(availableFunc.getSalesforceFunction());
                selectedFunc.setSyntax(availableFunc.getSyntax());
                selectedFunc.setDescription(availableFunc.getDescription());
                selectedFunc.setCompatibilityScore(availableFunc.getCompatibilityScore());
                selectedFunc.setPriority(calculateFunctionPriority(availableFunc, analysisResult));

                // Get detailed function metadata
                FormulaFunctionsMetadata.FunctionDefinition funcDef =
                        formulaMetadataUtil.getFunction(availableFunc.getSalesforceFunction());

                if (funcDef != null) {
                    selectedFunc.setReturnType(funcDef.getReturnType());
                    selectedFunc.setCategory(funcDef.getCategory());
                    selectedFunc.setExamples(funcDef.getExamples());
                    selectedFunc.setParameterDefinitions(funcDef.getParameters());
                }

                selectedFunctions.add(selectedFunc);
            }
        }

        // Sort by priority and compatibility
        selectedFunctions.sort((f1, f2) -> {
            int priorityCompare = Double.compare(f2.getPriority(), f1.getPriority());
            if (priorityCompare != 0) return priorityCompare;
            return Double.compare(f2.getCompatibilityScore(), f1.getCompatibilityScore());
        });

        log.info("[SELECTION_AGENT] Selected {} functions from {} available",
                selectedFunctions.size(), mappingResult.getAvailableFunctions().size());

        return selectedFunctions;
    }

    /**
     * Check if a function is needed based on analysis results
     */
    private boolean isFunctionNeeded(FunctionMappingAgent.AvailableFunction availableFunc,
                                     QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {

        String functionName = availableFunc.getSalesforceFunction();

        // Check if function category matches requirements
        if (analysisResult.getFunctionCategories().contains(getCategoryFromFunction(functionName))) {
            return true;
        }

        // Check if function matches specific operations
        if (analysisResult.getMathOperations() != null &&
            analysisResult.getMathOperations().contains(functionName)) {
            return true;
        }

        if (analysisResult.getLogicalOperations() != null &&
            analysisResult.getLogicalOperations().contains(functionName)) {
            return true;
        }

        if (analysisResult.getTextOperations() != null &&
            analysisResult.getTextOperations().contains(functionName)) {
            return true;
        }

        if (analysisResult.getDateTimeOperations() != null &&
            analysisResult.getDateTimeOperations().contains(functionName)) {
            return true;
        }

        // Check if function is mentioned in business logic
        String businessLogic = analysisResult.getBusinessLogic().toLowerCase();
        if (businessLogic.contains(functionName.toLowerCase())) {
            return true;
        }

        return false;
    }

    /**
     * Calculate function priority based on requirements
     */
    private double calculateFunctionPriority(FunctionMappingAgent.AvailableFunction availableFunc,
                                             QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {

        double priority = 0.0;

        // Base priority from compatibility score
        priority += availableFunc.getCompatibilityScore() * 0.4;

        // Priority boost for functions directly mentioned in requirements
        if (analysisResult.getBusinessLogic().toLowerCase()
                .contains(availableFunc.getSalesforceFunction().toLowerCase())) {
            priority += 0.3;
        }

        // Priority boost for functions matching output data type
        FormulaFunctionsMetadata.FunctionDefinition funcDef =
                formulaMetadataUtil.getFunction(availableFunc.getSalesforceFunction());

        if (funcDef != null && funcDef.getReturnType().equals(analysisResult.getOutputDataType())) {
            priority += 0.2;
        }

        // Priority boost for essential functions
        if (isEssentialFunction(availableFunc.getSalesforceFunction())) {
            priority += 0.1;
        }

        return Math.min(priority, 1.0);
    }

    /**
     * Map parameters for selected functions
     */
    private void mapFunctionParameters(List<SelectedFunction> selectedFunctions,
                                       QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                       String sessionId) {

        for (SelectedFunction selectedFunc : selectedFunctions) {

            log.debug("[SELECTION_AGENT] Mapping parameters for function: {}", selectedFunc.getFunctionName());

            List<ParameterMapping> mappings = new ArrayList<>();

            if (selectedFunc.getParameterDefinitions() != null) {
                for (FormulaFunctionsMetadata.ParameterDefinition paramDef : selectedFunc.getParameterDefinitions()) {

                    ParameterMapping mapping = new ParameterMapping();
                    mapping.setParameterName(paramDef.getName());
                    mapping.setParameterType(paramDef.getType());
                    mapping.setRequired(paramDef.isRequired());
                    mapping.setDescription(paramDef.getDescription());

                    // Map parameter value based on user requirements
                    String mappedValue = mapParameterValue(paramDef, analysisResult, sessionId);
                    mapping.setMappedValue(mappedValue);
                    mapping.setMappingSource(determineMappingSource(paramDef, analysisResult));

                    mappings.add(mapping);
                }
            }

            selectedFunc.setParameterMappings(mappings);
        }
    }

    /**
     * Map parameter value based on user requirements
     */
    private String mapParameterValue(FormulaFunctionsMetadata.ParameterDefinition paramDef,
                                     QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                     String sessionId) {

        String paramName = paramDef.getName().toLowerCase();
        String businessLogic = analysisResult.getBusinessLogic().toLowerCase();

        // Try to extract field references
        if (analysisResult.getFieldReferences() != null && !analysisResult.getFieldReferences().isEmpty()) {
            for (String fieldRef : analysisResult.getFieldReferences()) {
                if (paramName.contains("field") || paramName.contains("value")) {
                    return fieldRef;
                }
            }
        }

        // Common parameter patterns
        if (paramName.contains("condition") && businessLogic.contains("if")) {
            return extractConditionFromLogic(businessLogic);
        }

        if (paramName.contains("text") || paramName.contains("string")) {
            return extractTextFromLogic(businessLogic);
        }

        if (paramName.contains("number") || paramName.contains("amount")) {
            return extractNumberFromLogic(businessLogic);
        }

        if (paramName.contains("date")) {
            return extractDateFromLogic(businessLogic);
        }

        // Default values based on parameter type
        return getDefaultValueForType(paramDef.getType());
    }

    /**
     * Resolve parameter dependencies between functions
     */
    private void resolveParameterDependencies(List<SelectedFunction> selectedFunctions,
                                              QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {

        log.debug("[SELECTION_AGENT] Resolving parameter dependencies");

        for (int i = 0; i < selectedFunctions.size(); i++) {
            SelectedFunction currentFunc = selectedFunctions.get(i);

            for (int j = i + 1; j < selectedFunctions.size(); j++) {
                SelectedFunction nextFunc = selectedFunctions.get(j);

                // Check if next function can use output of current function
                if (canChainFunctions(currentFunc, nextFunc)) {

                    FunctionDependency dependency = new FunctionDependency();
                    dependency.setSourceFunction(currentFunc.getFunctionName());
                    dependency.setTargetFunction(nextFunc.getFunctionName());
                    dependency.setDependencyType("OUTPUT_INPUT");
                    dependency.setDescription(String.format("Output of %s feeds into %s",
                            currentFunc.getFunctionName(), nextFunc.getFunctionName()));

                    if (currentFunc.getDependencies() == null) {
                        currentFunc.setDependencies(new ArrayList<>());
                    }
                    currentFunc.getDependencies().add(dependency);
                }
            }
        }
    }

    /**
     * Validate parameter mappings
     */
    private void validateParameterMappings(List<SelectedFunction> selectedFunctions,
                                           FunctionSelectionResult result) {

        List<String> validationErrors = new ArrayList<>();

        for (SelectedFunction selectedFunc : selectedFunctions) {

            if (selectedFunc.getParameterMappings() != null) {
                for (ParameterMapping mapping : selectedFunc.getParameterMappings()) {

                    // Check required parameters
                    if (mapping.isRequired() &&
                        (mapping.getMappedValue() == null || mapping.getMappedValue().trim().isEmpty())) {

                        validationErrors.add(String.format(
                                "Required parameter '%s' for function '%s' is missing",
                                mapping.getParameterName(), selectedFunc.getFunctionName()));
                    }

                    // Check parameter type compatibility
                    if (mapping.getMappedValue() != null &&
                        !isTypeCompatible(mapping.getParameterType(), mapping.getMappedValue())) {

                        validationErrors.add(String.format(
                                "Parameter '%s' type mismatch in function '%s': expected %s",
                                mapping.getParameterName(), selectedFunc.getFunctionName(),
                                mapping.getParameterType()));
                    }
                }
            }
        }

        result.setValidationErrors(validationErrors);
        result.setValidationPassed(validationErrors.isEmpty());
    }

    /**
     * Optimize function selection for performance and correctness
     */
    private void optimizeFunctionSelection(List<SelectedFunction> selectedFunctions,
                                           FunctionSelectionResult result) {

        log.debug("[SELECTION_AGENT] Optimizing function selection");

        // Remove redundant functions
        selectedFunctions.removeIf(func -> isRedundantFunction(func, selectedFunctions));

        // Optimize function order for execution
        selectedFunctions.sort((f1, f2) -> {
            // Functions with no dependencies should come first
            int dep1 = f1.getDependencies() != null ? f1.getDependencies().size() : 0;
            int dep2 = f2.getDependencies() != null ? f2.getDependencies().size() : 0;
            return Integer.compare(dep1, dep2);
        });

        // Calculate optimization score
        double optimizationScore = calculateOptimizationScore(selectedFunctions);
        result.setOptimizationScore(optimizationScore);

        log.info("[SELECTION_AGENT] Function selection optimized. Score: {}", optimizationScore);
    }

    /**
     * Generate execution plan for selected functions
     */
    private void generateExecutionPlan(List<SelectedFunction> selectedFunctions,
                                       FunctionSelectionResult result) {

        ExecutionPlan plan = new ExecutionPlan();
        plan.setSteps(new ArrayList<>());

        for (int i = 0; i < selectedFunctions.size(); i++) {
            SelectedFunction func = selectedFunctions.get(i);

            ExecutionStep step = new ExecutionStep();
            step.setStepNumber(i + 1);
            step.setFunctionName(func.getFunctionName());
            step.setDescription(String.format("Execute %s with parameters: %s",
                    func.getFunctionName(), formatParameters(func.getParameterMappings())));
            step.setExpectedOutput(func.getReturnType());

            plan.getSteps().add(step);
        }

        plan.setEstimatedComplexity(calculateComplexity(selectedFunctions));
        plan.setEstimatedPerformance(calculatePerformance(selectedFunctions));

        result.setExecutionPlan(plan);
    }

    // Helper methods
    private String getCategoryFromFunction(String functionName) {
        FormulaFunctionsMetadata.FunctionDefinition funcDef = formulaMetadataUtil.getFunction(functionName);
        return funcDef != null ? funcDef.getCategory() : "Unknown";
    }

    private boolean isEssentialFunction(String functionName) {
        return List.of("IF", "AND", "OR", "CONCATENATE", "TEXT", "VALUE").contains(functionName);
    }

    private String determineMappingSource(FormulaFunctionsMetadata.ParameterDefinition paramDef,
                                          QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        return "USER_REQUIREMENTS";
    }

    private String extractConditionFromLogic(String businessLogic) {
        // Simple condition extraction - could be enhanced with NLP
        if (businessLogic.contains("if")) {
            return "condition"; // Placeholder
        }
        return "true";
    }

    private String extractTextFromLogic(String businessLogic) {
        return "text_value"; // Placeholder
    }

    private String extractNumberFromLogic(String businessLogic) {
        return "number_value"; // Placeholder
    }

    private String extractDateFromLogic(String businessLogic) {
        return "date_value"; // Placeholder
    }

    private String getDefaultValueForType(String type) {
        return switch (type.toLowerCase()) {
            case "string" -> "''";
            case "boolean" -> "false";
            case "int", "double" -> "0";
            case "date" -> "TODAY()";
            default -> "null";
        };
    }

    private boolean canChainFunctions(SelectedFunction source, SelectedFunction target) {
        // Check if source output type matches target input type
        if (source.getReturnType() != null && target.getParameterMappings() != null) {
            for (ParameterMapping param : target.getParameterMappings()) {
                if (isTypeCompatible(param.getParameterType(), source.getReturnType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTypeCompatible(String expectedType, String actualValue) {
        // Simple type compatibility check - could be enhanced
        return true; // Placeholder
    }

    private boolean isRedundantFunction(SelectedFunction func, List<SelectedFunction> allFunctions) {
        // Check if function provides unique value
        return false; // Placeholder
    }

    private double calculateOptimizationScore(List<SelectedFunction> selectedFunctions) {
        // Calculate optimization score based on efficiency metrics
        return 0.8; // Placeholder
    }

    private String formatParameters(List<ParameterMapping> parameterMappings) {
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return "none";
        }

        return parameterMappings.stream()
                .map(p -> p.getParameterName() + "=" + p.getMappedValue())
                .collect(Collectors.joining(", "));
    }

    private String calculateComplexity(List<SelectedFunction> selectedFunctions) {
        int complexity = selectedFunctions.size();
        if (complexity <= 2) return "Simple";
        if (complexity <= 5) return "Medium";
        return "Complex";
    }

    private String calculatePerformance(List<SelectedFunction> selectedFunctions) {
        // Estimate performance based on function types
        return "Good"; // Placeholder
    }

    private FunctionSelectionResult createFallbackSelection(
            QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
            FunctionMappingAgent.FunctionMappingResult mappingResult) {

        FunctionSelectionResult result = new FunctionSelectionResult();
        result.setSelectedFunctions(new ArrayList<>());
        result.setValidationErrors(List.of("Failed to select functions"));
        result.setValidationPassed(false);
        result.setConfidenceScore(0.3);

        return result;
    }

    private MessageChatMemoryAdvisor createMemoryAdvisor(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId != null ? sessionId : "function-selection-session")
                .build();
    }

    // Data classes
    public static class FunctionSelectionResult {
        private List<SelectedFunction> selectedFunctions;
        private List<String> validationErrors;
        private boolean validationPassed;
        private double confidenceScore = 0.8;
        private double optimizationScore;
        private ExecutionPlan executionPlan;

        // Getters and setters
        public List<SelectedFunction> getSelectedFunctions() {
            return selectedFunctions;
        }

        public void setSelectedFunctions(List<SelectedFunction> selectedFunctions) {
            this.selectedFunctions = selectedFunctions;
        }

        public List<String> getValidationErrors() {
            return validationErrors;
        }

        public void setValidationErrors(List<String> validationErrors) {
            this.validationErrors = validationErrors;
        }

        public boolean isValidationPassed() {
            return validationPassed;
        }

        public void setValidationPassed(boolean validationPassed) {
            this.validationPassed = validationPassed;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }

        public void setConfidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
        }

        public double getOptimizationScore() {
            return optimizationScore;
        }

        public void setOptimizationScore(double optimizationScore) {
            this.optimizationScore = optimizationScore;
        }

        public ExecutionPlan getExecutionPlan() {
            return executionPlan;
        }

        public void setExecutionPlan(ExecutionPlan executionPlan) {
            this.executionPlan = executionPlan;
        }
    }

    public static class SelectedFunction {
        private String functionName;
        private String originalSalesforceFunction;
        private String syntax;
        private String description;
        private String returnType;
        private String category;
        private double compatibilityScore;
        private double priority;
        private List<String> examples;
        private List<FormulaFunctionsMetadata.ParameterDefinition> parameterDefinitions;
        private List<ParameterMapping> parameterMappings;
        private List<FunctionDependency> dependencies;

        // Getters and setters
        public String getFunctionName() {
            return functionName;
        }

        public void setFunctionName(String functionName) {
            this.functionName = functionName;
        }

        public String getOriginalSalesforceFunction() {
            return originalSalesforceFunction;
        }

        public void setOriginalSalesforceFunction(String originalSalesforceFunction) {
            this.originalSalesforceFunction = originalSalesforceFunction;
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

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public double getCompatibilityScore() {
            return compatibilityScore;
        }

        public void setCompatibilityScore(double compatibilityScore) {
            this.compatibilityScore = compatibilityScore;
        }

        public double getPriority() {
            return priority;
        }

        public void setPriority(double priority) {
            this.priority = priority;
        }

        public List<String> getExamples() {
            return examples;
        }

        public void setExamples(List<String> examples) {
            this.examples = examples;
        }

        public List<FormulaFunctionsMetadata.ParameterDefinition> getParameterDefinitions() {
            return parameterDefinitions;
        }

        public void setParameterDefinitions(List<FormulaFunctionsMetadata.ParameterDefinition> parameterDefinitions) {
            this.parameterDefinitions = parameterDefinitions;
        }

        public List<ParameterMapping> getParameterMappings() {
            return parameterMappings;
        }

        public void setParameterMappings(List<ParameterMapping> parameterMappings) {
            this.parameterMappings = parameterMappings;
        }

        public List<FunctionDependency> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<FunctionDependency> dependencies) {
            this.dependencies = dependencies;
        }
    }

    public static class ParameterMapping {
        private String parameterName;
        private String parameterType;
        private boolean required;
        private String description;
        private String mappedValue;
        private String mappingSource;

        // Getters and setters
        public String getParameterName() {
            return parameterName;
        }

        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        public String getParameterType() {
            return parameterType;
        }

        public void setParameterType(String parameterType) {
            this.parameterType = parameterType;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getMappedValue() {
            return mappedValue;
        }

        public void setMappedValue(String mappedValue) {
            this.mappedValue = mappedValue;
        }

        public String getMappingSource() {
            return mappingSource;
        }

        public void setMappingSource(String mappingSource) {
            this.mappingSource = mappingSource;
        }
    }

    public static class FunctionDependency {
        private String sourceFunction;
        private String targetFunction;
        private String dependencyType;
        private String description;

        // Getters and setters
        public String getSourceFunction() {
            return sourceFunction;
        }

        public void setSourceFunction(String sourceFunction) {
            this.sourceFunction = sourceFunction;
        }

        public String getTargetFunction() {
            return targetFunction;
        }

        public void setTargetFunction(String targetFunction) {
            this.targetFunction = targetFunction;
        }

        public String getDependencyType() {
            return dependencyType;
        }

        public void setDependencyType(String dependencyType) {
            this.dependencyType = dependencyType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class ExecutionPlan {
        private List<ExecutionStep> steps;
        private String estimatedComplexity;
        private String estimatedPerformance;

        // Getters and setters
        public List<ExecutionStep> getSteps() {
            return steps;
        }

        public void setSteps(List<ExecutionStep> steps) {
            this.steps = steps;
        }

        public String getEstimatedComplexity() {
            return estimatedComplexity;
        }

        public void setEstimatedComplexity(String estimatedComplexity) {
            this.estimatedComplexity = estimatedComplexity;
        }

        public String getEstimatedPerformance() {
            return estimatedPerformance;
        }

        public void setEstimatedPerformance(String estimatedPerformance) {
            this.estimatedPerformance = estimatedPerformance;
        }
    }

    public static class ExecutionStep {
        private int stepNumber;
        private String functionName;
        private String description;
        private String expectedOutput;

        // Getters and setters
        public int getStepNumber() {
            return stepNumber;
        }

        public void setStepNumber(int stepNumber) {
            this.stepNumber = stepNumber;
        }

        public String getFunctionName() {
            return functionName;
        }

        public void setFunctionName(String functionName) {
            this.functionName = functionName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExpectedOutput() {
            return expectedOutput;
        }

        public void setExpectedOutput(String expectedOutput) {
            this.expectedOutput = expectedOutput;
        }
    }
}