package it.univaq.disim.spencer.injectblackhole.injection;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Path;

public class GitHunkFilter {
    private Path gitRepositoryPath;

    public GitHunkFilter(Path gitRepositoryPath) {
        this.gitRepositoryPath = findGitDir(gitRepositoryPath.toAbsolutePath().normalize()).getParent();
    }

    private static Path findGitDir(Path startingPath) {
        // Check if a .git directory is present in the starting path
        Path gitDir = startingPath.resolve(".git");
        if (gitDir.toFile().exists()) {
            return gitDir;
        }

        // Check parent directories
        return findGitDir(startingPath.getParent());
    }

    private BufferedReader runGitCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(gitRepositoryPath.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        process.waitFor();
        return new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    public String gitDiffFile(Path filePath) throws IOException, InterruptedException {
        // Run 'git diff <file>' command
        try (BufferedReader reader = runGitCommand("git", "diff", filePath.toString())) {
            StringBuilder diff = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                diff.append(line).append("\n");
            }
            return diff.toString();
        }
    }

    static String fixPatch(String patch, String keyword) {
        StringBuilder filteredPatch = new StringBuilder();

        // Read the patch line by line
        boolean withinHunk = false;
        boolean keepThisHunk = false;
        StringBuilder currentHunk = new StringBuilder();
        for (String line : patch.lines().toList()) {
            // Detect the start of a new hunk
            if (line.startsWith("@@ ")) {
                if (keepThisHunk) {
                    filteredPatch.append(currentHunk);
                }
                currentHunk = new StringBuilder();
                withinHunk = true;
                keepThisHunk = false;
            }

            // Keep the lines within the hunk
            if (withinHunk) {
                currentHunk.append(line).append("\n");
                // Only keep additions that contain the keyword
                if (line.startsWith("+") && line.contains(keyword)) {
                    keepThisHunk = true;
                }
            } else {
                filteredPatch.append(line).append("\n");
            }
        }

        // Append the last hunk
        if (keepThisHunk) {
            filteredPatch.append(currentHunk);
        }

        return filteredPatch.toString();

    }

    private String getFilteredPatch(Path filePath, String keyword) throws IOException, InterruptedException {
        // Run the git diff command to get the patch
        String patch = gitDiffFile(filePath);

        // Filter the patch to keep only the hunks that contain the keyword
        return fixPatch(patch, keyword);
    }

    public void discardChanges(Path filePath) throws IOException, InterruptedException {
        // Run 'git checkout -- <file>' to discard modifications
        runGitCommand("git", "checkout", "--", filePath.toString()).close();
    }

    private void applyPatch(String patchContent) throws IOException, InterruptedException, RuntimeException {
        // Start 'git apply' process
        ProcessBuilder processBuilder = new ProcessBuilder("git", "apply");
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(gitRepositoryPath.toFile());

        // Create process and write patch content to its input stream
        Process process = processBuilder.start();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
            writer.write(patchContent);
            writer.flush();
        }

        // Wait for the process to finish
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            return;
        }
        
        // In case of error, print the output of the process and throw an exception
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println(line);
            }
        }
        throw new RuntimeException("git apply failed with exit code " + exitCode);
    }

    public void applyFilteredPatch(Path filePath, String keyword) throws IOException, InterruptedException {
        Path relativePath = gitRepositoryPath.relativize(filePath);
        String patch = getFilteredPatch(relativePath, keyword);

        // Skip if the patch is empty
        if (patch.isEmpty()) {
            throw new RuntimeException("Patch is empty (no detected modifications)");
        }

        discardChanges(relativePath);

        // Skip if the patch contains removals
        for (String line : patch.lines().toList()) {
            if (line.startsWith("-") && !line.startsWith("---")) {
                throw new RuntimeException("Patch contains removals");
            }
        }

        applyPatch(patch);
    }
}
