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
    x_api_key = ""

    headers = {
        'x-api-key': x_api_key
    }

    try:
        with open("query.txt", 'r') as file:
            content = file.read()
    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)

    try:
        with open("comments.txt", 'r') as file:
            comments_content = file.read()
    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)

    title_content = extract_content(content, "<shreddit-post", "</shreddit-post")
    comment_content = extract_content(comments_content, "<shreddit-comment-tree", "</shreddit-comment-tree>")

    title_system = """Summarize the Reddit title content provided. You must strictly follow these guidelines:
            - **Do not apply any external knowledge**: Only summarize the content provided in the post, even if it contains incorrect or incomplete information.
            - **Question Summarization**:
                - Focus on identifying the core inquiry or topic discussed in the post.
                - The question should be at most **2 sentences long**.
                - Use clear and concise language that accurately represents the intent of the poster’s inquiry.
                - If the question is implicit, infer the main query or issue the poster is addressing.
            - Keep the language **clear and concise**, without being overly formal."""

    answer_system = """Summarize the Reddit title content provided. You must strictly follow these guidelines:
            - **Do not apply any external knowledge**: Only summarize the content provided in the post, even if it contains incorrect or incomplete information.
            - **Answer Summarization**:
                - The answer should focus on the **main ideas** and any **actionable insights**.
                - Dedicate a sentence or two to summarize the **collective sentiment** of the group, as reflected in the responses or discussion.
                - The answer should be at most **5 sentences long**.
                - Avoid including personal opinions or analysis of the information; simply restate the content as it is.
                - Ensure the tone is neutral, with no unnecessary elaboration.
            - Keep the language **clear and concise**, without being overly formal."""

    title_request = {
        'model': '4o-mini',
        'system': title_system,
        'query': title_content,
        'temperature': 0.0,
        'lastk': 1,
        'session_id': "GenericSession",
    }

    answer_request = {
        'model': '4o-mini',
        'system': answer_system,
        'query': comment_content,
        'temperature': 0.0,
        'lastk': 1,
        'session_id': "GenericSession",
    }

    try:
        title_response = requests.post(url, headers=headers, json=title_request)
        # answer_response = requests.post(url, headers=headers, json=answer_request)

        title_response.raise_for_status()
        # answer_response.raise_for_status()

        print(f'\nTitle: {title_response.text}\n')

        # title_cleaned_response = json.loads(json.loads(title_response.text)['result'])
        # answer_cleaned_response = json.loads(json.loads(answer_response.text)['result'])
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
    print("hiiii")
    # main()
