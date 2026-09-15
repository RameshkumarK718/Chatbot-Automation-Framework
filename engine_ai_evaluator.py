import os
import pandas as pd
from openai import OpenAI
# Configuration
input_file = "Frameworks_Results.xlsx"
output_file = "Frameworks_Results_Evaluated.xlsx"
client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"))
# Validate input file
if not os.path.exists(input_file):
    print(f"Error: {input_file} not found.")
    exit(1)
if not os.environ.get("OPENAI_API_KEY"):
    print("Error: OPENAI_API_KEY environment variable is not set.")
    exit(1)
print("Starting AI Evaluation and Report Generation...")
# Read Excel
xls = pd.ExcelFile(input_file)
sheet_names = xls.sheet_names
all_results = []
# Helper function
def get_text(value):
    if pd.isna(value):
        return ""
    return str(value).strip()
# Process each sheet
for sheet_name in sheet_names:
    df = pd.read_excel(input_file, sheet_name=sheet_name)
    print(f"Evaluating sheet: {sheet_name} ({len(df)} rows)")
    for idx, row in df.iterrows():
        question = get_text(row.get("User Question"))
        expected = get_text(row.get("Expected Answer"))
        chatbot_ans = get_text(row.get("Chatbot Answer"))
        # Skip empty questions
        if not question:
            continue
        # Handle chatbot errors
        if not chatbot_ans or "error" in chatbot_ans.lower():
            relevance = "Irrelevant"
            status = "FAIL"
            reason = "Chatbot response was empty or contained an error."
        else:
            # AI Evaluation Prompt
            prompt = f"""
You are an expert QA engineer and AI response auditor.
Evaluate the chatbot answer against the user question and expected answer.
Evaluation rules:
1. Relevance:
   - Relevant = the chatbot directly addresses the user's question.
   - Irrelevant = the chatbot does not address the question.
2. Status:
   - PASS = the answer is correct, relevant, and sufficiently satisfies the expected answer.
   - FAIL = the answer is incorrect, irrelevant, incomplete in a critical way, or contradicts the expected answer.
Do not mark an answer PASS simply because it is related to the question.
User Question:
{question}
Expected Answer:
{expected}
Chatbot Answer:
{chatbot_ans}
Return ONLY valid JSON in this exact format:
{{
  "relevance": "Relevant",
  "status": "PASS",
  "reason": "Brief explanation"
}}
"""
            try:
                response = client.chat.completions.create(
                    model="gpt-4o-mini",
                    messages=[
                        {
                            "role": "user",
                            "content": prompt
                        }
                    ],
                    temperature=0
                )
                content = response.choices[0].message.content.strip()
                # Remove markdown code fences if the model adds them
                content = content.replace("```json", "").replace("```", "").strip()
                import json
                result = json.loads(content)
                relevance = result.get("relevance", "Irrelevant")
                status = result.get("status", "FAIL")
                reason = result.get(
                    "reason",
                    "No evaluation reason provided."
                )
                # Safety validation
                if relevance not in ["Relevant", "Irrelevant"]:
                    relevance = "Irrelevant"
                if status not in ["PASS", "FAIL"]:
                    status = "FAIL"
            except Exception as e:
                # IMPORTANT:
                # API/parsing failure should NOT be considered PASS
                relevance = "Unknown"
                status = "ERROR"
                reason = f"AI evaluation failed: {str(e)}"
        # Update Excel row
        df.at[idx, "Relevance"] = relevance
        df.at[idx, "Status"] = status
        df.at[idx, "Pass and Failure Reason"] = reason
    all_results.append((sheet_name, df))
# Save results
with pd.ExcelWriter(
    output_file,
    engine="openpyxl"
) as writer:
    for sheet_name, df in all_results:
        df.to_excel(
            writer,
            sheet_name=sheet_name,
            index=False
        )
print()
print("AI Audit Evaluation complete!")
print(f"Saved results to: {output_file}")
