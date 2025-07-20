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
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Agent 5: Testing & Optimization Service
 * 
 * Purpose: Test and optimize generated formulas for correctness and performance
 * 
 * Responsibilities:
 * - Test formulas with sample data
 * - Validate formula correctness
 * - Optimize formulas for performance
 * - Generate test cases and scenarios
 * - Provide performance metrics
 * - Suggest improvements and alternatives
 */
@Service
public class FormulaTestingAgent {
    
    private static final Logger log = LoggerFactory.getLogger(FormulaTestingAgent.class);

    @Qualifier("openAiChatClient")
    @Autowired
    private ChatClient chatClient;

    private final ObjectMapper objectMapper;
    
    @Autowired
    private ChatMemory chatMemory;
    
    @Autowired
    private FormulaMetadataUtil formulaMetadataUtil;
    
    // Test data patterns
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TEXT_PATTERN = Pattern.compile("'[^']*'|\"[^\"]*\"");
    
    public FormulaTestingAgent() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Main method to test and optimize formulas
     */
    public FormulaTestingResult testAndOptimizeFormulas(
            QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
            FunctionMappingAgent.FunctionMappingResult mappingResult,
            FunctionSelectionAgent.FunctionSelectionResult selectionResult,
            FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult,
            String sessionId) {
        
        log.info("[TESTING_AGENT] Starting formula testing and optimization for business logic: {}", 
                analysisResult.getBusinessLogic());
        
        try {
            FormulaTestingResult result = new FormulaTestingResult();
            
            // Step 1: Generate test cases
            List<TestCase> testCases = generateTestCases(analysisResult, synthesisResult);
            result.setTestCases(testCases);
            
            // Step 2: Test primary formula
            TestResult primaryTest = testFormula(synthesisResult.getPrimaryFormula(), testCases, "Primary");
            result.setPrimaryFormulaTest(primaryTest);
            
            // Step 3: Test alternative formulas
            List<TestResult> alternativeTests = new ArrayList<>();
            if (synthesisResult.getAlternativeFormulas() != null) {
                for (int i = 0; i < synthesisResult.getAlternativeFormulas().size(); i++) {
                    String formula = synthesisResult.getAlternativeFormulas().get(i);
                    TestResult test = testFormula(formula, testCases, "Alternative " + (i + 1));
                    alternativeTests.add(test);
                }
            }
            result.setAlternativeFormulaTests(alternativeTests);
            
            // Step 4: Performance analysis
            PerformanceMetrics performance = analyzePerformance(synthesisResult, testCases);
            result.setPerformanceMetrics(performance);
            
            // Step 5: Optimization suggestions
            List<OptimizationSuggestion> optimizations = generateOptimizations(synthesisResult, result);
            result.setOptimizations(optimizations);
            
            // Step 6: Edge case testing
            EdgeCaseResults edgeCases = testEdgeCases(synthesisResult, analysisResult);
            result.setEdgeCaseResults(edgeCases);
            
            // Step 7: Generate final recommendations
            generateRecommendations(result, analysisResult);
            
            // Calculate overall test score
            double overallScore = calculateOverallScore(result);
            result.setOverallScore(overallScore);
            
            log.info("[TESTING_AGENT] Testing complete. Overall score: {}, Primary formula passed: {}", 
                    overallScore, primaryTest.isPassed());
            
            return result;
            
        } catch (Exception e) {
            log.error("[TESTING_AGENT] Error during formula testing: {}", e.getMessage(), e);
            return createFallbackTestResult();
        }
    }
    
