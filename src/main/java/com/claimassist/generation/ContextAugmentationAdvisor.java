package com.claimassist.generation;

import com.claimassist.retrieval.DenseDocumentRetriever;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;

/**
 * Pre-call advisor that retrieves relevant chunks and injects them into the system prompt.
 *
 * <p>Created per-call by {@link GenerationService} with the question and topK baked in. The
 * retrieved chunk list is stored in the request context under {@value #CHUNKS_KEY} so it survives
 * the model call and is accessible from {@code chatClientResponse.context()} — this is the reliable
 * citation pattern verified from {@code ChatModelCallAdvisor} bytecode.
 *
 * <p>{@code after()} is a pass-through; the response context already contains the chunks copied
 * from the request context by the framework.
 */
public class ContextAugmentationAdvisor implements BaseAdvisor {

  static final String CHUNKS_KEY = "retrieved_chunks";

  private final DenseDocumentRetriever retriever;
  private final String question;
  private final Integer topKOverride;

  public ContextAugmentationAdvisor(
      DenseDocumentRetriever retriever, String question, Integer topKOverride) {
    this.retriever = retriever;
    this.question = question;
    this.topKOverride = topKOverride;
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
    List<Document> chunks = retriever.retrieve(question, topKOverride);
    String contextBlock = buildContextBlock(chunks);
    return request
        .mutate()
        .prompt(request.prompt().augmentSystemMessage(contextBlock))
        .context(CHUNKS_KEY, chunks)
        .build();
  }

  @Override
  public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
    return response;
  }

  private String buildContextBlock(List<Document> chunks) {
    if (chunks.isEmpty()) {
      return "\n\n[CONTEXT]\nNo relevant documents were found for this question.\n[END CONTEXT]\n";
    }
    StringBuilder sb =
        new StringBuilder("\n\n[CONTEXT - answer using ONLY the excerpts below]\n\n");
    for (int i = 0; i < chunks.size(); i++) {
      Document doc = chunks.get(i);
      Map<String, Object> meta = doc.getMetadata();
      sb.append("--- Excerpt ").append(i + 1).append(" ---\n");
      String source = (String) meta.getOrDefault("source", "unknown");
      String section = (String) meta.get("section");
      if (section != null && !section.isBlank()) {
        sb.append("Location: ").append(source).append(" / ").append(section).append("\n");
      } else {
        sb.append("Source: ").append(source).append("\n");
      }
      sb.append(doc.getText()).append("\n\n");
    }
    sb.append("[END CONTEXT]\n");
    return sb.toString();
  }
}
