package info.hemendra.demoaiopenrouter.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Pattern;

/**
 * Agent 4: Formula Synthesis & Validation Service
 * 
 * Purpose: Synthesize complete formulas from selected functions and validate correctness
 * 
 * Responsibilities:
 * - Combine selected functions into complete formulas
 * - Handle nested function calls and dependencies
 * - Validate formula syntax and semantics
 * - Optimize formula structure for performance
 * - Generate multiple formula variations
 * - Provide syntax validation and error detection
 */
@Service
public class FormulaSynthesisAgent {
    
    private static final Logger log = LoggerFactory.getLogger(FormulaSynthesisAgent.class);

    @Qualifier("openAiChatClient")
    @Autowired
    private ChatClient chatClient;


    private final ObjectMapper objectMapper;
    
    @Autowired
    private ChatMemory chatMemory;
    
    @Autowired
    private FormulaMetadataUtil formulaMetadataUtil;
    
    // Formula validation patterns
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*\\(.*\\)$");
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern NESTED_FUNCTION_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9_]*\\(");
    
    public FormulaSynthesisAgent() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Main method to synthesize formulas from selected functions
     */
    public FormulaSynthesisResult synthesizeFormula(
            QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
            FunctionMappingAgent.FunctionMappingResult mappingResult,
            FunctionSelectionAgent.FunctionSelectionResult selectionResult,
            String sessionId) {
        
        log.info("[SYNTHESIS_AGENT] Synthesizing formula for business logic: {}", 
                analysisResult.getBusinessLogic());
        
        try {
            FormulaSynthesisResult result = new FormulaSynthesisResult();
            
            // Step 1: Generate primary formula
            String primaryFormula = generatePrimaryFormula(selectionResult, analysisResult);
            result.setPrimaryFormula(primaryFormula);
            
            // Step 2: Generate alternative formulas
            List<String> alternativeFormulas = generateAlternativeFormulas(selectionResult, analysisResult);
            result.setAlternativeFormulas(alternativeFormulas);
            
            // Step 3: Validate all formulas
            validateFormulas(result);
            
            // Step 4: Optimize formulas
            optimizeFormulas(result);
            
            // Step 5: Generate explanation
            generateFormulaExplanation(result, analysisResult);
            
            // Step 6: Add usage examples
            generateUsageExamples(result, analysisResult);
            
            log.info("[SYNTHESIS_AGENT] Formula synthesis complete. Primary: {}", 
                    result.getPrimaryFormula());
            
            return result;
            
        } catch (Exception e) {
            log.error("[SYNTHESIS_AGENT] Error during formula synthesis: {}", e.getMessage(), e);
            return createFallbackSynthesis(analysisResult);
        }
    }
    
    /**
     * Generate the primary formula from selected functions
     */
    private String generatePrimaryFormula(FunctionSelectionAgent.FunctionSelectionResult selectionResult,
                                        QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        
        List<FunctionSelectionAgent.SelectedFunction> selectedFunctions = selectionResult.getSelectedFunctions();
        
        if (selectedFunctions.isEmpty()) {
            return "No functions selected";
        }
        
        // Sort functions by execution order
        selectedFunctions.sort((f1, f2) -> {
            double p1 = f1.getPriority();
            double p2 = f2.getPriority();
            return Double.compare(p2, p1);
        });
        
        // Handle different formula patterns
        String formula = switch (analysisResult.getComplexityLevel()) {
            case "Simple" -> generateSimpleFormula(selectedFunctions);
            case "Medium" -> generateMediumFormula(selectedFunctions);
            case "Complex" -> generateComplexFormula(selectedFunctions);
            default -> generateGenericFormula(selectedFunctions);
        };
        
        log.info("[SYNTHESIS_AGENT] Generated primary formula: {}", formula);
        return formula;
    }
    
    /**
     * Generate simple formula with single function
     */
    private String generateSimpleFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        
        if (selectedFunctions.isEmpty()) {
            return "VALUE(0)";
        }
        
        FunctionSelectionAgent.SelectedFunction primaryFunction = selectedFunctions.get(0);
        
        StringBuilder formula = new StringBuilder();
        formula.append(primaryFunction.getFunctionName()).append("(");
        
        if (primaryFunction.getParameterMappings() != null && !primaryFunction.getParameterMappings().isEmpty()) {
            List<String> paramValues = new ArrayList<>();
            
            for (FunctionSelectionAgent.ParameterMapping param : primaryFunction.getParameterMappings()) {
                String value = param.getMappedValue();
                if (value != null && !value.trim().isEmpty()) {
                    // Add quotes for string parameters
                    if (param.getParameterType().equals("String") && !value.startsWith("'") && !value.startsWith("\"")) {
                        value = "'" + value + "'";
                    }
                    paramValues.add(value);
                }
            }
            
            formula.append(String.join(", ", paramValues));
        }
        
        formula.append(")");
        
        return formula.toString();
    }
    
    /**
     * Generate medium complexity formula with multiple functions
     */
    private String generateMediumFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        
        if (selectedFunctions.size() == 1) {
            return generateSimpleFormula(selectedFunctions);
        }
        
        // Find logical functions for combination
        FunctionSelectionAgent.SelectedFunction logicalFunction = selectedFunctions.stream()
                .filter(f -> "Logical".equals(f.getCategory()))
                .findFirst()
                .orElse(selectedFunctions.get(0));
        
        if ("IF".equals(logicalFunction.getFunctionName())) {
            return generateIfFormula(selectedFunctions);
        } else if ("CASE".equals(logicalFunction.getFunctionName())) {
            return generateCaseFormula(selectedFunctions);
        } else {
            return generateChainedFormula(selectedFunctions);
        }
    }
    
    /**
     * Generate complex formula with nested functions
     */
    private String generateComplexFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        
        StringBuilder formula = new StringBuilder();
        
        // Find the primary logical function
        FunctionSelectionAgent.SelectedFunction primaryFunction = selectedFunctions.stream()
                .filter(f -> "Logical".equals(f.getCategory()))
                .findFirst()
                .orElse(selectedFunctions.get(0));
        
        // Build nested formula structure
        if ("IF".equals(primaryFunction.getFunctionName())) {
            formula.append("IF(");
            
            // Add condition
            formula.append(buildCondition(selectedFunctions));
            formula.append(", ");
            
            // Add true value
            formula.append(buildTrueValue(selectedFunctions));
            formula.append(", ");
            
            // Add false value
            formula.append(buildFalseValue(selectedFunctions));
            formula.append(")");
        } else {
            // Build complex nested structure
            formula.append(buildNestedFormula(selectedFunctions));
        }
        
        return formula.toString();
    }
    
    /**
     * Generate IF-based formula
     */
    private String generateIfFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        
        StringBuilder formula = new StringBuilder("IF(");
        
        // Find condition
        String condition = extractCondition(selectedFunctions);
        formula.append(condition);
        
        // Find true/false values
        String trueValue = extractTrueValue(selectedFunctions);
        String falseValue = extractFalseValue(selectedFunctions);
        
        formula.append(", ").append(trueValue);
        formula.append(", ").append(falseValue);
        formula.append(")");
        
        return formula.toString();
    }
    
    /**
     * Generate CASE-based formula
     */
    private String generateCaseFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        
        StringBuilder formula = new StringBuilder("CASE(");
        
        // Add test expression
        formula.append("field_value");
        
        // Add case values
        formula.append(", 'value1', 'result1'");
        formula.append(", 'value2', 'result2'");
        formula.append(", 'default_result'");
        
        formula.append(")");
        
        return formula.toString();
    }
    
    /**
     * Generate chained formula with multiple functions
     */
    private String generateChainedFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        
        if (selectedFunctions.size() < 2) {
            return generateSimpleFormula(selectedFunctions);
        }
        
        // Chain functions based on dependencies
        FunctionSelectionAgent.SelectedFunction first = selectedFunctions.get(0);
        FunctionSelectionAgent.SelectedFunction second = selectedFunctions.get(1);
        
        // Create nested structure
        String innerFormula = generateSimpleFormula(List.of(first));
        
        StringBuilder outerFormula = new StringBuilder();
        outerFormula.append(second.getFunctionName()).append("(");
        outerFormula.append(innerFormula);
        
        // Add additional parameters if needed
        if (second.getParameterMappings() != null && second.getParameterMappings().size() > 1) {
            for (int i = 1; i < second.getParameterMappings().size(); i++) {
                FunctionSelectionAgent.ParameterMapping param = second.getParameterMappings().get(i);
                outerFormula.append(", ").append(param.getMappedValue());
            }
        }
        
        outerFormula.append(")");
        
        return outerFormula.toString();
    }
    
    /**
     * Generate alternative formulas for the same requirement
     */
    private List<String> generateAlternativeFormulas(FunctionSelectionAgent.FunctionSelectionResult selectionResult,
                                                    QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        
        List<String> alternatives = new ArrayList<>();
        
        // Generate variations based on different approaches
        alternatives.add(generateOptimizedFormula(selectionResult));
        alternatives.add(generateVerboseFormula(selectionResult));
        alternatives.add(generateCompactFormula(selectionResult));
        
        // Remove duplicates and invalid formulas
        alternatives.removeIf(formula -> formula == null || formula.trim().isEmpty());
        
        return alternatives;
    }
    
    /**
     * Validate all generated formulas
     */
    private void validateFormulas(FormulaSynthesisResult result) {
        
        List<FormulaValidation> validations = new ArrayList<>();
        
        // Validate primary formula
        FormulaValidation primaryValidation = validateSingleFormula(result.getPrimaryFormula(), "Primary");
        validations.add(primaryValidation);
        
        // Validate alternative formulas
        if (result.getAlternativeFormulas() != null) {
            for (int i = 0; i < result.getAlternativeFormulas().size(); i++) {
                String formula = result.getAlternativeFormulas().get(i);
                FormulaValidation validation = validateSingleFormula(formula, "Alternative " + (i + 1));
                validations.add(validation);
            }
        }
        
        result.setValidations(validations);
        
        // Set overall validation status
        boolean allValid = validations.stream().allMatch(FormulaValidation::isValid);
        result.setAllFormulasValid(allValid);
    }
    
    /**
     * Validate a single formula
     */
    private FormulaValidation validateSingleFormula(String formula, String formulaType) {
        
        FormulaValidation validation = new FormulaValidation();
        validation.setFormulaType(formulaType);
        validation.setFormula(formula);
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Check basic syntax
        if (formula == null || formula.trim().isEmpty()) {
            errors.add("Formula is empty");
        } else {
            // Check parentheses balance
            if (!areParenthesesBalanced(formula)) {
                errors.add("Unbalanced parentheses");
            }
            
            // Check function names
            if (!areValidFunctionNames(formula)) {
                warnings.add("Some function names may not be recognized");
            }
            
            // Check for common syntax errors
            if (formula.contains(",,")) {
                errors.add("Double comma found");
            }
            
            if (formula.contains("()")) {
                warnings.add("Empty parameter list found");
            }
        }
        
        validation.setErrors(errors);
        validation.setWarnings(warnings);
        validation.setValid(errors.isEmpty());
        
        return validation;
    }
    
    /**
     * Optimize formulas for performance and readability
     */
    private void optimizeFormulas(FormulaSynthesisResult result) {
        
        // Optimize primary formula
        String optimizedPrimary = optimizeSingleFormula(result.getPrimaryFormula());
        result.setPrimaryFormula(optimizedPrimary);
        
        // Optimize alternative formulas
        if (result.getAlternativeFormulas() != null) {
            List<String> optimizedAlternatives = result.getAlternativeFormulas().stream()
                    .map(this::optimizeSingleFormula)
                    .collect(java.util.stream.Collectors.toList());
            result.setAlternativeFormulas(optimizedAlternatives);
        }
        
        log.info("[SYNTHESIS_AGENT] Formulas optimized");
    }
    
    /**
     * Optimize a single formula
     */
    private String optimizeSingleFormula(String formula) {
        
        if (formula == null || formula.trim().isEmpty()) {
            return formula;
        }
        
        String optimized = formula;
        
        // Remove unnecessary spaces
        optimized = optimized.replaceAll("\\s+", " ").trim();
        
        // Optimize nested expressions
        optimized = optimizeNestedExpressions(optimized);
        
        // Simplify redundant operations
        optimized = simplifyRedundantOperations(optimized);
        
        return optimized;
    }
    
    /**
     * Generate explanation for the formula
     */
    private void generateFormulaExplanation(FormulaSynthesisResult result, 
                                          QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Formula Explanation:\n");
        explanation.append("Business Logic: ").append(analysisResult.getBusinessLogic()).append("\n");
        explanation.append("Expected Output: ").append(analysisResult.getOutputDataType()).append("\n\n");
        
        explanation.append("Primary Formula: ").append(result.getPrimaryFormula()).append("\n");
        explanation.append("This formula ");
        
        // Add specific explanation based on formula structure
        if (result.getPrimaryFormula().startsWith("IF(")) {
            explanation.append("uses conditional logic to return different values based on a condition.");
        } else if (result.getPrimaryFormula().startsWith("CASE(")) {
            explanation.append("uses multi-way branching to handle different scenarios.");
        } else {
            explanation.append("processes the input data according to the specified requirements.");
        }
        
        result.setExplanation(explanation.toString());
    }
    
    /**
     * Generate usage examples for the formula
     */
    private void generateUsageExamples(FormulaSynthesisResult result, 
                                     QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        
        List<String> examples = new ArrayList<>();
        
        // Generate examples based on formula type
        String formula = result.getPrimaryFormula();
        
        if (formula.startsWith("IF(")) {
            examples.add("Example 1: IF(amount > 1000, 'High', 'Low')");
            examples.add("Example 2: IF(status = 'Active', 'Enabled', 'Disabled')");
        } else if (formula.contains("CONCATENATE") || formula.contains("CONCAT")) {
            examples.add("Example 1: CONCATENATE(firstName, ' ', lastName)");
            examples.add("Example 2: CONCAT('Hello ', name, '!')");
        } else {
            examples.add("Example usage: " + formula);
        }
        
        result.setUsageExamples(examples);
    }
    
    // Helper methods
    private String generateGenericFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        if (selectedFunctions.isEmpty()) {
            return "TEXT('No formula generated')";
        }
        return generateSimpleFormula(selectedFunctions);
    }
    
    private String buildCondition(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        return "field_value > 0"; // Placeholder
    }
    
    private String buildTrueValue(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        return "'True'"; // Placeholder
    }
    
    private String buildFalseValue(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        return "'False'"; // Placeholder
    }
    
    private String buildNestedFormula(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        return generateChainedFormula(selectedFunctions);
    }
    
    private String extractCondition(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        return "condition"; // Placeholder
    }
    
    private String extractTrueValue(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        return "'True'"; // Placeholder
    }
    
    private String extractFalseValue(List<FunctionSelectionAgent.SelectedFunction> selectedFunctions) {
        return "'False'"; // Placeholder
    }
    
    private String generateOptimizedFormula(FunctionSelectionAgent.FunctionSelectionResult selectionResult) {
        return "OPTIMIZED_VERSION"; // Placeholder
    }
    
    private String generateVerboseFormula(FunctionSelectionAgent.FunctionSelectionResult selectionResult) {
        return "VERBOSE_VERSION"; // Placeholder
    }
    
    private String generateCompactFormula(FunctionSelectionAgent.FunctionSelectionResult selectionResult) {
        return "COMPACT_VERSION"; // Placeholder
    }
    
    private boolean areParenthesesBalanced(String formula) {
        int count = 0;
        for (char c : formula.toCharArray()) {
            if (c == '(') count++;
            else if (c == ')') count--;
            if (count < 0) return false;
        }
        return count == 0;
    }
    
    private boolean areValidFunctionNames(String formula) {
        // Simple validation - could be enhanced
        return !formula.contains("INVALID");
    }
    
    private String optimizeNestedExpressions(String formula) {
        // Placeholder for optimization logic
        return formula;
    }
    
    private String simplifyRedundantOperations(String formula) {
        // Placeholder for simplification logic
        return formula;
    }
    
    private FormulaSynthesisResult createFallbackSynthesis(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        FormulaSynthesisResult result = new FormulaSynthesisResult();
        result.setPrimaryFormula("TEXT('Formula generation failed')");
        result.setAlternativeFormulas(List.of());
        result.setAllFormulasValid(false);
        result.setExplanation("Unable to generate formula due to insufficient information");
        return result;
    }
    
    private MessageChatMemoryAdvisor createMemoryAdvisor(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId != null ? sessionId : "formula-synthesis-session")
                .build();
    }
    
    // Data classes
    public static class FormulaSynthesisResult {
        private String primaryFormula;
        private List<String> alternativeFormulas;
        private List<FormulaValidation> validations;
        private boolean allFormulasValid;
        private String explanation;
        private List<String> usageExamples;
        private double confidenceScore = 0.8;
        
        // Getters and setters
        public String getPrimaryFormula() { return primaryFormula; }
        public void setPrimaryFormula(String primaryFormula) { this.primaryFormula = primaryFormula; }
        
        public List<String> getAlternativeFormulas() { return alternativeFormulas; }
        public void setAlternativeFormulas(List<String> alternativeFormulas) { this.alternativeFormulas = alternativeFormulas; }
        
        public List<FormulaValidation> getValidations() { return validations; }
        public void setValidations(List<FormulaValidation> validations) { this.validations = validations; }
        
        public boolean isAllFormulasValid() { return allFormulasValid; }
        public void setAllFormulasValid(boolean allFormulasValid) { this.allFormulasValid = allFormulasValid; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        
        public List<String> getUsageExamples() { return usageExamples; }
        public void setUsageExamples(List<String> usageExamples) { this.usageExamples = usageExamples; }
        
        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    }
    
    public static class FormulaValidation {
        private String formulaType;
        private String formula;
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        
        // Getters and setters
        public String getFormulaType() { return formulaType; }
        public void setFormulaType(String formulaType) { this.formulaType = formulaType; }
        
        public String getFormula() { return formula; }
        public void setFormula(String formula) { this.formula = formula; }
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }
}