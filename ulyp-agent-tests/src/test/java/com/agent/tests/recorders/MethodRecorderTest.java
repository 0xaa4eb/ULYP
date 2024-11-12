package com.agent.tests.recorders;

import com.agent.tests.util.AbstractInstrumentationTest;
import com.agent.tests.util.ForkProcessBuilder;
import com.ulyp.core.Type;
import com.ulyp.core.recorders.MethodRecord;
import com.ulyp.storage.tree.CallRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodRecorderTest extends AbstractInstrumentationTest {

    @Test
    void testClassTypeReturning() {

        CallRecord root = runSubprocessAndReadFile(
                new ForkProcessBuilder()
                        .withMainClassName(PassClazz.class)
                        .withMethodToRecord("returnMethod")
        );

        MethodRecord methodRecord = (MethodRecord) root.getReturnValue();

        assertEquals("foo", methodRecord.getName());
        Type declaringType = methodRecord.getDeclaringType();
        assertEquals(X.class.getName(), declaringType.getName());
    }

    static class X {

        public void foo() {
        }
    }

    static class PassClazz {

        public static Method returnMethod() {
            try {
                return X.class.getDeclaredMethod("foo");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public static void main(String[] args) {
            System.out.println(returnMethod());
        }
    }
}
