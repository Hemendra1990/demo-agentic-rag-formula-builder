package info.hemendra.demoaiopenrouter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class ResearchService {
    private static final Logger log = LoggerFactory.getLogger(ResearchService.class);
    private final ChatClient openAiChatClient;
    private static final int MAX_TOKENS = 8000; // Adjust based on model limits
    private static final int CHUNK_SIZE = 4000; // Safe token count per chunk
    private record ResearchResult(String directive, String content) {}

    String generateResearchQuestionStr = """ 
                    Generate 5 critical research questions about '%s' that would help create a comprehensive report.
                    "Focus on: historical context, current state, key players, challenges, and future prospects.
                    "Return only a numbered list of questions.
            """;

    public ResearchService(ChatClient openAiChatClient) {
        this.openAiChatClient = openAiChatClient;
    }


    public String generateResearchReport(String topic) {

        // Step 1: Generate research questions
        List<String> questions = generateResearchQuestions(topic);
        log.info("Generated {} research questions.", questions.size());

        // Step 2: Gather information for each question
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("# Research Report: ").append(topic).append("\n\n");

        // Replace the existing futures processing code with this improved version:

        List<CompletableFuture<ResearchResult>> futures = questions.stream()
                .map(question ->
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                String answer = researchQuestion(question);
                                return new ResearchResult(question, answer);
                            } catch (Exception e) {
                                log.error("Failed to research question: {}", question, e);
                                // Return a result with error information instead of failing completely
                                return new ResearchResult(question, "Error occurred while researching this question: " + e.getMessage());
                            }
                        })
                ).toList();

        // Wait for all futures to complete (both successful and failed ones)
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        List<ResearchResult> results = new ArrayList<>();
        try {
            // Wait for all to complete
            allFutures.join();
            
            // Collect results from all futures
            for (CompletableFuture<ResearchResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    log.error("Future execution failed", e);
                    // Create a fallback result for failed futures
                    results.add(new ResearchResult("Failed Question", "Research failed due to: " + e.getCause().getMessage()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Research interrupted", e);
                }
            }
        } catch (Exception e) {
            log.error("Error waiting for research completion", e);
            throw new RuntimeException("Failed to complete research", e);
        }

        // Sort results to maintain original order (if needed)
        results.sort(Comparator.comparingInt(result -> {
            int index = questions.indexOf(result.directive());
            return index == -1 ? Integer.MAX_VALUE : index; // Handle failed questions that might not match
        }));

        // Build the report from results
        for (ResearchResult result : results) {
            reportBuilder.append("## ").append(result.directive()).append("\n\n");
            reportBuilder.append(result.content()).append("\n\n");
        }




        /*for (String question : questions) {
            String answer = researchQuestion(question);
            reportBuilder.append("## ").append(question).append("\n\n");
            reportBuilder.append(answer).append("\n\n");

            log.info("Answer for question {} is:\n{}", question, answer);
        }*/

        // Step 3: Synthesize findings
        String synthesis = synthesizeFindings(topic, reportBuilder.toString());
        reportBuilder.append("# Synthesis and Conclusions\n\n").append(synthesis);


        return reportBuilder.toString();
    }

    /*private String synthesizeFindings(String topic, String research) {
        String prompt = String.format(
                "Based on this research about '%s':\n%s\n\n" +
                "Create a synthesis section that: " +
                "1. Identifies key themes and patterns " +
                "2. Highlights most significant findings " +
                "3. Addresses contradictions in the research " +
                "4. Provides evidence-based conclusions " +
                "5. Suggests areas for further research",
                topic, research
        );

        return openAiChatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }*/

    private String researchQuestion(String question) {
        String prompt = String.format(
                "Research and provide a detailed answer to this question: '%s'. " +
                "Include: " +
                "1. Key facts and statistics " +
                "2. Expert opinions " +
                "3. Recent developments " +
                "4. Credible sources " +
                "5. Controversies or debates " +
                "Be comprehensive and cite sources where possible.",
                question
        );

        return openAiChatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    @Retryable(value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    private List<String> generateResearchQuestions(String topic) {
        String content = openAiChatClient.prompt(String.format(generateResearchQuestionStr, topic))
                .call().content();
        return List.of(content.split("\n"));
    }


    /*Research client*/

    private String synthesizeFindingsWithChunking(String topic, String research) {
        // If research is small enough, process directly
        if (estimateTokenCount(research) < CHUNK_SIZE) {
            return synthesizeFindings(topic, research);
        }

        // Split research into chunks
        List<String> chunks = splitIntoChunks(research, CHUNK_SIZE);
        List<String> partialSyntheses = new ArrayList<>();

        // Process each chunk in parallel
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String chunk : chunks) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    synthesizeChunk(topic, chunk, chunks.size())
            );
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results
        for (CompletableFuture<String> future : futures) {
            try {
                partialSyntheses.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error processing research chunk", e);
            }
        }

        // Combine partial syntheses and create final synthesis
        String combinedSyntheses = String.join("\n\n", partialSyntheses);
        return synthesizeFindings(topic, combinedSyntheses);
    }

    private String synthesizeChunk(String topic, String chunk, int totalChunks) {
        String prompt = String.format(
                "This is part %d of a research document about '%s'. " +
                "Analyze this section and provide key findings, patterns, and insights. " +
                "Focus on extracting the most important information that would contribute to a final synthesis.\n\n%s",
                totalChunks, topic, chunk
        );

        return callChatAPI(prompt);
    }

    @Retryable(value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    private String synthesizeFindings(String topic, String research) {
        String prompt = String.format(
                "Based on this research about '%s':\n%s\n\n" +
                "Create a synthesis section that: " +
                "1. Identifies key themes and patterns " +
                "2. Highlights most significant findings " +
                "3. Addresses contradictions in the research " +
                "4. Provides evidence-based conclusions " +
                "5. Suggests areas for further research " +
                "Keep the synthesis concise (under 1000 words) and well-structured.",
                topic, research
        );

        return callChatAPI(prompt);
    }

    private String callChatAPI(String prompt) {
        try {
            return openAiChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            // Check for token limit error
            if (e.getMessage() != null && e.getMessage().contains("token limit")) {
                throw new RuntimeException("Token limit exceeded", e);
            }
            throw e;
        }
    }



    private List<String> splitIntoChunks(String text, int maxTokens) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (estimateTokenCount(currentChunk.toString()) + estimateTokenCount(paragraph) > maxTokens) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(paragraph).append("\n\n");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    private int estimateTokenCount(String text) {
        // Simple estimation: 1 token ≈ 4 characters
        return text.length() / 4;
    }


}