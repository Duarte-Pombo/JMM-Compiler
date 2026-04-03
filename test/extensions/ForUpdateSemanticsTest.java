package extensions;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;

public class ForUpdateSemanticsTest extends JmmTestEnv {

    public ForUpdateSemanticsTest() {
        super("", "");
    }

    @Test
    public void forUpdateWithUndeclaredVariableShouldFail() {
        var semantics = semanticsFromSnippet("""
                package x;
                class Test {
                    int method() {
                        int i;
                        i = 0;
                        for (; i < 10; i = a + 1) {
                        }
                        return i;
                    }
                }""", true);

        var errors = semantics.getReports(ReportType.ERROR);
        assertTrue("Expected undeclared variable error in for update",
                errors.stream().anyMatch(report -> report.getMessage().contains("Variable 'a' does not exist.")));
    }

    @Test
    public void forUpdateWithNonIntExpressionShouldFail() {
        var semantics = semanticsFromSnippet("""
                package x;
                class Test {
                    int method() {
                        int i;
                        boolean a;
                        i = 0;
                        a = true;
                        for (; i < 10; i = a + 1) {
                        }
                        return i;
                    }
                }""", true);

        var errors = semantics.getReports(ReportType.ERROR);
        assertTrue("Expected invalid binary expression error in for update",
                errors.stream().anyMatch(report -> report.getMessage().contains("Invalid binary expression")));
    }
}
