// java
package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import java.util.List;

public class MySymbolTableTest extends JmmTestEnv {
    private static final String BASE_PATH = "core/semantics/symboltable/";
    private static final String RESOURCES_LOCATION = "test";

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
}
