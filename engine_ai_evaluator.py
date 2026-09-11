import json
import csv
import os
from openai import OpenAI
class AIEvaluator:
    def __init__(self, api_key: str = None):
        # Automatically picks up OPENAI_API_KEY environment variable if not passed explicitly
        self.client = OpenAI(api_key=api_key or os.getenv("OPENAI_API_KEY"))
    def evaluate_advanced(self, question: str, expected: str, actual: str, context: str = "") -> dict:
        prompt = f"""
        You are an expert QA Evaluator for conversational AI.
        Evaluate the chatbot's response against the expected criteria.   
        Question: {question}
        Context: {context if context else "None provided"}
        Expected Answer: {expected}
        Actual Answer: {actual}    
        Return a JSON object with:
        - score: float (0.0 to 1.0, where 1.0 is an exact semantic match)
        - pass: boolean (true if score >= 0.7)
        - hallucination: boolean (true if the actual answer introduces unverified or false claims outside context/expected intent)
        - feedback: string explanation of why this score was given
        """
        try:
            response = self.client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": "You output JSON only with keys: score, pass, hallucination, feedback."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.0
            )
            content = response.choices[0].message.content
            return json.loads(content)
        except Exception as e:
            return {
                "score": 0.0,
                "pass": False,
                "hallucination": True,
                "feedback": f"Evaluation Error: {str(e)}"
            }
def process_qa_framework_csv(csv_path: str = "FrameworkQA.csv"):
    if not os.path.exists(csv_path):
        print(f"Error: {csv_path} not found. Run your Java Selenium tests first.")
        return
    evaluator = AIEvaluator()
    rows = []   
    # Read existing CSV data
    with open(csv_path, mode='r', encoding='utf-8') as file:
        reader = csv.reader(file)
        for row in reader:
            rows.append(row)
    if not rows:
        print("CSV file is empty.")
        return
    header = rows[0]
    print(f"Loaded {len(rows) - 1} test cases for AI evaluation...")
    # Ensure header has columns for metrics if missing
    # Assuming columns: [TestCase, Question, Response, Expected, Score, Pass, Hallucination, Feedback]
    while len(header) < 7:
        header.extend(["Score", "Pass", "Hallucination", "Feedback"])
    for i in range(1, len(rows)):
        row = rows[i]
        # Pad row to ensure index safety
        while len(row) < 7:
            row.append("")
        test_case = row[0]
        question = row[1]
        actual_response = row[2]
        expected_answer = row[3] if len(row) > 3 and row[3] else "Provide an accurate and helpful response."
        if not actual_response or actual_response.startswith("ERROR"):
            row[4] = "0.0"
            row[5] = "false"
            row[6] = "true"
            row_feedback = "Skipped evaluation due to execution error or empty response."
            if len(row) > 7:
                row[7] = row_feedback
            else:
                row.append(row_feedback)
            continue
        print(f"Evaluating [{test_case}]: {question[:40]}...")
        result = evaluator.evaluate_advanced(question, expected_answer, actual_response)
        # Update row with evaluation metrics
        row[4] = str(result.get("score", 0.0))
        row[5] = str(result.get("pass", False)).lower()
        row[6] = str(result.get("hallucination", False)).lower()    
        feedback_text = result.get("feedback", "")
        if len(row) > 7:
            row[7] = feedback_text
        else:
            row.append(feedback_text)
    # Save evaluated results back
    with open(csv_path, mode='w', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)
        writer.writerows(rows)
    print(f"\nEvaluation complete! Detailed metrics saved back to {csv_path}")
if __name__ == "__main__":
    process_qa_framework_csv()
