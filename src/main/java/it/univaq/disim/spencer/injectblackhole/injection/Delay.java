package it.univaq.disim.spencer.injectblackhole.injection;

import org.openjdk.jmh.infra.Blackhole;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;

public class Delay {

    private static final String BLACKHOLE_CONSUME_SIGNATURE = "void org.openjdk.jmh.infra.Blackhole#consumeCPU(long)";
    private long delay;

    public Delay(long delay) {
        this.delay = delay;
    }

    /**
     * Create the "Blackhole.consumeCPU(delay)" invocation
     * @param factory Spoon factory
     * @return the invocation
     */
    public CtInvocation<Object> createBlackholeConsume(Factory factory) {
        CtTypeAccess<Blackhole> typeAccess = factory.Code()
                .createTypeAccess(factory.Type().createReference(Blackhole.class));
    
        return factory.Code().createInvocation(
                typeAccess,
                factory.Executable().createReference(BLACKHOLE_CONSUME_SIGNATURE),
                factory.Code().createLiteral(delay)
        );
    }

    /**
     * Inject an invocation at the beginning of a method
     * @param method the method to inject the invocation
     * @param invocation the invocation to inject
     */
    public void injectAtBegin(CtMethod<?> method, CtInvocation<Object> invocation) {
        method.getBody().insertBegin(invocation);
    }

    /**
     * Inject an invocation before a statement in a method
     * @param statement the statement that will follow our invocation
     * @param invocation the invocation to inject
     */
    public void injectBeforeStatement(CtStatement statement, CtInvocation<Object> invocation) {
        statement.insertBefore(invocation);
    }
}
