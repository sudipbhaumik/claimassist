package com.claimassist.generation;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;

/**
 * Pre-call advisor that injects pre-retrieved chunks into the system prompt as a grounded context
 * block.
 *
 * <p>The fallback gate in {@link GenerationService} guarantees that this advisor is only invoked
 * when {@code chunks} is non-empty and above the configured similarity threshold. The chunks are
 * stored in the request context under {@value #CHUNKS_KEY}; {@code ChatModelCallAdvisor} copies the
 * request context into the response context, making them accessible at
 * {@code chatClientResponse.context().get(CHUNKS_KEY)} for citation building — verified from
 * {@code ChatModelCallAdvisor} bytecode.
 *
 * <p>{@code after()} is a pass-through; the framework propagates the context automatically.
 */
public class ContextAugmentationAdvisor implements BaseAdvisor {

  static final String CHUNKS_KEY = "retrieved_chunks";

  private final List<Document> chunks;

  public ContextAugmentationAdvisor(List<Document> chunks) {
    this.chunks = chunks;
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
    return request
        .mutate()
        .prompt(request.prompt().augmentSystemMessage(buildContextBlock(chunks)))
        .context(CHUNKS_KEY, chunks)
        .build();
  }

  @Override
  public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
    return response;
  }

  private String buildContextBlock(List<Document> chunks) {
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
