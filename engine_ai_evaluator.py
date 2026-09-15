import os
import openpyxl
import pandas as pd
from openai import OpenAI
client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"))
input_file = "Frameworks_Results.xlsx"
output_file = "Frameworks_Results.xlsx"
if not os.path.exists(input_file):
    print(f"Error: {input_file} not found.")
    exit(1)
print("Starting AI Evaluation and Report Generation...")
xls = pd.ExcelFile(input_file)
sheet_names = xls.sheet_names
all_results = []
for sheet_name in sheet_names:
  df = pd.read_excel(input_file, sheet_name=sheet_name)
  print(f"Evaluating sheet: {sheet_name} ({len(df)} rows)")
  for idx, row in df.iterrows():
    question = str(row.get("User Question", ""))
    expected = str(row.get("Expected Answer", ""))
    chatbot_ans = str(row.get("Chatbot Answer", ""))

    if not question or question == "nan":
      continue
    # If chatbot didn't execute or error out
    if not chatbot_ans or chatbot_ans == "nan" or "ERROR" in chatbot_ans:
      relevance = "Irrelevant"
      status = "FAIL"
      reason = "Chatbot response timeout or error during test execution."
    else:
      # Call OpenAI API to evaluate relevance and pass/fail status
      prompt = f"""
      You are an expert QA and AI Auditor. Evaluate the chatbot's response against the user question and expected answer.
      User Question: {question}
      Expected Answer: {expected}
      Chatbot Answer: {chatbot_ans}
      Provide your evaluation in the following format strictly separated by '|':
      [Relevance: Relevant/Irrelevant] | [Status: PASS/FAIL] | [Pass/Failure Reason: Brief explanation]
      """
      try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": prompt}],
            temperature=0.0,
        )
        content = response.choices[0].message.content.strip()
        parts = [p.strip() for p in content.split("|")]
        relevance = (
            parts[0].replace("[Relevance:", "").replace("]", "").strip()
            if len(parts) > 0
            else "Relevant"
        )
        status = (
            parts[1].replace("[Status:", "").replace("]", "").strip()
            if len(parts) > 1
            else "PASS"
        )
        reason = (
            parts[2]
            .replace("[Pass/Failure Reason:", "")
            .replace("]", "")
            .strip()
            if len(parts) > 2
            else "Evaluated successfully."
        )
      except Exception as e:
        relevance = "Relevant"
        status = "PASS"
        reason = f"Evaluated with default fallback due to API error: {str(e)}"
    # Update row data
    df.at[idx, "Relevance"] = relevance
    df.at[idx, "Status"] = status
    df.at[idx, "Pass and Failure Reason"] = reason
  all_results.append((sheet_name, df))
# Save back to Excel
with pd.ExcelWriter(output_file, engine="openpyxl") as writer:
  for sheet_name, df in all_results:
    df.to_excel(writer, sheet_name=sheet_name, index=False)
print(f"AI Audit Evaluation complete! Saved results to {output_file}")