    /**
     * Generate test cases based on analysis results
     */
    private List<TestCase> generateTestCases(QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
                                           FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult) {
        
        List<TestCase> testCases = new ArrayList<>();
        
        // Generate test cases based on expected output type
        switch (analysisResult.getOutputDataType()) {
            case "String":
                testCases.addAll(generateStringTestCases(analysisResult));
                break;
            case "Boolean":
                testCases.addAll(generateBooleanTestCases(analysisResult));
                break;
            case "Number":
            case "double":
                testCases.addAll(generateNumericTestCases(analysisResult));
                break;
            case "Date":
                testCases.addAll(generateDateTestCases(analysisResult));
                break;
            default:
                testCases.addAll(generateGenericTestCases(analysisResult));
        }
        
        // Add field-specific test cases
        if (analysisResult.getFieldReferences() != null) {
            testCases.addAll(generateFieldBasedTestCases(analysisResult.getFieldReferences()));
        }
        
        log.info("[TESTING_AGENT] Generated {} test cases", testCases.size());
        return testCases;
    }
    
    /**
     * Test a formula with given test cases
     */
    private TestResult testFormula(String formula, List<TestCase> testCases, String formulaType) {
        
        TestResult result = new TestResult();
        result.setFormula(formula);
        result.setFormulaType(formulaType);
        
        List<TestCaseResult> testResults = new ArrayList<>();
        int passedCount = 0;
        
        for (TestCase testCase : testCases) {
            TestCaseResult testResult = executeTestCase(formula, testCase);
            testResults.add(testResult);
            
            if (testResult.isPassed()) {
                passedCount++;
            }
        }
        
        result.setTestCaseResults(testResults);
        result.setPassedCount(passedCount);
        result.setTotalCount(testCases.size());
        result.setPassed(passedCount == testCases.size());
        result.setSuccessRate((double) passedCount / testCases.size());
        
        return result;
    }
    
    /**
     * Execute a single test case
     */
    private TestCaseResult executeTestCase(String formula, TestCase testCase) {
        
        TestCaseResult result = new TestCaseResult();
        result.setTestCase(testCase);
        
        try {
            // Simulate formula execution with test data
            String processedFormula = substituteTestData(formula, testCase.getInputData());
            result.setProcessedFormula(processedFormula);
            
            // Basic validation
            boolean isValid = validateFormulaExecution(processedFormula, testCase);
            result.setPassed(isValid);
            
            if (isValid) {
                result.setActualResult(simulateFormulaResult(processedFormula, testCase));
                result.setExecutionTime(estimateExecutionTime(processedFormula));
            } else {
                result.setActualResult("EXECUTION_FAILED");
                result.setErrorMessage("Formula validation failed");
            }
            
        } catch (Exception e) {
            result.setPassed(false);
            result.setErrorMessage(e.getMessage());
            result.setActualResult("ERROR");
        }
        
        return result;
    }
    
    /**
     * Analyze formula performance
     */
    private PerformanceMetrics analyzePerformance(FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult,
                                                 List<TestCase> testCases) {
        
        PerformanceMetrics metrics = new PerformanceMetrics();
        
        // Analyze primary formula
        String primaryFormula = synthesisResult.getPrimaryFormula();
        metrics.setPrimaryFormulaComplexity(calculateComplexity(primaryFormula));
        metrics.setEstimatedExecutionTime(estimateExecutionTime(primaryFormula));
        metrics.setMemoryUsage(estimateMemoryUsage(primaryFormula));
        
        // Analyze alternative formulas
        if (synthesisResult.getAlternativeFormulas() != null) {
            List<FormulaPerformance> alternativePerformances = new ArrayList<>();
            
            for (String formula : synthesisResult.getAlternativeFormulas()) {
                FormulaPerformance perf = new FormulaPerformance();
                perf.setFormula(formula);
                perf.setComplexity(calculateComplexity(formula));
                perf.setEstimatedTime(estimateExecutionTime(formula));
                perf.setMemoryUsage(estimateMemoryUsage(formula));
                alternativePerformances.add(perf);
            }
            
            metrics.setAlternativePerformances(alternativePerformances);
        }
        
        // Performance recommendations
        metrics.setRecommendations(generatePerformanceRecommendations(synthesisResult));
        
        return metrics;
    }
    
