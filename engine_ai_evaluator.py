import os
import json
import pandas as pd
from openai import OpenAI
class AIEvaluator:
    def __init__(self, api_key: str = None):
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
        - hallucination: boolean (true if the actual answer introduces unverified or false claims)
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
            return json.loads(response.choices[0].message.content)
        except Exception as e:
            return {
                "score": 0.0,
                "pass": False,
                "hallucination": True,
                "feedback": f"Evaluation Error: {str(e)}"
            }
def process_excel_framework(excel_path: str = "Frameworks_Results.xlsx"):
    if not os.path.exists(excel_path):
        print(f"Error: {excel_path} not found. Run your Java Selenium tests first.")
        return         
    evaluator = AIEvaluator()          
    # Read all sheets from the Excel file
    excel_file = pd.ExcelFile(excel_path)
    sheet_names = excel_file.sheet_names  
    print(f"Loaded Excel sheets for AI evaluation: {sheet_names}")        
    with pd.ExcelWriter(excel_path, engine='openpyxl', mode='w') as writer:
        for sheet_name in sheet_names:
            df = pd.read_excel(excel_file, sheet_name=sheet_name)                       
            if len(df.columns) < 10:
                print(f"Skipping sheet {sheet_name}: Unexpected column structure.")
                df.to_excel(writer, sheet_name=sheet_name, index=False)
                continue                
            print(f"\nEvaluating sheet: [{sheet_name}] ({len(df)} rows)...")              
            for index, row in df.iterrows():
                question = str(row.iloc[4]) if pd.notna(row.iloc[4]) else ""
                expected = str(row.iloc[5]) if pd.notna(row.iloc[5]) else "Provide an accurate response."                          
                # Safely handle missing/NaN chatbot answers
                raw_actual = row.iloc[6]
                actual = str(raw_actual) if pd.notna(raw_actual) else ""                       
                if not question.strip():
                    continue                        
                # Skip evaluation if actual answer is empty, NaN, or starts with ERROR
                if not actual.strip() or actual.lower() == "nan" or actual.startswith("ERROR"):
                    df.iloc[index, 7] = "Score: 0.0" # Score column
                    df.iloc[index, 8] = "Fail" # Status column
                    df.iloc[index, 9] = "Skipped evaluation due to missing chatbot answer or execution error." # Feedback column
                    continue                       
                print(f"  -> Evaluating Row {index + 1}: {question[:35]}...")
                result = evaluator.evaluate_advanced(question, expected, actual)                                      
                score = result.get("score", 0.0)
                passed = result.get("pass", False)
                feedback = result.get("feedback", "")                          
                df.iloc[index, 7] = f"Score: {score}"
                df.iloc[index, 8] = "Pass" if passed else "Fail"
                df.iloc[index, 9] = feedback                   
            df.to_excel(writer, sheet_name=sheet_name, index=False)                    
    print(f"\nAI Evaluation complete! Results saved back to {excel_path}")
if __name__ == "__main__":
    process_excel_framework()
