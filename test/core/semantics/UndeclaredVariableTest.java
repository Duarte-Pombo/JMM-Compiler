package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class UndeclaredVariableTest extends JmmTestEnv {

	public UndeclaredVariableTest() {
		super("", "");
	}

	@Test
	public void declaredLocalAndParameterProduceNoUndeclaredVariableErrors() {
		var semantics = analyzeSnippet("""
				package x;
				class A {
					public int m(int p) {
						int l;
						l = p;
						return l;
					}
				}""");

		var errors = semanticErrors(semantics);
		assertEquals("Expected no semantic errors for declared local/parameter references", 0, errors.size());
	}

	@Test
	public void undeclaredLocalUseProducesSemanticError() {
		var semantics = analyzeSnippet("""
				package x;
				class A {
					public int m() {
						return missing;
					}
				}""");

		var errors = semanticErrors(semantics);
		assertEquals("Expected one semantic error for undeclared variable", 1, errors.size());
		assertTrue("Error should mention undeclared variable name",
				errors.getFirst().getMessage().contains("missing"));
	}

	@Test
	public void undeclaredVariableInSingleMethodReportsOnce() {
		var semantics = analyzeSnippet("""
				package x;
				class A {
					public int ok(int p) {
						return p;
					}

					public int bad() {
						return ghost;
					}
				}""");

		var errors = semanticErrors(semantics);
		assertEquals("Expected one semantic error only in method with undeclared variable", 1, errors.size());
		assertTrue("Error should mention undeclared variable name",
				errors.getFirst().getMessage().contains("ghost"));
	}

    @Test
    public void fieldAccessShouldNotProduceError() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int f;

                    public int m() {
                        return f;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",0, errors.size());
    }

    @Test
    public void thisFieldAccessShouldNotProduceError() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int f;

                    public int m() {
                        return this.f;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",0, errors.size());
    }

    @Test
    public void missingThisFieldProducesSemanticError() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int f;

                    public int m() {
                        return this.g;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",1, errors.size());
        assertTrue("erm", errors.getFirst().getMessage().contains("g"));
    }

    @Test
    public void localShadowsField() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int x;

                    public int m() {
                        int x;
                        x = 5;
                        return x;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",0, errors.size());
    }

    @Test
    public void undeclaredVariableInAssignment() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int a;
                        a = b;
                        return a;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",1, errors.size());
        assertTrue("erm",errors.getFirst().getMessage().contains("b"));
    }

    @Test
    public void undeclaredVariableInExpression() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int a;
                        a = 1 + b;
                        return a;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",1, errors.size());
        assertTrue("erm",errors.getFirst().getMessage().contains("b"));
    }

    @Test
    public void multipleUndeclaredVariables() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        return a + b;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",2, errors.size());
    }

    @Test
    public void undeclaredInsideIf() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        if (true) {
                            return x;
                        }
                        return 0;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",1, errors.size());
    }

    @Test
    public void parameterUsedInExpression() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m(int p) {
                        return p + 1;
                    }
                }""");

        var errors = semanticErrors(semantics);
        assertEquals("erm",0, errors.size());
    }

	private JmmSemanticsResult analyzeSnippet(String code) {
		var parserResult = parseSnippet(code);
		var analysis = new JmmAnalysisImpl();
		var symbolTableResult = analysis.buildSymbolTable(parserResult);
		return analysis.semanticAnalysis(symbolTableResult);
	}

	private List<Report> semanticErrors(JmmSemanticsResult semantics) {
		return semantics.getReports(ReportType.ERROR).stream()
				.filter(report -> report.getMessage().contains("does not exist"))
				.toList();
	}
}
