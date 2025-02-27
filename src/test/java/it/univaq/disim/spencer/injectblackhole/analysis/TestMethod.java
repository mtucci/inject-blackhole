package it.univaq.disim.spencer.injectblackhole.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import spoon.reflect.declaration.CtMethod;

public class TestMethod {

    private static final String TEST_CLASS = "/TestClass.java";
    private static final String TEST_METHOD = "nestedStatements";
    private static Method method;

    @BeforeAll
    static void setup() throws IOException {
        Path classFile = Paths.get(TestMethod.class.getResource(TEST_CLASS).getPath());
        CodeBase codeBase = new CodeBase(classFile);
        codeBase.load();
        for (CtMethod<?> m : codeBase.getMethods()) {
            if (m.getSimpleName().equals(TEST_METHOD)) {
                method = new Method(m, codeBase);
                break;
            }
        }
    }

    @Test
    public static void testGetTopLevelStatements() {
        int expected = 12;
        int actual = method.getTopLevelStatements().size();
        assert expected == actual : "Expected " + expected + " statements, but got " + actual;
    }
}
