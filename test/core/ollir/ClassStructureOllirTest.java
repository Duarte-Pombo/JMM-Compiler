package core.ollir;

import org.junit.Test;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.type.BuiltinKind;
import org.specs.comp.ollir.type.BuiltinType;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class ClassStructureOllirTest extends JmmTestEnv {

    public ClassStructureOllirTest() {
        super("", "");
    }

    @Test
    public void defaultConstructorShouldExist() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                }""");

        assertTrue("Default constructor must exist", classUnit.getMethods().stream().anyMatch(Method::isConstructMethod));
    }

    @Test
    public void classWithSuperShouldKeepSuperClass() {
        var classUnit = toOllirClass("""
                package x;
                import examples.Quicksort;
                class A extends Quicksort {
                }""");

        assertEquals("Super class name should be Quicksort", "Quicksort", classUnit.getSuperClass());
    }

    @Test
    public void classWithFieldsShouldGenerateFieldDeclarations() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    int intField;
                    boolean boolField;
                }""");

        assertEquals("Class should have two fields", 2, classUnit.getNumFields());
        var intField = classUnit.getFields().stream().filter(f -> f.getFieldName().equals("intField")).findFirst();
        assertTrue("Class should contain field intField", intField.isPresent());
        assertTrue("intField should be int", BuiltinType.is(intField.orElseThrow().getFieldType(), BuiltinKind.INT32));

        var boolField = classUnit.getFields().stream().filter(f -> f.getFieldName().equals("boolField")).findFirst();
        assertTrue("Class should contain field boolField", boolField.isPresent());
        assertTrue("boolField should be boolean", BuiltinType.is(boolField.orElseThrow().getFieldType(), BuiltinKind.BOOLEAN));
    }

    @Test
    public void classWithImportsShouldKeepImportsInOllir() {
        var classUnit = toOllirClass("""
                package x;
                import util.io;
                import examples.Quicksort;
                class A {
                }""");

        assertEquals("Class should have two imports", 2, classUnit.getImports().size());
    }

    private ClassUnit toOllirClass(String code) {
        var parserResult = parseSnippet(code);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        var classUnit = ollirResult.getOllirClass();
        assertNotNull("Ollir class unit should not be null", classUnit);
        return classUnit;
    }
}
