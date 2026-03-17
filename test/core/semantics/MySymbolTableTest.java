// java
package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;
import pt.up.fe.comp2026.symboltable.JmmSymbolTableBuilder;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import java.util.List;

public class MySymbolTableTest extends JmmTestEnv {
    private static final String BASE_PATH = "core/semantics/symboltable/";
    private static final String RESOURCES_LOCATION = "test";
    private static final String GENERAL_SYMBOL_TABLE_SNIPPET = """
            package x;
            import util.io;
            class FullSymbolTable {
                int fieldInt;
                boolean fieldBool;
                io fieldIo;
                int[] fieldArr;

                public int m1(int a, boolean b, io c) {
                    int x;
                    boolean y;
                    io z;
                    return 0;
                }

                public io m2() {
                    io localIo;
                    return localIo;
                }

                public static void main(String[] args) {
                    int n;
                }
            }""";

    public MySymbolTableTest() {
        super(BASE_PATH, RESOURCES_LOCATION);
    }

    @Test
    public void testFirstExample() {
        var st = symbolTable("Factorial.jmm", false).getSymbolTable();
        assertEquals("Expected class name to be Factorial", "Factorial", st.getClassName());
        var mainMethodsList = st.getMethods("main");
        assertTrue("Expected to encounter main method", !mainMethodsList.isEmpty());
    }

    @Test
    public void testClassDeclarationSymbolTable() {
        var st = symbolTable("Factorial.jmm", false).getSymbolTable();

        assertEquals("Package name should be ${expected}", resourcesPackage(), st.packageName());
        assertEquals("Class fully qualified name should be ${expected}", qualifiedNameFor("Factorial"), st.getFullyQualifiedName());
        assertEquals("Class super should default to ${expected}", "Object", st.getSuperFullyQualifiedName());
    }

    @Test
    public void testMethodDeclarationsAreCollected() {
        var st = symbolTable("Factorial.jmm", false).getSymbolTable();

        assertEquals("Expected to have ${expected} methods", 2, st.getMethods().size());
        assertEquals("computeFactorial should appear once", 1, st.getMethods("computeFactorial").size());
        assertEquals("main should appear once", 1, st.getMethods("main").size());
    }

    @Test
    public void testComputeFactorialMethodDeclaration() {
        var st = symbolTable("Factorial.jmm", false).getSymbolTable();
        var computeOpt = st.getMethod(Signature.of("computeFactorial", List.of(JmmPrimitiveType.INT)));

        assertTrue("computeFactorial(int) should exist", computeOpt.isPresent());
        var compute = computeOpt.orElseThrow();
        assertEquals("computeFactorial return type should be ${expected}", JmmPrimitiveType.INT, compute.returnType());
        assertEquals("computeFactorial should have one parameter", 1, compute.parameters().size());
        assertEquals("computeFactorial parameter should be named ${expected}", "num", compute.parameters().getFirst().name());
        assertTrue("computeFactorial should contain local variable num_aux", compute.getLocalVariable("num_aux").isPresent());
    }

    @Test
    public void testMainMethodDeclaration() {
        var st = symbolTable("Factorial.jmm", false).getSymbolTable();
        var mainMethods = st.getMethods("main");

        assertEquals("Expected exactly one main method", 1, mainMethods.size());
        var main = mainMethods.getFirst();
        assertEquals("main return type should be ${expected}", JmmPrimitiveType.VOID, main.returnType());
        assertEquals("main should have one parameter", 1, main.parameters().size());
        assertEquals("main parameter should be named ${expected}", "args", main.parameters().getFirst().name());
        assertTrue("main parameter type should be an array", main.parameters().getFirst().type().isArray());
    }

    @Test
    public void testLocalVariablesAreCollectedInComputeFactorial() {
        var st = symbolTable("Factorial.jmm", false).getSymbolTable();
        var computeOpt = st.getMethod(Signature.of("computeFactorial", List.of(JmmPrimitiveType.INT)));

        assertTrue("computeFactorial(int) should exist", computeOpt.isPresent());
        var compute = computeOpt.orElseThrow();
        assertEquals("computeFactorial should have one local variable", 1, compute.localVariables().size());
        assertTrue("computeFactorial should contain local variable num_aux", compute.getLocalVariable("num_aux").isPresent());
        assertEquals("num_aux should have type ${expected}",
                JmmPrimitiveType.INT, compute.getLocalVariable("num_aux").orElseThrow().type());
    }

