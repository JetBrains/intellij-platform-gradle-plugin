package org.jetbrains.intellij

import org.gradle.api.plugins.BasePlugin
import org.junit.Assume
import spock.lang.Stepwise

@Stepwise
class DownloadIntelliJSpec extends IntelliJPluginSpecBase {
    def 'download idea dependencies'() {
        given:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.3')
        Assume.assumeFalse("it was already cached. test is senseless until gradle clean", cacheDir.exists())

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        cacheDir.list() as Set == ['b52fd6ecd1178b17bbebe338a9efe975c95f7037', 'e71057c345e163250c544ee6b56411ce9ac7e23'] as Set
        new File(cacheDir, 'b52fd6ecd1178b17bbebe338a9efe975c95f7037').list() as Set == ['ideaIC-14.1.3.pom'] as Set
        new File(cacheDir, 'e71057c345e163250c544ee6b56411ce9ac7e23').list() as Set == ['ideaIC-14.1.3', 'ideaIC-14.1.3.zip'] as Set
    }

    def 'download sources if option is enabled'() {
        given:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.3')
        def sourcesJar = new File(cacheDir, "b6e282e0e4f49b6cdcb62f180f141ff1a7464ba2/ideaIC-14.1.3-sources.jar").exists()
        Assume.assumeFalse("it was already cached. test is senseless until gradle clean", sourcesJar)

        buildFile << 'intellij { downloadSources = true }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        cacheDir.list() as Set == ['b6e282e0e4f49b6cdcb62f180f141ff1a7464ba2', 'b52fd6ecd1178b17bbebe338a9efe975c95f7037', 'e71057c345e163250c544ee6b56411ce9ac7e23'] as Set
        new File(cacheDir, 'b52fd6ecd1178b17bbebe338a9efe975c95f7037').list() as Set == ['ideaIC-14.1.3.pom'] as Set
        new File(cacheDir, 'e71057c345e163250c544ee6b56411ce9ac7e23').list() as Set == ['ideaIC-14.1.3', 'ideaIC-14.1.3.zip'] as Set
        new File(cacheDir, 'b6e282e0e4f49b6cdcb62f180f141ff1a7464ba2').list() as Set == ['ideaIC-14.1.3-sources.jar'] as Set
    }

    def 'download ultimate idea dependencies'() {
        given:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIU/14.1.4')
        def ideaCommunityCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.4')
        Assume.assumeFalse("it was already cached. test is senseless until gradle clean", cacheDir.exists() || ideaCommunityCacheDir.exists())

        buildFile << """intellij { 
            version 'IU-14.1.4'
            downloadSources true 
}"""
        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        cacheDir.list() as Set == ['b8993c44c83fe4a39dbb6b72ab6d87a117769534', 'f8eb5ad49abba6374eeec643cecf20f7268cbfee'] as Set
        new File(cacheDir, 'b8993c44c83fe4a39dbb6b72ab6d87a117769534').list() as Set == ['ideaIU-14.1.4.pom'] as Set
        new File(cacheDir, 'f8eb5ad49abba6374eeec643cecf20f7268cbfee').list() as Set == ['ideaIU-14.1.4', 'ideaIU-14.1.4.zip'] as Set

        // do not download ideaIC dist
        ideaCommunityCacheDir.list() as Set == ['87ce88382f970b94fc641304e0a80af1d70bfba7', 'f5169c4a780da12ca4eec17553de9f6d43a49d52'] as Set
        new File(ideaCommunityCacheDir, '87ce88382f970b94fc641304e0a80af1d70bfba7').list() as Set == ['ideaIC-14.1.4.pom'] as Set
        new File(ideaCommunityCacheDir, 'f5169c4a780da12ca4eec17553de9f6d43a49d52').list() as Set == ['ideaIC-14.1.4-sources.jar'] as Set
    }
}
