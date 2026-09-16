import os
import json
import pandas as pd
from openai import OpenAI

# Updated input file name
input_file = "Frameworks.xlsx"
output_file = "Frameworks-Result.xlsx"

# Validate input file without failing build
if not os.path.exists(input_file):
    print(f"Warning: {input_file} not found. Skipping AI evaluation.")
    pd.DataFrame({"Status": ["Skipped - Missing Input File"]}).to_excel(output_file, index=False)
    exit(0)

api_key = os.environ.get("OPENAI_API_KEY")
if not api_key:
    print("Warning: OPENAI_API_KEY is not set. Skipping AI evaluation.")
    pd.DataFrame({"Status": ["Skipped - Missing API Key"]}).to_excel(output_file, index=False)
    exit(0)

client = OpenAI(api_key=api_key)
print("Starting AI Evaluation and Report Generation...")

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

    # FIX: Ensure evaluation columns exist and are explicitly cast as string/object type 
    # to prevent Pandas float64 TypeError when writing text strings.
    for col in ["Relevance", "Status", "Pass and Failure Reason"]:
        if col not in df.columns:
            df[col] = ""
        df[col] = df[col].fillna("").astype(str)

    for idx, row in df.iterrows():
        question = get_text(row.get("User Question") or row.get("Question"))
        expected = get_text(row.get("Expected Answer"))
        chatbot_ans = get_text(row.get("Chatbot Answer") or row.get("Response") or row.get("Chatbot Response"))
        
        if not question:
            continue
            
        if not chatbot_ans or "error" in chatbot_ans.lower():
            relevance = "Irrelevant"
            status = "FAIL"
            reason = "Chatbot response was empty or contained an error."
        else:
            prompt = f"""
User Question / Context:
{question}

You are an QA Engineer and AI Response Auditor.
Evaluate the chatbot response against the user’s question, relevant conversation context, and expected answer.
Determine whether the response correctly understands and uses the relevant context to answer the question accurately and consistently.

Evaluation rules:
1. Relevance:
   - Relevant = the chatbot directly addresses the user's question.
   - Irrelevant = the chatbot does not address the question.

2. Status:
   - PASS = correct, relevant, and sufficiently satisfies the expected answer.
   - FAIL = incorrect, irrelevant, critically incomplete, or contradictory.

Expected Answer:
{expected}

Chatbot Answer:
{chatbot_ans}

Return JSON:
{{
  "relevance": "Relevant",
  "status": "PASS",
  "reason": "Brief explanation"
}}
"""
            try:
                response = client.chat.completions.create(
                    model="gpt-4o-mini",
                    response_format={"type": "json_object"},
                    messages=[{"role": "user", "content": prompt}],
                    temperature=0
                )
                
                content = response.choices[0].message.content.strip()
                result = json.loads(content)       
                
                relevance = result.get("relevance", "Irrelevant")
                status = result.get("status", "FAIL")
                reason = result.get("reason", "No evaluation reason provided.")
                
                if relevance not in ["Relevant", "Irrelevant"]:
                    relevance = "Irrelevant"
                if status not in ["PASS", "FAIL"]:
                    status = "FAIL"
            except Exception as e:
                relevance = "Unknown"
                status = "ERROR"
                reason = f"AI evaluation failed: {str(e)}"
                
        df.at[idx, "Relevance"] = relevance
        df.at[idx, "Status"] = status
        df.at[idx, "Pass and Failure Reason"] = reason
        
    all_results.append((sheet_name, df))

with pd.ExcelWriter(output_file, engine="openpyxl") as writer:
    for sheet_name, df in all_results:
        df.to_excel(writer, sheet_name=sheet_name, index=False)

print("\nAI Audit Evaluation complete!")
print(f"Saved results to: {output_file}")
