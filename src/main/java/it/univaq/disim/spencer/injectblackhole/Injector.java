package it.univaq.disim.spencer.injectblackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import it.univaq.disim.spencer.injectblackhole.analysis.CodeBase;
import it.univaq.disim.spencer.injectblackhole.analysis.Method;
import it.univaq.disim.spencer.injectblackhole.exception.NoSuitableStatementsInMethod;
import it.univaq.disim.spencer.injectblackhole.injection.Delay;
import it.univaq.disim.spencer.injectblackhole.injection.GitHunkFilter;
import spoon.SpoonException;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;

public class Injector {

    private static final Logger LOGGER = Logger.getLogger(Injector.class.getName());
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

    public Optional<Method> findMethod(String fqMethodName) {
        Method target = new Method(fqMethodName);

        // Convert package name to path
        if (target.getPackageName() == null) {
            return Optional.empty();
        }
        Path packagePath = targetLibraryPath.resolve(target.getPackageName().replace('.', '/'));
        if (!Files.exists(packagePath)) {
            return Optional.empty();
        }

        // Build the Spoon model for the entire library
        CodeBase fileModel = new CodeBase(packagePath);
        fileModel.load();

        // Find the method by name
        List<CtMethod<?>> methods = fileModel.getMethods();
        for (CtMethod<?> m : methods) {
            Method thisMethod = new Method(m);
            if (thisMethod.equals(target)) {
                thisMethod.setCodeBase(fileModel);
                return Optional.of(thisMethod);
            }
        }
        return Optional.empty();
    }

    public Optional<Method> getRandomMethod(Path javaFile) {
        // Build the Spoon model for the entire library
        CodeBase fileModel = new CodeBase(javaFile);
        fileModel.load();

        // Randomly select a method to inject the delay
        List<CtMethod<?>> methods = fileModel.getMethods().stream()
            .filter(method -> !method.isAbstract()) // Exclude abstract methods
            .collect(Collectors.toList());
        if (methods.isEmpty()) {
            return Optional.empty();
        }
        Method randomMethod = new Method(methods.get(random.nextInt(methods.size())));
        randomMethod.setCodeBase(fileModel);
        return Optional.of(randomMethod);
    }

    public Path getRandomJavaFile() {
        // Randomly select a Java file
        List<Path> javaFiles = getJavaFiles();
        return javaFiles.get(random.nextInt(javaFiles.size()));
    }

    private void injectBeforeRandomStatement(Method method, CtInvocation<Object> invocation) {
        // Get all the statements at any depth, including within blocks like if and for
        List<CtStatement> statements = method.getTopLevelStatements();
        if (statements.isEmpty()) {
            throw new NoSuitableStatementsInMethod("No suitable statements in method " + method.getFQMethodName());
        }

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

            try {
                // Save the modified class file
                method.getCodeBase().save();
            } catch (SpoonException e) {
                LOGGER.warning("Error saving the modified class file: " + e.getMessage());
                failedPositions.add(position);
                try {
                    git.discardChanges(method.getClassFile());
                } catch (IOException | InterruptedException e1) {
                    LOGGER.severe("Error discarding changes: " + e1.getMessage());
                    e1.printStackTrace();
                }
                continue;
            }

            try {
                // Check if the injection was successful by checking if the invocation is present in the diff
                if (git.gitDiffFile(method.getClassFile()).isEmpty()) {
                    failedPositions.add(position);
                } else {
                    return;
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.warning("Error during git diff check: " + e.getMessage());
                failedPositions.add(position);
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
        method.getCodeBase().save();
    }
}
