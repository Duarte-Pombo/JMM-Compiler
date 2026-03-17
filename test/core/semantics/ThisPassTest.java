package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;

public class ThisPassTest extends JmmTestEnv {

    public ThisPassTest() {
        super("", "");
    }

    @Test
    public void thisInStaticMethodFails() {
        var semantics = semanticsFromSnippet("""
                package x;
                class A {
                    public static int m() {
                        this;
                        return 0;
                    }
                }""", true);

        assertTrue("Expected semantic error for 'this' in static method",
                semantics.getReports(ReportType.ERROR).stream()
                        .anyMatch(report -> report.getMessage().contains("'this' cannot be used in a static function")));
    }

    @Test
    public void thisInInstanceMethodIsAccepted() {
        semanticsFromSnippet("""
                package x;
                class A {
                    public int m() {
                        this;
                        return 0;
                    }
                }""", false);
    }

    @Test
    public void thisAssignedToCurrentClassIsAccepted() {
        semanticsFromSnippet("""
                package x;
                class A {
                    public A m() {
                        A a;
                        a = this;
                        return a;
                    }
                }""", false);
    }

    @Test
    public void thisAssignedToSuperclassIsAccepted() {
        semanticsFromSnippet("""
                package x;
                class A extends Object {
                    public Object m() {
                        Object a;
                        a = this;
                        return this;
                    }
                }""", false);
    }

    @Test
    public void thisAssignedToIncompatibleObjectFails() {
        var semantics = semanticsFromSnippet("""
                package x;
                import util.io;
                class A {
                    public A m() {
                        io a;
                        a = this;
                        return this;
                    }
                }""", true);

        assertTrue("Expected semantic error for incompatible object assignment with 'this'",
                semantics.getReports(ReportType.ERROR).stream()
                        .anyMatch(report -> report.getMessage().contains("'this' is not compatible with type 'util.io'")));
    }

    @Test
    public void staticMethodWithoutThisIsAccepted() {
        semanticsFromSnippet("""
                package x;
                class A {
                    public static int m() {
                        return 0;
                    }
                }""", false);
    }
}
