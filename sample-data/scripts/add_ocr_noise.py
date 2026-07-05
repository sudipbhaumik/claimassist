#!/usr/bin/env python3
"""
add_ocr_noise.py — inject mild, realistic OCR-style noise into a text file.

Purpose: simulate the artifacts a scanned/OCR'd document produces, so the ingestion
pipeline (normalization + chunking) has a realistic messy input to handle. Used on the
CLM-1004 estimate in the ClaimAssist synthetic corpus.

Design goals:
- MILD noise: the text must stay human-readable. We want a challenge, not corruption.
- DETERMINISTIC by default (fixed seed) so the committed output is reproducible.
- Idempotent-ish: re-running regenerates from the CLEAN source, not compounding noise.

Usage:
    python3 add_ocr_noise.py <input_clean.txt> <output_noised.txt> [--seed N] [--rate R]

Example:
    python3 add_ocr_noise.py estimate_clean.txt ../documents/CLM-1004/estimate_ocr.txt

Then commit the noised output (and optionally the clean source).
"""

import argparse
import random
import sys

# characters occasionally inserted as OCR speckle
STRAY_CHARS = ["|", "~", "^", "`", ".", ","]


def split_word(word: str) -> str:
    """Split a word with a space or hyphen, mimicking OCR breaking a word."""
    if len(word) < 4:
        return word
    pos = random.randint(1, len(word) - 1)
    sep = random.choice([" ", "-"])
    return word[:pos] + sep + word[pos:]


def add_stray_char(word: str) -> str:
    """Insert a stray speckle character somewhere in the word."""
    if not word:
        return word
    pos = random.randint(0, len(word))
    return word[:pos] + random.choice(STRAY_CHARS) + word[pos:]


def noise_line(line: str, rate: float) -> str:
    """Apply per-word noise at the given probability."""
    words = line.split(" ")
    out = []
    for w in words:
        r = random.random()
        if r < rate * 0.5:          # ~half the noise budget: split the word
            out.append(split_word(w))
        elif r < rate:              # ~other half: stray character
            out.append(add_stray_char(w))
        else:
            out.append(w)
    return " ".join(out)


def maybe_break_or_merge(lines, rate: float):
    """Occasionally break a line mid-sentence or merge two lines (OCR layout errors)."""
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        r = random.random()
        if r < rate * 0.3 and len(line.split()) > 6:
            # break the line at a random word boundary
            words = line.split(" ")
            cut = random.randint(2, len(words) - 2)
            result.append(" ".join(words[:cut]))
            result.append(" ".join(words[cut:]))
            i += 1
        elif r < rate * 0.5 and i + 1 < len(lines) and lines[i] and lines[i + 1]:
            # merge this line with the next (lost line break)
            result.append(line + " " + lines[i + 1])
            i += 2
        else:
            result.append(line)
            i += 1
    return result


def main():
    ap = argparse.ArgumentParser(description="Add mild OCR-style noise to a text file.")
    ap.add_argument("input", help="clean input text file")
    ap.add_argument("output", help="noised output file to write")
    ap.add_argument("--seed", type=int, default=42, help="random seed (default 42, reproducible)")
    ap.add_argument("--rate", type=float, default=0.06,
                    help="per-word noise probability (default 0.06 = ~6%%). Keep it mild.")
    args = ap.parse_args()

    if not (0 < args.rate < 0.25):
        print("WARNING: rate should be small (0<rate<0.25) to keep text readable.", file=sys.stderr)

    random.seed(args.seed)

    with open(args.input, "r", encoding="utf-8") as f:
        lines = f.read().split("\n")

    # 1. per-word noise (splits + stray chars)
    lines = [noise_line(ln, args.rate) for ln in lines]
    # 2. occasional line break/merge (layout errors)
    lines = maybe_break_or_merge(lines, args.rate)

    with open(args.output, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"Wrote noised file: {args.output} (seed={args.seed}, rate={args.rate})")


if __name__ == "__main__":
    main()
