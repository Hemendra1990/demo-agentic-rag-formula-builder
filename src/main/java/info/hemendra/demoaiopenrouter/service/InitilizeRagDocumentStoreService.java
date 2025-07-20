package info.hemendra.demoaiopenrouter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InitilizeRagDocumentStoreService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(InitilizeRagDocumentStoreService.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.vectorstore.pgvector.table-name}")
    String tableName;

    @Autowired
    private VectorStore vectorStore;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterPropertiesSet() throws Exception {
        List<Map<String, Object>> maps = jdbcTemplate.queryForList("select count(*) from " + tableName);
        if (!maps.isEmpty()) {
            Map<String, Object> map = maps.get(0);
            long count = (long) map.get("count");
            if (count == 0) {
                log.info("Initializing the AI Vector Store with formula functions...");
                initializeVectorStore();
            } else {
                log.info("Vector store already contains {} documents", count);
            }
        }
    }
    
    private void initializeVectorStore() {
        try {
            log.info("Starting vector store initialization with formula functions metadata...");
            
            // Load the JSON file
            ClassPathResource resource = new ClassPathResource("formula/formula-functions-metadata.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            // Process and create documents
            List<Document> documents = processFormulaFunctions(rootNode);
            
            // Add documents to vector store
            vectorStore.add(documents);
            
            log.info("Successfully initialized vector store with {} documents", documents.size());
            
        } catch (IOException e) {
            log.error("Error initializing vector store", e);
            throw new RuntimeException("Failed to initialize vector store", e);
        }
    }

    private List<Document> processFormulaFunctions(JsonNode rootNode) {
        List<Document> documents = new ArrayList<>();
        
        JsonNode functionsNode = rootNode.get("functions");
        if (functionsNode != null) {
            functionsNode.fields().forEachRemaining(entry -> {
                String functionName = entry.getKey();
                JsonNode functionData = entry.getValue();
                
                // Create multiple document types for better search coverage
                documents.addAll(createFunctionDocuments(functionName, functionData));
            });
        }
        
        // Add category-based documents
        documents.addAll(createCategoryDocuments(rootNode.get("categories")));
        
        // Add common patterns documents
        documents.addAll(createPatternDocuments(rootNode.get("common_patterns")));
        
        return documents;
    }

    private List<Document> createFunctionDocuments(String functionName, JsonNode functionData) {
        List<Document> documents = new ArrayList<>();
        
        // Main function document with comprehensive information
        String mainContent = buildMainFunctionContent(functionName, functionData);
        Map<String, Object> mainMetadata = buildMainMetadata(functionName, functionData);
        documents.add(new Document(mainContent, mainMetadata));
        
        // Example-focused document for usage patterns
        String exampleContent = buildExampleContent(functionName, functionData);
        Map<String, Object> exampleMetadata = buildExampleMetadata(functionName, functionData);
        documents.add(new Document(exampleContent, exampleMetadata));
        
        // Use case focused document for problem-solving
        String useCaseContent = buildUseCaseContent(functionName, functionData);
        Map<String, Object> useCaseMetadata = buildUseCaseMetadata(functionName, functionData);
        documents.add(new Document(useCaseContent, useCaseMetadata));
        
        return documents;
    }

    private String buildMainFunctionContent(String functionName, JsonNode functionData) {
        StringBuilder content = new StringBuilder();
        
        content.append("FUNCTION: ").append(functionName).append("\n");
        content.append("CATEGORY: ").append(functionData.get("category").asText()).append("\n");
        content.append("DESCRIPTION: ").append(functionData.get("description").asText()).append("\n");
        
        // Parameters
        JsonNode parameters = functionData.get("parameters");
        if (parameters != null && parameters.isArray()) {
            content.append("PARAMETERS:\n");
            for (JsonNode param : parameters) {
                content.append("  - ").append(param.get("name").asText())
                       .append(" (").append(param.get("type").asText()).append("): ")
                       .append(param.get("description").asText());
                if (param.get("required").asBoolean()) {
                    content.append(" [REQUIRED]");
                }
                content.append("\n");
            }
        }
        
        content.append("RETURN TYPE: ").append(functionData.get("return_type").asText()).append("\n");
        
        // Related functions
        JsonNode relatedFunctions = functionData.get("related_functions");
        if (relatedFunctions != null && relatedFunctions.isArray()) {
            content.append("RELATED FUNCTIONS: ");
            List<String> related = new ArrayList<>();
            for (JsonNode func : relatedFunctions) {
                related.add(func.asText());
            }
            content.append(String.join(", ", related)).append("\n");
        }
        
        return content.toString();
    }

    private String buildExampleContent(String functionName, JsonNode functionData) {
        StringBuilder content = new StringBuilder();
        
        content.append("FUNCTION EXAMPLES FOR: ").append(functionName).append("\n");
        content.append("CATEGORY: ").append(functionData.get("category").asText()).append("\n");
        content.append("DESCRIPTION: ").append(functionData.get("description").asText()).append("\n\n");
        
        JsonNode examples = functionData.get("examples");
        if (examples != null && examples.isArray()) {
            content.append("USAGE EXAMPLES:\n");
            int i = 1;
            for (JsonNode example : examples) {
                content.append(i++).append(". ").append(example.asText()).append("\n");
            }
        }
        
        // Add parameter context for better matching
        JsonNode parameters = functionData.get("parameters");
        if (parameters != null && parameters.isArray()) {
            content.append("\nPARAMETERS:\n");
            for (JsonNode param : parameters) {
                content.append("- ").append(param.get("name").asText())
                       .append(": ").append(param.get("description").asText()).append("\n");
            }
        }
        
        return content.toString();
    }

    private String buildUseCaseContent(String functionName, JsonNode functionData) {
        StringBuilder content = new StringBuilder();
        
        content.append("USE CASES FOR: ").append(functionName).append("\n");
        content.append("CATEGORY: ").append(functionData.get("category").asText()).append("\n");
        content.append("DESCRIPTION: ").append(functionData.get("description").asText()).append("\n\n");
        
        JsonNode useCases = functionData.get("use_cases");
        if (useCases != null && useCases.isArray()) {
            content.append("COMMON USE CASES:\n");
            for (JsonNode useCase : useCases) {
                content.append("- ").append(useCase.asText()).append("\n");
            }
        }
        
        // Add examples in context
        JsonNode examples = functionData.get("examples");
        if (examples != null && examples.isArray()) {
            content.append("\nEXAMPLE IMPLEMENTATIONS:\n");
            for (JsonNode example : examples) {
                content.append("- ").append(example.asText()).append("\n");
            }
        }
        
        return content.toString();
    }

    private Map<String, Object> buildMainMetadata(String functionName, JsonNode functionData) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("function_name", functionName);
        metadata.put("category", functionData.get("category").asText());
        metadata.put("document_type", "function_reference");
        metadata.put("return_type", functionData.get("return_type").asText());
        
        if (functionData.has("class")) {
            metadata.put("implementation_class", functionData.get("class").asText());
        }
        if (functionData.has("method")) {
            metadata.put("implementation_method", functionData.get("method").asText());
        }
        
        return metadata;
    }

    private Map<String, Object> buildExampleMetadata(String functionName, JsonNode functionData) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("function_name", functionName);
        metadata.put("category", functionData.get("category").asText());
        metadata.put("document_type", "function_examples");
        metadata.put("search_type", "usage_patterns");
        
        return metadata;
    }

    private Map<String, Object> buildUseCaseMetadata(String functionName, JsonNode functionData) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("function_name", functionName);
        metadata.put("category", functionData.get("category").asText());
        metadata.put("document_type", "use_cases");
        metadata.put("search_type", "problem_solving");
        
        return metadata;
    }

    private List<Document> createCategoryDocuments(JsonNode categoriesNode) {
        List<Document> documents = new ArrayList<>();
        
        if (categoriesNode != null) {
            categoriesNode.fields().forEachRemaining(entry -> {
                String categoryName = entry.getKey();
                JsonNode categoryData = entry.getValue();
                
                StringBuilder content = new StringBuilder();
                content.append("CATEGORY: ").append(categoryName).append("\n");
                content.append("DESCRIPTION: ").append(categoryData.get("description").asText()).append("\n");
                
                JsonNode functions = categoryData.get("functions");
                if (functions != null && functions.isArray()) {
                    content.append("FUNCTIONS IN THIS CATEGORY:\n");
                    for (JsonNode func : functions) {
                        content.append("- ").append(func.asText()).append("\n");
                    }
                }
                
                JsonNode commonUseCases = categoryData.get("common_use_cases");
                if (commonUseCases != null && commonUseCases.isArray()) {
                    content.append("COMMON USE CASES:\n");
                    for (JsonNode useCase : commonUseCases) {
                        content.append("- ").append(useCase.asText()).append("\n");
                    }
                }
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("category", categoryName);
                metadata.put("document_type", "category_overview");
                metadata.put("search_type", "category_browsing");
                
                documents.add(new Document(content.toString(), metadata));
            });
        }
        
        return documents;
    }

    private List<Document> createPatternDocuments(JsonNode patternsNode) {
        List<Document> documents = new ArrayList<>();
        
        if (patternsNode != null) {
            patternsNode.fields().forEachRemaining(entry -> {
                String patternName = entry.getKey();
                JsonNode patternData = entry.getValue();
                
                StringBuilder content = new StringBuilder();
                content.append("PATTERN: ").append(patternName.replace("_", " ").toUpperCase()).append("\n");
                content.append("DESCRIPTION: ").append(patternData.get("description").asText()).append("\n");
                
                JsonNode examples = patternData.get("examples");
                if (examples != null && examples.isArray()) {
                    content.append("PATTERN EXAMPLES:\n");
                    for (JsonNode example : examples) {
                        content.append("- ").append(example.asText()).append("\n");
                    }
                }
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("pattern_name", patternName);
                metadata.put("document_type", "pattern_reference");
                metadata.put("search_type", "pattern_matching");
                
                documents.add(new Document(content.toString(), metadata));
            });
        }
        
        return documents;
    }
}
