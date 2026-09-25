import os
import json
import difflib
import argparse
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
Evaluate the chatbot response against the user's question and expected answer.

User Question:
{question}

Expected Answer:
{expected}

Chatbot Answer:
{actual}

Context:
{context}

Evaluation Rules:
1. Relevance:
- "Relevant" if the chatbot directly addresses the user's question.
- "Irrelevant" if it does not answer the question, is unrelated, or fails to address the requested information.

2. Hallucination:
- true if the chatbot contains factually incorrect, fabricated, or misleading information.
- false if the response is factually correct.
- Additional valid information must NOT be considered hallucination simply because it is not present in the expected answer.

3. Semantic Score:
- Give a score between 0.0 and 1.0.
- 1.0 means the response fully satisfies the question and expected answer.
- 0.0 means it does not satisfy the question.

Return ONLY valid JSON:
{{
    "relevance": "Relevant",
    "hallucination": false,
    "score": 0.85,
    "reason": "The response directly answers the question and contains no hallucinated information."
}}
"""
        try:
            response = self.client.chat.completions.create(
                   model="gpt-4o-mini",
             #   Primary Engine (gpt-4o-mini): Delivers enterprise-grade reliability, strict JSON compliance, and stable CI/CD test reports.
             # model="openrouter/free",
            # Automatic Fallback (openrouter/free): Provides built-in fault tolerance so our test pipelines never fail during transient API outages.

            messages=[
                    {
                        "role": "system",
                        "content": "You are a QA evaluator. Return valid JSON only."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
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
                "reason": f"AI evaluation failed: {str(e)}"
            }

def get_first_text(row, columns):
    """Return the first non-empty value from the specified columns."""
    for column in columns:
        if column not in row.index:
            continue
        value = row[column]
        if pd.isna(value):
            continue
        value = str(value).strip()
        if value and value.lower() != "nan":
            return value
    return ""

def calculate_match_percentage(expected, actual):
    """Calculate text similarity percentage using SequenceMatcher."""
    if not expected and not actual:
        return 100.0
    if not expected or not actual:
        return 0.0
    similarity = difflib.SequenceMatcher(
        None,
        expected,
        actual
    ).ratio()
    return round(similarity * 100, 2)

def is_hallucinated(value):
    """Convert AI hallucination response to a boolean."""
    if isinstance(value, str):
        return value.strip().lower() in {
            "true",
            "yes",
            "1"
        }
    return bool(value)

def process_qa_framework_excel(
    input_file="Frameworks_Output.xlsx",
    output_file="Frameworks-Result.xlsx"
):
    """Process all sheets from Java output and generate the evaluated Excel report."""
    if not os.path.exists(input_file):
        print(f"Input file not found: {input_file}")
        pd.DataFrame(
            {
                "Status": [
                    "Skipped - Missing Input File"
                ]
            }
        ).to_excel(
            output_file,
            index=False
        )
        return

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        print("OPENAI_API_KEY is not set.")
        pd.DataFrame(
            {
                "Status": [
                    "Skipped - Missing API Key"
                ]
            }
        ).to_excel(
            output_file,
            index=False
        )
        return

    evaluator = AIEvaluator(api_key)
    excel_file = pd.ExcelFile(input_file)
    results = {}

    for sheet_name in excel_file.sheet_names:
        print(f"\nProcessing sheet: {sheet_name}")
        df = pd.read_excel(
            input_file,
            sheet_name=sheet_name,
            dtype=str
        )
        # Drop unnamed/blank pandas index columns if any exist
        df = df.loc[
            :,
            ~df.columns.str.contains(
                "^Unnamed",
                case=False,
                regex=True
            )
        ]
        updated_rows = []

        for index, row in df.iterrows():
            row_dict = row.to_dict()
            
            # Map precisely to columns populated by Java runner
            question = get_first_text(row, ["User Question", "Question", "Question / Input to enter"])
            expected = get_first_text(row, ["Expected Answer", "Expected Result", "What it tests / Expected Answer"])
            chatbot_answer = get_first_text(row, ["Chatbot Answer", "Actual Result"])
            context = get_first_text(row, ["Context", "Conversation Context"])

            match_pct = calculate_match_percentage(expected, chatbot_answer)
            match_pct_str = f"{match_pct:.2f}%"

            if (
                not chatbot_answer
                or chatbot_answer.lower() == "nan"
                or chatbot_answer.lower().startswith("error")
                or chatbot_answer.lower().startswith("exception")
            ):
                row_dict["Match Percentage"] = match_pct_str
                row_dict["Semantic Score"] = "0.00"
                row_dict["Relevance"] = "Irrelevant"
                row_dict["Hallucination"] = "Yes"
                row_dict["Status"] = "FAIL"
                row_dict["Pass and Failure Reason"] = "Chatbot response was empty, threw an exception, or returned an error."
                updated_rows.append(row_dict)
                print(f"Row {index + 1}: FAIL | Match: {match_pct_str} | Empty/Error response")
                continue

            # Run Advanced OpenAI evaluation
            result = evaluator.evaluate_advanced(
                question=question,
                expected=expected,
                actual=chatbot_answer,
                context=context
            )
            relevance = str(result.get("relevance", "Irrelevant")).strip()
            hallucinated = is_hallucinated(result.get("hallucination", False))
            try:
                semantic_score = float(result.get("score", 0.0))
            except (TypeError, ValueError):
                semantic_score = 0.0

            # Pass/Fail determination criteria
            if (
                match_pct >= 70.0
                and relevance.lower() == "relevant"
                and not hallucinated
            ):
                status = "PASS"
                reason = f"Match Percentage is {match_pct:.2f}% (>= 70.00%), the response is relevant, and no hallucination was detected."
            else:
                status = "FAIL"
                if match_pct < 70.0:
                    reason = f"Match Percentage is {match_pct:.2f}% (< 70.00% threshold required for passing)."
                elif relevance.lower() != "relevant":
                    reason = "Chatbot response was classified as irrelevant."
                elif hallucinated:
                    reason = result.get("reason", "Chatbot response contains hallucinated or misleading information.")
                else:
                    reason = result.get("reason", "Response did not satisfy the evaluation criteria.")

            row_dict["Match Percentage"] = match_pct_str
            row_dict["Semantic Score"] = f"{semantic_score:.2f}"
            row_dict["Relevance"] = relevance
            row_dict["Hallucination"] = "Yes" if hallucinated else "No"
            row_dict["Status"] = status
            row_dict["Pass and Failure Reason"] = reason
            updated_rows.append(row_dict)
            
            print(
                f"Row {index + 1}: {status} | "
                f"Match: {match_pct_str} | "
                f"Semantic Score: {semantic_score:.2f} | "
                f"Relevance: {relevance} | "
                f"Hallucination: {'Yes' if hallucinated else 'No'}"
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
            "Hallucination",
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
            
    print("\nEvaluation completed successfully.")
    print(f"Output file: {output_file}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run AI Evaluator on Chatbot Results")
    parser.add_argument("--input", default="Frameworks_Output.xlsx", help="Input Excel path generated by Java runner")
    parser.add_argument("--output", default="Frameworks-Result.xlsx", help="Final evaluated Excel report path")
    args = parser.parse_args()
    
    process_qa_framework_excel(input_file=args.input, output_file=args.output)
