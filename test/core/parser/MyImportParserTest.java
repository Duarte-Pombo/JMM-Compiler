// java
package core.parser;

import org.junit.Test;
import pt.up.fe.comp.test.env.JmmTestEnv;

public class MyImportParserTest extends JmmTestEnv {

    public MyImportParserTest() {
        super("", "");
    }

    @Test
    public void mainTest() {
        var res = parseSnippet("""
                package importDeclarations.test;
                import util.io;
                class Test {
                    Object foo() {
                        int a;
                        a = io.read();
                        io.print(a);
                        return 0;
                    }
                }""");
    }

    @Test
    public void staticImport() {
        var res = parseSnippet("""
                package importDeclarations.test;
                import static java.lang.Math.PI;
                class Test {
                    Object foo() {
                        int a;
                        a = io.read();
                        io.print(a);
                        return 0;
                    }
                }""");
    }
}
