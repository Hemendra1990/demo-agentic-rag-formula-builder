package info.hemendra.demoaiopenrouter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.hemendra.demoaiopenrouter.model.FormulaFunctionsMetadata;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for reading and parsing formula functions metadata JSON
 */
@Component
public class FormulaMetadataUtil {
    
    private static final Logger log = LoggerFactory.getLogger(FormulaMetadataUtil.class);
    private static final String METADATA_FILE_PATH = "formula/formula-functions-metadata.json";
    
    private final ObjectMapper objectMapper;
    private FormulaFunctionsMetadata cachedMetadata;
    
    public FormulaMetadataUtil() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Load and parse the formula functions metadata JSON file
     * @return FormulaFunctionsMetadata object containing all function definitions
     */
    public FormulaFunctionsMetadata loadMetadata() {
        if (cachedMetadata != null) {
            return cachedMetadata;
        }
        
        try {
            log.info("[METADATA_UTIL] Loading formula functions metadata from: {}", METADATA_FILE_PATH);
            
            ClassPathResource resource = new ClassPathResource(METADATA_FILE_PATH);
            
            if (!resource.exists()) {
                log.error("[METADATA_UTIL] Metadata file not found: {}", METADATA_FILE_PATH);
                return createEmptyMetadata();
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                cachedMetadata = objectMapper.readValue(inputStream, FormulaFunctionsMetadata.class);
                log.info("[METADATA_UTIL] Successfully loaded {} functions across {} categories", 
                        cachedMetadata.getFunctions().size(), 
                        cachedMetadata.getCategories().size());
                
                return cachedMetadata;
            }
            
        } catch (IOException e) {
            log.error("[METADATA_UTIL] Failed to load formula metadata: {}", e.getMessage(), e);
            return createEmptyMetadata();
        }
    }
    
    /**
     * Load metadata as raw JsonNode for custom processing
     * @return JsonNode representing the entire metadata structure
     */
    public JsonNode loadMetadataAsJson() {
        try {
            log.debug("[METADATA_UTIL] Loading formula metadata as JsonNode");
            
            ClassPathResource resource = new ClassPathResource(METADATA_FILE_PATH);
            
            if (!resource.exists()) {
                log.error("[METADATA_UTIL] Metadata file not found: {}", METADATA_FILE_PATH);
                return objectMapper.createObjectNode();
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode rootNode = objectMapper.readTree(inputStream);
                log.debug("[METADATA_UTIL] Successfully loaded metadata as JsonNode");
                return rootNode;
            }
            
        } catch (IOException e) {
            log.error("[METADATA_UTIL] Failed to load formula metadata as JsonNode: {}", e.getMessage(), e);
            return objectMapper.createObjectNode();
        }
    }
    
    /**
     * Get a specific function definition by name
     * @param functionName Name of the function to retrieve
     * @return Function definition or null if not found
     */
    public FormulaFunctionsMetadata.FunctionDefinition getFunction(String functionName) {
        FormulaFunctionsMetadata metadata = loadMetadata();
        Optional<String> first = metadata.getFunctions().keySet().stream().filter(key -> key.toUpperCase().equals(functionName.toUpperCase())).findFirst();
        return metadata.getFunctions().get(first.get());
    }
    
