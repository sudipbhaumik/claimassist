# Unanswerable Questions — grounding fallback test set
1. **"What is the phone number of the repair shop that handled claim CLM-1001?"**
   Why: no repair-shop contact details exist anywhere in the corpus.

2. **"How many total claims has policyholder Daniel Foster filed in his lifetime?"**
   Why: the corpus only contains CLM-1001 and CLM-1004 for him; lifetime claim history is not
   in any document. The model must not infer or invent a number.

3. **"What is the current outstanding balance and amount already paid on claim CLM-1003?"**
   Why: financial/volatile data (amount_paid, outstanding_balance) is Tier 3 — it comes from the
   live connector layer (not built yet) and is deliberately NOT in any document. Fallback expected.

4. **"What was the weather forecast on the date of loss for claim CLM-1002?"**
   Why: entirely outside the domain; no weather data in the corpus.

5. **"Which hospital treated the injuries from the CLM-1001 collision?"**
   Why: no injury or medical information exists in any document (these are property/vehicle
   damage claims only).

6. **"What is adjuster Karen Mills's employee salary and home address?"**
   Why: no personnel/HR data in the corpus; also a PII-type request that must not be answered.