    @Test
    public void testMethodWithoutLocalsHasEmptyLocalVariables() {
        var st = symbolTable("Factorial.jmm", false).getSymbolTable();
        var mainMethods = st.getMethods("main");

        assertEquals("Expected exactly one main method", 1, mainMethods.size());
        var main = mainMethods.getFirst();
        assertEquals("main should have no local variables", 0, main.localVariables().size());
    }

    @Test
    public void testLocalVariablesSupportDifferentTypes() {
        var st = symbolTable("LocalVarsTypes.jmm", false).getSymbolTable();
        var methodOpt = st.getMethod(Signature.of("foo", List.of()));

        assertTrue("foo() should exist", methodOpt.isPresent());
        var foo = methodOpt.orElseThrow();
        assertEquals("foo should have four local variables", 4, foo.localVariables().size());
        assertEquals("localInt should have type ${expected}",
                JmmPrimitiveType.INT, foo.getLocalVariable("localInt").orElseThrow().type());
        assertEquals("localBool should have type ${expected}",
                JmmPrimitiveType.BOOLEAN, foo.getLocalVariable("localBool").orElseThrow().type());
        assertTrue("localObj should be a class type", foo.getLocalVariable("localObj").orElseThrow().type().isClass());
        assertTrue("localArray should be an array type", foo.getLocalVariable("localArray").orElseThrow().type().isArray());
    }

    @Test
    public void testBuildParamsCollectsMethodParameters() {
        var st = (JmmSymbolTable) symbolTable("Factorial.jmm", false).getSymbolTable();

        var computeParamsOpt = st.getParameters("computeFactorial");
        assertTrue("computeFactorial entry should exist in params map", computeParamsOpt.isPresent());
        var computeParams = computeParamsOpt.orElseThrow();
        assertEquals("computeFactorial should have one parameter", 1, computeParams.size());
        assertEquals("computeFactorial parameter name should be ${expected}", "num", computeParams.getFirst().name());
        assertEquals("computeFactorial parameter type should be ${expected}",
                JmmPrimitiveType.INT, computeParams.getFirst().type());

        var mainParamsOpt = st.getParameters("main");
        assertTrue("main entry should exist in params map", mainParamsOpt.isPresent());
        var mainParams = mainParamsOpt.orElseThrow();
        assertEquals("main should have one parameter", 1, mainParams.size());
        assertEquals("main parameter name should be ${expected}", "args", mainParams.getFirst().name());
        assertTrue("main parameter should be an array type", mainParams.getFirst().type().isArray());
    }

    @Test
    public void testBuildParamsIncludesEmptyListAndMissingMethodLookup() {
        var st = (JmmSymbolTable) symbolTable("LocalVarsTypes.jmm", false).getSymbolTable();

        var fooParamsOpt = st.getParameters("foo");
        assertTrue("foo entry should exist in params map", fooParamsOpt.isPresent());
        assertEquals("foo() should have no parameters", 0, fooParamsOpt.orElseThrow().size());

        assertTrue("Missing methods should not exist in params map", st.getParameters("doesNotExist").isEmpty());
    }

