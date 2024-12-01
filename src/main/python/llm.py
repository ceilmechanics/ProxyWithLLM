import json
import requests
import sys

def extract_content(content, start_string, end_string):
    try:
        start_index = content.find(start_string)
        end_index = content.find(end_string) + len(end_string)

        if start_index == -1:
            raise ValueError(f"The file does not contain {start_string}")
        if end_index == -1:
            raise ValueError(f"The file does not contain {end_string}")

        return content[start_index:end_index]

    except Exception as e:
        print(f"An error occurred: {e}")


def main():
    # dont change
    url = "https://a061igc186.execute-api.us-east-1.amazonaws.com/dev"

    # set your API key
    x_api_key = "comp112yPgr18pHejCorRftczvjnYjKNEG1M3LqOtethzi3"

    headers = {
        'x-api-key': x_api_key
    }

    if len(sys.argv) != 2:
        print("Usage: script.py <param1> <param2>")
        sys.exit(1)

    try:
        with open(sys.argv[1], 'r') as file:
            content = file.read()
    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)

    title_system = """Summarize the Reddit title content provided. You must strictly follow these guidelines:
            - **Do not apply any external knowledge**: Only summarize the content provided in the post, even if it contains incorrect or incomplete information.
            - **Question Summarization**:
                - Focus on identifying the core inquiry or topic discussed in the post.
                - The question should be at most **3 sentences long**.
                - Use clear and concise language that accurately represents the intent of the poster’s inquiry.
                - If the question is implicit, infer the main query or issue the poster is addressing.
            - Keep the language **clear and concise**, without being overly formal."""

    title_request = {
        'model': '4o-mini',
        'system': title_system,
        'query': content,
        'temperature': 0.0,
        'lastk': 0,
        'session_id': "GenericSession",
    }

    try:
        title_response = requests.post(url, headers=headers, json=title_request)
        title_response.raise_for_status()

        cleaned_response = json.loads(title_response.text)['result']

        div_content = f'<div style="color: orangered;font-weight: 600;margin: 1rem;border: 1px solid black;padding: 10px;border-radius: 10px;background-color: lightgrey;">{cleaned_response}</div>'

        position = content.find('<h1 id="post-title')

        if position != -1:
            f = open("title_div.txt", "w")
            print(f"write")
            f.write(div_content)
            f.close()
        else:
            print("Error: <h1 id=\"post-title tag not found.")
            sys.exit(1)



        #
        # print(f'\nTitle Question: {title_cleaned_response["question"]}')
        # print(f'Title Answer: {title_cleaned_response["answer"]}\n')
        #
        # print(f'\nComment Question: {answer_cleaned_response["question"]}')
        # print(f'Comment Answer: {answer_cleaned_response["answer"]}\n')

    except requests.exceptions.RequestException as e:
        print(f"Request error: {e}")
    except (json.JSONDecodeError, KeyError) as e:
        print(f"Error parsing response: {e}")

if __name__ == '__main__':
    main()
