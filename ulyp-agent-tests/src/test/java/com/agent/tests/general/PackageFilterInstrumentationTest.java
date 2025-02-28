package com.agent.tests.general;

import com.agent.tests.general.a.A;
import com.agent.tests.general.a.c.C;
import com.agent.tests.util.AbstractInstrumentationTest;
import com.agent.tests.util.ForkProcessBuilder;
import com.ulyp.storage.tree.CallRecord;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PackageFilterInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void shouldInstrumentAndTraceAllClasses() {
        CallRecord root = runSubprocessAndReadFile(
                new ForkProcessBuilder()
                        .withMain(A.class)
                        .withInstrumentedPackages("com.agent.tests.general.a")
        );

        assertThat(root.getMethod().getName(), is("main"));
        assertThat(root.getMethod().getType().getName(), is(A.class.getName()));
        assertThat(root.getChildren(), Matchers.hasSize(2));
    }

    @Test
    void shouldExcludeInstrumentationPackage() {
        CallRecord root = runSubprocessAndReadFile(
                new ForkProcessBuilder()
                        .withMain(A.class)
                        .withInstrumentedPackages("com.agent.tests.general.a")
                        .withExcludedFromInstrumentationPackages("com.agent.tests.general.a.b")
        );

        assertThat(root.getMethod().getName(), is("main"));
        assertThat(root.getMethod().getType().getName(), is("com.agent.tests.general.a.A"));
        assertThat(root.getChildren(), Matchers.hasSize(1));

        CallRecord callRecord = root.getChildren().get(0);

        assertThat(callRecord.getMethod().getType().getName(), is(C.class.getName()));
        assertThat(callRecord.getMethod().getName(), is("c"));
    }

    @Test
    void shouldExcludeTwoPackages() {
        CallRecord root = runSubprocessAndReadFile(
                new ForkProcessBuilder()
                        .withMain(A.class)
                        .withInstrumentedPackages("com.agent.tests.general.a")
                        .withExcludedFromInstrumentationPackages("com.agent.tests.general.a.b", "com.agent.tests.general.a.c")
                        .withLogLevel("INFO")
        );

        assertThat(root.getMethod().getName(), is("main"));
        assertThat(root.getMethod().getType().getName(), is("com.agent.tests.general.a.A"));
        assertThat(root.getChildren(), Matchers.empty());
    }
}
