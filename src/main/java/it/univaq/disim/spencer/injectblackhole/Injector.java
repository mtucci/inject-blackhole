package it.univaq.disim.spencer.injectblackhole;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import it.univaq.disim.spencer.injectblackhole.injection.Delay;
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

    public Method getRandomMethod() {
        // Build the Spoon model for the entire library
        CodeBase targetLibrary = new CodeBase(targetLibraryPath);
        targetLibrary.load();

        // Randomly select a method to inject the delay
        List<CtMethod<?>> methods = targetLibrary.getMethods();
        return new Method(methods.get(random.nextInt(methods.size())));
    }

    private CtStatement getRandomStatement(CtMethod<?> method) {
        // Get all the statements at any depth, including within blocks like if and for
        List<CtStatement> statements = method.getBody().getElements(new TypeFilter<>(CtStatement.class));

        // Randomly select a statement
        return statements.get(random.nextInt(statements.size()));
    }

    public void injectInMethod(Method method, InjectionMode mode) {
        // Only load the class file of the method, not the entire library,
        // so that we can save only the modified class file.
        CodeBase clazz = new CodeBase(method.getClassFile());
        clazz.load();

        // Find the method in the class file
        CtMethod<?> ctMethod = clazz.getLauncher().getFactory().Class().getAll().stream()
            .flatMap(ctClass -> ctClass.getMethods().stream())
            .filter(ctM -> new Method(ctM).equals(method))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Method not found: " + method.getFQMethodName()));
        
        // Create the Blackhole invocation
        CtInvocation<Object> invocation = delay.createBlackholeConsume(clazz.getLauncher().getFactory());

        // Inject the invocation, depending on the mode
        switch (mode) {
            case BEGIN:
                delay.injectAtBegin(ctMethod, invocation);
                break;
            case RANDOM_POSITION:
                delay.injectBeforeStatement(getRandomStatement(ctMethod), invocation);
                break;
        }

        // Save the modified class file
        clazz.save(targetLibraryPath);
    }
}
