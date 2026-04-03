package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;

import java.util.List;

public class AssignableTypesTest extends JmmTestEnv {

    public AssignableTypesTest() {
        super("", "");
    }

    @Test
    public void importedSubclassAssignmentShouldPass() {
        var semantics = semanticsFromSnippet("""
                package x;
                class A {
                    public int m() {
                        Exception e;
                        e = new NullPointerException();
                        return 0;
                    }
                }""", false);

        assertEquals("Expected no assignment errors", 0, assignmentErrors(semantics).size());
    }

    @Test
    public void importedInterfaceAssignmentShouldPass() {
        var semantics = semanticsFromSnippet("""
                package x;
                import java.util.List;
                import java.util.ArrayList;
                class A {
                    public int m() {
                        List list;
                        ArrayList arrayList;
                        list = arrayList;
                        return 0;
                    }
                }""", false);

        assertEquals("Expected no assignment errors when assigning ArrayList to List", 0, assignmentErrors(semantics).size());
    }

    @Test
    public void importedInterfaceAssignmentWrongDirectionShouldFail() {
        var semantics = semanticsFromSnippet("""
                package x;
                import java.util.List;
                import java.util.ArrayList;
                class A {
                    public int m() {
                        List list;
                        ArrayList arrayList;
                        arrayList = list;
                        return 0;
                    }
                }""", true);

        assertEquals("Expected one assignment error when assigning List to ArrayList", 1, assignmentErrors(semantics).size());
    }

    @Test
    public void importedInterfaceArgumentShouldPass() {
        var semantics = semanticsFromSnippet("""
                package x;
                import java.util.List;
                import java.util.ArrayList;
                class A {
                    public void use(List list) {
                    }

                    public int m() {
                        ArrayList arrayList;
                        use(arrayList);
                        return 0;
                    }
                }""", false);

        assertEquals("Expected no semantic errors when passing ArrayList to a List parameter",
                0, semantics.getReports(ReportType.ERROR).size());
    }

    @Test
    public void importedInterfaceArgumentShouldFail() {
        var semantics = semanticsFromSnippet("""
                package x;
                import java.util.List;
                import java.util.ArrayList;
                class A {
                    public void use(ArrayList arrayList) {
                    }

                    public int m() {
                        List list;
                        use(list);
                        return 0;
                    }
                }""", true);

        assertTrue("Expected a semantic error when passing List to an ArrayList parameter",
                semantics.getReports(ReportType.ERROR).stream()
                        .anyMatch(report -> report.getMessage().contains("Method call failed")));
    }

    private List<Report> assignmentErrors(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR).stream()
                .filter(r -> r.getMessage().contains("Cannot assign expression of type"))
                .toList();
    }
}
