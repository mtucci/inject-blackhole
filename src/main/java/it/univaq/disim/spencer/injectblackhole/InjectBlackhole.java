package it.univaq.disim.spencer.injectblackhole;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import it.univaq.disim.spencer.injectblackhole.analysis.Method;
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

    public void injectInRandomMethod(Injector injector) {
        // Randomly select a method
        Path classFile = injector.getRandomJavaFile();
        Method method = injector.getRandomMethod(classFile);
        LOGGER.info("Selected method %s in file %s".formatted(
            method.getFQMethodName(), classFile));

        // Inject the delay
        LOGGER.info("Injecting delay at %s".formatted(injectionMode));
        injector.injectInMethod(method, injectionMode);

        // Sometimes Spoon messes up the code in other parts when writing back the modifications.
        // These seems to happen when the class contains some weird formatting.
        // To avoid this, we resort to filter out all the diff hunks that do not contain our invocation.
        try {
            new GitHunkFilter(targetLibraryPath)
                .applyFilteredPatch(method.getClassFile(), "Blackhole.consumeCPU");
        } catch (Exception e) {
            LOGGER.severe("Error while filtering the patch: " + e.getMessage());
            e.printStackTrace();
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
        Injector injector = new Injector(targetLibraryPath, delay);
        if (randomSeed != 0) {
            injector.setSeed(randomSeed);
        }

        for (int i = 0; i < numInjections; i++) {
            injectInRandomMethod(injector);
        }

        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new InjectBlackhole()).execute(args);
        System.exit(exitCode);
    }
}
