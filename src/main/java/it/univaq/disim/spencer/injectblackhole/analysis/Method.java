package it.univaq.disim.spencer.injectblackhole.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public class Method {

    private CtMethod<?> method;
    private String methodName;
    private String fqClassName;
    private Path classFile;
    private CodeBase codeBase;

    public Method(CtMethod<?> method, CodeBase codeBase) {
        this.method = method;
        this.codeBase = codeBase;
        this.methodName = method.getSimpleName();
        this.fqClassName = method.getDeclaringType().getQualifiedName();
        this.classFile = method.getPosition().getFile().toPath();
    }

    public CtMethod<?> getMethod() {
        return method;
    }

    public CodeBase getCodeBase() {
        return codeBase;
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

    /**
     * Get statements in the method body, including those inside blocks (like try/catch, loops).
     * Avoid argument expressions.
     * @return List of statements
     */
    public List<CtStatement> getTopLevelStatements() {
        List<CtStatement> topLevelStatements = new ArrayList<>();

        // Get direct statements in the method body
        if (method.getBody() != null) {
            for (CtStatement stmt : method.getBody().getElements(new TypeFilter<>(CtStatement.class))) {
                String thisStmt = stmt.toString();
                String parentStmt = stmt.getParent().toString();

                // Skip statements inside catch blocks
                if (stmt.getParent(CtCatch.class) != null) {
                    continue;
                }

                // Skip statements at the same line as their parent
                if (stmt.getPosition().getLine() == stmt.getParent().getPosition().getLine()) {
                    continue;
                }

                // Skip blocks starting and ending with a brace, because we will get individual statements inside them
                if (thisStmt.startsWith("{") && thisStmt.endsWith("}")) {
                    continue;
                }

                // Skip argument expressions (check if inside parentheses)
                Pattern argExpr = Pattern.compile("\\([^(]*" + Pattern.quote(thisStmt) + "[^)]*\\)");
                Matcher argExprMatcher = argExpr.matcher(parentStmt);
                if (argExprMatcher.find()) {
                    continue;
                }

                topLevelStatements.add(stmt);
            }
        }
        return topLevelStatements;
    }
}
