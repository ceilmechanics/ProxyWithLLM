package org.example;

public class Prompt {
    public static String getPrompt(String type, String question) {
        return switch (type) {
            case "suggestedQuestions" -> SUGGESTED_QUESTION_PROMPT;
            case "summary" -> SUMMARY_PROMPT;
            case "chat" -> String.format(CHAT_PROMPT, question);
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

    private static final String SUMMARY_PROMPT = """
            Your task is to summarize the content of web pages provided.
            You must strictly follow these:
            1. Highlight the main ideas, key details, and any actionable insights.
            2. Keep the summary concise and tailored for end users to quickly grasp essential information.
            3. Exclude irrelevant elements such as advertisements, navigation links, or unrelated content.
            4. Present the summary in bullet points to enhance readability.
            5. Maintain a professional and neutral tone throughout the summary.
            """;

    private static final String CHAT_PROMPT = """
            You are tasked with answering a question clearly and comprehensively.
            Question: %s
            1.	Provide a complete and accurate answer to the question.
            2.	Include relevant details, examples, or explanations as necessary to enhance understanding.
            3.	If there are multiple possible answers, outline the options and explain their differences.
            4.	Keep the response concise (try make it no more than 5 sentences) and focused unless otherwise specified.
            """;
}
