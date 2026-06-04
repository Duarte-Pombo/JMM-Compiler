package core.jasmin;

import org.junit.Test;
import org.specs.comp.ollir.Ollir;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.backend.JasminBackendImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

import java.util.List;
import java.util.Map;

public class InvokeSpecialReceiverJasminTest extends JmmTestEnv {

    public InvokeSpecialReceiverJasminTest() {
        super("", "");
    }

    @Test
    public void nonConstructorInvokeSpecialShouldLoadReceiverParameter() {
        var jasminCode = toJasminCodeFromOllir("""
                package x;

                A extends Object {
                    .construct "<init>"().V {
                        invokespecial(this."java.lang.Object", "<init>").V;
                    }

                    .method private helper().V {
                        ret.V;
                    }

                    .method public callHelper(other.A).V {
                        invokespecial(other.A, "helper").V;
                        ret.V;
                    }
                }
                """);

        assertTrue("invokespecial on parameter 'other' should load the receiver",
                jasminCode.contains("aload_1\n   invokespecial x/A/helper()V"));
    }

    @Test
    public void nonConstructorInvokeSpecialShouldLoadReceiverLocal() {
        var jasminCode = toJasminCodeFromOllir("""
                package x;

                A extends Object {
                    .construct "<init>"().V {
                        invokespecial(this."java.lang.Object", "<init>").V;
                    }

                    .method private helper().V {
                        ret.V;
                    }

                    .method public callHelper(other.A).V {
                        alias.A :=.A other.A;
                        invokespecial(alias.A, "helper").V;
                        ret.V;
                    }
                }
                """);

        assertTrue("invokespecial on local 'alias' should load the receiver",
                jasminCode.contains("aload_2\n   invokespecial x/A/helper()V"));
    }

    private String toJasminCodeFromOllir(String ollirCode) {
        var parseResult = Ollir.parse(ollirCode);
        assertFalse("OLLIR parse should not have errors", parseResult.hasErrors());

        var classUnit = parseResult.classUnit();
        classUnit.buildVarTables();

        var ollirResult = new OllirResult(classUnit, List.<Report>of(), Map.of());
        var jasminBackend = new JasminBackendImpl();
        JasminResult jasminResult = jasminBackend.toJasmin(ollirResult);
        assertNotNull("Jasmin result should not be null", jasminResult);
        return jasminResult.getJasminCode();
    }

    public static class FieldInitializerJasminTest extends JmmTestEnv {

        public FieldInitializerJasminTest() {
            super("", "");
        }

        @Test
        public void multipleFieldInitializersShouldBeGeneratedInConstructor() {
            var jasminCode = toJasminCode("""
                    package x;
                    class A {
                        int a = 1;
                        int b = 2 + 3;
                        boolean c = false;
                    }""");

            assertEquals("Constructor should initialize all three fields",
                    3, countOccurrences(jasminCode, "putfield x/A/"));
            assertTrue("Initializer for field a should be emitted",
                    jasminCode.contains("putfield x/A/a I"));
            assertTrue("Initializer for field b should be emitted",
                    jasminCode.contains("putfield x/A/b I"));
            assertTrue("Initializer for field c should be emitted",
                    jasminCode.contains("putfield x/A/c Z"));
        }

        @Test
        public void initializerReferencingPreviousFieldShouldReadAndStoreInConstructor() {
            var jasminCode = toJasminCode("""
                    package x;
                    class A {
                        int base = 4;
                        int derived = base + 6;
                    }""");

            assertTrue("Initializer should read the previous field",
                    jasminCode.contains("getfield x/A/base I"));
            assertTrue("Initializer should store the derived field",
                    jasminCode.contains("putfield x/A/derived I"));
        }

        private String toJasminCode(String code) {
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
            JasminResult jasminResult = jasminBackend.toJasmin(ollirResult);
            assertNotNull("Jasmin result should not be null", jasminResult);
            return jasminResult.getJasminCode();
        }

        private int countOccurrences(String text, String target) {
            int count = 0;
            int fromIndex = 0;

            while ((fromIndex = text.indexOf(target, fromIndex)) >= 0) {
                count++;
                fromIndex += target.length();
            }

            return count;
        }
    }
}