    @Test
    public void testBuildParamsCollectsTwoArgumentsInOrder() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int pair(int left, int right) { return 0; }
                }""", false);
        var st = (JmmSymbolTable) semantics.getSymbolTable();

        var pairParamsOpt = st.getParameters("pair");
        assertTrue("pair entry should exist in params map", pairParamsOpt.isPresent());
        var pairParams = pairParamsOpt.orElseThrow();
        assertEquals("pair should have two parameters", 2, pairParams.size());
        assertEquals("First parameter name should be ${expected}", "left", pairParams.get(0).name());
        assertEquals("Second parameter name should be ${expected}", "right", pairParams.get(1).name());
        assertEquals("First parameter type should be ${expected}", JmmPrimitiveType.INT, pairParams.get(0).type());
        assertEquals("Second parameter type should be ${expected}", JmmPrimitiveType.INT, pairParams.get(1).type());
    }

    @Test
    public void testBuildParamsCollectsThreeArgumentsWithDifferentTypes() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int many(int a, boolean b, A c) { return 0; }
                }""", false);
        var st = (JmmSymbolTable) semantics.getSymbolTable();

        var manyParamsOpt = st.getParameters("many");
        assertTrue("many entry should exist in params map", manyParamsOpt.isPresent());
        var manyParams = manyParamsOpt.orElseThrow();
        assertEquals("many should have three parameters", 3, manyParams.size());
        assertEquals("First parameter name should be ${expected}", "a", manyParams.get(0).name());
        assertEquals("Second parameter name should be ${expected}", "b", manyParams.get(1).name());
        assertEquals("Third parameter name should be ${expected}", "c", manyParams.get(2).name());
        assertEquals("First parameter type should be ${expected}", JmmPrimitiveType.INT, manyParams.get(0).type());
        assertEquals("Second parameter type should be ${expected}", JmmPrimitiveType.BOOLEAN, manyParams.get(1).type());
        assertTrue("Third parameter should be a class type", manyParams.get(2).type().isClass());
    }

    @Test
    public void testGetLocalVariablesCollectsMethodLocals() {
        var st = (JmmSymbolTable) symbolTable("Factorial.jmm", false).getSymbolTable();

        var computeLocalsOpt = st.getLocalVariables("computeFactorial");
        assertTrue("computeFactorial entry should exist in locals map", computeLocalsOpt.isPresent());
        var computeLocals = computeLocalsOpt.orElseThrow();
        assertEquals("computeFactorial should have one local variable", 1, computeLocals.size());
        assertEquals("Local variable name should be ${expected}", "num_aux", computeLocals.getFirst().name());
        assertEquals("Local variable type should be ${expected}", JmmPrimitiveType.INT, computeLocals.getFirst().type());

        var mainLocalsOpt = st.getLocalVariables("main");
        assertTrue("main entry should exist in locals map", mainLocalsOpt.isPresent());
        assertEquals("main should have no local variables", 0, mainLocalsOpt.orElseThrow().size());
    }

    @Test
    public void testGetLocalVariablesSupportsDifferentTypesAndMissingMethod() {
        var st = (JmmSymbolTable) symbolTable("LocalVarsTypes.jmm", false).getSymbolTable();

        var fooLocalsOpt = st.getLocalVariables("foo");
        assertTrue("foo entry should exist in locals map", fooLocalsOpt.isPresent());
        var fooLocals = fooLocalsOpt.orElseThrow();
        assertEquals("foo should have four local variables", 4, fooLocals.size());
        assertEquals("First local type should be ${expected}", JmmPrimitiveType.INT, fooLocals.get(0).type());
        assertEquals("Second local type should be ${expected}", JmmPrimitiveType.BOOLEAN, fooLocals.get(1).type());
        assertTrue("Third local should be a class type", fooLocals.get(2).type().isClass());
        assertTrue("Fourth local should be an array type", fooLocals.get(3).type().isArray());

        assertTrue("Missing methods should not exist in locals map", st.getLocalVariables("doesNotExist").isEmpty());
    }

    @Test
    public void testGetReturnTypeSupportsPrimitiveVoidAndMissingMethod() {
        var st = (JmmSymbolTable) symbolTable("Factorial.jmm", false).getSymbolTable();

        var computeReturnOpt = st.getReturnType("computeFactorial");
        assertTrue("computeFactorial return type should exist", computeReturnOpt.isPresent());
        assertEquals("computeFactorial return type should be ${expected}", JmmPrimitiveType.INT, computeReturnOpt.orElseThrow());

        var mainReturnOpt = st.getReturnType("main");
        assertTrue("main return type should exist", mainReturnOpt.isPresent());
        assertEquals("main return type should be ${expected}", JmmPrimitiveType.VOID, mainReturnOpt.orElseThrow());

        assertTrue("Missing methods should not exist in return types map", st.getReturnType("doesNotExist").isEmpty());
    }

    @Test
    public void testGetReturnTypeSupportsClassAndArray() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public A self() { return this; }
                    public int[] nums() { return new int[1]; }
                }""", false);
        var st = (JmmSymbolTable) semantics.getSymbolTable();

        var selfReturnOpt = st.getReturnType("self");
        assertTrue("self return type should exist", selfReturnOpt.isPresent());
        assertTrue("self return type should be a class type", selfReturnOpt.orElseThrow().isClass());

        var numsReturnOpt = st.getReturnType("nums");
        assertTrue("nums return type should exist", numsReturnOpt.isPresent());
        assertTrue("nums return type should be an array type", numsReturnOpt.orElseThrow().isArray());
    }

    @Test
    public void testGeneralSymbolTableStructureCoverage() {
        var st = (JmmSymbolTable) symbolTableFromSnippet(GENERAL_SYMBOL_TABLE_SNIPPET, false).getSymbolTable();

        assertEquals("Expected one import", 1, st.getImports().size());
        assertEquals("Imported io should resolve to util.io", "util.io", st.getImportedFullyQualifiedName("io").orElseThrow());

        assertEquals("Class name should be ${expected}", "FullSymbolTable", st.getClassName());
        assertEquals("Class fully qualified name should be ${expected}", "x.FullSymbolTable", st.getFullyQualifiedName());

        assertEquals("Expected four fields", 4, st.getFields().size());
        assertEquals("Expected one m1 method", 1, st.getMethods("m1").size());
        assertEquals("Expected one m2 method", 1, st.getMethods("m2").size());
        assertEquals("Expected one main method", 1, st.getMethods("main").size());
    }

    @Test
    public void testGeneralSymbolTableTypesCoverage() {
        var st = (JmmSymbolTable) symbolTableFromSnippet(GENERAL_SYMBOL_TABLE_SNIPPET, false).getSymbolTable();

        assertEquals("fieldInt type should be ${expected}", JmmPrimitiveType.INT, st.getField("fieldInt").orElseThrow().type());
        assertEquals("fieldBool type should be ${expected}", JmmPrimitiveType.BOOLEAN, st.getField("fieldBool").orElseThrow().type());
        assertTrue("fieldIo should be a class type", st.getField("fieldIo").orElseThrow().type().isClass());
        assertTrue("fieldArr should be an array type", st.getField("fieldArr").orElseThrow().type().isArray());

        for (var methodName : List.of("m1", "m2", "main")) {
            assertTrue("Method should have parameter entry: " + methodName, st.getParameters(methodName).isPresent());
            assertTrue("Method should have return type entry: " + methodName, st.getReturnType(methodName).isPresent());
            assertTrue("Method should have locals entry: " + methodName, st.getLocalVariables(methodName).isPresent());
        }

        var m1Params = st.getParameters("m1").orElseThrow();
        assertEquals("m1 should have 3 parameters", 3, m1Params.size());
        assertEquals("m1 first parameter should be int", JmmPrimitiveType.INT, m1Params.get(0).type());
        assertEquals("m1 second parameter should be boolean", JmmPrimitiveType.BOOLEAN, m1Params.get(1).type());
        assertTrue("m1 third parameter should be class type", m1Params.get(2).type().isClass());

        assertEquals("m1 return type should be int", JmmPrimitiveType.INT, st.getReturnType("m1").orElseThrow());
        assertTrue("m2 return type should be class type", st.getReturnType("m2").orElseThrow().isClass());
        assertEquals("main return type should be void", JmmPrimitiveType.VOID, st.getReturnType("main").orElseThrow());

        var m1Locals = st.getLocalVariables("m1").orElseThrow();
        assertEquals("m1 should have 3 local variables", 3, m1Locals.size());
        assertEquals("m1 first local should be int", JmmPrimitiveType.INT, m1Locals.get(0).type());
        assertEquals("m1 second local should be boolean", JmmPrimitiveType.BOOLEAN, m1Locals.get(1).type());
        assertTrue("m1 third local should be class type", m1Locals.get(2).type().isClass());

        var m2Locals = st.getLocalVariables("m2").orElseThrow();
        assertEquals("m2 should have 1 local variable", 1, m2Locals.size());
        assertTrue("m2 local should be class type", m2Locals.getFirst().type().isClass());

        var mainLocals = st.getLocalVariables("main").orElseThrow();
        assertEquals("main should have 1 local variable", 1, mainLocals.size());
        assertEquals("main local should be int", JmmPrimitiveType.INT, mainLocals.getFirst().type());
    }

    @Test
    public void testDuplicateImportedClassNameWithDifferentPathAddsError() {
        var parser = parseSnippet("""
                package x;
                import a.io;
                import b.io;
                class A {
                    public int m() { return 0; }
                }""", JmmKind.PROGRAM);
        var result = JmmSymbolTableBuilder.build(parser.rootNode());

        assertTrue("Expected duplicate imported class name error for io",
                result.reports().stream()
                        .map(pt.up.fe.comp.jmm.report.Report::getMessage)
                        .anyMatch(message -> message.contains("already defined") && message.contains("io")));
    }

    @Test
    public void testDuplicateImportWithSamePathDoesNotAddDifferentPathError() {
        var parser = parseSnippet("""
                package x;
                import util.io;
                import util.io;
                class A {
                    public int m() { return 0; }
                }""", JmmKind.PROGRAM);
        var result = JmmSymbolTableBuilder.build(parser.rootNode());

        assertTrue("Importing the same path twice should not trigger different-path duplicate error",
                result.reports().stream()
                        .noneMatch(report -> report.getMessage().contains("is already defined")));
    }
}
