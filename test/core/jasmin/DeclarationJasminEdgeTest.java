package core.jasmin;

import org.junit.Test;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JasminTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.backend.JasminBackendImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

import java.lang.reflect.Modifier;

public class DeclarationJasminEdgeTest extends JasminTestEnv {

    public DeclarationJasminEdgeTest() {
        super("", "");
    }

    @Test
    public void packagePrivateMethodShouldNotEmitPublicPrivateOrProtected() {
        var jasminResult = toJasminResult("""
                package x;
                class A {
                    int helper() {
                        return 7;
                    }

                    public int call() {
                        return this.helper();
                    }
                }""");

        var jasminCode = jasminResult.getJasminCode();
        assertContainsLine(jasminCode, ".method helper()I");
        assertTrue("Package-private method must not be emitted as public",
                !jasminCode.contains(".method public helper()I"));
        assertTrue("Package-private method must not be emitted as private",
                !jasminCode.contains(".method private helper()I"));
        assertTrue("Package-private method must not be emitted as protected",
                !jasminCode.contains(".method protected helper()I"));

        var compiled = compile(jasminResult);
        var method = compiled.getJavaMethod("helper");
        var modifiers = method.getModifiers();
        assertTrue("Reflection should see helper as package-private",
                !Modifier.isPublic(modifiers) && !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers));
        assertEquals("Public caller should still be able to invoke package-private helper", 7,
                compiled.invoke("call", Integer.class).returnValue());
    }

    @Test
    public void staticMethodsShouldKeepVisibilityBeforeStaticModifier() {
        var jasminCode = toJasminResult("""
                package x;
                class A {
                    private static int hidden() {
                        return 40;
                    }

                    protected static int visibleToChildren() {
                        return 2;
                    }

                    static int packageStatic() {
                        return 3;
                    }
                }""").getJasminCode();

        assertContainsLine(jasminCode, ".method private static hidden()I");
        assertContainsLine(jasminCode, ".method protected static visibleToChildren()I");
        assertContainsLine(jasminCode, ".method static packageStatic()I");
        assertTrue("Static method visibility should not be emitted after static",
                !jasminCode.contains(".method static private hidden()I"));
        assertTrue("Static method visibility should not be emitted after static",
                !jasminCode.contains(".method static protected visibleToChildren()I"));
    }

    @Test
    public void fieldsDeclaredAfterMethodsShouldBeEmittedBeforeConstructorAndMethods() {
        var jasminResult = toJasminResult("""
                package x;
                class A {
                    public int readLate() {
                        return this.late;
                    }

                    int late;

                    public int readBoth() {
                        if (this.flag) {
                            return this.late + 1;
                        }
                        return this.late;
                    }

                    boolean flag;

                    public void set(int value, boolean enabled) {
                        this.late = value;
                        this.flag = enabled;
                    }
                }""");

        var jasminCode = jasminResult.getJasminCode();
        assertBefore(jasminCode, ".super java/lang/Object", ".field public late I");
        assertBefore(jasminCode, ".field public late I", ".field public flag Z");
        assertBefore(jasminCode, ".field public flag Z", ".method public <init>()V");
        assertBefore(jasminCode, ".field public flag Z", ".method public readLate()I");

        var compiled = compile(jasminResult);
        var instance = compiled.newInstance();
        compiled.invoke(instance, "set", 10, true);
        assertEquals("Method declared before fields should read fields correctly", 10,
                compiled.invoke(instance, "readLate", Integer.class).returnValue());
        assertEquals("Method between field declarations should read both fields correctly", 11,
                compiled.invoke(instance, "readBoth", Integer.class).returnValue());
    }

    @Test
    public void privateMethodDeclaredBeforeFieldShouldStillReadThatField() {
        var jasminResult = toJasminResult("""
                package x;
                class A {
                    private int hiddenRead() {
                        return this.value;
                    }

                    int value;

                    public int run(int input) {
                        this.value = input;
                        return this.hiddenRead();
                    }
                }""");

        var jasminCode = jasminResult.getJasminCode();
        assertContainsLine(jasminCode, ".method private hiddenRead()I");
        assertBefore(jasminCode, ".field public value I", ".method private hiddenRead()I");

        var compiled = compile(jasminResult);
        assertEquals("Private method should read a field declared later in the source", 23,
                compiled.invoke("run", Integer.class, 23).returnValue());
    }

    private void assertContainsLine(String code, String expectedLine) {
        for (var line : code.split("\\R")) {
            if (line.strip().equals(expectedLine)) {
                return;
            }
        }

        assertTrue("Expected Jasmin line: " + expectedLine + "\nActual code:\n" + code, false);
    }

    private void assertBefore(String code, String before, String after) {
        var beforeIndex = code.indexOf(before);
        var afterIndex = code.indexOf(after);

        assertTrue("Expected to find '" + before + "' in Jasmin code", beforeIndex >= 0);
        assertTrue("Expected to find '" + after + "' in Jasmin code", afterIndex >= 0);
        assertTrue("Expected '" + before + "' before '" + after + "'", beforeIndex < afterIndex);
    }

    private JasminResult toJasminResult(String code) {
        var parserResult = parseSnippet(code);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        assertNotNull("Ollir code should not be null", ollirResult.getOllirCode());

        var jasminBackend = new JasminBackendImpl();
        var jasminResult = jasminBackend.toJasmin(ollirResult);
        assertNotNull("Jasmin result should not be null", jasminResult);
        assertNotNull("Jasmin code should not be null", jasminResult.getJasminCode());
        return jasminResult;
    }
}
