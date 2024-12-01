package org.example;
import java.io.*;

public class PythonRunner {
    public static void main(String[] args) {
        try {
            String[] cmd = {"python3", "/python/example.py", "arg1", "arg2"};
            Process process = Runtime.getRuntime().exec(cmd);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("Python script exited with code: " + exitCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}