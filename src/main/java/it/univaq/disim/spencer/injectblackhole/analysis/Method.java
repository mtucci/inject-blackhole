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
    private String className;
    private String packageName;
    private Path classFile;
    private CodeBase codeBase;

    public Method(String fqMethodName) {
        fqMethodNameToFields(fqMethodName);
    }

    public Method(CtMethod<?> method) {
        this.method = method;
        this.methodName = method.getSignature();
        this.classFile = method.getPosition().getFile().toPath();
        fqClassNameToFields(method.getDeclaringType().getQualifiedName());
    }

    public CtMethod<?> getMethod() {
        return method;
    }

    public CodeBase getCodeBase() {
        return codeBase;
    }

    public void setCodeBase(CodeBase codeBase) {
        this.codeBase = codeBase;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFQClassName() {
        return packageName + "." + className;
    }

    public Path getClassFile() {
        return classFile;
    }

    public void setClassFile(Path classFile) {
        this.classFile = classFile;
    }

    public String getFQMethodName() {
        return getFQClassName() + "." + methodName;
    }

    public String getPackageName() {
        return packageName;
    }

    // TODO match the method separately and re-use fqClassNameToFields
    private void fqMethodNameToFields(String fqMethodName) {
        Pattern pattern = Pattern.compile("^((?:[a-z0-9_]+\\.?)+)\\.([A-Z][^\\.]+)\\.(.+)$");
        Matcher matcher = pattern.matcher(fqMethodName);
        if (matcher.find()) {
            this.packageName = matcher.group(1);
            this.className = matcher.group(2);
            this.methodName = matcher.group(3);
        }
    }

    private void fqClassNameToFields(String fqClassName) {
        Pattern pattern = Pattern.compile("^((?:[a-z0-9_]+\\.?)+)\\.([A-Z][^\\.]+)");
        Matcher matcher = pattern.matcher(fqClassName);
        if (matcher.find()) {
            this.packageName = matcher.group(1);
            this.className = matcher.group(2);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Method)) {
            return false;
        }
        Method other = (Method) obj;
        // TODO change the comparison to signature and class
        //return this.getFQMethodName().equals(other.getFQMethodName()) &&
        //       this.getClassFile().equals(other.getClassFile());
        return this.packageName.equals(other.packageName) &&
               this.className.equals(other.className) &&
               this.methodName.equals(other.methodName);
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