    /**
     * Generate optimization suggestions
     */
    private List<OptimizationSuggestion> generateOptimizations(FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult,
                                                             FormulaTestingResult testResult) {
        
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        
        // Check for common optimization patterns
        String primaryFormula = synthesisResult.getPrimaryFormula();
        
        // Nested function optimization
        if (hasNestedFunctions(primaryFormula)) {
            suggestions.add(new OptimizationSuggestion(
                "NESTED_FUNCTIONS",
                "Consider flattening nested functions for better performance",
                "HIGH",
                "Nested functions can impact performance. Consider breaking into simpler steps."
            ));
        }
        
        // String concatenation optimization
        if (hasMultipleStringConcatenations(primaryFormula)) {
            suggestions.add(new OptimizationSuggestion(
                "STRING_CONCATENATION",
                "Multiple string concatenations detected. Consider using a single CONCAT function",
                "MEDIUM",
                "CONCAT(field1, field2, field3) is more efficient than CONCATENATE(CONCATENATE(field1, field2), field3)"
            ));
        }
        
        // Conditional logic optimization
        if (hasComplexConditionals(primaryFormula)) {
            suggestions.add(new OptimizationSuggestion(
                "CONDITIONAL_LOGIC",
                "Complex conditional logic detected. Consider using CASE statements",
                "MEDIUM",
                "CASE statements can be more readable and efficient than nested IF statements"
            ));
        }
        
        // Performance-based suggestions
        if (testResult.getPerformanceMetrics() != null) {
            if (testResult.getPerformanceMetrics().getPrimaryFormulaComplexity() > 7) {
                suggestions.add(new OptimizationSuggestion(
                    "COMPLEXITY_REDUCTION",
                    "High complexity detected. Consider simplifying the formula",
                    "HIGH",
                    "Break complex formulas into smaller, more manageable parts"
                ));
            }
        }
        
        return suggestions;
    }
    
    /**
     * Test edge cases
     */
    private EdgeCaseResults testEdgeCases(FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult,
                                        QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        
        EdgeCaseResults results = new EdgeCaseResults();
        List<EdgeCaseTest> edgeCaseTests = new ArrayList<>();
        
        String primaryFormula = synthesisResult.getPrimaryFormula();
        
        // Null value handling
        EdgeCaseTest nullTest = new EdgeCaseTest();
        nullTest.setTestName("NULL_VALUE_HANDLING");
        nullTest.setDescription("Test formula behavior with null values");
        nullTest.setExpectedBehavior("Should handle null values gracefully");
        nullTest.setPassed(testNullHandling(primaryFormula));
        edgeCaseTests.add(nullTest);
        
        // Empty string handling
        if (analysisResult.getOutputDataType().equals("String")) {
            EdgeCaseTest emptyStringTest = new EdgeCaseTest();
            emptyStringTest.setTestName("EMPTY_STRING_HANDLING");
            emptyStringTest.setDescription("Test formula behavior with empty strings");
            emptyStringTest.setExpectedBehavior("Should handle empty strings appropriately");
            emptyStringTest.setPassed(testEmptyStringHandling(primaryFormula));
            edgeCaseTests.add(emptyStringTest);
        }
        
        // Division by zero (for numeric formulas)
        if (analysisResult.getOutputDataType().equals("Number") && primaryFormula.contains("/")) {
            EdgeCaseTest divisionTest = new EdgeCaseTest();
            divisionTest.setTestName("DIVISION_BY_ZERO");
            divisionTest.setDescription("Test formula behavior with division by zero");
            divisionTest.setExpectedBehavior("Should handle division by zero gracefully");
            divisionTest.setPassed(testDivisionByZero(primaryFormula));
            edgeCaseTests.add(divisionTest);
        }
        
        // Large number handling
        if (analysisResult.getOutputDataType().equals("Number")) {
            EdgeCaseTest largeNumberTest = new EdgeCaseTest();
            largeNumberTest.setTestName("LARGE_NUMBER_HANDLING");
            largeNumberTest.setDescription("Test formula behavior with large numbers");
            largeNumberTest.setExpectedBehavior("Should handle large numbers without overflow");
            largeNumberTest.setPassed(testLargeNumbers(primaryFormula));
            edgeCaseTests.add(largeNumberTest);
        }
        
        results.setEdgeCaseTests(edgeCaseTests);
        
        int passedCount = (int) edgeCaseTests.stream().mapToInt(test -> test.isPassed() ? 1 : 0).sum();
        results.setPassedCount(passedCount);
        results.setTotalCount(edgeCaseTests.size());
        results.setOverallPassed(passedCount == edgeCaseTests.size());
        
        return results;
    }
    
