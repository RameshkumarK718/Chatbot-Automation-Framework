import os
import json
import difflib
import pandas as pd
from openai import OpenAI

class AIEvaluator:
    def __init__(self, api_key=None):
        self.api_key = api_key or os.getenv("OPENAI_API_KEY")
        if not self.api_key:
            raise ValueError("OPENAI_API_KEY is not set.")
        self.client = OpenAI(api_key=self.api_key)

    def evaluate_advanced(self, question, expected, actual, context=""):
        prompt = f"""
You are an expert QA engineer and AI response auditor.
Evaluate the chatbot response against the user question and expected answer.

User Question:
{question}

Expected Answer:
{expected}

Chatbot Answer:
{actual}

Context:
{context}

Evaluation rules:
1. Relevance:
- "Relevant" if the chatbot directly addresses the user's question.
- "Irrelevant" if it does not answer the question, is unrelated, or fails to address the requested information.

2. Hallucination:
- true if the chatbot contains factually incorrect, fabricated, or misleading information.
- false if the response is factually correct.
- Additional valid information should NOT be considered hallucination simply because it is not present in the expected answer.

3. Semantic Score:
- Give a score between 0.0 and 1.0.
- 1.0 means the response fully satisfies the question and expected answer.
- 0.0 means it does not satisfy the question.

4. AI Pass:
- Pass only when:
  - semantic score >= 0.70
  - relevance is Relevant
  - hallucination is false

Return ONLY valid JSON in this format:
{{
    "relevance": "Relevant",
    "hallucination": false,
    "score": 0.85,
    "pass": true,
    "reason": "The response directly answers the question and contains no hallucinated information."
}}
"""
        try:
            response = self.client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": "You are a QA evaluator. Return valid JSON only."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.0
            )
            return json.loads(response.choices[0].message.content)
        except Exception as e:
            return {
                "relevance": "Irrelevant",
                "hallucination": True,
                "score": 0.0,
                "pass": False,
                "reason": f"AI evaluation failed: {str(e)}"
            }

def process_qa_framework_excel(input_file="Frameworks.xlsx", output_file="Frameworks-Result.xlsx"):
    if not os.path.exists(input_file):
        print(f"Input file not found: {input_file}")
        pd.DataFrame({"Status": ["Skipped - Missing Input File"]}).to_excel(output_file, index=False)
        return

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        print("OPENAI_API_KEY is not set.")
        pd.DataFrame({"Status": ["Skipped - Missing API Key"]}).to_excel(output_file, index=False)
        return

    evaluator = AIEvaluator(api_key)
    excel_file = pd.ExcelFile(input_file)
    results = {}

    for sheet_name in excel_file.sheet_names:
        print(f"Processing sheet: {sheet_name}")
        df = pd.read_excel(input_file, sheet_name=sheet_name, dtype=str)
        df = df.loc[:, ~df.columns.str.contains('^Unnamed')]

        def get_first_text(row, columns):
            for column in columns:
                if column in row.index:
                    value = row[column]
                    if pd.notna(value) and str(value).strip().lower() != "nan":
                        value = str(value).strip()
                        if value:
                            return value
            return ""

        updated_rows = []

        for index, row in df.iterrows():
            row_dict = row.to_dict()

            question = get_first_text(row, ["User Question", "Question"])
            expected = get_first_text(row, ["Expected Answer"])
            chatbot_answer = get_first_text(row, ["Chatbot Answer", "Response", "Chatbot Response"])
            context = get_first_text(row, ["Context", "Conversation Context"])

            # Calculate string similarity ratio just as a reference metric
            if not expected and not chatbot_answer:
                match_pct = 100.0
            elif not expected or not chatbot_answer:
                match_pct = 0.0
            else:
                similarity = difflib.SequenceMatcher(None, expected, chatbot_answer).ratio()
                match_pct = round(similarity * 100, 2)

            match_pct_str = f"{match_pct:.2f}%"

            # Handle empty or error responses immediately
            if not chatbot_answer or chatbot_answer.lower() == "nan" or chatbot_answer.lower().startswith("error"):
                row_dict["Match Percentage"] = match_pct_str
                row_dict["Semantic Score"] = "0.00"
                row_dict["Relevance"] = "Irrelevant"
                row_dict["Hallucination"] = "Yes"
                row_dict["Status"] = "FAIL"
                row_dict["Pass and Failure Reason"] = "Chatbot response was empty or contained an error."
                updated_rows.append(row_dict)
                continue

            # Call OpenAI Advanced Evaluator
            result = evaluator.evaluate_advanced(
                question=question,
                expected=expected,
                actual=chatbot_answer,
                context=context
            )

            relevance = str(result.get("relevance", "Irrelevant")).strip()
            hallucination = result.get("hallucination", False)
            ai_score = float(result.get("score", 0.0))

            if isinstance(hallucination, str):
                is_hallucinated = hallucination.lower() in ["true", "yes", "1"]
            else:
                is_hallucinated = bool(hallucination)

            # Updated Pass/Fail condition using Semantic Score instead of rigid text matching
            if ai_score >= 0.70 and relevance.lower() == "relevant" and not is_hallucinated:
                status = "PASS"
                reason = (
                    f"Semantic score is {ai_score:.2f} (>= 0.70), "
                    "the response is relevant, and no hallucination was detected."
                )
            else:
                status = "FAIL"
                if ai_score < 0.70:
                    reason = f"Semantic score is {ai_score:.2f} (< 0.70 threshold required for passing)."
                elif relevance.lower() != "relevant":
                    reason = "Chatbot response was classified as irrelevant."
                elif is_hallucinated:
                    reason = result.get(
                        "reason",
                        "Chatbot response contains hallucinated or misleading information."
                    )
                else:
                    reason = result.get(
                        "reason",
                        "Response did not satisfy the evaluation criteria."
                    )

            row_dict["Match Percentage"] = match_pct_str
            row_dict["Semantic Score"] = f"{ai_score:.2f}"
            row_dict["Relevance"] = relevance
            row_dict["Hallucination"] = "Yes" if is_hallucinated else "No"
            row_dict["Status"] = status
            row_dict["Pass and Failure Reason"] = reason
            
            updated_rows.append(row_dict)

            print(
                f"Row {index + 1}: {status} | "
                f"Semantic Score: {ai_score:.2f} | "
                f"Match: {match_pct_str} | "
                f"Relevance: {relevance}"
            )

        target_columns = [
            "Test Case ID",
            "Category",
            "Subcategory",
            "User Question",
            "Expected Answer",
            "Chatbot Answer",
            "Match Percentage",
            "Semantic Score",
            "Relevance",
            "Status",
            "Pass and Failure Reason"
        ]
        
        result_df = pd.DataFrame(updated_rows)
        existing_columns = [col for col in target_columns if col in result_df.columns]
        leftover_columns = [col for col in result_df.columns if col not in target_columns]
        
        results[sheet_name] = result_df[existing_columns + leftover_columns]

    with pd.ExcelWriter(output_file, engine="openpyxl") as writer:
        for sheet_name, result_df in results.items():
            result_df.to_excel(writer, sheet_name=sheet_name, index=False)

    print("Evaluation completed successfully.")
    print(f"Output file: {output_file}")

if __name__ == "__main__":
    process_qa_framework_excel()
