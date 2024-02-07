package com.perf.agent.benchmarks.libs;

import com.perf.agent.benchmarks.RecordingBenchmark;
import com.perf.agent.benchmarks.libs.util.ApplicationConfiguration;
import com.perf.agent.benchmarks.libs.util.Department;
import com.perf.agent.benchmarks.libs.util.DepartmentService;
import com.perf.agent.benchmarks.libs.util.Person;
import com.perf.agent.benchmarks.util.BenchmarkConstants;
import org.openjdk.jmh.annotations.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 10, time = 3)
public class SpringHibernateBenchmark extends RecordingBenchmark {

    private static final int PEOPLE_PER_DEPT = Integer.parseInt(System.getProperty("peoplePerDeptCount", "30"));
    private static final int DEPT_COUNT = Integer.parseInt(System.getProperty("deptCount", "20"));
    private DepartmentService departmentService;

    @Setup(Level.Trial)
    public void setup() {
        ApplicationContext context = new AnnotationConfigApplicationContext(ApplicationConfiguration.class);
        departmentService = context.getBean(DepartmentService.class);

        for (int i = 0; i < DEPT_COUNT; i++) {
            Department department = new Department();

            for (int z = 0; z < PEOPLE_PER_DEPT; z++) {
                Person person = new Person();
                person.setFirstName("Jeff_" + i + "_" + z);
                person.setLastName("");
                person.setPhoneNumber("+15324234234");
                person.setAge(ThreadLocalRandom.current().nextInt(50));
                department.addPerson(person);
            }

            departmentService.save(department);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        int peopleCount = departmentService.countPeople();
        if (peopleCount != DEPT_COUNT * PEOPLE_PER_DEPT) {
            throw new RuntimeException("People " + peopleCount);
        }

        departmentService.removeAll();
    }

    @Fork(value = 2)
    @Benchmark
    public void shufflePeopleBaseline() {
        departmentService.shufflePeople();
    }

    @Fork(jvmArgs = {
            BenchmarkConstants.AGENT_PROP,
            "-Dulyp.file=/tmp/test.dat",
            "-Dulyp.methods=**.SpringHibernateBenchmark.asdasd",
            "-Dcom.ulyp.slf4j.simpleLogger.defaultLogLevel=OFF",
            "-Dulyp.constructors"
    }, value = 2)
    @Benchmark
    public void shufflePeopleInstrumented() {
        departmentService.shufflePeople();
    }

    @Fork(jvmArgs = {
            BenchmarkConstants.AGENT_PROP,
            "-Dulyp.file=/tmp/test.dat",
            "-Dulyp.methods=**.DepartmentService.shufflePeople",
            "-Dcom.ulyp.slf4j.simpleLogger.defaultLogLevel=OFF",
            "-Dulyp.constructors"
    }, value = 2)
    @Benchmark
    public void shufflePeopleRecord() {
        departmentService.shufflePeople();
    }

    @Fork(jvmArgs = {
        BenchmarkConstants.AGENT_PROP,
        "-Dulyp.file=/tmp/test.dat",
        "-Dulyp.methods=**.DepartmentService.shufflePeople",
        "-Dcom.ulyp.slf4j.simpleLogger.defaultLogLevel=OFF",
        "-Dulyp.constructors"
    }, value = 2)
    @Benchmark
    public void shufflePeopleRecordSync(Counters counters) {
        execRecordAndSync(counters, () -> departmentService.shufflePeople());
    }
}
