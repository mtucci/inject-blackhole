package it.univaq.disim.spencer.injectblackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import it.univaq.disim.spencer.injectblackhole.injection.Delay;
import it.univaq.disim.spencer.injectblackhole.injection.GitHunkFilter;
import it.univaq.disim.spencer.injectblackhole.analysis.CodeBase;
import it.univaq.disim.spencer.injectblackhole.analysis.Method;

public class Injector {

    private static Random random = new Random();
    private Path targetLibraryPath;
    private Delay delay;

    public Injector(Path targetLibraryPath, long delay) {
        this.targetLibraryPath = targetLibraryPath;
        this.delay = new Delay(delay);
    }

    public void setSeed(int seed) {
        random.setSeed(seed);
    }

    public List<Path> getJavaFiles() {
        try {
            return Files.walk(targetLibraryPath)
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Error retrieving Java files", e);
        }
    }

    public Method getRandomMethod(Path javaFile) {
        // Build the Spoon model for the entire library
        CodeBase fileModel = new CodeBase(javaFile);
        fileModel.load();

        // Randomly select a method to inject the delay
        List<CtMethod<?>> methods = fileModel.getMethods();
        return new Method(methods.get(random.nextInt(methods.size())), fileModel);
    }

    public Path getRandomJavaFile() {
        // Randomly select a Java file
        List<Path> javaFiles = getJavaFiles();
        return javaFiles.get(random.nextInt(javaFiles.size()));
    }

    private void injectBeforeRandomStatement(Method method, CtInvocation<Object> invocation) {
        // Get all the statements at any depth, including within blocks like if and for
        List<CtStatement> statements = method.getTopLevelStatements();

        // Randomly select a position
        // Sometimes Spoon will not be able to inject the invocation at the selected position,
        // so we keep track of the failed positions and retry until we find a good one
        List<Integer> failedPositions = new ArrayList<>();
        int position;
        GitHunkFilter git = new GitHunkFilter(targetLibraryPath);
        do {
            position = random.nextInt(statements.size());
            if (failedPositions.contains(position)) {
                continue;
            }

            // Try the injection
            delay.injectBeforeStatement(statements.get(position), invocation);

            // Save the modified class file
            method.getCodeBase().save(targetLibraryPath);

            // Check if the injection was successful by checking if the invocation is present in the diff
            try {
                if (git.gitDiffFile(method.getClassFile()).isEmpty()) {
                    failedPositions.add(position);
                } else {
                    return;
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        } while (failedPositions.size() < statements.size());

        // If we reach this point, we were not able to inject the invocation
        throw new RuntimeException("Failed to inject the invocation in method " + method.getFQMethodName());
    }

    public void injectInMethod(Method method, InjectionMode mode) {
        // Create the Blackhole invocation
        CtInvocation<Object> invocation = delay.createBlackholeConsume(method.getCodeBase().getLauncher().getFactory());

        // Inject the invocation, depending on the mode
        switch (mode) {
            case BEGIN:
                delay.injectAtBegin(method.getMethod(), invocation);
                break;
            case RANDOM_POSITION:
                injectBeforeRandomStatement(method, invocation);
                break;
        }

        // Save the modified class file
        method.getCodeBase().save(targetLibraryPath);
    }
}
