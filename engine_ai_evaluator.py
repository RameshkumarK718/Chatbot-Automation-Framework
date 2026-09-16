import os
import json
import difflib
import pandas as pd
from openai import OpenAI

class AIEvaluator:
    def __init__(self, api_key: str = None):
        # Automatically picks up OPENAI_API_KEY environment variable if not passed explicitly
        self.client = OpenAI(api_key=api_key or os.getenv("OPENAI_API_KEY"))

    def evaluate_advanced(self, question: str, expected: str, actual: str, context: str = "") -> dict:
        prompt = f"""
You are an expert QA Evaluator and AI Response Auditor for conversational AI.
Evaluate the chatbot's response against the user’s question, relevant conversation context, and expected answer.

Question: {question}
Context: {context if context else "None provided"}
Expected Answer: {expected}
Actual Answer: {actual}

Evaluation rules:
1. Relevance:
   - "Relevant" = the chatbot directly addresses the user's question.
   - "Irrelevant" = the chatbot does not address the question or failed.

2. Hallucination:
   - true = the chatbot introduces false facts, incorrect external information, or completely contradicts/goes beyond the scope of the expected answer in a misleading way.
   - false = the chatbot sticks strictly to truthful facts relevant to the domain/expected answer without fabricating info.

3. Status & Score:
   - score: float (0.0 to 1.0, where 1.0 is an exact semantic match)
   - pass: boolean (true if score >= 0.7, relevant, and non-hallucinated; otherwise false)
   - reason: string explanation covering correctness, relevance, and hallucination check.

Return a JSON object strictly with these keys:
{{
  "relevance": "Relevant",
  "hallucination": false,
  "score": 0.95,
  "pass": true,
  "reason": "Brief explanation"
}}
"""
        try:
            response = self.client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": "You output JSON only."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.0
            )
            content = response.choices[0].message.content
            return json.loads(content)
        except Exception as e:
            return {
                "relevance": "Irrelevant",
                "hallucination": True,
                "score": 0.0,
                "pass": False,
                "reason": f"AI Evaluation Error: {str(e)}"
            }

def process_qa_framework_excel(input_file: str = "Frameworks.xlsx", output_file: str = "Frameworks-Result.xlsx"):
    # Validate input file without failing build
    if not os.path.exists(input_file):
        print(f"Warning: {input_file} not found. Skipping AI evaluation.")
        pd.DataFrame({"Status": ["Skipped - Missing Input File"]}).to_excel(output_file, index=False)
        return

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Warning: OPENAI_API_KEY is not set. Skipping AI evaluation.")
        pd.DataFrame({"Status": ["Skipped - Missing API Key"]}).to_excel(output_file, index=False)
        return

    evaluator = AIEvaluator(api_key=api_key)
    print("Starting AI Evaluation, Match Percentage Calculation, and Report Generation...")

    xls = pd.ExcelFile(input_file)
    sheet_names = xls.sheet_names
    all_results = []

    def get_text(value):
        if pd.isna(value):
            return ""
        return str(value).strip()

    for sheet_name in sheet_names:
        df = pd.read_excel(input_file, sheet_name=sheet_name)
        print(f"Evaluating sheet: {sheet_name} ({len(df)} rows)")

        # Ensure required evaluation columns exist and are cast as string type
        for col in ["Match Percentage", "Relevance", "Hallucination", "Status", "Pass and Failure Reason"]:
            if col not in df.columns:
                df[col] = ""
            df[col] = df[col].fillna("").astype(str)

        for idx, row in df.iterrows():
            question = get_text(row.get("User Question") or row.get("Question"))
            expected = get_text(row.get("Expected Answer"))
            chatbot_ans = get_text(row.get("Chatbot Answer") or row.get("Response") or row.get("Chatbot Response"))
            
            if not question:
                continue
                
            # Calculate text match percentage using SequenceMatcher
            if not expected and not chatbot_ans:
                match_pct = 100.0
            elif not expected or not chatbot_ans:
                match_pct = 0.0
            else:
                match_pct = round(difflib.SequenceMatcher(None, expected, chatbot_ans).ratio() * 100, 2)

            match_pct_str = f"{match_pct}%"

            if not chatbot_ans or "error" in chatbot_ans.lower():
                df.at[idx, "Match Percentage"] = match_pct_str
                df.at[idx, "Relevance"] = "Irrelevant"
                df.at[idx, "Hallucination"] = "True"
                df.at[idx, "Status"] = "FAIL"
                df.at[idx, "Pass and Failure Reason"] = "Chatbot response was empty or contained an error."
                continue

            print(f"Evaluating [{row.get('Test Case ID', f'Row {idx}')}] (Match: {match_pct_str}): {question[:40]}...")
            result = evaluator.evaluate_advanced(question, expected, chatbot_ans)
            
            relevance = result.get("relevance", "Relevant" if match_pct >= 70.0 else "Irrelevant")
            is_hallucinated = result.get("hallucination", False)
            hallucination_str = "Yes" if is_hallucinated else "No"
            
            # Strict Threshold Rule: Below 70% is FAIL, 70% and above is PASS
            if match_pct >= 70.0 and not is_hallucinated:
                status = "PASS"
                reason = f"Match percentage is {match_pct_str} (>= 70%), meeting the threshold successfully."
            else:
                status = "FAIL"
                if match_pct < 70.0:
                    reason = f"Match percentage is {match_pct_str} (< 70% threshold required for passing)."
                else:
                    reason = result.get("reason", "Failed due to hallucination or mismatch.")

            df.at[idx, "Match Percentage"] = match_pct_str
            df.at[idx, "Relevance"] = relevance
            df.at[idx, "Hallucination"] = hallucination_str
            df.at[idx, "Status"] = status
            df.at[idx, "Pass and Failure Reason"] = reason
            
        all_results.append((sheet_name, df))

    with pd.ExcelWriter(output_file, engine="openpyxl") as writer:
        for sheet_name, df in all_results:
            df.to_excel(writer, sheet_name=sheet_name, index=False)

    print(f"\nAI Audit Evaluation complete! Detailed metrics saved back to {output_file}")

if __name__ == "__main__":
    process_qa_framework_excel()
