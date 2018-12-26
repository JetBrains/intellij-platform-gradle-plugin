package org.jetbrains.intellij

import java.util.zip.ZipFile

class BuildPluginTaskSpec extends IntelliJPluginSpecBase {
    def 'build plugin distribution'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/nonIncluded.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name><depends config-file="other.xml"/></idea-plugin>'
        buildFile << """
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright']
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }""".stripIndent()

        when:
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        distribution.exists()

        def zipFile = new ZipFile(distribution)
        collectPaths(zipFile) == ['myPluginName/', 'myPluginName/lib/', 'myPluginName/lib/joda-time-2.8.1.jar',
                                  'myPluginName/lib/projectName-0.42.123.jar',
                                  'myPluginName/lib/searchableOptions-0.42.123.jar'] as Set

        def jar = new ZipFile(extractFile(zipFile, 'myPluginName/lib/projectName-0.42.123.jar'))
        collectPaths(jar) == ['App.class', 'META-INF/', 'META-INF/MANIFEST.MF',
                              'META-INF/nonIncluded.xml', 'META-INF/other.xml', 'META-INF/plugin.xml'] as Set

        fileText(jar, 'META-INF/plugin.xml') == """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="201.6668" until-build="201.*"/>
              <name>MyPluginName</name>
              <depends config-file="other.xml"/>
            </idea-plugin>""".stripIndent()
    }

    def 'build plugin distribution with Gradle 4 and Kotlin 1.1.4'() {
        given:
        writeJavaFile()
        writeKotlinUIFile()
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name></idea-plugin>'
        buildFile << """
            intellij.pluginName = 'myPluginName'
            version='0.42.123'
           """.stripIndent()
        buildFile.text = buildFile.text.replace("kotlin-gradle-plugin:1.0.6", "kotlin-gradle-plugin:1.1.4")

        when:
        disableDebug("while debugging Gradle 4.0 includes all dependencies, including intellij-plugin-structure, " +
                "which depends on asm-all different from IDEA-builtin asm-all")
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        distribution.exists()

        def zipFile = new ZipFile(distribution)
        collectPaths(zipFile) == ['myPluginName/',
                                  'myPluginName/lib/',
                                  'myPluginName/lib/projectName-0.42.123.jar',
                                  'myPluginName/lib/searchableOptions-0.42.123.jar'] as Set

        def jar = new ZipFile(extractFile(zipFile, 'myPluginName/lib/projectName-0.42.123.jar'))
        collectPaths(jar) == ['App.class', 'pack/', 'pack/AppKt.class', 'META-INF/',
                              'META-INF/MANIFEST.MF', 'META-INF/plugin.xml', 'META-INF/projectName.kotlin_module'] as Set
    }

    def 'use custom sandbox for distribution'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/nonIncluded.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name><depends config-file="other.xml"/></idea-plugin>'
        def sandboxPath = adjustWindowsPath("$dir.root.absolutePath/customSandbox")
        buildFile << """
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
                sandboxDirectory = '$sandboxPath'
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }""".stripIndent()

        when:
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        distribution.exists()
        collectPaths(new ZipFile(distribution)) as Set == ['myPluginName/',
                                                           'myPluginName/lib/',
                                                           'myPluginName/lib/joda-time-2.8.1.jar',
                                                           'myPluginName/lib/projectName-0.42.123.jar',
                                                           'myPluginName/lib/searchableOptions-0.42.123.jar'] as Set
    }

    def 'use gradle project name for distribution if plugin name is not defined'() {
        given:
        buildFile << 'version="0.42.123"'
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name></idea-plugin>'

        when:
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        //noinspection GrEqualsBetweenInconvertibleTypes
        new File(buildDirectory, "distributions").list() == ['projectName-0.42.123.zip']
    }


    def 'can compile classes that depends on external plugins'() {
        given:
        file('src/main/java/App.java') << """
import java.lang.String;
import org.jetbrains.annotations.NotNull;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
class App {
    public static void main(@NotNull String[] strings) {
        System.out.println(MarkdownLanguage.INSTANCE.getDisplayName());
    }
}
"""
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name></idea-plugin>'
        buildFile << """
            version='0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['org.intellij.plugins.markdown:201.6668.74']
            }
            """.stripIndent()

        when:
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        distribution.exists()
        def jar = extractFile(new ZipFile(distribution), 'myPluginName/lib/projectName-0.42.123.jar')
        (new ZipFile(jar).entries().collect { it.name }).contains('App.class')
    }

    def 'can compile classes that depend on external plugin with classes directory'() {
        given:
        file('src/main/java/App.java') << """
import java.lang.String;
import org.jetbrains.annotations.NotNull;
import org.asciidoc.intellij.AsciiDoc;
class App {
    public static void main(@NotNull String[] strings) {
        System.out.println(AsciiDoc.class.getName());
    }
}
"""
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name></idea-plugin>'
        buildFile << """
            version='0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['org.asciidoctor.intellij.asciidoc:0.20.6']
            }
            """.stripIndent()
        when:
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        distribution.exists()
        def jar = extractFile(new ZipFile(distribution), 'myPluginName/lib/projectName-0.42.123.jar')
        (new ZipFile(jar).entries().collect { it.name }).contains('App.class')
    }

    def 'build plugin without sources'() {
        given:
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name></idea-plugin>'
        buildFile << """
            version='0.42.123'
            intellij { pluginName = 'myPluginName' }
            """.stripIndent()

        when:
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        distribution.exists()

        def zip = new ZipFile(distribution)
        zip.entries().collect { it.name } == ['myPluginName/',
                                              'myPluginName/lib/',
                                              'myPluginName/lib/projectName-0.42.123.jar',
                                              'myPluginName/lib/searchableOptions-0.42.123.jar']
        def jar = extractFile(zip, 'myPluginName/lib/projectName-0.42.123.jar')
        collectPaths(new ZipFile(jar)) == ['META-INF/', 'META-INF/MANIFEST.MF', 'META-INF/plugin.xml'] as Set
    }

    def 'include only relevant searchableOptions.jar'() {
        given:
        pluginXml << '<idea-plugin version="2"><name>MyPluginName</name></idea-plugin>'
        buildFile << """
            version='0.42.321'
            intellij { pluginName = 'myPluginName' }
            """.stripIndent()
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)
        buildFile << """
            version='0.42.123'
            intellij { pluginName = 'myPluginName' }
            """.stripIndent()

        when:
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        distribution.exists()

        def zip = new ZipFile(distribution)
        zip.entries().collect { it.name } == ['myPluginName/',
                                              'myPluginName/lib/',
                                              'myPluginName/lib/projectName-0.42.123.jar',
                                              'myPluginName/lib/searchableOptions-0.42.123.jar']
        def jar = extractFile(zip, 'myPluginName/lib/projectName-0.42.123.jar')
        collectPaths(new ZipFile(jar)) == ['META-INF/', 'META-INF/MANIFEST.MF', 'META-INF/plugin.xml'] as Set
    }
}
