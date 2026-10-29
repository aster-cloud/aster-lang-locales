package aster.lang.de;

import aster.core.lexicon.LexiconPlugin;
import aster.lang.testsupport.DiagnosticOverlayContract;
import org.junit.jupiter.api.DisplayName;

@DisplayName("de-DE 诊断 overlay 契约")
class DeDeDiagnosticOverlayTest extends DiagnosticOverlayContract {

    @Override
    protected LexiconPlugin plugin() {
        return new DeDeLexiconPlugin();
    }
}
