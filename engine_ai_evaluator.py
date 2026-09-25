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
        # Define the models to try in order of preference (Primary -> Fallback)
        models_to_try = [
            {
                "model": "gpt-4o-mini", 
                "strict_json": True, 
                "desc": "Primary Engine (gpt-4o-mini)"
            },
            {
               "model": "openrouter/free", 
               "strict_json": False, 
               "desc": "Automatic Fallback (openrouter/free)"
            }
        ]

        last_exception = None

        for attempt in models_to_try:
            try:
                # Build request parameters dynamically
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
                
                # Only apply strict OpenAI JSON format if supported by the model
                if attempt["strict_json"]:
                    kwargs["response_format"] = {"type": "json_object"}

                response = self.client.chat.completions.create(**kwargs)
                content = response.choices[0].message.content
                
                # Clean up markdown code blocks if a free model adds them
                if "```json" in content:
                    content = content.split("```json")[1].split("```")[0].strip()
                elif "```" in content:
                    content = content.split("```")[1].split("```")[0].strip()

                return json.loads(content)

            except Exception as e:
                last_exception = e
                print(f"Warning: {attempt['desc']} failed ({str(e)}). Attempting fallback...")
                continue

        # If all models fail, return a structured fallback failure response
        return {
            "relevance": "Irrelevant",
            "hallucination": True,
            "score": 0.0,
            "reason": f"All AI evaluation models failed. Last error: {str(last_exception)}"
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

def update_html_dashboard(output_file="Frameworks-Result.xlsx", html_file="index.html"):
    """Dynamically reads the evaluated Excel results and updates the index.html dashboard."""
    try:
        if not os.path.exists(output_file):
            print(f"Result file {output_file} not found for HTML generation.")
            return

        excel_file = pd.ExcelFile(output_file)
        total_tests = 0
        total_passed = 0
        total_failed = 0
        summary_rows_html = ""

        for sheet_name in excel_file.sheet_names:
            df = pd.read_excel(output_file, sheet_name=sheet_name, dtype=str)
            if "Status" in df.columns:
                sheet_total = len(df)
                sheet_passed = len(df[df["Status"].str.upper() == "PASS"])
                sheet_failed = len(df[df["Status"].str.upper() == "FAIL"])
                
                total_tests += sheet_total
                total_passed += sheet_passed
                total_failed += sheet_failed

                summary_rows_html += f"""
                <tr>
                    <td>{sheet_name}</td>
                    <td>{sheet_total}</td>
                    <td class="pass">{sheet_passed}</td>
                    <td class="fail">{sheet_failed}</td>
                </tr>"""

        pass_percentage = (total_passed / total_tests * 100) if total_tests > 0 else 0

        html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Universal Chatbot Automation Dashboard</title>
    <style>
        body {{ font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 40px; background: #f8f9fa; color: #333; }}
        .container {{ max-width: 1000px; margin: auto; }}
        h1 {{ color: #2c3e50; border-bottom: 2px solid #dee2e6; padding-bottom: 10px; }}
        .card-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-top: 20px; }}
        .card {{ background: white; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.05); text-align: center; }}
        .card h3 {{ margin: 0 0 10px 0; color: #6c757d; font-size: 14px; text-transform: uppercase; }}
        .card p {{ margin: 0; font-size: 24px; font-weight: bold; color: #2c3e50; }}
        .pass {{ color: #28a745; font-weight: bold; }}
        .fail {{ color: #dc3545; font-weight: bold; }}
        table {{ width: 100%; border-collapse: collapse; background: white; margin-top: 30px; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05); }}
        th, td {{ padding: 12px 15px; text-align: left; border-bottom: 1px solid #dee2e6; }}
        th {{ background-color: #343a40; color: white; }}
        tr:hover {{ background-color: #f1f3f5; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🤖 Chatbot Automation Dashboard</h1>
        <div class="card-grid">
            <div class="card">
                <h3>Total Test Cases</h3>
                <p>{total_tests}</p>
            </div>
            <div class="card">
                <h3>Passed</h3>
                <p class="pass">{total_passed}</p>
            </div>
            <div class="card">
                <h3>Failed</h3>
                <p class="fail">{total_failed}</p>
            </div>
            <div class="card">
                <h3>Pass Rate</h3>
                <p>{pass_percentage:.1f}%</p>
            </div>
        </div>

        <h2>Module Breakdown</h2>
        <table>
            <thead>
                <tr>
                    <th>Module / Sheet Name</th>
                    <th>Total Tests</th>
                    <th>Passed</th>
                    <th>Failed</th>
                </tr>
            </thead>
            <tbody>
                {summary_rows_html}
            </tbody>
        </table>
    </div>
</body>
</html>
"""
        with open(html_file, "w", encoding="utf-8") as f:
            f.write(html_content)
        print(f"Dashboard successfully updated: {html_file}")
    except Exception as e:
        print(f"Failed to update HTML dashboard: {e}")

def process_qa_framework_excel(
    input_file="Frameworks_Output.xlsx",
    output_file="Frameworks-Result.xlsx"
):
    """Process all sheets from Java output, generate evaluated Excel report, and update index.html."""
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
        update_html_dashboard(output_file=output_file, html_file="index.html")
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
        update_html_dashboard(output_file=output_file, html_file="index.html")
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

    # Automatically generate / update index.html after evaluation
    update_html_dashboard(output_file=output_file, html_file="index.html")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run AI Evaluator on Chatbot Results")
    parser.add_argument("--input", default="Frameworks_Output.xlsx", help="Input Excel path generated by Java runner")
    parser.add_argument("--output", default="Frameworks-Result.xlsx", help="Final evaluated Excel report path")
    args = parser.parse_args()
    
    process_qa_framework_excel(input_file=args.input, output_file=args.output)
