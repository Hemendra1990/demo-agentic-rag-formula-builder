package info.hemendra.demoaiopenrouter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FormulaRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(FormulaRecommendationService.class);

    @Autowired
    private VectorStore vectorStore;

    /**
     * Search for relevant formula functions based on user query
     * @param query User's natural language query
     * @param topK Number of results to return (default 5)
     * @return List of relevant formula functions with similarity scores
     */
    public List<FormulaRecommendation> searchFormulas(String query, int topK) {
        log.info("Searching for formulas matching query: '{}'", query);
        
        List<Document> documents = vectorStore.similaritySearch(query);
        
        return documents.stream()
                .limit(topK)
                .map(this::mapToRecommendation)
                .collect(Collectors.toList());
    }

    /**
     * Search for formulas by category
     * @param category Category name (e.g., "Math", "Text", "Date/Time")
     * @param topK Number of results to return
     * @return List of functions in the specified category
     */
    public List<FormulaRecommendation> searchByCategory(String category, int topK) {
        log.info("Searching for formulas in category: '{}'", category);
        
        String categoryQuery = "CATEGORY: " + category;
        
        List<Document> documents = vectorStore.similaritySearch(categoryQuery);
        
        return documents.stream()
                .filter(doc -> category.equals(doc.getMetadata().get("category")))
                .limit(topK)
                .map(this::mapToRecommendation)
                .collect(Collectors.toList());
    }

    /**
     * Search for usage examples of specific functions
     * @param functionName Name of the function
     * @return List of examples for the function
     */
    public List<FormulaRecommendation> getExamples(String functionName) {
        log.info("Getting examples for function: '{}'", functionName);
        
        String exampleQuery = "FUNCTION EXAMPLES FOR: " + functionName;
        
        List<Document> documents = vectorStore.similaritySearch(exampleQuery);
        
        return documents.stream()
                .filter(doc -> "function_examples".equals(doc.getMetadata().get("document_type")))
                .limit(5)
                .map(this::mapToRecommendation)
                .collect(Collectors.toList());
    }

    /**
     * Search for functions that solve specific use cases
     * @param useCase Description of the use case
     * @param topK Number of results to return
     * @return List of functions that can solve the use case
     */
    public List<FormulaRecommendation> searchByUseCase(String useCase, int topK) {
        log.info("Searching for functions for use case: '{}'", useCase);
        
        List<Document> documents = vectorStore.similaritySearch(useCase);
        
        return documents.stream()
                .limit(topK)
                .map(this::mapToRecommendation)
                .collect(Collectors.toList());
    }

    /**
     * Get comprehensive information about a specific function
     * @param functionName Name of the function
     * @return Detailed information about the function
     */
    public FormulaRecommendation getFunctionDetails(String functionName) {
        log.info("Getting details for function: '{}'", functionName);
        
        String detailQuery = "FUNCTION: " + functionName;
        
        List<Document> documents = vectorStore.similaritySearch(detailQuery);
        
        return documents.stream()
                .filter(doc -> "function_reference".equals(doc.getMetadata().get("document_type")))
                .findFirst()
                .map(this::mapToRecommendation)
                .orElse(null);
    }

    /**
     * Search for common patterns that match a specific scenario
     * @param pattern Description of the pattern or scenario
     * @param topK Number of results to return
     * @return List of matching patterns
     */
    public List<FormulaRecommendation> searchPatterns(String pattern, int topK) {
        log.info("Searching for patterns matching: '{}'", pattern);
        
        List<Document> documents = vectorStore.similaritySearch(pattern);
        
        return documents.stream()
                .filter(doc -> "pattern_reference".equals(doc.getMetadata().get("document_type")))
                .limit(topK)
                .map(this::mapToRecommendation)
                .collect(Collectors.toList());
    }

    private FormulaRecommendation mapToRecommendation(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        
        FormulaRecommendation recommendation = new FormulaRecommendation();
        recommendation.setContent(document.getText());
        recommendation.setFunctionName((String) metadata.get("function_name"));
        recommendation.setCategory((String) metadata.get("category"));
        recommendation.setDocumentType((String) metadata.get("document_type"));
        recommendation.setReturnType((String) metadata.get("return_type"));
        recommendation.setImplementationClass((String) metadata.get("implementation_class"));
        recommendation.setImplementationMethod((String) metadata.get("implementation_method"));
        recommendation.setSearchType((String) metadata.get("search_type"));
        recommendation.setPatternName((String) metadata.get("pattern_name"));
        
        return recommendation;
    }

    /**
     * Data class for formula recommendations
     */
    public static class FormulaRecommendation {
        private String content;
        private String functionName;
        private String category;
        private String documentType;
        private String returnType;
        private String implementationClass;
        private String implementationMethod;
        private String searchType;
        private String patternName;

        // Getters and setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getFunctionName() { return functionName; }
        public void setFunctionName(String functionName) { this.functionName = functionName; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }

        public String getReturnType() { return returnType; }
        public void setReturnType(String returnType) { this.returnType = returnType; }

        public String getImplementationClass() { return implementationClass; }
        public void setImplementationClass(String implementationClass) { this.implementationClass = implementationClass; }

        public String getImplementationMethod() { return implementationMethod; }
        public void setImplementationMethod(String implementationMethod) { this.implementationMethod = implementationMethod; }

        public String getSearchType() { return searchType; }
        public void setSearchType(String searchType) { this.searchType = searchType; }

        public String getPatternName() { return patternName; }
        public void setPatternName(String patternName) { this.patternName = patternName; }
    }
}