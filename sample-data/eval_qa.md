# Evaluation Q&A — ground-truth test set (v1)
### Q1 — Coverage (CLM-1001)
- **Question:** Is rear-end collision damage covered under the policy for claim CLM-1001?
- **Expected answer:** Yes. Policy POL-5501 (auto) includes collision coverage, so rear-end
  collision damage to the bumper and trunk is covered, subject to the applicable deductible.
- **Source:** documents/CLM-1001/policy.txt (COVERAGE), CLM-1001/estimate.txt

### Q2 — Damage amount (CLM-1001)
- **Question:** What is the total estimated repair cost for claim CLM-1001?
- **Expected answer:** $3,435.00
- **Source:** documents/CLM-1001/estimate.txt (TOTAL row)

### Q3 — Deductible (CLM-1001)
- **Question:** What deductible applies to the CLM-1001 auto claim?
- **Expected answer:** $500 collision deductible.
- **Source:** documents/CLM-1001/policy.txt (DEDUCTIBLES)

### Q4 — Temporal / coverage-in-effect (CLM-1002)  ← edge case
- **Question:** Was policy POL-5502 in effect on the date of loss for claim CLM-1002?
- **Expected answer:** Yes. The date of loss (2026-02-12) falls within the policy period
  (2025-05-15 to 2026-02-14), so coverage was in effect (2 days before expiry).
- **Source:** documents/CLM-1002/policy.pdf (effective/expiry dates), loss_date metadata

### Q5 — Damage type (CLM-1002)
- **Question:** What kind of damage is claimed in CLM-1002?
- **Expected answer:** Hail damage to the hood, roof, and windshield.
- **Source:** documents/CLM-1002/estimate.html

### Q6 — Denial decision (CLM-1003)  ← denial case
- **Question:** Was claim CLM-1003 approved or denied, and why?
- **Expected answer:** Denied. The damage resulted from water intrusion caused by a flooded
  drain, which is excluded under policy POL-5503's water damage/flood and drain backup exclusion.
- **Source:** documents/CLM-1003/correspondence_final.txt, CLM-1003/policy.txt (EXCLUSIONS)

### Q7 — Exclusion detail (CLM-1003)
- **Question:** Does the CLM-1003 policy cover water damage from a flooded drain?
- **Expected answer:** No. Policy POL-5503 excludes water damage caused by flood, surface water,
  or water backing up through drains or sewers.
- **Source:** documents/CLM-1003/policy.txt (EXCLUSIONS)

### Q8 — Loss description (CLM-1003)
- **Question:** What was the cause of loss for CLM-1003?
- **Expected answer:** Basement water damage caused by a flooded drain backup on 2026-03-05.
- **Source:** documents/CLM-1003/estimate.txt, claim notes

### Q9 — Adjuster (CLM-1003)
- **Question:** Who is the adjuster handling claim CLM-1003?
- **Expected answer:** Alicia Reyes (ADJ-033).
- **Source:** documents/CLM-1003/correspondence_final.txt, claim notes

### Q10 — Policy period (CLM-1001)
- **Question:** What is the policy period for POL-5501?
- **Expected answer:** 2025-06-01 through 2026-06-01.
- **Source:** documents/CLM-1001/policy.txt (DECLARATIONS)

---

## CLM-1004 checks (VERIFIED against generated OCR estimate)

### Q11 — Damage type (CLM-1004)
- **Question:** What is the damage in claim CLM-1004?
- **Expected answer:** A cracked windshield caused by road debris, the crack extending across
  the driver's field of vision; replacement (not repair) is recommended. DOL 2026-04-22.
- **Source:** documents/CLM-1004/estimate_ocr.txt

### Q12 — Damage amount (CLM-1004)
- **Question:** What is the total estimated repair cost for claim CLM-1004?
- **Expected answer:** $1,080.50
- **Source:** documents/CLM-1004/estimate_ocr.txt (TOTAL row)

### Q13 — Scoping isolation (CLM-1004 vs CLM-1001)  ← critical Stage 2 test
- **Question:** For claim CLM-1004, what is the cause of loss?
- **Expected answer:** A windshield crack from road debris (DOL 2026-04-22). Must NOT return
  CLM-1001's rear-end collision, despite both sharing policy POL-5501, policyholder PH-9001,
  and adjuster ADJ-014.
- **Source:** documents/CLM-1004/estimate_ocr.txt (scoped to claim_id = CLM-1004)

### Q14 — OCR robustness (CLM-1004)  ← optional, tests noise recovery
- **Question:** What is the cost of the ADAS camera recalibration for claim CLM-1004?
- **Expected answer:** $210.00
- **Note:** In the OCR-noised document this appears as "$2 10.00" (split by noise). This tests
  whether normalization/retrieval recovers the figure. A correct answer must return $210.00.
- **Source:** documents/CLM-1004/estimate_ocr.txt (ADAS recalibration line)
