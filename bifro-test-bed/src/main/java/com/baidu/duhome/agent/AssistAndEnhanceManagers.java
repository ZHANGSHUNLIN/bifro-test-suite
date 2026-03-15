package com.baidu.duhome.agent;

import io.netty.bootstrap.Bootstrap;
import io.vertx.core.net.ClientOptionsBase;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

@Slf4j
public class AssistAndEnhanceManagers {


    public static void doEnhance(){
        Instrumentation inst = ByteBuddyAgent.install();
        Class<?> targetClass;
        try {
            targetClass = Class.forName("io.vertx.core.spi.transport.Transport");
            byte[] enhancedBytes = new ByteBuddy()
                    .redefine(targetClass)
                    .method(ElementMatchers.named("configure")
                            .and(ElementMatchers.takesArguments(
                                    ClientOptionsBase.class,
                                    boolean.class,
                                    Bootstrap.class
                            ))).intercept(MethodDelegation.to(TransportMonitor.class))
                    .make()
                    .getBytes();
            inst.redefineClasses(new ClassDefinition(targetClass, enhancedBytes));
        } catch (ClassNotFoundException | UnmodifiableClassException e) {
            throw new RuntimeException(e);
        }


    }


}
