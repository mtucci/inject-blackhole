package it.univaq.disim.spencer.injectblackhole;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import it.univaq.disim.spencer.injectblackhole.analysis.Method;
import it.univaq.disim.spencer.injectblackhole.exception.NoSuitableStatementsInMethod;
import it.univaq.disim.spencer.injectblackhole.injection.GitHunkFilter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

enum InjectionMode {
    BEGIN,
    RANDOM_POSITION
}

@Command(
    name = "inject-blackhole",
    mixinStandardHelpOptions = true,
    version = "inject-blackhole 1.0",
    description = "Inject a delay (CPU busy) using a JMH Blackhole"
)
public class InjectBlackhole implements Callable<Integer> {
    private static final Logger LOGGER = Logger.getLogger(InjectBlackhole.class.getName());
    private Injector injector;

    @Option(names = { "-t", "--target" }, required = true,
            description = "The target library path")
    private Path targetLibraryPath;

    @Option(names = { "-d", "--delay" }, required = true,
            description = "The delay in number of tokens")
    private long delay;

    @Option(names = { "-s", "--seed" },
            description = "Random seed for reproducibility")
    private int randomSeed;

    @Option(names = { "-m", "--mode" }, defaultValue = "BEGIN",
            description = "Injection mode: ${COMPLETION-CANDIDATES}")
    private InjectionMode injectionMode;

    @Option(names = { "-n", "--num-injections" }, defaultValue = "1",
            description = "Number of injections to perform (each in a different method)")
    private int numInjections = 1;

    private Method selectRandomMethod() {
        Method method = null;
        while (method == null) {
            Path classFile = injector.getRandomJavaFile();
            Optional<Method> optionalMethod = injector.getRandomMethod(classFile);
            if (optionalMethod.isPresent()) {
                method = optionalMethod.get();
            }
        }
        return method;
    }

    private void injectInRandomMethods(int numInjections) {
        List<Method> successfulInjections = new ArrayList<>();
        List<Method> failedInjections = new ArrayList<>();
        while (successfulInjections.size() < numInjections) {
            // Randomly select a method
            Method method = null;
            do {
                method = selectRandomMethod();
            } while (failedInjections.contains(method) || successfulInjections.contains(method));
            LOGGER.info("Selected method %s in file %s".formatted(
                method.getFQMethodName(), method.getClassFile()));

            // Inject the delay
            LOGGER.info("Injecting delay at %s".formatted(injectionMode));
            try {
                injector.injectInMethod(method, injectionMode);
            } catch (NoSuitableStatementsInMethod e) {
                LOGGER.info("No suitable statements found in method %s".formatted(method.getFQMethodName()));
                continue;
            } catch (RuntimeException e) {
                LOGGER.severe("Error while injecting the delay: " + e.getMessage());
                LOGGER.info("Selecting anothet Java file and method...");
                failedInjections.add(method);
                continue;
            }

            // Sometimes Spoon messes up the code in other parts when writing back the modifications.
            // These seems to happen when the class contains some weird formatting.
            // To avoid this, we resort to filter out all the diff hunks that do not contain our invocation.
            try {
                new GitHunkFilter(targetLibraryPath)
                    .applyFilteredPatch(method.getClassFile(), "Blackhole.consumeCPU");
            } catch (IOException | InterruptedException | RuntimeException e) {
                LOGGER.severe("Error while filtering the patch: " + e.getMessage());
                LOGGER.info("Selecting anothet Java file and method...");
                failedInjections.add(method);
                continue;
            }

            // If the injection was successful, add the method to the list
            successfulInjections.add(method);
        }

        // Print successful and failed injections
        LOGGER.info("Successful injections:");
        for (Method method : successfulInjections) {
            LOGGER.info("  %s in %s".formatted(method.getFQMethodName(), method.getClassFile()));
        }
        LOGGER.info("Failed injections:");
        for (Method method : failedInjections) {
            LOGGER.info("  %s in %s".formatted(method.getFQMethodName(), method.getClassFile()));
        }
    }

    @Override
    public Integer call() throws Exception {
        // Check if the path is valid
        if (!targetLibraryPath.toFile().exists()) {
            LOGGER.severe("Invalid path: " + targetLibraryPath);
            return 1;
        }

        LOGGER.info("Analyzing target library: " + targetLibraryPath);
        injector = new Injector(targetLibraryPath, delay);
        if (randomSeed != 0) {
            injector.setSeed(randomSeed);
        }

        LOGGER.info("Injecting delay in %d random methods".formatted(numInjections));
        injectInRandomMethods(numInjections);

        return 0;
    }

    public static void main(String... args) {
        LoggerConfig.configureGlobalLogger();
        int exitCode = new CommandLine(new InjectBlackhole()).execute(args);
        System.exit(exitCode);
    }
}
