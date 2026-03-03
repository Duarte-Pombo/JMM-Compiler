// java
package pt.up.fe.comp.cp1.core.parser.declarations;

import org.junit.Test;
import pt.up.fe.comp.test.env.JmmTestEnv;

import static pt.up.fe.comp.cp1.core.parser.RulesNames.IMPORT;

public class ImportParserTest extends JmmTestEnv {


    public ImportParserTest() {
        super("", "");
    }

    @Test
    public void testImportSinglePackage() {
        setDescription("Parse a single import declaration");
        var res = parseSnippet("import bar.Foo;", IMPORT);
    }

    @Test
    public void testImportMultiPackage() {
        setDescription("Parse a multi-segment import declaration");
        parseSnippet("import bar.foo.A;", IMPORT);
    }


    @Test
    public void testImportClassWithNameMain() {
        setDescription("Import a class with name Main and with 'main' as package name");
        parseSnippet("import my.main.Main;", IMPORT);
    }

}
