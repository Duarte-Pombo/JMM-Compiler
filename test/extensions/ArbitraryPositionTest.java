package extensions;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import static org.junit.Assert.*;

public class ArbitraryPositionTest extends JmmTestEnv {

    public ArbitraryPositionTest() {
        super("", "");
    }

    /**
     * A field declared AFTER a method must appear in the symbol table.
     */
    @Test
    public void fieldAfterMethodInSymbolTable() {
        var st = buildSymbolTable("""
                package x;
                class A {
                    public void method() {}
                    int field;
                }""");

        var fields = st.getFields();
        assertEquals("Expected 1 field", 1, fields.size());
        assertEquals("Field name mismatch", "field", fields.get(0).name());

        var methods = st.getMethods("method");
        assertEquals("Expected 1 method", 1, methods.size());
    }

    /**
     * Two fields interleaved between two methods must all appear in the
     * symbol table in declaration order.
     */
    @Test
    public void fieldsAndMethodsInterleavedInSymbolTable() {
        var st = buildSymbolTable("""
                package x;
                class A {
                    public void method1() {}
                    int field1;
                    boolean field2;
                    public void method2() {}
                }""");

        var fields = st.getFields();
        assertEquals("Expected 2 fields", 2, fields.size());
        assertEquals("First field name", "field1", fields.get(0).name());
        assertEquals("Second field name", "field2", fields.get(1).name());

        var methods = st.getMethods();
        // filter out any implicitly generated methods
        var named = methods.stream()
                .filter(m -> m.name().equals("method1") || m.name().equals("method2"))
                .toList();
        assertEquals("Expected 2 methods", 2, named.size());
    }

    /**
     * Methods declared first, then all fields — fields must still appear.
     */
    @Test
    public void allMethodsBeforeAllFieldsInSymbolTable() {
        var st = buildSymbolTable("""
                package x;
                class B {
                    public int compute() { return field1 + field2; }
                    public boolean flag()  { return field3; }
                    int field1;
                    int field2;
                    boolean field3;
                }""");

        var fields = st.getFields();
        assertEquals("Expected 3 fields", 3, fields.size());
        assertEquals("First field name mismatch", "field1", fields.get(0).name());
        assertEquals("Second field name mismatch", "field2", fields.get(1).name());
        assertEquals("Third field name mismatch", "field3", fields.get(2).name());
    }

    /**
     * All fields before all methods — the baseline case must remain unbroken.
     */
    @Test
    public void allFieldsBeforeAllMethodsInSymbolTable() {
        var st = buildSymbolTable("""
                package x;
                class C {
                    int x;
                    int y;
                    public int sum() { return x + y; }
                    public int diff() { return x - y; }
                }""");

        var fields = st.getFields();
        assertEquals("Expected 2 fields", 2, fields.size());

        var methods = st.getMethods().stream()
                .filter(m -> m.name().equals("sum") || m.name().equals("diff"))
                .toList();
        assertEquals("Expected 2 methods", 2, methods.size());
    }

    /**
     * A method declared BEFORE its field must be able to read that field
     * with zero semantic errors.
     */
    @Test
    public void methodBeforeFieldAccessShouldPass() {
        var result = analyze("""
                package x;
                class A {
                    public int m() {
                        return field;
                    }
                    int field;
                }""");

        assertEquals("Expected 0 errors", 0, result.getReports(ReportType.ERROR).size());
    }

    /**
     * Multiple methods referencing fields that are all declared AFTER them.
     */
    @Test
    public void multipleMethodsBeforeFieldsShouldPass() {
        var result = analyze("""
                package x;
                class A {
                    public int sum()  { return x + y; }
                    public int diff() { return x - y; }
                    int x;
                    int y;
                }""");

        assertEquals("Expected 0 errors", 0, result.getReports(ReportType.ERROR).size());
    }

    /**
     * Interleaved: method, field, method, field — each method reads the
     * field declared after itself.
     */
    @Test
    public void interleavedMethodsAndFieldsShouldPass() {
        var result = analyze("""
                package x;
                class A {
                    public boolean getFlag() { return flag; }
                    boolean flag;
                    public int getVal()  { return val; }
                    int val;
                }""");

        assertEquals("Expected 0 errors", 0, result.getReports(ReportType.ERROR).size());
    }

    /**
     * A method declared AFTER its field must still work (regression guard).
     */
    @Test
    public void methodAfterFieldAccessShouldPass() {
        var result = analyze("""
                package x;
                class A {
                    int field;
                    public int m() { return field; }
                }""");

        assertEquals("Expected 0 errors", 0, result.getReports(ReportType.ERROR).size());
    }

    /**
     * Accessing a field that genuinely does not exist must still produce an
     * error (i.e. we haven't accidentally suppressed identifier checks).
     */
    @Test
    public void accessingUndeclaredFieldShouldFail() {
        var result = analyze("""
                package x;
                class A {
                    public int m() { return ghost; }
                    int field;
                }""");

        assertFalse("Expected at least 1 error for undeclared variable",
                result.getReports(ReportType.ERROR).isEmpty());
    }

    /**
     * Field declared between two methods; second method must see the field.
     */
    @Test
    public void fieldBetweenMethodsShouldBeVisibleToAll() {
        var result = analyze("""
                package x;
                class A {
                    public int before() { return shared; }
                    int shared;
                    public int after()  { return shared; }
                }""");

        assertEquals("Expected 0 errors", 0, result.getReports(ReportType.ERROR).size());
    }

    /**
     * Assignment to a field declared after the method must pass.
     */
    @Test
    public void assignToFieldDeclaredAfterMethodShouldPass() {
        var result = analyze("""
                package x;
                class A {
                    public void setter() {
                        int tmp;
                        tmp = 5;
                        value = tmp;
                    }
                    int value;
                }""");

        assertEquals("Expected 0 errors", 0, result.getReports(ReportType.ERROR).size());
    }

    private SymbolTable buildSymbolTable(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        return analysis.buildSymbolTable(parserResult).getSymbolTable();
    }

    private JmmSemanticsResult analyze(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var stResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(stResult);
    }
}