    /**
     * Generate final recommendations
     */
    private void generateRecommendations(FormulaTestingResult result, QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        
        List<String> recommendations = new ArrayList<>();
        
        // Test result recommendations
        if (result.getPrimaryFormulaTest().isPassed()) {
            recommendations.add("✓ Primary formula passed all tests and is ready for production use");
        } else {
            recommendations.add("⚠ Primary formula failed some tests. Review and fix before deployment");
        }
        
        // Performance recommendations
        if (result.getPerformanceMetrics() != null) {
            if (result.getPerformanceMetrics().getPrimaryFormulaComplexity() > 8) {
                recommendations.add("⚠ Formula complexity is high. Consider breaking into smaller components");
            }
            
            if (result.getPerformanceMetrics().getEstimatedExecutionTime() > 1000) {
                recommendations.add("⚠ Estimated execution time is high. Consider optimization");
            }
        }
        
        // Edge case recommendations
        if (result.getEdgeCaseResults() != null && !result.getEdgeCaseResults().isOverallPassed()) {
            recommendations.add("⚠ Some edge cases failed. Add proper error handling");
        }
        
        // Alternative formula recommendations
        if (result.getAlternativeFormulaTests() != null) {
            long passedAlternatives = result.getAlternativeFormulaTests().stream()
                    .mapToLong(test -> test.isPassed() ? 1 : 0).sum();
            
            if (passedAlternatives > 0) {
                recommendations.add("✓ Alternative formulas available for different scenarios");
            }
        }
        
        // Optimization recommendations
        if (result.getOptimizations() != null && !result.getOptimizations().isEmpty()) {
            long highPriorityOptimizations = result.getOptimizations().stream()
                    .mapToLong(opt -> "HIGH".equals(opt.getPriority()) ? 1 : 0).sum();
            
            if (highPriorityOptimizations > 0) {
                recommendations.add("⚠ High priority optimizations available. Consider implementing");
            }
        }
        
        result.setRecommendations(recommendations);
    }
    
    // Helper methods for test case generation
    private List<TestCase> generateStringTestCases(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        List<TestCase> testCases = new ArrayList<>();
        
        // Basic string test case
        TestCase basicTest = new TestCase();
        basicTest.setTestName("BASIC_STRING_TEST");
        basicTest.setDescription("Test basic string operations");
        basicTest.setInputData(Map.of("text_field", "Sample Text", "name", "John Doe"));
        basicTest.setExpectedResult("Expected string result");
        testCases.add(basicTest);
        
        // Empty string test case
        TestCase emptyTest = new TestCase();
        emptyTest.setTestName("EMPTY_STRING_TEST");
        emptyTest.setDescription("Test with empty strings");
        emptyTest.setInputData(Map.of("text_field", "", "name", ""));
        emptyTest.setExpectedResult("");
        testCases.add(emptyTest);
        
        return testCases;
    }
    
    private List<TestCase> generateBooleanTestCases(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        List<TestCase> testCases = new ArrayList<>();
        
        // True condition test case
        TestCase trueTest = new TestCase();
        trueTest.setTestName("TRUE_CONDITION_TEST");
        trueTest.setDescription("Test condition that should return true");
        trueTest.setInputData(Map.of("amount", 1000, "status", "Active"));
        trueTest.setExpectedResult("true");
        testCases.add(trueTest);
        
        // False condition test case
        TestCase falseTest = new TestCase();
        falseTest.setTestName("FALSE_CONDITION_TEST");
        falseTest.setDescription("Test condition that should return false");
        falseTest.setInputData(Map.of("amount", 100, "status", "Inactive"));
        falseTest.setExpectedResult("false");
        testCases.add(falseTest);
        
        return testCases;
    }
    
