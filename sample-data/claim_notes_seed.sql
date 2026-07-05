INSERT INTO claim_notes (claim_id, policy_id, author, note_text, note_date, ingested) VALUES
  ('CLM-1001','POL-5501','ADJ-014','FNOL received. Claim CLM-1001 assigned to ADJ-014. Loss reported per DOL 2026-02-10 involving rear-end collision.','2026-02-10', false),
  ('CLM-1001','POL-5501','ADJ-014','Spoke with the insured to obtain initial loss details. Photos requested and vehicle inspection discussed; insured advised repair work should not begin until inspection is completed.','2026-02-11', false),
  ('CLM-1001','POL-5501','ADJ-014','Initial claim review complete. No apparent coverage issues at this stage. Insp. scheduled for 2026-02-15.','2026-02-12', false),
  ('CLM-1001','POL-5501','ADJ-014','Vehicle insp. completed. Visible damage noted to rear bumper and trunk; no obvious structural damage observed. Additional concealed damage possible after teardown.','2026-02-15', false),
  ('CLM-1001','POL-5501','ADJ-014','Est. requested from approved repair facility based on inspection findings. Awaiting itemized repair estimate and parts pricing before completing evaluation.','2026-02-15', false),
  ('CLM-1001','POL-5501','ADJ-014','Follow-up pending receipt of final estimate. Reserve maintained pending review of repair costs and any supplemental damage identified during repairs.','2026-02-16', false);

INSERT INTO claim_notes (claim_id, policy_id, author, note_text, note_date, ingested) VALUES
  ('CLM-1002','POL-5501','ADJ-021','FNOL received. Claim CLM-1002 assigned to ADJ-021. Loss reported per DOL 2026-02-10 involving rear-end collision with rear bumper/trunk damage.', '2026-02-10', false),
  ('CLM-1002','POL-5501','ADJ-021','Spoke with insured to confirm loss details. Photos requested; advised no repairs until inspection completed.', '2026-02-11', false),
  ('CLM-1002','POL-5501','ADJ-021','Coverage review initiated. No coverage concerns identified at FNOL stage; prelim reserve set.', '2026-02-12', false),
  ('CLM-1002','POL-5501','ADJ-021','Vehicle insp. completed. Confirmed damage to rear bumper assembly and trunk lid; possible hidden damage pending teardown.', '2026-02-15', false),
  ('CLM-1002','POL-5501','ADJ-021','Estimate requested from repair facility based on inspection findings. Awaiting itemized repair breakdown and parts pricing.', '2026-02-16', false),
  ('CLM-1002','POL-5501','ADJ-021','Estimate reviewed and approved for processing. Proceeding with standard repair authorization workflow.', '2026-02-18', false);

INSERT INTO claim_notes (claim_id, policy_id, author, note_text, note_date, ingested) VALUES
  ('CLM-1003','POL-5503','ADJ-033','FNOL received. Claim CLM-1003 assigned to ADJ-033. Loss reported per DOL 2026-03-05 involving basement water intrusion from flooded drain backup.', '2026-03-05', false),
  ('CLM-1003','POL-5503','ADJ-033','Initial contact made with insured to confirm circumstances. Advised documentation and photos required; mitigation efforts already in progress per insured.', '2026-03-06', false),
  ('CLM-1003','POL-5503','ADJ-033','Insp. scheduled and completed. Observed significant water damage to basement finishes, flooring, and lower drywall sections.', '2026-03-08', false),
  ('CLM-1003','POL-5503','ADJ-033','Preliminary review completed. Cause of loss appears consistent with drain backup / external water intrusion; potential policy exclusion identified.', '2026-03-10', false),
  ('CLM-1003','POL-5503','ADJ-033','Coverage review ongoing. Policy POL-5503 water/flood exclusion under evaluation; no coverage confirmation pending final decision.', '2026-03-12', false),
  ('CLM-1003','POL-5503','ADJ-033','Final determination issued. Claim denied due to water damage exclusion for flood/backup through drains under policy terms.', '2026-03-18', false);

INSERT INTO claim_notes (claim_id, policy_id, author, note_text, note_date, ingested) VALUES
  ('CLM-1004','POL-5501','ADJ-014','FNOL received. Claim CLM-1004 assigned to ADJ-014. Loss reported per DOL 2026-04-22 involving windshield crack from road debris.','2026-04-22', false),
  ('CLM-1004','POL-5501','ADJ-014','Spoke with insured. Damage limited to windshield per initial report; no other vehicle damage indicated. Requested photos and confirmed repair facility preference.','2026-04-23', false),
  ('CLM-1004','POL-5501','ADJ-014','Insp. scheduled for 2026-04-25.','2026-04-24', false),
  ('CLM-1004','POL-5501','ADJ-014','Vehicle inspected. Crack extends across driver''s field of vision and is not repairable. No visible body or structural damage noted during visual inspection.','2026-04-25', false),
  ('CLM-1004','POL-5501','ADJ-014','Est. requested from approved glass vendor for windshield replacement and ADAS recalibration, if required.','2026-04-25', false),
  ('CLM-1004','POL-5501','ADJ-014','Estimate received and under review. Pending final coverage verification before repair authorization.','2026-04-26', false);