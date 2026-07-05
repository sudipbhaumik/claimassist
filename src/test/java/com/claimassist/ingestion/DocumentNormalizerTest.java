package com.claimassist.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimassist.ingestion.normalize.DocumentNormalizer;
import org.junit.jupiter.api.Test;

class DocumentNormalizerTest {

  private final DocumentNormalizer normalizer = new DocumentNormalizer();

  @Test
  void nullInput_returnsEmpty() {
    assertThat(normalizer.normalize(null)).isEmpty();
  }

  @Test
  void blankInput_returnsEmpty() {
    assertThat(normalizer.normalize("   \n\n\t  ")).isEmpty();
  }

  @Test
  void normalizesLineEndings() {
    assertThat(normalizer.normalize("a\r\nb\rc")).isEqualTo("a\nb\nc");
  }

  @Test
  void collapsesHorizontalWhitespace() {
    assertThat(normalizer.normalize("word1   word2\t\tword3")).isEqualTo("word1 word2 word3");
  }

  @Test
  void removesBlankLines() {
    assertThat(normalizer.normalize("first\n\n\nsecond\n  \nthird"))
        .isEqualTo("first\nsecond\nthird");
  }

  @Test
  void stripsLeadingAndTrailingWhitespacePerLine() {
    assertThat(normalizer.normalize("  leading\ntrailing  \n  both  "))
        .isEqualTo("leading\ntrailing\nboth");
  }

  @Test
  void stripsNonPrintableControlChars() {
    // Build input with NUL (0x00), BEL (0x07), DEL (0x7F) embedded between visible text.
    // They must be stripped; surrounding letters must survive.
    String nul = String.valueOf((char) 0x00);
    String bel = String.valueOf((char) 0x07);
    String del = String.valueOf((char) 0x7F);
    String input = "text" + nul + "NUL" + bel + "BEL" + del + "DEL";
    assertThat(normalizer.normalize(input)).isEqualTo("textNULBELDEL");
  }

  @Test
  void preservesTabAndNewline() {
    // TAB (0x09) collapses to a single space; LF (0x0A) is preserved as line separator
    assertThat(normalizer.normalize("col1\tcol2\nrow2")).isEqualTo("col1 col2\nrow2");
  }

  @Test
  void deterministicOnSameInput() {
    String input = "  Some  claim  text.\r\n  More  text.  ";
    assertThat(normalizer.normalize(input)).isEqualTo(normalizer.normalize(input));
  }
}