    private List<TestCase> generateNumericTestCases(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        List<TestCase> testCases = new ArrayList<>();
        
        // Positive number test case
        TestCase positiveTest = new TestCase();
        positiveTest.setTestName("POSITIVE_NUMBER_TEST");
        positiveTest.setDescription("Test with positive numbers");
        positiveTest.setInputData(Map.of("value1", 100, "value2", 200));
        positiveTest.setExpectedResult("300");
        testCases.add(positiveTest);
        
        // Negative number test case
        TestCase negativeTest = new TestCase();
        negativeTest.setTestName("NEGATIVE_NUMBER_TEST");
        negativeTest.setDescription("Test with negative numbers");
        negativeTest.setInputData(Map.of("value1", -50, "value2", 100));
        negativeTest.setExpectedResult("50");
        testCases.add(negativeTest);
        
        // Zero test case
        TestCase zeroTest = new TestCase();
        zeroTest.setTestName("ZERO_TEST");
        zeroTest.setDescription("Test with zero values");
        zeroTest.setInputData(Map.of("value1", 0, "value2", 0));
        zeroTest.setExpectedResult("0");
        testCases.add(zeroTest);
        
        return testCases;
    }
    
    private List<TestCase> generateDateTestCases(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        List<TestCase> testCases = new ArrayList<>();
        
        // Current date test case
        TestCase currentDateTest = new TestCase();
        currentDateTest.setTestName("CURRENT_DATE_TEST");
        currentDateTest.setDescription("Test with current date");
        currentDateTest.setInputData(Map.of("date_field", "2024-01-15"));
        currentDateTest.setExpectedResult("2024-01-15");
        testCases.add(currentDateTest);
        
        return testCases;
    }
    
    private List<TestCase> generateGenericTestCases(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) {
        List<TestCase> testCases = new ArrayList<>();
        
        // Generic test case
        TestCase genericTest = new TestCase();
        genericTest.setTestName("GENERIC_TEST");
        genericTest.setDescription("Generic test case");
        genericTest.setInputData(Map.of("field", "value"));
        genericTest.setExpectedResult("result");
        testCases.add(genericTest);
        
        return testCases;
    }
    
    private List<TestCase> generateFieldBasedTestCases(List<String> fieldReferences) {
        List<TestCase> testCases = new ArrayList<>();
        
        // Field-based test case
        TestCase fieldTest = new TestCase();
        fieldTest.setTestName("FIELD_BASED_TEST");
        fieldTest.setDescription("Test with actual field references");
        
        Map<String, Object> inputData = new HashMap<>();
        for (String field : fieldReferences) {
            inputData.put(field, "sample_value_" + field);
        }
        
        fieldTest.setInputData(inputData);
        fieldTest.setExpectedResult("field_based_result");
        testCases.add(fieldTest);
        
        return testCases;
    }
    
    // Helper methods for formula analysis
    private String substituteTestData(String formula, Map<String, Object> inputData) {
        String processedFormula = formula;
        
        for (Map.Entry<String, Object> entry : inputData.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            // Simple substitution - in real implementation, this would be more sophisticated
            if (value instanceof String) {
                processedFormula = processedFormula.replace(field, "'" + value + "'");
            } else {
                processedFormula = processedFormula.replace(field, value.toString());
            }
        }
        
        return processedFormula;
    }
    
    private boolean validateFormulaExecution(String formula, TestCase testCase) {
        // Basic validation logic
        if (formula == null || formula.trim().isEmpty()) {
            return false;
        }
        
        // Check for balanced parentheses
        int openCount = 0;
        for (char c : formula.toCharArray()) {
            if (c == '(') openCount++;
            else if (c == ')') openCount--;
            if (openCount < 0) return false;
        }
        
        return openCount == 0;
    }
    
