package org.jetbrains.intellij

import org.jetbrains.intellij.dependency.PluginDependencyNotation
import org.junit.Test

class UtilsTest {
    @Test
    void 'dependency parsing'() {
        assert Utils.parsePluginDependencyString("hello:1.23@alpha") == new PluginDependencyNotation('hello', '1.23', 'alpha')
        assert Utils.parsePluginDependencyString("hello:@alpha") == new PluginDependencyNotation('hello', null, 'alpha')
        assert Utils.parsePluginDependencyString("hello@alpha") == new PluginDependencyNotation('hello', null, 'alpha')
        assert Utils.parsePluginDependencyString("hello") == new PluginDependencyNotation('hello', null, null)
        assert Utils.parsePluginDependencyString("hello:1.23") == new PluginDependencyNotation('hello', '1.23', null)
        assert Utils.parsePluginDependencyString("@alpha") == new PluginDependencyNotation(null, null, 'alpha')
        assert Utils.parsePluginDependencyString(":1.23") == new PluginDependencyNotation(null, '1.23', null)
        assert Utils.parsePluginDependencyString(":1.23@alpha") == new PluginDependencyNotation(null, '1.23', 'alpha')
        assert Utils.parsePluginDependencyString(":@alpha") == new PluginDependencyNotation(null, null, 'alpha')
    }
}
