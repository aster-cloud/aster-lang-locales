package aster.lang.zh;

import aster.core.lexicon.LexiconPlugin;
import aster.lang.testsupport.DiagnosticOverlayContract;
import org.junit.jupiter.api.DisplayName;

@DisplayName("zh-CN 诊断 overlay 契约")
class ZhCnDiagnosticOverlayTest extends DiagnosticOverlayContract {

    @Override
    protected LexiconPlugin plugin() {
        return new ZhCnPlugin();
    }
}