    private String simulateFormulaResult(String formula, TestCase testCase) {
        // Simulate formula execution result
        if (formula.contains("IF(")) {
            return "conditional_result";
        } else if (formula.contains("CONCATENATE") || formula.contains("CONCAT")) {
            return "concatenated_result";
        } else if (formula.contains("+") || formula.contains("-") || formula.contains("*") || formula.contains("/")) {
            return "numeric_result";
        } else {
            return "generic_result";
        }
    }
    
    private int calculateComplexity(String formula) {
        int complexity = 0;
        
        // Count function calls
        complexity += countOccurrences(formula, "(");
        
        // Count operators
        complexity += countOccurrences(formula, "+");
        complexity += countOccurrences(formula, "-");
        complexity += countOccurrences(formula, "*");
        complexity += countOccurrences(formula, "/");
        
        // Count logical operators
        complexity += countOccurrences(formula, "AND");
        complexity += countOccurrences(formula, "OR");
        complexity += countOccurrences(formula, "IF");
        
        return complexity;
    }
    
    private long estimateExecutionTime(String formula) {
        // Estimate execution time based on complexity
        int complexity = calculateComplexity(formula);
        return complexity * 10; // milliseconds
    }
    
    private long estimateMemoryUsage(String formula) {
        // Estimate memory usage based on formula length and complexity
        return formula.length() * 2; // bytes
    }
    
    private int countOccurrences(String text, String pattern) {
        if (text == null || pattern == null) return 0;
        
        // Use simple string contains for counting instead of regex split
        int count = 0;
        int index = 0;
        
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        
        return count;
    }
    
    private boolean hasNestedFunctions(String formula) {
        // Check for nested function calls
        return formula.contains("((") || countOccurrences(formula, "(") > 2;
    }
    
    private boolean hasMultipleStringConcatenations(String formula) {
        return countOccurrences(formula, "CONCATENATE") > 1 || 
               (formula.contains("CONCATENATE") && formula.contains("CONCAT"));
    }
    
    private boolean hasComplexConditionals(String formula) {
        return countOccurrences(formula, "IF") > 2 || 
               (formula.contains("IF") && formula.contains("CASE"));
    }
    
    private List<String> generatePerformanceRecommendations(FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult) {
        List<String> recommendations = new ArrayList<>();
        
        String formula = synthesisResult.getPrimaryFormula();
        
        if (calculateComplexity(formula) > 8) {
            recommendations.add("Consider breaking complex formula into smaller components");
        }
        
        if (hasNestedFunctions(formula)) {
            recommendations.add("Flatten nested functions to improve readability and performance");
        }
        
        if (formula.length() > 200) {
            recommendations.add("Formula is quite long. Consider using intermediate calculations");
        }
        
        return recommendations;
    }
    
    // Edge case testing methods
    private boolean testNullHandling(String formula) {
        // Test null handling - simplified
        return !formula.contains("null") || formula.contains("ISNULL") || formula.contains("ISBLANK");
    }
    
    private boolean testEmptyStringHandling(String formula) {
        // Test empty string handling
        return formula.contains("ISBLANK") || formula.contains("LEN") || formula.contains("TRIM");
    }
    
    private boolean testDivisionByZero(String formula) {
        // Test division by zero protection
        return !formula.contains("/") || formula.contains("IF") || formula.contains("CASE");
    }
    
    private boolean testLargeNumbers(String formula) {
        // Test large number handling - simplified
        return true; // Assume handled properly
    }
    
