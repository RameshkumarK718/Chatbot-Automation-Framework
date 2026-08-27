import json
from openai import OpenAI

class AIEvaluator:
    def __init__(self, api_key: str):
        self.client = OpenAI(api_key=api_key)

    def evaluate_advanced(self, question: str, expected: str, actual: str, context: str) -> dict:
        prompt = f"""
        You are an expert QA Evaluator for conversational AI.
        Evaluate the chatbot's response against the expected criteria.

        Question: {question}
        Context: {context}
        Expected Answer: {expected}
        Actual Answer: {actual}

        Return a JSON object with:
        - score: float (0.0 to 1.0)
        - pass: boolean (true if score >= 0.7)
        - hallucination: boolean
        - feedback: string explanation
        """

        response = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "system", "content": "You output JSON only."},
                      {"role": "user", "content": prompt}],
            response_format={"type": "json_object"}
        )

        return json.loads(response.choices[0].message.content)