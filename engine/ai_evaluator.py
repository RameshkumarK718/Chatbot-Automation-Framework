
import json

import openai



class AIEvaluator:

    def __init__(self, api_key: str):

        self.client = openai.OpenAI(api_key=api_key)



    def evaluate(self, question: str, q_type: str, context: str, expected: str, actual: str) -> dict:

        prompt = f"""

        You are an automated software test evaluator for a chatbot system.

        Evaluate the chatbot's actual response against the expected criteria.



        Question: {question}

        Question Type: {q_type}

        Previous Context: {context}

        Expected Answer: {expected}

        Actual Chatbot Answer: {actual}



        Return ONLY a JSON object with the exact keys:

        {{

          "relevance": "Relevant" or "Non-Relevant",

          "status": "Pass" or "Fail",

          "accuracy_score": <number between 0 and 100>,

          "evaluation_reason": "<Short concise reason>",

          "remarks": "<Detailed explanation of evaluation>"

        }}

        """



        response = self.client.chat.completions.create(

            model="gpt-4o-mini",

            response_format={"type": "json_object"},

            messages=[{"role": "user", "content": prompt}],

            temperature=0.0

        )

        return json.loads(response.choices[0].message.content)

