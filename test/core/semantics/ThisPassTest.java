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
