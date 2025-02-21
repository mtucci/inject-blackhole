package it.univaq.disim.spencer.injectblackhole.analysis;

import java.nio.file.Path;

import spoon.reflect.declaration.CtMethod;

public class Method {

    private CtMethod<?> method;
    private String methodName;
    private String fqClassName;
    private Path classFile;

    public Method(CtMethod<?> method) {
        this.method = method;
        this.methodName = method.getSimpleName();
        this.fqClassName = method.getDeclaringType().getQualifiedName();
        this.classFile = method.getPosition().getFile().toPath();
    }

    public CtMethod<?> getMethod() {
        return method;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFQClassName() {
        return fqClassName;
    }

    public Path getClassFile() {
        return classFile;
    }

    public String getFQMethodName() {
        return fqClassName + "#" + methodName;
    }

    @Override
    public boolean equals(Object obj) {
        return fqClassName.equals(((Method) obj).getFQClassName());
    }
}
