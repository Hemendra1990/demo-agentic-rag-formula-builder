# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.5.3 application that integrates with AI models through Spring AI framework. It demonstrates chat functionality using both OpenRouter API and local Ollama models with function calling capabilities.

## Development Commands

### Build and Run
```bash
# Build the application
./mvnw clean compile

# Run the application
./mvnw spring-boot:run

# Package the application
./mvnw clean package

# Run tests
./mvnw test
```

### Development Server
The application runs on `http://localhost:8080` by default.

## Architecture Overview

### AI Agent Services

#### QueryUnderstandingAgent (Agent 1)
**Purpose**: Query Understanding & Intent Classification Service

**Location**: `src/main/java/info/hemendra/demoaiopenrouter/service/QueryUnderstandingAgent.java`

**Responsibilities**:
- Extract business logic requirements from natural language
- Identify required function categories (Math, Date/Time, Logical, Text, etc.)
- Determine output data type (Number, Text, Boolean, Date, Currency, Percent)
- Extract field references and relationships
- Identify conditional logic patterns

**Key Features**:
- Structured JSON output for downstream processing
- Confidence scoring for analysis quality
- Fallback parsing when AI response isn't properly formatted
- Session-based memory integration
- Comprehensive logging and error handling

**API Endpoint**: `POST /api/query-analysis/analyze?query={query}&sessionId={sessionId}`

**Function Categories Supported**:
- MATH: Basic arithmetic, calculations, rounding
- DATE_TIME: Date calculations, comparisons, formatting
- LOGICAL: IF statements, AND/OR conditions, comparisons
- TEXT: String manipulation, concatenation, formatting
- LOOKUP: VLOOKUP, reference functions
- VALIDATION: Data validation, error checking
- CONVERSION: Type conversions, formatting
- AGGREGATION: SUM, COUNT, AVERAGE operations

**Conditional Patterns Detected**:
- IF_THEN_ELSE: Basic conditional logic
- NESTED_IF: Multiple condition levels
- AND_OR_LOGIC: Complex boolean conditions
- CASE_WHEN: Switch-like logic
- RANGE_CHECK: Value within range validation
- NULL_CHECK: Handling empty/null values

**Usage Example**:
```bash
curl -X POST "http://localhost:8080/api/query-analysis/analyze?query=Calculate commission as 5% of sales amount" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
{
  "businessLogic": "Calculate commission as 5% of sales amount",
  "functionCategories": ["MATH"],
  "outputDataType": "Currency",
  "fieldReferences": ["sales_amount"],
  "conditionalPatterns": [],
  "mathOperations": ["PERCENTAGE"],
  "complexityLevel": "Simple",
  "confidenceScore": 0.95
}
```

#### FunctionMappingAgent (Agent 2)
**Purpose**: Function Mapping & Availability Check Service

**Location**: `src/main/java/info/hemendra/demoaiopenrouter/service/FunctionMappingAgent.java`

**Responsibilities**:
- Check function availability in your CRM system
- Map Salesforce functions to your custom functions  
- Identify missing functions and suggest alternatives
- Validate function compatibility and limitations

**Key Features**:
- Comprehensive function mapping database with 60+ Salesforce functions
- Compatibility scoring for each function mapping
- AI-enhanced mapping for complex scenarios
- Alternative function suggestions for unsupported operations
- Detailed compatibility warnings and limitations

**API Endpoint**: `POST /api/function-mapping/map?sessionId={sessionId}`

**Supported Function Categories**:
- **MATH**: ADD, SUBTRACT, MULTIPLY, DIVIDE, ROUND, ABS, CEILING, FLOOR
- **DATE_TIME**: TODAY, NOW, YEAR, MONTH, DAY, ADDMONTHS, DATEVALUE, WEEKDAY
- **LOGICAL**: IF, AND, OR, NOT, ISNULL, ISBLANK, CASE, NESTED_IF
- **TEXT**: CONCATENATE, LEN, LEFT, RIGHT, MID, UPPER, LOWER, TRIM, FIND, SUBSTITUTE
- **LOOKUP**: VLOOKUP (partial), LOOKUP (partial), HLOOKUP (missing), INDEX (missing)
- **VALIDATION**: ISNUMBER, ISTEXT, ISERROR (partial)
- **CONVERSION**: VALUE, TEXT, CURRENCY, PERCENT (partial)
- **AGGREGATION**: SUM, COUNT, AVERAGE, MIN, MAX, MEDIAN (partial)