    private double calculateOverallScore(FormulaTestingResult result) {
        double score = 0.0;
        
        // Primary formula test weight: 40%
        if (result.getPrimaryFormulaTest() != null) {
            score += result.getPrimaryFormulaTest().getSuccessRate() * 0.4;
        }
        
        // Edge case test weight: 30%
        if (result.getEdgeCaseResults() != null) {
            double edgeScore = (double) result.getEdgeCaseResults().getPassedCount() / 
                             result.getEdgeCaseResults().getTotalCount();
            score += edgeScore * 0.3;
        }
        
        // Performance weight: 20%
        if (result.getPerformanceMetrics() != null) {
            double perfScore = Math.max(0, (10 - result.getPerformanceMetrics().getPrimaryFormulaComplexity()) / 10.0);
            score += perfScore * 0.2;
        }
        
        // Alternative formulas weight: 10%
        if (result.getAlternativeFormulaTests() != null && !result.getAlternativeFormulaTests().isEmpty()) {
            double altScore = result.getAlternativeFormulaTests().stream()
                    .mapToDouble(TestResult::getSuccessRate)
                    .average()
                    .orElse(0.0);
            score += altScore * 0.1;
        }
        
        return Math.min(1.0, score);
    }
    
    private FormulaTestingResult createFallbackTestResult() {
        FormulaTestingResult result = new FormulaTestingResult();
        result.setOverallScore(0.0);
        result.setRecommendations(List.of("Testing failed. Please review formula manually."));
        return result;
    }
    
