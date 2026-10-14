import os
import json
import difflib
import argparse
import time
import pandas as pd
from openai import OpenAI

class AIEvaluator:
    """
    AI-based chatbot response evaluator.
    Primary: OpenAI gpt-4o-mini
    Fallback: OpenRouter openrouter/free
    """

    def __init__(self):
        self.openai_key = os.getenv("OPENAI_API_KEY")
        self.openrouter_key = os.getenv("OPENROUTER_API_KEY")

        self.openai_client = None
        self.openrouter_client = None

        if self.openai_key:
            self.openai_client = OpenAI(api_key=self.openai_key)

        if self.openrouter_key:
            self.openrouter_client = OpenAI(
                api_key=self.openrouter_key,
                base_url="https://openrouter.ai/api/v1"
            )

        if not self.openai_client and not self.openrouter_client:
            raise ValueError(
                "Neither OPENAI_API_KEY nor OPENROUTER_API_KEY is set in environment variables."
            )

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

Return ONLY valid JSON.

Required JSON format:
{{
    "relevance": "Relevant",
    "hallucination": false,
    "score": 0.85,
    "reason": "The response directly answers the question and contains no hallucinated information."
}}
"""

        models_to_try = [
            {
                "model": "gpt-4o-mini",
                "client": self.openai_client,
                "strict_json": True,
                "desc": "Primary Engine (OpenAI gpt-4o-mini)"
            },
            {
                "model": "openrouter/free",
                "client": self.openrouter_client,
                "strict_json": False,
                "desc": "Fallback Engine (OpenRouter Free Router)"
            }
        ]

        last_exception = None

        for attempt in models_to_try:
            try:
                if attempt["client"] is None:
                    raise ValueError(f"API key not configured for {attempt['desc']}")

                kwargs = {
                    "model": attempt["model"],
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a QA evaluator. Return valid JSON only."
                        },
                        {
                            "role": "user",
                            "content": prompt
                        }
                    ],
                    "temperature": 0.0
                }

                if attempt["strict_json"]:
                    kwargs["response_format"] = {"type": "json_object"}

                start_time = time.time()
                response = attempt["client"].chat.completions.create(**kwargs)
                latency_ms = int((time.time() - start_time) * 1000)

                usage = getattr(response, "usage", None)
                prompt_tokens = usage.prompt_tokens if usage else 0
                completion_tokens = usage.completion_tokens if usage else 0
                total_tokens = usage.total_tokens if usage else (prompt_tokens + completion_tokens)

                content = response.choices[0].message.content
                if not content:
                    raise ValueError("AI returned an empty response.")

                content = content.strip()
                if "```json" in content:
                    content = content.split("```json", 1)[1].split("```", 1)[0].strip()
                elif "```" in content:
                    content = content.split("```", 1)[1].split("```", 1)[0].strip()

                result = json.loads(content)

                if "relevance" not in result or "hallucination" not in result or "score" not in result:
                    raise ValueError("AI response missing required fields.")

                result["prompt_tokens"] = prompt_tokens
                result["completion_tokens"] = completion_tokens
                result["total_tokens"] = total_tokens
                result["latency_ms"] = latency_ms

                print(f"AI evaluation successful using {attempt['desc']}")
                return result

            except Exception as e:
                last_exception = e
                print(f"Warning: {attempt['desc']} failed: {str(e)}")

        return {
            "relevance": "Irrelevant",
            "hallucination": True,
            "score": 0.0,
            "reason": f"All AI evaluation models failed. Last error: {str(last_exception)}",
            "prompt_tokens": 0,
            "completion_tokens": 0,
            "total_tokens": 0,
            "latency_ms": 0
        }


def get_first_text(row, columns):
    """
    Return the first non-empty value from specified columns using case-insensitive lookup.
    """
    row_cols_lower = {str(col).strip().lower(): col for col in row.index}
    for column in columns:
        col_lower = str(column).strip().lower()
        if col_lower in row_cols_lower:
            actual_col = row_cols_lower[col_lower]
            value = row[actual_col]
            if pd.isna(value):
                continue
            value = str(value).strip()
            if value and value.lower() != "nan":
                return value
    return ""


def calculate_match_percentage(expected, actual):
    if not expected and not actual:
        return 100.0
    if not expected or not actual:
        return 0.0
    similarity = difflib.SequenceMatcher(None, expected, actual).ratio()
    return round(similarity * 100, 2)


def is_hallucinated(value):
    if isinstance(value, str):
        return value.strip().lower() in {"true", "yes", "1"}
    return bool(value)


def update_html_dashboard(output_file="Frameworks-Result(EFI).xlsx", html_file="index.html"):
    try:
        if not os.path.exists(output_file):
            print(f"Result file {output_file} not found for HTML generation.")
            return

        excel_file = pd.ExcelFile(output_file)
        total_tests, total_passed, total_failed = 0, 0, 0

        for sheet_name in excel_file.sheet_names:
            df = pd.read_excel(output_file, sheet_name=sheet_name, dtype=str)
            status_col = None
            for col in df.columns:
                if col.strip().lower() in {"status", "pass / fail", "pass/fail"}:
                    status_col = col
                    break
            
            if not status_col:
                continue

            sheet_total = len(df)
            sheet_passed = len(df[df[status_col].fillna("").str.upper().str.contains("PASS")])
            sheet_failed = len(df[df[status_col].fillna("").str.upper().str.contains("FAIL")])

            total_tests += sheet_total
            total_passed += sheet_passed
            total_failed += sheet_failed

        print(f"Dashboard status updated successfully for {output_file}")
    except Exception as e:
        print(f"Failed to update HTML dashboard: {str(e)}")


def create_skipped_report(output_file, status):
    pd.DataFrame({"Status": [status]}).to_excel(output_file, index=False)
    update_html_dashboard(output_file=output_file, html_file="index.html")


def process_qa_framework_excel(input_file="Frameworks-Result(EFI).xlsx", output_file="Frameworks-Result(EFI).xlsx"):
    if not os.path.exists(input_file):
        print(f"Input file not found: {input_file}")
        create_skipped_report(output_file, "Skipped - Missing Input File")
        return

    openai_key = os.getenv("OPENAI_API_KEY")
    openrouter_key = os.getenv("OPENROUTER_API_KEY")

    if not openai_key and not openrouter_key:
        print("Neither OPENAI_API_KEY nor OPENROUTER_API_KEY is set.")
        create_skipped_report(output_file, "Skipped - Missing API Keys")
        return

    try:
        evaluator = AIEvaluator()
    except Exception as e:
        print(f"Unable to initialize AI evaluator: {str(e)}")
        create_skipped_report(output_file, "Skipped - AI Evaluator Initialization Failed")
        return

    excel_file = pd.ExcelFile(input_file)
    results = {}

    for sheet_name in excel_file.sheet_names:
        print(f"\nProcessing sheet: {sheet_name}")
        df = pd.read_excel(input_file, sheet_name=sheet_name, dtype=str)
        df = df.loc[:, ~df.columns.str.contains("^Unnamed", case=False, regex=True)]

        updated_rows = []

        for index, row in df.iterrows():
            row_dict = row.to_dict()

            question = get_first_text(
                row,
                ["User Question", "Question", "Question / Input to enter", "#"]
            )

            expected = get_first_text(
                row,
                ["Expected Answer", "Expected Result", "Expected result", "What it tests / Expected Answer", "What it tests / expected answer"]
            )

            chatbot_answer = get_first_text(
                row,
                ["Chatbot Answer", "Actual Result", "Actual result", "Response", "Actual Output"]
            )

            context = get_first_text(
                row,
                ["Context", "Conversation Context"]
            )

            match_pct = calculate_match_percentage(expected, chatbot_answer)
            match_pct_str = f"{match_pct:.2f}%"

            is_blank = not chatbot_answer or not chatbot_answer.strip() or chatbot_answer.lower() == "nan"
            is_error_response = chatbot_answer.lower().startswith("error") or chatbot_answer.lower().startswith("exception")

            if is_blank or is_error_response:
                row_dict["Match Percentage"] = match_pct_str
                row_dict["Semantic Score"] = "0.00"
                row_dict["Relevance"] = "Irrelevant"
                row_dict["Hallucination"] = "Yes"
                row_dict["Status"] = "FAIL"
                row_dict["Pass and Failure Reason"] = "Chatbot response was blank, threw an exception, or returned an error."
                row_dict["Prompt Tokens"] = 0
                row_dict["Completion Tokens"] = 0
                row_dict["Total Tokens"] = 0
                row_dict["Latency MS"] = 0
                updated_rows.append(row_dict)
                print(f"Row {index + 1}: FAIL | Match: {match_pct_str} | Blank or Error response")
                continue

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

            semantic_score = max(0.0, min(semantic_score, 1.0))
            SEMANTIC_THRESHOLD = 0.75

            is_relevant = (relevance.lower() == "relevant")
            no_hallucination = (not hallucinated)
            score_meets_threshold = (semantic_score >= SEMANTIC_THRESHOLD)

            if is_relevant and no_hallucination and score_meets_threshold:
                status = "PASS"
                reason = f"Passed: Response is relevant, no hallucination detected, and semantic score ({semantic_score:.2f}) meets threshold (>= {SEMANTIC_THRESHOLD})."
            else:
                status = "FAIL"
                if not is_relevant:
                    reason = "Chatbot response was classified as irrelevant."
                elif not no_hallucination:
                    reason = result.get("reason", "Chatbot response contains hallucinated or misleading information.")
                else:
                    reason = f"Semantic Score is {semantic_score:.2f} (below required threshold of {SEMANTIC_THRESHOLD})."

            row_dict["Match Percentage"] = match_pct_str
            row_dict["Semantic Score"] = f"{semantic_score:.2f}"
            row_dict["Relevance"] = relevance
            row_dict["Hallucination"] = "Yes" if hallucinated else "No"
            row_dict["Status"] = status
            row_dict["Pass and Failure Reason"] = reason
            row_dict["Prompt Tokens"] = result.get("prompt_tokens", 0)
            row_dict["Completion Tokens"] = result.get("completion_tokens", 0)
            row_dict["Total Tokens"] = result.get("total_tokens", 0)
            row_dict["Latency MS"] = result.get("latency_ms", 0)

            updated_rows.append(row_dict)

            print(
                f"Row {index + 1}: {status} | "
                f"Match: {match_pct_str} | "
                f"Semantic Score: {semantic_score:.2f} | "
                f"Relevance: {relevance} | "
                f"Hallucination: {'Yes' if hallucinated else 'No'}"
            )

        result_df = pd.DataFrame(updated_rows)
        results[sheet_name] = result_df

    with pd.ExcelWriter(output_file, engine="openpyxl") as writer:
        for sheet_name, result_df in results.items():
            result_df.to_excel(writer, sheet_name=sheet_name, index=False)

    print(f"\nEvaluation completed successfully. Output saved to: {output_file}")
    update_html_dashboard(output_file=output_file, html_file="index.html")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run AI Evaluator on Chatbot Results")
    parser.add_argument("--input", default="Frameworks-Result(EFI).xlsx", help="Input Excel path (Result file)")
    parser.add_argument("--output", default="Frameworks-Result(EFI).xlsx", help="Final evaluated Excel report path")
    args = parser.parse_args()

    process_qa_framework_excel(input_file=args.input, output_file=args.output)
