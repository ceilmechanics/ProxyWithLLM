package org.example;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PythonRunner {
    public static void main(String[] args) {
        try {
            Process process = Runtime.getRuntime().exec("../../../../python/llm.py");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println("Error running Python script: " + e.getMessage());
        }
    }
}