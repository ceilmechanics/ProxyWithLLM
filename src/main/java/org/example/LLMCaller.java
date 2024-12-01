package org.example;
import java.io.*;

public class LLMCaller {
    public static void main(String[] args) {
        String pythonExecutablePath = "src/main/python/dist/llm";
        String param1 = "test.txt";

        ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutablePath, param1);

        try {
            // Start the process
            Process process = processBuilder.start();

            // Get the output of the process (if any)
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Wait for the process to finish
            int exitCode = process.waitFor();
            System.out.println("Python script executed with exit code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}