**Compatibility Levels**:
- **1.0**: Full compatibility, direct mapping
- **0.9**: High compatibility, minor syntax differences
- **0.8**: Good compatibility, some limitations
- **0.7**: Moderate compatibility, requires workarounds
- **0.6**: Limited compatibility, complex implementation needed
- **0.5**: Minimal compatibility, alternative approaches required

#### FunctionSelectionAgent (Agent 3)
**Purpose**: Function Selection & Parameter Mapping Service

**Location**: `src/main/java/info/hemendra/demoaiopenrouter/service/FunctionSelectionAgent.java`

**Responsibilities**:
- Select optimal functions from available mappings
- Map user parameters to function parameters
- Resolve parameter types and validation
- Handle parameter dependencies and relationships
- Optimize function selection for performance

**Key Features**:
- Priority-based function selection algorithm
- Automatic parameter mapping from user requirements
- Dependency resolution between functions
- Parameter validation and type checking
- Execution plan generation
- Performance optimization suggestions

**Selection Criteria**:
- Compatibility score (40% weight)
- Direct mention in requirements (30% weight)
- Output data type match (20% weight)
- Essential function bonus (10% weight)

**Parameter Mapping Sources**:
- Field references from user input
- Extracted conditions and logic patterns
- Default values based on parameter types
- Context-aware value extraction

#### FormulaSynthesisAgent (Agent 4)
**Purpose**: Formula Synthesis & Validation Service

**Location**: `src/main/java/info/hemendra/demoaiopenrouter/service/FormulaSynthesisAgent.java`

**Responsibilities**:
- Combine selected functions into complete formulas
- Handle nested function calls and dependencies
- Validate formula syntax and semantics
- Optimize formula structure for performance
- Generate multiple formula variations
- Provide syntax validation and error detection

**Key Features**:
- Multi-complexity formula generation (Simple, Medium, Complex)
- Automatic formula structure optimization
- Syntax validation with error reporting
- Alternative formula generation
- Performance-aware formula construction
- Comprehensive formula explanations

**Formula Patterns**:
- **Simple**: Single function with parameters
- **Medium**: Multiple functions with logical operators
- **Complex**: Nested functions with conditional logic

**Validation Rules**:
- Balanced parentheses checking
- Function name validation
- Parameter type compatibility
- Syntax error detection
- Performance impact assessment

#### FormulaTestingAgent (Agent 5)
**Purpose**: Testing & Optimization Service

**Location**: `src/main/java/info/hemendra/demoaiopenrouter/service/FormulaTestingAgent.java`

**Responsibilities**:
- Test formulas with sample data
- Validate formula correctness
- Optimize formulas for performance
- Generate test cases and scenarios
- Provide performance metrics
- Suggest improvements and alternatives

**Key Features**:
- Automated test case generation based on output types
- Comprehensive edge case testing
- Performance analysis and optimization
- Test result scoring and reporting
- Optimization recommendations
- Formula complexity analysis

**Test Categories**:
- **Basic Tests**: Standard input/output validation
- **Edge Cases**: Null values, empty strings, division by zero
- **Performance Tests**: Complexity analysis, execution time estimation
- **Type Tests**: Data type compatibility and conversion

**Optimization Suggestions**:
- **HIGH Priority**: Complex formula simplification, nested function flattening
- **MEDIUM Priority**: String concatenation optimization, conditional logic improvement
- **LOW Priority**: Minor performance tweaks, readability improvements

### Multi-Agent Workflow Integration

The complete multi-agent workflow is orchestrated in the `OpenAiService.generateFormulaWithAgents()` method:

```java
// Step 1: Query Understanding & Intent Classification
QueryAnalysisResult queryResult = queryUnderstandingAgent.analyzeQuery(message, sessionId);

// Step 2: Function Mapping & Availability Check  
FunctionMappingResult mappingResult = functionMappingAgent.mapFunctions(queryResult, sessionId);

// Step 3: Function Selection & Parameter Mapping
FunctionSelectionResult selectionResult = functionSelectionAgent.selectFunctions(queryResult, mappingResult, sessionId);

// Step 4: Formula Synthesis & Validation
FormulaSynthesisResult synthesisResult = formulaSynthesisAgent.synthesizeFormula(queryResult, mappingResult, selectionResult, sessionId);

// Step 5: Testing & Optimization
FormulaTestingResult testingResult = formulaTestingAgent.testAndOptimizeFormulas(queryResult, mappingResult, selectionResult, synthesisResult, sessionId);
```

**Workflow Benefits**:
- **Systematic Approach**: Each agent focuses on specific expertise
- **Quality Assurance**: Multiple validation layers ensure accuracy
- **Performance Optimization**: Built-in testing and optimization
- **Extensibility**: Easy to add new agents or modify existing ones
- **Comprehensive Logging**: Full traceability of the formula generation process

### Formula Metadata Utility

**Purpose**: Centralized access to formula function metadata

**Location**: `src/main/java/info/hemendra/demoaiopenrouter/util/FormulaMetadataUtil.java`

**Key Features**:
- Caching mechanism for improved performance
- Comprehensive API for metadata access
- JSON-based metadata storage
- Function search and filtering capabilities
- Category-based function organization

**API Endpoints**:
- `GET /api/formula-metadata/all` - Get all metadata
- `GET /api/formula-metadata/function/{name}` - Get specific function
- `GET /api/formula-metadata/category/{category}` - Get functions by category
- `GET /api/formula-metadata/search?term={term}` - Search functions
- `GET /api/formula-metadata/function-names` - Get all function names
- `GET /api/formula-metadata/categories` - Get all categories

### Testing

**Location**: `src/test/java/info/hemendra/demoaiopenrouter/AgentWorkflowTests.java`

**Test Coverage**:
- Complete multi-agent workflow integration
- Individual agent functionality
- Error handling and edge cases
- Performance validation
- API endpoint testing

**Running Tests**:
```bash
# Run all tests
./mvnw test

# Run specific agent tests
./mvnw test -Dtest=AgentWorkflowTests

# Run multi-agent workflow test
./mvnw test -Dtest=AgentWorkflowTests#testMultiAgentWorkflow
```

### Performance Metrics

The system provides comprehensive performance tracking:
- **Formula Complexity**: Scale of 1-10 based on function count and nesting
- **Execution Time**: Estimated based on formula complexity
- **Memory Usage**: Calculated from formula length and structure
- **Test Success Rate**: Percentage of tests passed
- **Overall Score**: Weighted combination of all metrics

### Error Handling

All agents include robust error handling:
- **Graceful Degradation**: Fallback mechanisms for each agent
- **Comprehensive Logging**: Detailed error tracking and debugging
- **User-Friendly Messages**: Clear error messages for troubleshooting
- **Recovery Strategies**: Automatic retry and alternative approaches

### Core Components

- **Main Application**: `DemoAiOpenrouterApplication.java` - Standard Spring Boot entry point
- **Configuration**: `config/AiConfig.java` - Currently contains commented ChatClient bean configuration
- **Service Layer**: `service/OpenAiService.java` - Handles AI chat interactions using Spring AI ChatClient
- **Controllers**: 
  - `ChatController.java` - Web controller serving chat UI and handling chat requests
  - `OpenAIController.java` - REST API controller for `/openai/hello` endpoint
- **Tools**: `tools/DateTimeTools.java` - Spring AI tool for providing current date/time to AI models
- **Frontend**: `templates/chat.html` - Complete chat interface with real-time messaging

### AI Integration

