package it.univaq.disim.spencer.injectblackhole.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.declaration.CtMethod;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class CodeBase {

    private Path path;
    private Launcher launcher;
    
    public CodeBase(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public void load() {
        launcher = new Launcher();
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setPrettyPrinterCreator(() -> {
           return new SniperJavaPrettyPrinter(env);
          }
        );
        launcher.addInputResource(path.toString());
        launcher.setSourceOutputDirectory(path.toString());
        launcher.buildModel();
    } 

    public void save() throws IllegalStateException{
        launcher.prettyprint();
    }

    public List<CtMethod<?>> getMethods() {
        return launcher.getFactory().Class().getAll().stream()
                .flatMap(ctClass -> ctClass.getMethods().stream())
                .collect(Collectors.toList());
    }
}