    /**
     * Get all functions in a specific category
     * @param categoryName Name of the category
     * @return List of function definitions in the category
     */
    public java.util.List<FormulaFunctionsMetadata.FunctionDefinition> getFunctionsByCategory(String categoryName) {
        FormulaFunctionsMetadata metadata = loadMetadata();
        
        return metadata.getFunctions().values().stream()
                .filter(func -> categoryName.equals(func.getCategory()))
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get all available function names
     * @return Set of all function names
     */
    public java.util.Set<String> getAllFunctionNames() {
        FormulaFunctionsMetadata metadata = loadMetadata();
        return metadata.getFunctions().keySet();
    }
    
    /**
     * Get all available categories
     * @return Set of all category names
     */
    public java.util.Set<String> getAllCategories() {
        FormulaFunctionsMetadata metadata = loadMetadata();
        return metadata.getCategories().keySet();
    }
    
    /**
     * Check if a function exists
     * @param functionName Name of the function to check
     * @return true if function exists, false otherwise
     */
    public boolean functionExists(String functionName) {
        FormulaFunctionsMetadata metadata = loadMetadata();
        return metadata.getFunctions().containsKey(functionName);
    }
    
    /**
     * Get category information
     * @param categoryName Name of the category
     * @return Category definition or null if not found
     */
    public FormulaFunctionsMetadata.CategoryDefinition getCategory(String categoryName) {
        FormulaFunctionsMetadata metadata = loadMetadata();

        Optional<String> first = metadata.getCategories()
                .keySet()
                .stream().filter(key -> key.toUpperCase().equals(categoryName.toUpperCase()))
                .findFirst();

        return metadata.getCategories().get(first.orElse(null));
    }

    public List<FormulaFunctionsMetadata.CategoryDefinition> getCategoryDefinitions(List<String> categories) {
        FormulaFunctionsMetadata metadata = loadMetadata();

        List<String> keys = metadata.getCategories()
                .keySet()
                .stream()
                .map(String::toUpperCase)
                .filter(categories::contains)
                .toList();

        List<FormulaFunctionsMetadata.CategoryDefinition> categoryDefinitions = new ArrayList<>();
        keys.forEach(key -> {
            FormulaFunctionsMetadata.CategoryDefinition category = metadata.getCategories().get(StringUtils.capitalize(key.toLowerCase()));
            categoryDefinitions.add(category);
        });

        return categoryDefinitions;
    }
    
    /**
     * Search functions by description or use case
     * @param searchTerm Term to search for
     * @return List of matching function definitions
     */
    public java.util.List<FormulaFunctionsMetadata.FunctionDefinition> searchFunctions(String searchTerm) {
        FormulaFunctionsMetadata metadata = loadMetadata();
        String lowerSearchTerm = searchTerm.toLowerCase();
        
        return metadata.getFunctions().values().stream()
                .filter(func -> 
                    func.getDescription().toLowerCase().contains(lowerSearchTerm) ||
                    func.getUseCases().stream().anyMatch(useCase -> 
                        useCase.toLowerCase().contains(lowerSearchTerm)) ||
                    func.getExamples().stream().anyMatch(example -> 
                        example.toLowerCase().contains(lowerSearchTerm))
                )
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get functions that have specific return type
     * @param returnType The return type to filter by
     * @return List of functions with the specified return type
     */
    public java.util.List<FormulaFunctionsMetadata.FunctionDefinition> getFunctionsByReturnType(String returnType) {
        FormulaFunctionsMetadata metadata = loadMetadata();
        
        return metadata.getFunctions().values().stream()
                .filter(func -> returnType.equals(func.getReturnType()))
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get metadata statistics
     * @return Metadata object containing statistics
     */
    public FormulaFunctionsMetadata.MetadataInfo getMetadataInfo() {
        FormulaFunctionsMetadata metadata = loadMetadata();
        return metadata.getMetadata();
    }
    
    /**
     * Reload metadata from file (clears cache)
     * @return Reloaded FormulaFunctionsMetadata
     */
    public FormulaFunctionsMetadata reloadMetadata() {
        log.info("[METADATA_UTIL] Reloading formula metadata from file");
        cachedMetadata = null;
        return loadMetadata();
    }
    
    /**
     * Create empty metadata object as fallback
     * @return Empty FormulaFunctionsMetadata object
     */
    private FormulaFunctionsMetadata createEmptyMetadata() {
        FormulaFunctionsMetadata metadata = new FormulaFunctionsMetadata();
        metadata.setFunctions(new java.util.HashMap<>());
        metadata.setCategories(new java.util.HashMap<>());
        metadata.setCommonPatterns(new java.util.HashMap<>());
        
        FormulaFunctionsMetadata.MetadataInfo metadataInfo = new FormulaFunctionsMetadata.MetadataInfo();
        metadataInfo.setTotalFunctions(0);
        metadataInfo.setCategoriesCount(0);
        metadataInfo.setVersion("0.0");
        metadataInfo.setSystem("CRM Formula Engine");
        metadata.setMetadata(metadataInfo);
        
        return metadata;
    }

    public List<FormulaFunctionsMetadata.FunctionDefinition> getFunctionDefinition(List<String> functions) {
        FormulaFunctionsMetadata metadata = loadMetadata();

        List<String> keys = metadata.getFunctions().keySet().stream().map(String::toUpperCase)
                .filter(functions::contains).distinct().toList();

        List<FormulaFunctionsMetadata.FunctionDefinition> functionDefinitions = new ArrayList<>();
        keys.forEach(key -> {
            FormulaFunctionsMetadata.FunctionDefinition function = metadata.getFunctions().get(key);
            functionDefinitions.add(function);
        });

        return functionDefinitions;
    }
}