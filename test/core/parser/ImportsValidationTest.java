package core.parser;

import org.junit.Test;
import pt.up.fe.comp.test.env.JmmTestEnv;

import static pt.up.fe.comp.cp1.core.parser.RulesNames.IMPORT;

public class ImportsValidationTest extends JmmTestEnv {

    public ImportsValidationTest() {
        super("", "");
    }

    @Test
    public void importWithoutPackageShouldBeRejected() {
        parseSnippetWithErrors("import Date;", IMPORT);
    }

    @Test
    public void programImportWithoutPackageShouldBeRejected() {
        parseSnippetWithErrors("""
                package x;
                import Date;
                class A {
                }""");
    }
}
