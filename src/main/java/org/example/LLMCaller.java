package org.example;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LLMCaller {
    public static void main(String[] args) {
        String beforeMain;
        String betweenMain;
        String afterMain;

        try {
            // read file
            String content = new String(Files.readAllBytes(Paths.get("query.txt")));

            // TODO: place for optimization
            beforeMain = content.split("<main")[0];
            afterMain = content.split("</main>")[1];
            betweenMain = "<main" + content.split("<main")[1].split("</main>")[0] + "</main>";

            // write to file
            Files.write(Paths.get("data.txt"), betweenMain.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        String pythonExecutablePath = "src/main/python/dist/llm";
        ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutablePath, "data.txt");

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("Python script executed with exit code: " + exitCode);

            // read file
            String retrieved_content = new String(Files.readAllBytes(Paths.get("title_div.txt")));
            betweenMain = betweenMain.replaceFirst("<h1 id=\"post-title", retrieved_content + "<h1 id=\"post-title");

            // write file
            File file = new File("result.txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter writer = new FileWriter(file, false); // 'false' to overwrite the file
            writer.write(beforeMain + betweenMain + afterMain);
            writer.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}