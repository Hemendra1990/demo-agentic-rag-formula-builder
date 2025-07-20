package info.hemendra.demoaiopenrouter.controller;

import info.hemendra.demoaiopenrouter.service.FunctionMappingAgent;
import info.hemendra.demoaiopenrouter.service.QueryUnderstandingAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Function Mapping & Availability Check
 */
@Deprecated(since = "v1.1.0", forRemoval = true)
@RestController
@RequestMapping("/api/function-mapping")
public class FunctionMappingController {
    
    private static final Logger log = LoggerFactory.getLogger(FunctionMappingController.class);
    
    @Autowired
    private FunctionMappingAgent functionMappingAgent;
    
    @Autowired
    private QueryUnderstandingAgent queryUnderstandingAgent;
    
    /**
     * Map functions based on query analysis result
     */
    @PostMapping("/map")
    public ResponseEntity<FunctionMappingAgent.FunctionMappingResult> mapFunctions(
            @RequestBody QueryUnderstandingAgent.QueryAnalysisResult analysisResult,
            @RequestParam(required = false) String sessionId) {
        
        log.info("[MAPPING_CONTROLLER] Received mapping request for categories: {}", 
                analysisResult.getFunctionCategories());
        
        try {
            FunctionMappingAgent.FunctionMappingResult result = functionMappingAgent.mapFunctions(analysisResult, sessionId);
            
            log.info("[MAPPING_CONTROLLER] Mapping completed - Available: {}, Missing: {}, Compatibility: {}", 
                    result.getAvailableFunctions().size(),
                    result.getMissingFunctions().size(),
                    String.format("%.2f", result.getOverallCompatibility()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("[MAPPING_CONTROLLER] Error during function mapping: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * End-to-end analysis and mapping from user query
     */
    @PostMapping("/analyze-and-map")
    public ResponseEntity<CombinedAnalysisResult> analyzeAndMap(
            @RequestParam String query,
            @RequestParam(required = false) String sessionId) {
        
        log.info("[MAPPING_CONTROLLER] Received end-to-end request for query: {}", 
                query.length() > 50 ? query.substring(0, 50) + "..." : query);
        
        try {
            // Step 1: Analyze the query
            QueryUnderstandingAgent.QueryAnalysisResult analysisResult = 
                queryUnderstandingAgent.analyzeQuery(query, sessionId);
            
            // Step 2: Map the functions
            FunctionMappingAgent.FunctionMappingResult mappingResult = 
                functionMappingAgent.mapFunctions(analysisResult, sessionId);
            
            // Combine results
            CombinedAnalysisResult combinedResult = new CombinedAnalysisResult();
            combinedResult.setOriginalQuery(query);
            combinedResult.setAnalysisResult(analysisResult);
            combinedResult.setMappingResult(mappingResult);
            
            log.info("[MAPPING_CONTROLLER] End-to-end analysis completed successfully");
            
            return ResponseEntity.ok(combinedResult);
            
        } catch (Exception e) {
            log.error("[MAPPING_CONTROLLER] Error during end-to-end analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Test function mapping with predefined examples
     */
    @GetMapping("/test-examples")
    public ResponseEntity<String> getTestExamples() {
        String examples = """
                # Function Mapping Test Examples
                
                ## Example 1: Math Operations
                POST /api/function-mapping/analyze-and-map?query=Calculate commission as 5% of sales amount
                
                Expected Mapping:
                - MULTIPLY → MULTIPLY (100% compatible)
                - PERCENTAGE → MULTIPLY with 0.01 (100% compatible)
                
                ## Example 2: Date Functions
                POST /api/function-mapping/analyze-and-map?query=Calculate days between created date and close date
                
                Expected Mapping:
                - DATE_SUBTRACT → DATE_DIFF (95% compatible)
                - TODAY → CURRENT_DATE (100% compatible)
                
                ## Example 3: Text Operations
                POST /api/function-mapping/analyze-and-map?query=Concatenate first name and last name with a space
                
                Expected Mapping:
                - CONCATENATE → CONCAT (100% compatible)
                - TEXT → CAST_TEXT (90% compatible)
                
                ## Example 4: Complex Logic
                POST /api/function-mapping/analyze-and-map?query=If account type is Customer and revenue > 100000 then 10% else 5%
                
                Expected Mapping:
                - IF → IF (100% compatible)
                - AND → AND (100% compatible)
                - PERCENTAGE → MULTIPLY (100% compatible)
                
                ## Example 5: Lookup Functions (Limited Support)
                POST /api/function-mapping/analyze-and-map?query=Lookup account name from opportunity
                
                Expected Mapping:
                - LOOKUP → JOIN_LOOKUP (60% compatible, requires JOIN syntax)
                - Missing: VLOOKUP (No direct equivalent)
                
                Expected Response Format:
                {
                    "originalQuery": "User query",
                    "analysisResult": { ... },
                    "mappingResult": {
                        "availableFunctions": [
                            {
                                "salesforceFunction": "CONCATENATE",
                                "crmFunction": "CONCAT",
                                "syntax": "CONCAT(text1, text2, ...)",
                                "description": "Concatenate strings",
                                "compatibilityScore": 1.0,
                                "fullySupported": true
                            }
                        ],
                        "missingFunctions": [
                            {
                                "salesforceFunction": "VLOOKUP",
                                "reason": "No direct equivalent",
                                "suggestedAlternative": "Use JOIN syntax"
                            }
                        ],
                        "overallCompatibility": 0.85,
                        "confidenceScore": 0.9
                    }
                }
                """;
        
        return ResponseEntity.ok(examples);
    }
    
    /**
     * Get available function categories and their support level
     */
    @GetMapping("/function-matrix")
    public ResponseEntity<FunctionMatrix> getFunctionMatrix() {
        FunctionMatrix matrix = new FunctionMatrix();
        
        // Math Functions
        matrix.addCategory("MATH", 1.0, "Full support for arithmetic operations");
        matrix.addFunction("MATH", "ADD", "ADD", 1.0, true);
        matrix.addFunction("MATH", "SUBTRACT", "SUBTRACT", 1.0, true);
        matrix.addFunction("MATH", "MULTIPLY", "MULTIPLY", 1.0, true);
        matrix.addFunction("MATH", "DIVIDE", "DIVIDE", 1.0, true);
        matrix.addFunction("MATH", "ROUND", "ROUND", 1.0, true);
        
        // Date/Time Functions
        matrix.addCategory("DATE_TIME", 0.9, "Good support for date operations");
        matrix.addFunction("DATE_TIME", "TODAY", "CURRENT_DATE", 1.0, true);
        matrix.addFunction("DATE_TIME", "NOW", "CURRENT_TIMESTAMP", 1.0, true);
        matrix.addFunction("DATE_TIME", "YEAR", "EXTRACT_YEAR", 1.0, true);
        matrix.addFunction("DATE_TIME", "ADDMONTHS", "DATE_ADD_MONTHS", 0.9, true);
        matrix.addFunction("DATE_TIME", "WEEKDAY", "DAYOFWEEK", 0.7, false);
        
        // Logical Functions
        matrix.addCategory("LOGICAL", 1.0, "Full support for logical operations");
        matrix.addFunction("LOGICAL", "IF", "IF", 1.0, true);
        matrix.addFunction("LOGICAL", "AND", "AND", 1.0, true);
        matrix.addFunction("LOGICAL", "OR", "OR", 1.0, true);
        matrix.addFunction("LOGICAL", "CASE", "CASE_WHEN", 1.0, true);
        
        // Text Functions
        matrix.addCategory("TEXT", 0.95, "Excellent support for text operations");
        matrix.addFunction("TEXT", "CONCATENATE", "CONCAT", 1.0, true);
        matrix.addFunction("TEXT", "LEN", "LENGTH", 1.0, true);
        matrix.addFunction("TEXT", "UPPER", "UPPER", 1.0, true);
        matrix.addFunction("TEXT", "FIND", "LOCATE", 0.9, true);
        
        // Lookup Functions (Limited)
        matrix.addCategory("LOOKUP", 0.6, "Limited support - requires custom implementation");
        matrix.addFunction("LOOKUP", "VLOOKUP", "JOIN_LOOKUP", 0.6, false);
        matrix.addFunction("LOOKUP", "LOOKUP", "SUBQUERY", 0.7, false);
        
        return ResponseEntity.ok(matrix);
    }
    
    /**
     * Combined analysis result containing both query analysis and function mapping
     */
    public static class CombinedAnalysisResult {
        private String originalQuery;
        private QueryUnderstandingAgent.QueryAnalysisResult analysisResult;
        private FunctionMappingAgent.FunctionMappingResult mappingResult;
        
        // Getters and setters
        public String getOriginalQuery() { return originalQuery; }
        public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
        
        public QueryUnderstandingAgent.QueryAnalysisResult getAnalysisResult() { return analysisResult; }
        public void setAnalysisResult(QueryUnderstandingAgent.QueryAnalysisResult analysisResult) { this.analysisResult = analysisResult; }
        
        public FunctionMappingAgent.FunctionMappingResult getMappingResult() { return mappingResult; }
        public void setMappingResult(FunctionMappingAgent.FunctionMappingResult mappingResult) { this.mappingResult = mappingResult; }
    }
    
    /**
     * Function support matrix for documentation
     */
    public static class FunctionMatrix {
        private java.util.Map<String, CategoryInfo> categories = new java.util.HashMap<>();
        
        public void addCategory(String name, double supportLevel, String description) {
            categories.put(name, new CategoryInfo(name, supportLevel, description));
        }
        
        public void addFunction(String category, String salesforceFunc, String crmFunc, double compatibility, boolean supported) {
            CategoryInfo cat = categories.get(category);
            if (cat != null) {
                cat.addFunction(new FunctionInfo(salesforceFunc, crmFunc, compatibility, supported));
            }
        }
        
        public java.util.Map<String, CategoryInfo> getCategories() { return categories; }
        
        public static class CategoryInfo {
            private String name;
            private double supportLevel;
            private String description;
            private java.util.List<FunctionInfo> functions = new java.util.ArrayList<>();
            
            public CategoryInfo(String name, double supportLevel, String description) {
                this.name = name;
                this.supportLevel = supportLevel;
                this.description = description;
            }
            
            public void addFunction(FunctionInfo function) { functions.add(function); }
            
            // Getters
            public String getName() { return name; }
            public double getSupportLevel() { return supportLevel; }
            public String getDescription() { return description; }
            public java.util.List<FunctionInfo> getFunctions() { return functions; }
        }
        
        public static class FunctionInfo {
            private String salesforceFunction;
            private String crmFunction;
            private double compatibilityScore;
            private boolean fullySupported;
            
            public FunctionInfo(String salesforceFunction, String crmFunction, double compatibilityScore, boolean fullySupported) {
                this.salesforceFunction = salesforceFunction;
                this.crmFunction = crmFunction;
                this.compatibilityScore = compatibilityScore;
                this.fullySupported = fullySupported;
            }
            
            // Getters
            public String getSalesforceFunction() { return salesforceFunction; }
            public String getCrmFunction() { return crmFunction; }
            public double getCompatibilityScore() { return compatibilityScore; }
            public boolean isFullySupported() { return fullySupported; }
        }
    }
}