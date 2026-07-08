package com.claimassist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.config.ClaimAssistProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class DenseDocumentRetrieverTest {

  private static final int DEFAULT_TOP_K = 5;
  private static final double DEFAULT_THRESHOLD = 0.0;

  private VectorStore vectorStore;
  private DenseDocumentRetriever retriever;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    vectorStore = mock(VectorStore.class);
    meterRegistry = new SimpleMeterRegistry();
    retriever =
        new DenseDocumentRetriever(
            vectorStore, makeProps(DEFAULT_TOP_K, DEFAULT_THRESHOLD), meterRegistry);
  }

  @Test
  void retrieve_buildsCorrectSearchRequest() {
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    when(vectorStore.similaritySearch(captor.capture())).thenReturn(List.of());

    retriever.retrieve("What is covered?", null);

    SearchRequest captured = captor.getValue();
    assertThat(captured.getQuery()).isEqualTo("What is covered?");
    assertThat(captured.getTopK()).isEqualTo(DEFAULT_TOP_K);
    assertThat(captured.getSimilarityThreshold()).isEqualTo(DEFAULT_THRESHOLD);
  }

  @Test
  void retrieve_topKOverride_usedInRequest() {
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    when(vectorStore.similaritySearch(captor.capture())).thenReturn(List.of());

    retriever.retrieve("question", 3);

    assertThat(captor.getValue().getTopK()).isEqualTo(3);
  }

  @Test
  void retrieve_nullTopK_fallsBackToConfigDefault() {
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    when(vectorStore.similaritySearch(captor.capture())).thenReturn(List.of());

    retriever.retrieve("question", null);

    assertThat(captor.getValue().getTopK()).isEqualTo(DEFAULT_TOP_K);
  }

  @Test
  void retrieve_emptyResult_isValidNotAnError() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    List<Document> results = retriever.retrieve("unrelated question", null);

    assertThat(results).isEmpty();
  }

  @Test
  void retrieve_returnsDocumentsFromStore() {
    Document doc = new Document("Coverage includes liability.", Map.of("doc_type", "policy"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

    List<Document> results = retriever.retrieve("What is covered?", null);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getText()).isEqualTo("Coverage includes liability.");
  }

  @Test
  void retrieve_vectorStoreThrows_wrappedAsClaimAssistException() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new RuntimeException("DB connection lost"));

    assertThatThrownBy(() -> retriever.retrieve("question", null))
        .isInstanceOf(ClaimAssistException.class)
        .hasMessageContaining("Dense retrieval failed");
  }

  @Test
  void similaritySearch_delegatesToVectorStore() {
    SearchRequest req =
        SearchRequest.builder().query("test").topK(3).similarityThreshold(0.5).build();
    when(vectorStore.similaritySearch(req)).thenReturn(List.of());

    retriever.similaritySearch(req);

    verify(vectorStore).similaritySearch(req);
  }

  @Test
  void retrieve_recordsLatencyMetric() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    retriever.retrieve("question", null);

    assertThat(meterRegistry.find("claimassist.ask.retrieval.latency").timer()).isNotNull();
  }

  @Test
  void retrieve_recordsMatchCountMetric() {
    Document d1 = new Document("chunk one");
    Document d2 = new Document("chunk two");
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(d1, d2));

    retriever.retrieve("question", null);

    assertThat(meterRegistry.find("claimassist.ask.matches.count").summary().count()).isEqualTo(1);
    assertThat(meterRegistry.find("claimassist.ask.matches.count").summary().totalAmount())
        .isEqualTo(2.0);
  }

  // -------------------------------------------------------------------------

  private static ClaimAssistProperties makeProps(int topK, double threshold) {
    ClaimAssistProperties props = new ClaimAssistProperties();
    ClaimAssistProperties.Retrieval retrieval = new ClaimAssistProperties.Retrieval();
    retrieval.setTopK(topK);
    retrieval.setThreshold(threshold);
    props.setRetrieval(retrieval);
    return props;
  }
}
