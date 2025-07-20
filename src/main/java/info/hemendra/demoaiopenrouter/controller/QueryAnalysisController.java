package info.hemendra.demoaiopenrouter.controller;

import info.hemendra.demoaiopenrouter.service.QueryUnderstandingAgent;
import info.hemendra.demoaiopenrouter.service.QueryUnderstandingAgent.QueryAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Query Understanding & Intent Classification
 */
@RestController
@RequestMapping("/api/query-analysis")
public class QueryAnalysisController {
    
    private static final Logger log = LoggerFactory.getLogger(QueryAnalysisController.class);
    
    @Autowired
    private QueryUnderstandingAgent queryUnderstandingAgent;
    
    /**
     * Analyze user query and extract structured requirements
     */
    @PostMapping("/analyze")
    public ResponseEntity<QueryAnalysisResult> analyzeQuery(
            @RequestParam String query,
            @RequestParam(required = false) String sessionId) {
        
        log.info("[QUERY_CONTROLLER] Received analysis request for query: {}", 
                query.length() > 50 ? query.substring(0, 50) + "..." : query);
        
        try {
            QueryAnalysisResult result = queryUnderstandingAgent.analyzeQuery(query, sessionId);
            
            log.info("[QUERY_CONTROLLER] Analysis completed successfully with confidence: {}", 
                    result.getConfidenceScore());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("[QUERY_CONTROLLER] Error during query analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Test endpoint with predefined examples
     */
    @GetMapping("/test-examples")
    public ResponseEntity<String> getTestExamples() {
        String examples = """
                # Query Analysis Test Examples
                
                ## Example 1: Math Operation
                POST /api/query-analysis/analyze?query=Calculate the commission as 5% of the sales amount
                
                ## Example 2: Conditional Logic
                POST /api/query-analysis/analyze?query=If the opportunity stage is Closed Won, then show 'Success', otherwise show 'In Progress'
                
                ## Example 3: Date Calculation
                POST /api/query-analysis/analyze?query=Calculate days between created date and close date
                
                ## Example 4: Text Operations
                POST /api/query-analysis/analyze?query=Concatenate first name and last name with a space
                
                ## Example 5: Complex Logic
                POST /api/query-analysis/analyze?query=If the account type is Customer and the annual revenue is greater than 100000, then calculate 10% discount, otherwise 5%
                
                Expected Response Format:
                {
                    "businessLogic": "Description of what user wants to achieve",
                    "functionCategories": ["MATH", "LOGICAL"],
                    "outputDataType": "Number",
                    "fieldReferences": ["sales_amount"],
                    "conditionalPatterns": ["IF_THEN_ELSE"],
                    "mathOperations": ["PERCENTAGE"],
                    "complexityLevel": "Simple",
                    "confidenceScore": 0.95
                }
                """;
        
        return ResponseEntity.ok(examples);
    }
}