package com.claimassist.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Wires Spring AI model abstractions. Provider (Ollama) is a dependency + config choice only — no
 * provider SDK is referenced here. Swapping to Azure/OpenAI requires only a starter swap and
 * application.yml changes, not code changes.
 */
@Configuration
public class ModelConfig {

  /**
   * ChatClient backed by the auto-configured Ollama chat model, with the externalized system prompt
   * loaded from {@code classpath:prompts/system-prompt.st}. The system prompt instructs the model
   * to answer only from retrieved context — enforcement is via {@code
   * ContextAugmentationAdvisor.before()} which appends the [CONTEXT] block per call.
   */
  @Bean
  public ChatClient chatClient(ChatClient.Builder builder, ClaimAssistProperties props) {
    String location = props.getGeneration().getSystemPromptLocation();
    String classpathPath =
        location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
    return builder.defaultSystem(new ClassPathResource(classpathPath)).build();
  }
}
