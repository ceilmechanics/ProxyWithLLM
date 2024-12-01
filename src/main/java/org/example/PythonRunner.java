package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;

public class PythonRunner {
    private Process pythonProcess;
    private BufferedWriter writer;
    private BufferedReader reader;

    public PythonRunner(String pythonScriptPath) throws IOException {
        // Start the Python process
        ProcessBuilder processBuilder = new ProcessBuilder("python3", System.getProperty("user.dir") + "/src/main/python/llm.py", "kkk");
//        processBuilder.command("python3", pythonScriptPath);
//        processBuilder.directory(new java.io.File(System.getProperty("user.dir"))); // Set working directory to the project root
        pythonProcess = processBuilder.start();

        // Get input and output streams
        writer = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
    }

    public String callPythonFunction(String largeText) throws IOException {
        // Send the large text to Python
        writer.write(largeText + "\n");
        writer.flush();

        // Read the result from Python
        String result = reader.readLine();
        return result;
    }

    public void close() throws IOException {
        // Terminate the Python process when done
        writer.write("exit\n");
        writer.flush();
        pythonProcess.destroy();
    }

    public static void main(String[] args) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", System.getProperty("user.dir") + "/src/main/python/llm.py", "kkk");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader readers = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String lines = null;

            while ((lines = reader.readLine()) != null) {
                System.out.println("lines" + lines);
            }

            while ((lines = readers.readLine()) != null) {
                System.out.println("Error lines" + lines);
            }


//            // Initialize the PythonRunner with the path to the Python script
//            PythonRunner pythonRunner = new PythonRunner("src/main/python/llm.py");
//
//            // Call the Python function with a large text
//            String largeText = "This is a very large piece of text...";
//            String result = pythonRunner.callPythonFunction(largeText);
//
//            // Print the result returned by the Python function
//            System.out.println("Result from Python: " + result);
//
//            // Close the Python process when done
//            pythonRunner.close();
        } catch (IOException e) {
            System.err.println("Error running Python script: " + e.getMessage());
        }
    }
}