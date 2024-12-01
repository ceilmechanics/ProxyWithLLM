package org.example;
import java.io.*;

public class PythonCaller {
    public static void main(String[] args) {
        try {
            // Path to the Python interpreter in the virtual environment
            String pythonInterpreter = "/opt/homebrew/bin/python3";
            // Path to the Python script
            String pythonScript = "/src/main/python/llm.py";

            // Create a process builder
            ProcessBuilder processBuilder = new ProcessBuilder(pythonInterpreter, pythonScript);

            // Start the process
            Process process = processBuilder.start();

            // Read the output of the Python script
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Wait for the Python script to finish
            int exitCode = process.waitFor();
            System.out.println("Python script executed with exit code " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}