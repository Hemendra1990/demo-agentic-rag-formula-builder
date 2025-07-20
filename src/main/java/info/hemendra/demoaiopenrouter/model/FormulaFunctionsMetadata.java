package info.hemendra.demoaiopenrouter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * POJO representing the complete formula functions metadata structure
 */
public class FormulaFunctionsMetadata {
    
    @JsonProperty("functions")
    private Map<String, FunctionDefinition> functions;
    
    @JsonProperty("categories")
    private Map<String, CategoryDefinition> categories;
    
    @JsonProperty("common_patterns")
    private Map<String, PatternDefinition> commonPatterns;
    
    @JsonProperty("metadata")
    private MetadataInfo metadata;
    
    // Constructors
    public FormulaFunctionsMetadata() {}
    
    // Getters and Setters
    public Map<String, FunctionDefinition> getFunctions() { return functions; }
    public void setFunctions(Map<String, FunctionDefinition> functions) { this.functions = functions; }
    
    public Map<String, CategoryDefinition> getCategories() { return categories; }
    public void setCategories(Map<String, CategoryDefinition> categories) { this.categories = categories; }
    
    public Map<String, PatternDefinition> getCommonPatterns() { return commonPatterns; }
    public void setCommonPatterns(Map<String, PatternDefinition> commonPatterns) { this.commonPatterns = commonPatterns; }
    
    public MetadataInfo getMetadata() { return metadata; }
    public void setMetadata(MetadataInfo metadata) { this.metadata = metadata; }
    
    /**
     * Individual function definition
     */
    public static class FunctionDefinition {
        
        @JsonProperty("category")
        private String category;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("parameters")
        private List<ParameterDefinition> parameters;
        
        @JsonProperty("return_type")
        private String returnType;
        
        @JsonProperty("examples")
        private List<String> examples;
        
        @JsonProperty("use_cases")
        private List<String> useCases;
        
        @JsonProperty("related_functions")
        private List<String> relatedFunctions;
        
        @JsonProperty("class")
        private String className;
        
        @JsonProperty("method")
        private String methodName;

        public String toFunctionString() {
            return
                   "category='" + category + '\'' +
                   ", description='" + description + '\'' +
                   ", parameters=" + parameters +
                   ", returnType='" + returnType + '\'' +
                   ", examples=" + examples +
                   ", useCases=" + useCases +
                   ", relatedFunctions=" + relatedFunctions +
                   ", className='" + className + '\'' +
                   ", methodName='" + methodName + '\'';

        }

        // Constructors
        public FunctionDefinition() {}
        
        // Getters and Setters
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public List<ParameterDefinition> getParameters() { return parameters; }
        public void setParameters(List<ParameterDefinition> parameters) { this.parameters = parameters; }
        
        public String getReturnType() { return returnType; }
        public void setReturnType(String returnType) { this.returnType = returnType; }
        
        public List<String> getExamples() { return examples; }
        public void setExamples(List<String> examples) { this.examples = examples; }
        
        public List<String> getUseCases() { return useCases; }
        public void setUseCases(List<String> useCases) { this.useCases = useCases; }
        
        public List<String> getRelatedFunctions() { return relatedFunctions; }
        public void setRelatedFunctions(List<String> relatedFunctions) { this.relatedFunctions = relatedFunctions; }
        
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        
        @Override
        public String toString() {
            return String.format("FunctionDefinition{category='%s', description='%s', returnType='%s', " +
                    "parameters=%d, examples=%d, useCases=%d}", 
                    category, description, returnType, 
                    parameters != null ? parameters.size() : 0,
                    examples != null ? examples.size() : 0,
                    useCases != null ? useCases.size() : 0);
        }
    }
    
    /**
     * Function parameter definition
     */
    public static class ParameterDefinition {
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("required")
        private Boolean required;
        
        // Constructors
        public ParameterDefinition() {}
        
        public ParameterDefinition(String name, String type, String description, Boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        
        public boolean isRequired() { return required != null && required; }
        
        @Override
        public String toString() {
            return String.format("ParameterDefinition{name='%s', type='%s', required=%s}", 
                    name, type, required);
        }
    }
    
    /**
     * Category definition
     */
    public static class CategoryDefinition {
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("functions")
        private List<String> functions;
        
        @JsonProperty("common_use_cases")
        private List<String> commonUseCases;
        
        // Constructors
        public CategoryDefinition() {}
        
        // Getters and Setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public List<String> getFunctions() { return functions; }
        public void setFunctions(List<String> functions) { this.functions = functions; }
        
        public List<String> getCommonUseCases() { return commonUseCases; }
        public void setCommonUseCases(List<String> commonUseCases) { this.commonUseCases = commonUseCases; }
        
        @Override
        public String toString() {
            return String.format("CategoryDefinition{description='%s', functions=%d, commonUseCases=%d}", 
                    description, 
                    functions != null ? functions.size() : 0,
                    commonUseCases != null ? commonUseCases.size() : 0);
        }
    }
    
    /**
     * Common pattern definition
     */
    public static class PatternDefinition {
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("examples")
        private List<String> examples;
        
        // Constructors
        public PatternDefinition() {}
        
        // Getters and Setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public List<String> getExamples() { return examples; }
        public void setExamples(List<String> examples) { this.examples = examples; }
        
        @Override
        public String toString() {
            return String.format("PatternDefinition{description='%s', examples=%d}", 
                    description, examples != null ? examples.size() : 0);
        }
    }
    
    /**
     * Metadata information
     */
    public static class MetadataInfo {
        
        @JsonProperty("total_functions")
        private Integer totalFunctions;
        
        @JsonProperty("categories_count")
        private Integer categoriesCount;
        
        @JsonProperty("last_updated")
        private String lastUpdated;
        
        @JsonProperty("version")
        private String version;
        
        @JsonProperty("system")
        private String system;
        
        @JsonProperty("integration")
        private String integration;
        
        // Constructors
        public MetadataInfo() {}
        
        // Getters and Setters
        public Integer getTotalFunctions() { return totalFunctions; }
        public void setTotalFunctions(Integer totalFunctions) { this.totalFunctions = totalFunctions; }
        
        public Integer getCategoriesCount() { return categoriesCount; }
        public void setCategoriesCount(Integer categoriesCount) { this.categoriesCount = categoriesCount; }
        
        public String getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getSystem() { return system; }
        public void setSystem(String system) { this.system = system; }
        
        public String getIntegration() { return integration; }
        public void setIntegration(String integration) { this.integration = integration; }
        
        @Override
        public String toString() {
            return String.format("MetadataInfo{totalFunctions=%d, categoriesCount=%d, version='%s', system='%s'}", 
                    totalFunctions, categoriesCount, version, system);
        }
    }
    
    @Override
    public String toString() {
        return String.format("FormulaFunctionsMetadata{functions=%d, categories=%d, patterns=%d, metadata=%s}", 
                functions != null ? functions.size() : 0,
                categories != null ? categories.size() : 0,
                commonPatterns != null ? commonPatterns.size() : 0,
                metadata);
    }
}