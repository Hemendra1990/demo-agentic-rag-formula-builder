package info.hemendra.demoaiopenrouter.controller;

import info.hemendra.demoaiopenrouter.service.FormulaRecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/formulas")
public class FormulaRecommendationController {

    @Autowired
    private FormulaRecommendationService formulaRecommendationService;

    /**
     * Search for formulas based on natural language query
     * @param query User's query
     * @param topK Number of results (default 5)
     * @return List of relevant formulas
     */
    @GetMapping("/search")
    public List<FormulaRecommendationService.FormulaRecommendation> searchFormulas(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        return formulaRecommendationService.searchFormulas(query, topK);
    }

    /**
     * Get formulas by category
     * @param category Category name
     * @param topK Number of results (default 10)
     * @return List of formulas in the category
     */
    @GetMapping("/category/{category}")
    public List<FormulaRecommendationService.FormulaRecommendation> getByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "10") int topK) {
        return formulaRecommendationService.searchByCategory(category, topK);
    }

    /**
     * Get examples for a specific function
     * @param functionName Name of the function
     * @return List of examples
     */
    @GetMapping("/{functionName}/examples")
    public List<FormulaRecommendationService.FormulaRecommendation> getExamples(
            @PathVariable String functionName) {
        return formulaRecommendationService.getExamples(functionName);
    }

    /**
     * Get detailed information about a function
     * @param functionName Name of the function
     * @return Function details
     */
    @GetMapping("/{functionName}/details")
    public FormulaRecommendationService.FormulaRecommendation getFunctionDetails(
            @PathVariable String functionName) {
        return formulaRecommendationService.getFunctionDetails(functionName);
    }

    /**
     * Search for functions that solve specific use cases
     * @param useCase Description of the use case
     * @param topK Number of results (default 5)
     * @return List of relevant functions
     */
    @GetMapping("/usecase")
    public List<FormulaRecommendationService.FormulaRecommendation> searchByUseCase(
            @RequestParam String useCase,
            @RequestParam(defaultValue = "5") int topK) {
        return formulaRecommendationService.searchByUseCase(useCase, topK);
    }

    /**
     * Search for common patterns
     * @param pattern Description of the pattern
     * @param topK Number of results (default 5)
     * @return List of matching patterns
     */
    @GetMapping("/patterns")
    public List<FormulaRecommendationService.FormulaRecommendation> searchPatterns(
            @RequestParam String pattern,
            @RequestParam(defaultValue = "5") int topK) {
        return formulaRecommendationService.searchPatterns(pattern, topK);
    }
}