The application uses Spring AI with two possible configurations:
1. **Ollama** (currently active): Local model `qwen3:0.6b` running on `http://localhost:11434`
2. **OpenRouter** (commented): Remote models via OpenRouter API with `qwen/qwen3-8b` model

The AI service integrates function calling through the `DateTimeTools` class, allowing AI models to retrieve current date/time information.

### Streaming Support

The application supports real-time streaming responses with thinking capabilities:
- **Streaming endpoint**: `/chat/stream` - Returns streaming responses using Server-Sent Events
- **Thinking tags**: Supports both `<think>` and `<thinking>` tags for displaying AI reasoning
- **UI separation**: Thinking content appears in a highlighted yellow panel, final responses in the main panel
- **Real-time updates**: Text streams character by character as the AI generates responses

### Key Configuration

- **Spring AI Version**: 1.0.0
- **Java Version**: 17
- **Ollama Model**: `qwen3:0.6b` (configured in `application.properties`)
- **OpenRouter**: Commented configuration available for `qwen/qwen3-8b` model

### Project Structure

```
src/main/java/info/hemendra/demoaiopenrouter/
├── DemoAiOpenrouterApplication.java
├── config/
│   └── AiConfig.java
├── controller/
│   ├── ChatController.java
│   └── OpenAIController.java
├── service/
│   └── OpenAiService.java
└── tools/
    └── DateTimeTools.java
```

### Dependencies

- Spring Boot Starter Web
- Spring Boot Starter WebFlux (for reactive streaming)
- Spring Boot Starter Thymeleaf
- Spring Boot Starter Actuator
- Spring AI Starter Model Ollama
- Spring AI Starter Model OpenAI (commented)
- Spring AI PGVector Vector Store (for RAG functionality)
- Spring AI Chat Memory Repository JDBC

## Working with AI Models

To switch between Ollama and OpenRouter:
1. Update `application.properties` to comment/uncomment the appropriate configuration
2. Ensure the `AiConfig.java` ChatClient bean is properly configured if needed
3. For OpenRouter, ensure valid API key is provided

The `DateTimeTools` class demonstrates how to create Spring AI tools that can be called by AI models during conversations.

## RAG Vector Store Setup

The application includes a comprehensive RAG (Retrieval-Augmented Generation) setup using PGVector for similarity search:

### Vector Store Configuration
- **Database**: PostgreSQL with pgvector extension
- **Table**: `crm_formula_agent` (auto-created)
- **Embedding Model**: `all-minilm:l6-v2` (Ollama)
- **Connection**: `jdbc:postgresql://localhost:5432/my-crm-formula-agent`

### Formula Functions Knowledge Base
The application automatically processes `src/main/resources/formula/formula-functions-metadata.json` and creates optimized documents for similarity search:

#### Document Types Created:
1. **Function Reference**: Main function documentation with parameters, return types, and related functions
2. **Usage Examples**: Specific examples and implementation patterns
3. **Use Cases**: Problem-solving scenarios and applications
4. **Category Overview**: Functions grouped by category with common use cases
5. **Pattern References**: Common coding patterns and best practices

#### Services:
- **InitilizeRagDocumentStoreService**: Processes JSON metadata and populates vector store on startup
- **FormulaRecommendationService**: Provides similarity search functionality

### API Endpoints for Formula Search:
- `GET /api/formulas/search?query={query}&topK={count}` - Natural language search
- `GET /api/formulas/category/{category}?topK={count}` - Search by category
- `GET /api/formulas/{functionName}/examples` - Get function examples
- `GET /api/formulas/{functionName}/details` - Get function details
- `GET /api/formulas/usecase?useCase={useCase}&topK={count}` - Search by use case
- `GET /api/formulas/patterns?pattern={pattern}&topK={count}` - Search patterns

### Search Optimization:
- **Multi-document strategy**: Each function creates multiple specialized documents for better search coverage
- **Metadata filtering**: Documents can be filtered by type, category, and search intent
- **Semantic search**: Natural language queries are converted to vector embeddings for similarity matching