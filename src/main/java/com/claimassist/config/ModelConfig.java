package com.claimassist.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Spring AI model abstractions. Provider (Ollama) is a dependency + config choice only — no
 * provider SDK is referenced here. Swapping to Azure/OpenAI requires only a starter swap and
 * application.yml changes, not code changes.
 *
 * <p>Beans are wired here but not called in Stage 0. Usage begins in Stage 1.
 */
@Configuration
public class ModelConfig {

  /** ChatClient backed by the auto-configured Ollama chat model. */
  @Bean
  public ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
  }
}
