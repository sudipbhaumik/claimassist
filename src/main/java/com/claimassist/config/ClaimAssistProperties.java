package com.claimassist.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed configuration for ClaimAssist. All tunables live here — no magic numbers in code. */
@ConfigurationProperties(prefix = "claimassist")
@Validated
public class ClaimAssistProperties {

  @Valid private Model model = new Model();
  @Valid private Chunk chunk = new Chunk();
  @Valid private Retrieval retrieval = new Retrieval();
  @Valid private Guardrail guardrail = new Guardrail();

  public Model getModel() {
    return model;
  }

  public void setModel(Model model) {
    this.model = model;
  }

  public Chunk getChunk() {
    return chunk;
  }

  public void setChunk(Chunk chunk) {
    this.chunk = chunk;
  }

  public Retrieval getRetrieval() {
    return retrieval;
  }

  public void setRetrieval(Retrieval retrieval) {
    this.retrieval = retrieval;
  }

  public Guardrail getGuardrail() {
    return guardrail;
  }

  public void setGuardrail(Guardrail guardrail) {
    this.guardrail = guardrail;
  }

  public static class Model {
    @NotBlank private String chat = "llama3.1";
    @NotBlank private String embedding = "nomic-embed-text";

    public String getChat() {
      return chat;
    }

    public void setChat(String chat) {
      this.chat = chat;
    }

    public String getEmbedding() {
      return embedding;
    }

    public void setEmbedding(String embedding) {
      this.embedding = embedding;
    }
  }

  public static class Chunk {
    @Positive private int size = 512;
    @PositiveOrZero private int overlap = 50;

    public int getSize() {
      return size;
    }

    public void setSize(int size) {
      this.size = size;
    }

    public int getOverlap() {
      return overlap;
    }

    public void setOverlap(int overlap) {
      this.overlap = overlap;
    }
  }

  public static class Retrieval {
    @Positive private int topK = 5;

    @DecimalMin("0.0")
    private double threshold = 0.0;

    @Positive private int rrfK = 60;

    public int getTopK() {
      return topK;
    }

    public void setTopK(int topK) {
      this.topK = topK;
    }

    public double getThreshold() {
      return threshold;
    }

    public void setThreshold(double threshold) {
      this.threshold = threshold;
    }

    public int getRrfK() {
      return rrfK;
    }

    public void setRrfK(int rrfK) {
      this.rrfK = rrfK;
    }
  }

  public static class Guardrail {
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double groundingThreshold = 0.8;

    public double getGroundingThreshold() {
      return groundingThreshold;
    }

    public void setGroundingThreshold(double groundingThreshold) {
      this.groundingThreshold = groundingThreshold;
    }
  }
}
