package info.hemendra.demoaiopenrouter.controller;

import info.hemendra.demoaiopenrouter.model.FormulaFunctionsMetadata;
import info.hemendra.demoaiopenrouter.util.FormulaMetadataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Controller for accessing formula metadata
 */
@RestController
@RequestMapping("/api/formula-metadata")
public class FormulaMetadataController {
    
    private static final Logger log = LoggerFactory.getLogger(FormulaMetadataController.class);
    
    @Autowired
    private FormulaMetadataUtil formulaMetadataUtil;
    
    /**
     * Get all formula functions metadata
     */
    @GetMapping("/all")
    public ResponseEntity<FormulaFunctionsMetadata> getAllMetadata() {
        log.info("[METADATA_CONTROLLER] Retrieving all formula metadata");
        
        try {
            FormulaFunctionsMetadata metadata = formulaMetadataUtil.loadMetadata();
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error retrieving metadata: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get specific function definition
     */
    @GetMapping("/function/{functionName}")
    public ResponseEntity<FormulaFunctionsMetadata.FunctionDefinition> getFunction(
            @PathVariable String functionName) {
        
        log.info("[METADATA_CONTROLLER] Retrieving function: {}", functionName);
        
        try {
            FormulaFunctionsMetadata.FunctionDefinition function = 
                formulaMetadataUtil.getFunction(functionName.toUpperCase());
            
            if (function != null) {
                return ResponseEntity.ok(function);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error retrieving function {}: {}", functionName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get functions by category
     */
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<List<FormulaFunctionsMetadata.FunctionDefinition>> getFunctionsByCategory(
            @PathVariable String categoryName) {
        
        log.info("[METADATA_CONTROLLER] Retrieving functions for category: {}", categoryName);
        
        try {
            List<FormulaFunctionsMetadata.FunctionDefinition> functions = 
                formulaMetadataUtil.getFunctionsByCategory(categoryName);
            
            return ResponseEntity.ok(functions);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error retrieving functions for category {}: {}", 
                    categoryName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Search functions by term
     */
    @GetMapping("/search")
    public ResponseEntity<List<FormulaFunctionsMetadata.FunctionDefinition>> searchFunctions(
            @RequestParam String term) {
        
        log.info("[METADATA_CONTROLLER] Searching functions with term: {}", term);
        
        try {
            List<FormulaFunctionsMetadata.FunctionDefinition> functions = 
                formulaMetadataUtil.searchFunctions(term);
            
            return ResponseEntity.ok(functions);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error searching functions with term {}: {}", 
                    term, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all available function names
     */
    @GetMapping("/function-names")
    public ResponseEntity<Set<String>> getAllFunctionNames() {
        log.info("[METADATA_CONTROLLER] Retrieving all function names");
        
        try {
            Set<String> functionNames = formulaMetadataUtil.getAllFunctionNames();
            return ResponseEntity.ok(functionNames);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error retrieving function names: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all available categories
     */
    @GetMapping("/categories")
    public ResponseEntity<Set<String>> getAllCategories() {
        log.info("[METADATA_CONTROLLER] Retrieving all categories");
        
        try {
            Set<String> categories = formulaMetadataUtil.getAllCategories();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error retrieving categories: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get functions by return type
     */
    @GetMapping("/return-type/{returnType}")
    public ResponseEntity<List<FormulaFunctionsMetadata.FunctionDefinition>> getFunctionsByReturnType(
            @PathVariable String returnType) {
        
        log.info("[METADATA_CONTROLLER] Retrieving functions with return type: {}", returnType);
        
        try {
            List<FormulaFunctionsMetadata.FunctionDefinition> functions = 
                formulaMetadataUtil.getFunctionsByReturnType(returnType);
            
            return ResponseEntity.ok(functions);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error retrieving functions by return type {}: {}", 
                    returnType, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get metadata statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<FormulaFunctionsMetadata.MetadataInfo> getMetadataStats() {
        log.info("[METADATA_CONTROLLER] Retrieving metadata statistics");
        
        try {
            FormulaFunctionsMetadata.MetadataInfo metadataInfo = formulaMetadataUtil.getMetadataInfo();
            return ResponseEntity.ok(metadataInfo);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error retrieving metadata stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Check if function exists
     */
    @GetMapping("/exists/{functionName}")
    public ResponseEntity<Boolean> functionExists(@PathVariable String functionName) {
        log.info("[METADATA_CONTROLLER] Checking if function exists: {}", functionName);
        
        try {
            boolean exists = formulaMetadataUtil.functionExists(functionName.toUpperCase());
            return ResponseEntity.ok(exists);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error checking function existence {}: {}", 
                    functionName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Reload metadata from file
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadMetadata() {
        log.info("[METADATA_CONTROLLER] Reloading metadata from file");
        
        try {
            FormulaFunctionsMetadata metadata = formulaMetadataUtil.reloadMetadata();
            String message = String.format("Metadata reloaded successfully. Functions: %d, Categories: %d", 
                    metadata.getFunctions().size(), metadata.getCategories().size());
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("[METADATA_CONTROLLER] Error reloading metadata: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get API usage examples
     */
    @GetMapping("/examples")
    public ResponseEntity<String> getUsageExamples() {
        String examples = """
                # Formula Metadata API Usage Examples
                
                ## Get All Metadata
                GET /api/formula-metadata/all
                
                ## Get Specific Function
                GET /api/formula-metadata/function/IF
                GET /api/formula-metadata/function/CONCATENATE
                
                ## Get Functions by Category
                GET /api/formula-metadata/category/Logical
                GET /api/formula-metadata/category/Math
                GET /api/formula-metadata/category/Text
                
                ## Search Functions
                GET /api/formula-metadata/search?term=date
                GET /api/formula-metadata/search?term=calculation
                
                ## Get Function Names
                GET /api/formula-metadata/function-names
                
                ## Get Categories
                GET /api/formula-metadata/categories
                
                ## Get Functions by Return Type
                GET /api/formula-metadata/return-type/String
                GET /api/formula-metadata/return-type/Boolean
                GET /api/formula-metadata/return-type/double
                
                ## Get Statistics
                GET /api/formula-metadata/stats
                
                ## Check Function Existence
                GET /api/formula-metadata/exists/UPPER
                GET /api/formula-metadata/exists/NONEXISTENT
                
                ## Reload Metadata
                POST /api/formula-metadata/reload
                
                ## Response Examples
                
                ### Function Definition Response:
                {
                  "category": "Logical",
                  "description": "Returns one value if a condition is true, and another if false",
                  "parameters": [
                    {
                      "name": "condition",
                      "type": "String",
                      "description": "The condition to evaluate",
                      "required": true
                    },
                    {
                      "name": "trueValue",
                      "type": "Object",
                      "description": "Value to return if condition is true",
                      "required": true
                    },
                    {
                      "name": "falseValue",
                      "type": "Object",
                      "description": "Value to return if condition is false",
                      "required": true
                    }
                  ],
                  "returnType": "Object",
                  "examples": [
                    "IF(isActive, 'Active', 'Inactive')",
                    "IF(amount > 50000, 'High Value', 'Standard')"
                  ],
                  "useCases": [
                    "Conditional formatting",
                    "Status determination",
                    "Default value handling"
                  ],
                  "relatedFunctions": ["CASE", "AND", "OR", "ISBLANK"],
                  "className": "LogicalFormulaFieldService",
                  "methodName": "IF"
                }
                """;
        
        return ResponseEntity.ok(examples);
    }
}