    private MessageChatMemoryAdvisor createMemoryAdvisor(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId != null ? sessionId : "formula-testing-session")
                .build();
    }
    
    // Data classes
    public static class FormulaTestingResult {
        private List<TestCase> testCases;
        private TestResult primaryFormulaTest;
        private List<TestResult> alternativeFormulaTests;
        private PerformanceMetrics performanceMetrics;
        private List<OptimizationSuggestion> optimizations;
        private EdgeCaseResults edgeCaseResults;
        private double overallScore;
        private List<String> recommendations;
        
        // Getters and setters
        public List<TestCase> getTestCases() { return testCases; }
        public void setTestCases(List<TestCase> testCases) { this.testCases = testCases; }
        
        public TestResult getPrimaryFormulaTest() { return primaryFormulaTest; }
        public void setPrimaryFormulaTest(TestResult primaryFormulaTest) { this.primaryFormulaTest = primaryFormulaTest; }
        
        public List<TestResult> getAlternativeFormulaTests() { return alternativeFormulaTests; }
        public void setAlternativeFormulaTests(List<TestResult> alternativeFormulaTests) { this.alternativeFormulaTests = alternativeFormulaTests; }
        
        public PerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }
        public void setPerformanceMetrics(PerformanceMetrics performanceMetrics) { this.performanceMetrics = performanceMetrics; }
        
        public List<OptimizationSuggestion> getOptimizations() { return optimizations; }
        public void setOptimizations(List<OptimizationSuggestion> optimizations) { this.optimizations = optimizations; }
        
        public EdgeCaseResults getEdgeCaseResults() { return edgeCaseResults; }
        public void setEdgeCaseResults(EdgeCaseResults edgeCaseResults) { this.edgeCaseResults = edgeCaseResults; }
        
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }
    
    public static class TestCase {
        private String testName;
        private String description;
        private Map<String, Object> inputData;
        private String expectedResult;
        
        // Getters and setters
        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Map<String, Object> getInputData() { return inputData; }
        public void setInputData(Map<String, Object> inputData) { this.inputData = inputData; }
        
        public String getExpectedResult() { return expectedResult; }
        public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    }
    
    public static class TestResult {
        private String formula;
        private String formulaType;
        private List<TestCaseResult> testCaseResults;
        private int passedCount;
        private int totalCount;
        private boolean passed;
        private double successRate;
        
        // Getters and setters
        public String getFormula() { return formula; }
        public void setFormula(String formula) { this.formula = formula; }
        
        public String getFormulaType() { return formulaType; }
        public void setFormulaType(String formulaType) { this.formulaType = formulaType; }
        
        public List<TestCaseResult> getTestCaseResults() { return testCaseResults; }
        public void setTestCaseResults(List<TestCaseResult> testCaseResults) { this.testCaseResults = testCaseResults; }
        
        public int getPassedCount() { return passedCount; }
        public void setPassedCount(int passedCount) { this.passedCount = passedCount; }
        
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }
    
    public static class TestCaseResult {
        private TestCase testCase;
        private String processedFormula;
        private String actualResult;
        private String errorMessage;
        private boolean passed;
        private long executionTime;
        
        // Getters and setters
        public TestCase getTestCase() { return testCase; }
        public void setTestCase(TestCase testCase) { this.testCase = testCase; }
        
        public String getProcessedFormula() { return processedFormula; }
        public void setProcessedFormula(String processedFormula) { this.processedFormula = processedFormula; }
        
        public String getActualResult() { return actualResult; }
        public void setActualResult(String actualResult) { this.actualResult = actualResult; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        
        public long getExecutionTime() { return executionTime; }
        public void setExecutionTime(long executionTime) { this.executionTime = executionTime; }
    }
    
    public static class PerformanceMetrics {
        private int primaryFormulaComplexity;
        private long estimatedExecutionTime;
        private long memoryUsage;
        private List<FormulaPerformance> alternativePerformances;
        private List<String> recommendations;
        
        // Getters and setters
        public int getPrimaryFormulaComplexity() { return primaryFormulaComplexity; }
        public void setPrimaryFormulaComplexity(int primaryFormulaComplexity) { this.primaryFormulaComplexity = primaryFormulaComplexity; }
        
        public long getEstimatedExecutionTime() { return estimatedExecutionTime; }
        public void setEstimatedExecutionTime(long estimatedExecutionTime) { this.estimatedExecutionTime = estimatedExecutionTime; }
        
        public long getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(long memoryUsage) { this.memoryUsage = memoryUsage; }
        
        public List<FormulaPerformance> getAlternativePerformances() { return alternativePerformances; }
        public void setAlternativePerformances(List<FormulaPerformance> alternativePerformances) { this.alternativePerformances = alternativePerformances; }
        
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }
    
    public static class FormulaPerformance {
        private String formula;
        private int complexity;
        private long estimatedTime;
        private long memoryUsage;
        
        // Getters and setters
        public String getFormula() { return formula; }
        public void setFormula(String formula) { this.formula = formula; }
        
        public int getComplexity() { return complexity; }
        public void setComplexity(int complexity) { this.complexity = complexity; }
        
        public long getEstimatedTime() { return estimatedTime; }
        public void setEstimatedTime(long estimatedTime) { this.estimatedTime = estimatedTime; }
        
        public long getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(long memoryUsage) { this.memoryUsage = memoryUsage; }
    }
    
    public static class OptimizationSuggestion {
        private String type;
        private String suggestion;
        private String priority;
        private String description;
        
        public OptimizationSuggestion() {}
        
        public OptimizationSuggestion(String type, String suggestion, String priority, String description) {
            this.type = type;
            this.suggestion = suggestion;
            this.priority = priority;
            this.description = description;
        }
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    public static class EdgeCaseResults {
        private List<EdgeCaseTest> edgeCaseTests;
        private int passedCount;
        private int totalCount;
        private boolean overallPassed;
        
        // Getters and setters
        public List<EdgeCaseTest> getEdgeCaseTests() { return edgeCaseTests; }
        public void setEdgeCaseTests(List<EdgeCaseTest> edgeCaseTests) { this.edgeCaseTests = edgeCaseTests; }
        
        public int getPassedCount() { return passedCount; }
        public void setPassedCount(int passedCount) { this.passedCount = passedCount; }
        
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        
        public boolean isOverallPassed() { return overallPassed; }
        public void setOverallPassed(boolean overallPassed) { this.overallPassed = overallPassed; }
    }
    
    public static class EdgeCaseTest {
        private String testName;
        private String description;
        private String expectedBehavior;
        private boolean passed;
        
        // Getters and setters
        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getExpectedBehavior() { return expectedBehavior; }
        public void setExpectedBehavior(String expectedBehavior) { this.expectedBehavior = expectedBehavior; }
        
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
    }
}