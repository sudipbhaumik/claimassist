package com.claimassist.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claimassist.chunking.TextChunk;
import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.config.ClaimAssistProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

class EmbeddingServiceTest {

  private static final int DIMS = 768; // must match claimassist.embed.expected-dimensions default

  private EmbeddingModel embeddingModel;
  private EmbeddingService embeddingService;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    embeddingModel = mock(EmbeddingModel.class);
    meterRegistry = new SimpleMeterRegistry();
    ClaimAssistProperties props = propsWithBatchSize(3);
    embeddingService = new EmbeddingService(embeddingModel, props, meterRegistry);
  }

  @Test
  void embed_returnsOneVectorPerChunk() {
    List<TextChunk> chunks = chunks("a", "b", "c");
    when(embeddingModel.embedForResponse(anyList())).thenReturn(fakeResponse(3));

    List<float[]> result = embeddingService.embed(chunks);

    assertThat(result).hasSize(3);
    result.forEach(v -> assertThat(v).hasSize(DIMS));
  }

  @Test
  void embed_splitsBatchesCorrectly() {
    // batchSize=3, 7 chunks → 3 batches: [3, 3, 1]
    List<TextChunk> chunks = chunks("a", "b", "c", "d", "e", "f", "g");
    when(embeddingModel.embedForResponse(anyList()))
        .thenReturn(fakeResponse(3))
        .thenReturn(fakeResponse(3))
        .thenReturn(fakeResponse(1));

    List<float[]> result = embeddingService.embed(chunks);

    assertThat(result).hasSize(7);
    verify(embeddingModel, times(3)).embedForResponse(anyList());
  }

  @Test
  void embed_emptyList_returnsEmpty() {
    List<float[]> result = embeddingService.embed(List.of());
    assertThat(result).isEmpty();
    verify(embeddingModel, times(0)).embedForResponse(anyList());
  }

  @Test
  void embed_wrongDimensions_throwsLoudly() {
    List<TextChunk> chunks = chunks("x");
    // Return 512-dim vector — should fail validation
    when(embeddingModel.embedForResponse(anyList())).thenReturn(responseWithDim(512));

    assertThatThrownBy(() -> embeddingService.embed(chunks))
        .isInstanceOf(ClaimAssistException.class)
        .hasMessageContaining("768")
        .hasMessageContaining("512");
  }

  @Test
  void embed_allZeroVector_throwsLoudly() {
    List<TextChunk> chunks = chunks("x");
    when(embeddingModel.embedForResponse(anyList())).thenReturn(allZeroResponse());

    assertThatThrownBy(() -> embeddingService.embed(chunks))
        .isInstanceOf(ClaimAssistException.class)
        .hasMessageContaining("zero");
  }

  @Test
  void embed_nullVector_throwsLoudly() {
    List<TextChunk> chunks = chunks("x");
    when(embeddingModel.embedForResponse(anyList())).thenReturn(nullVectorResponse());

    assertThatThrownBy(() -> embeddingService.embed(chunks))
        .isInstanceOf(ClaimAssistException.class)
        .hasMessageContaining("Null");
  }

  @Test
  void embed_modelThrows_wrappedAsClaimAssistException() {
    List<TextChunk> chunks = chunks("x");
    when(embeddingModel.embedForResponse(anyList()))
        .thenThrow(new RuntimeException("Ollama timeout"));

    assertThatThrownBy(() -> embeddingService.embed(chunks))
        .isInstanceOf(ClaimAssistException.class)
        .hasMessageContaining("Embedding batch")
        .hasMessageContaining("Ollama timeout");
  }

  @Test
  void embed_recordsBatchSizeMetric() {
    List<TextChunk> chunks = chunks("a", "b");
    when(embeddingModel.embedForResponse(anyList())).thenReturn(fakeResponse(2));

    embeddingService.embed(chunks);

    assertThat(meterRegistry.summary("claimassist.embed.batch.size").count()).isEqualTo(1);
    assertThat(meterRegistry.summary("claimassist.embed.batch.size").totalAmount()).isEqualTo(2.0);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static ClaimAssistProperties propsWithBatchSize(int batchSize) {
    ClaimAssistProperties props = new ClaimAssistProperties();
    ClaimAssistProperties.Embed embed = new ClaimAssistProperties.Embed();
    embed.setBatchSize(batchSize);
    props.setEmbed(embed);
    return props;
  }

  private static List<TextChunk> chunks(String... texts) {
    List<TextChunk> list = new ArrayList<>();
    for (int i = 0; i < texts.length; i++) {
      list.add(new TextChunk(texts[i], "hash" + i, Map.of()));
    }
    return list;
  }

  private static EmbeddingResponse fakeResponse(int count) {
    List<Embedding> embeddings = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      float[] v = new float[DIMS];
      Arrays.fill(v, 0.1f + i * 0.01f);
      embeddings.add(new Embedding(v, i));
    }
    return new EmbeddingResponse(embeddings);
  }

  private static EmbeddingResponse responseWithDim(int dim) {
    float[] v = new float[dim];
    Arrays.fill(v, 0.1f);
    return new EmbeddingResponse(List.of(new Embedding(v, 0)));
  }

  private static EmbeddingResponse allZeroResponse() {
    float[] v = new float[DIMS]; // all zeros by default
    return new EmbeddingResponse(List.of(new Embedding(v, 0)));
  }

  private static EmbeddingResponse nullVectorResponse() {
    return new EmbeddingResponse(List.of(new Embedding(null, 0)));
  }
}
