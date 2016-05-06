package org.jetbrains.intellij

import org.junit.Test

class UtilsTest {
    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    @Test
    void 'dependency parsing'() {
        assert Utils.parsePluginDependencyString("hello:1.23@alpha") == ['hello', '1.23', 'alpha'] 
        assert Utils.parsePluginDependencyString("hello:@alpha") == ['hello', null, 'alpha'] 
        assert Utils.parsePluginDependencyString("hello@alpha") == ['hello', null, 'alpha'] 
        assert Utils.parsePluginDependencyString("hello") == ['hello', null, null] 
        assert Utils.parsePluginDependencyString("hello:1.23") == ['hello', '1.23', null] 
        assert Utils.parsePluginDependencyString("@alpha") == [null, null, 'alpha']
        assert Utils.parsePluginDependencyString(":1.23") == [null, '1.23', null]
        assert Utils.parsePluginDependencyString(":1.23@alpha") == [null, '1.23', 'alpha']
        assert Utils.parsePluginDependencyString(":@alpha") == [null, null, 'alpha'] 
    }
}
