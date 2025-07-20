package info.hemendra.demoaiopenrouter;

import info.hemendra.demoaiopenrouter.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AgentWorkflowTests {

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
    
    @Test
    void testMultiAgentWorkflow() {
        // Test the complete multi-agent workflow
        String testQuery = "Create a formula to calculate discount percentage if amount is greater than 1000";
        String sessionId = "test-session-001";
        
        // Step 1: Query Understanding
        QueryUnderstandingAgent.QueryAnalysisResult queryResult = queryUnderstandingAgent.analyzeQuery(testQuery, sessionId);
        assertNotNull(queryResult);
        assertNotNull(queryResult.getBusinessLogic());
        
        // Step 2: Function Mapping
        FunctionMappingAgent.FunctionMappingResult mappingResult = functionMappingAgent.mapFunctions(queryResult, sessionId);
        assertNotNull(mappingResult);
        assertNotNull(mappingResult.getAvailableFunctions());
        
        // Step 3: Function Selection
        FunctionSelectionAgent.FunctionSelectionResult selectionResult = functionSelectionAgent.selectFunctions(queryResult, mappingResult, sessionId);
        assertNotNull(selectionResult);
        assertNotNull(selectionResult.getSelectedFunctions());
        
        // Step 4: Formula Synthesis
        FormulaSynthesisAgent.FormulaSynthesisResult synthesisResult = formulaSynthesisAgent.synthesizeFormula(queryResult, mappingResult, selectionResult, sessionId);
        assertNotNull(synthesisResult);
        assertNotNull(synthesisResult.getPrimaryFormula());
        
        // Step 5: Formula Testing
        FormulaTestingAgent.FormulaTestingResult testingResult = formulaTestingAgent.testAndOptimizeFormulas(queryResult, mappingResult, selectionResult, synthesisResult, sessionId);
        assertNotNull(testingResult);
        assertTrue(testingResult.getOverallScore() >= 0.0);
        assertTrue(testingResult.getOverallScore() <= 1.0);
        
        // Verify workflow completion
        System.out.println("Multi-agent workflow completed successfully!");
        System.out.println("Business Logic: " + queryResult.getBusinessLogic());
        System.out.println("Available Functions: " + mappingResult.getAvailableFunctions().size());
        System.out.println("Selected Functions: " + selectionResult.getSelectedFunctions().size());
        System.out.println("Primary Formula: " + synthesisResult.getPrimaryFormula());
        System.out.println("Overall Test Score: " + testingResult.getOverallScore());
    }
    
    @Test
    void testQueryUnderstandingAgent() {
        String testQuery = "Calculate the total amount with tax";
        String sessionId = "test-session-002";
        
        QueryUnderstandingAgent.QueryAnalysisResult result = queryUnderstandingAgent.analyzeQuery(testQuery, sessionId);
        
        assertNotNull(result);
        assertNotNull(result.getBusinessLogic());
        assertNotNull(result.getOutputDataType());
        assertNotNull(result.getFunctionCategories());
        
        System.out.println("Query Understanding Test:");
        System.out.println("Business Logic: " + result.getBusinessLogic());
        System.out.println("Output Data Type: " + result.getOutputDataType());
        System.out.println("Function Categories: " + result.getFunctionCategories());
    }
    
    @Test
    void testFunctionMappingAgent() {
        // Create a simple query analysis result for testing
        QueryUnderstandingAgent.QueryAnalysisResult mockQueryResult = new QueryUnderstandingAgent.QueryAnalysisResult();
        mockQueryResult.setBusinessLogic("Calculate percentage");
        mockQueryResult.setOutputDataType("Number");
        mockQueryResult.setFunctionCategories(java.util.List.of("Math"));
        
        String sessionId = "test-session-003";
        
        FunctionMappingAgent.FunctionMappingResult result = functionMappingAgent.mapFunctions(mockQueryResult, sessionId);
        
        assertNotNull(result);
        assertNotNull(result.getAvailableFunctions());
        assertTrue(result.getConfidenceScore() >= 0.0);
        assertTrue(result.getConfidenceScore() <= 1.0);
        
        System.out.println("Function Mapping Test:");
        System.out.println("Available Functions: " + result.getAvailableFunctions().size());
        System.out.println("Confidence Score: " + result.getConfidenceScore());
    }
    
    @Test
    void testAgentIntegration() {
        // Test that all agents are properly initialized and can work together
        assertNotNull(queryUnderstandingAgent);
        assertNotNull(functionMappingAgent);
        assertNotNull(functionSelectionAgent);
        assertNotNull(formulaSynthesisAgent);
        assertNotNull(formulaTestingAgent);
        
        System.out.println("All agents are properly initialized and ready for integration!");
    }
}