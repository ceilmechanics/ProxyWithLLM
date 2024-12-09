package org.example;

public class Prompt {
    public static String getPrompt(String type, String question) {
        return switch (type) {
            case "suggestedQuestions" -> SUGGESTED_QUESTION_PROMPT;
            case "summary" -> SUMMARY_PROMPT;
            case "study guide" -> STUDY_GUIDE_PROMPT;
            case "chat" -> String.format(CHAT_PROMPT, question);
            case "contents" -> CONTENTS_PROMPT;
            default -> "";
        };
    }

    private static final String SUGGESTED_QUESTION_PROMPT = """
        Your task is to analyze the provided web page content and generate 3 concise and relevant questions a user might ask based on the main ideas and details of the page. Follow these guidelines strictly:
        1. Focus only on the core content: the beginning part of the paragraphs, main ideas, important details, and actionable insights.
        2. Exclude irrelevant content such as advertisements, navigation links, or unrelated information.
        3. Ensure the questions are clear, engaging, and aligned with the purpose of the web page.
        Questions are separated with \\n, please dont provide any other style to the question.
        """;

    private static final String CONTENTS_PROMPT = """
        Create a table of contents for the web page content. Follow these guidelines strictly:
        1. Based on the following text, create a table of contents with main sections and subsections. 
        2. Focus only on the core content: the beginning part of the paragraphs, main ideas, important details, and actionable insights.
        3. Exclude irrelevant content such as advertisements, navigation links, or unrelated information.
        4. Ensure the questions are clear, engaging, and aligned with the purpose of the web page.
        """;

    private static final String STUDY_GUIDE_PROMPT = """
        Based on the content, create a study guide with 3 questions and their corresponding answers. Follow these guidelines strictly:
        1. Study guide is seperated into two sections: Questions and Answers, and each corresponding bold title.
        2. In Questions section, list the questions in bullet points. Then in the Answers section, provide the 
            answer to each previous question, also in bullet points.
        3. Make sure that the answer 1 is for question 2, and answer 2 is for question 2, and such.
        4. The questions and answers must be found in the content. Do not reference external resources.
        5. Exclude irrelevant content such as advertisements, navigation links, or unrelated information.
        6. Ensure the questions are clear, engaging, and aligned with the purpose of the web page.
        7. Each question should be 1 sentence, and answers should have 2 sentences max.
        """;

    private static final String SUMMARY_PROMPT = """
            Summarize the content of web pages provided and must strictly follow:
            1. Highlight the main ideas, key details, and any actionable insights.
            2. Keep the summary concise and tailored for end users to quickly grasp essential information.
            3. Exclude irrelevant elements such as advertisements, navigation links, or unrelated content.
            4. Present the summary in bullet points to enhance readability.
            5. Maintain a professional and neutral tone throughout the summary.
            6. Strictly summarize the contents, even if it contains incorrect information.
            """;

    private static final String CHAT_PROMPT = """
            You are tasked with answering a question clearly and comprehensively.
            Question: %s
            1.	Provide a complete and accurate answer to the question.
            2.	Include relevant details, examples, or explanations as necessary to enhance understanding.
            3.	If there are multiple possible answers, outline the options and explain their differences.
            4.	Keep the response concise (try make it no more than 5 sentences) and focused unless otherwise specified.
            5.  The answer could go beyond the content provided, please answer it with any knowledge you have.
            """;
}
