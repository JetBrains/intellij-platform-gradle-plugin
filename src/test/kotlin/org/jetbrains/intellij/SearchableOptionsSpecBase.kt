package org.jetbrains.intellij

import org.intellij.lang.annotations.Language
import java.io.File

abstract class SearchableOptionsSpecBase : IntelliJPluginSpecBase() {

    @Language("XML")
    fun getPluginXmlWithSearchableConfigurable() = """
        <idea-plugin>
           <name>PluginName</name>
           <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit. A amet perspiciatis quasi.</description>
           <vendor>PluginVendor</vendor>
           <extensions defaultExtensionNs="com.intellij">
               <projectConfigurable instance="TestSearchableConfigurable"/>
           </extensions>
       </idea-plugin>
    """.trimIndent()

    fun getTestSearchableConfigurableJava() = file("src/main/java/TestSearchableConfigurable.java")

    @Language("Java")
    fun getSearchableConfigurableCode() = """
        import com.intellij.openapi.options.ConfigurationException;
        import com.intellij.openapi.options.SearchableConfigurable;
        import org.jetbrains.annotations.Nls;
        import org.jetbrains.annotations.NotNull;
        import org.jetbrains.annotations.Nullable;
        
        import javax.swing.*;
        
        public class TestSearchableConfigurable implements SearchableConfigurable {

            @NotNull
            @Override
            public String getId() {
                return "test.searchable.configurable";
            }
            
            @Nls(capitalization = Nls.Capitalization.Title)
            @Override
            public String getDisplayName() {
                return "Test Searchable Configurable";
            }
            
            @Nullable
            @Override
            public JComponent createComponent() {
                return new JLabel("Label for Test Searchable Configurable");
            }
            
            @Override
            public boolean isModified() {
                return false;
            }
            
            @Override
            public void apply() {
            }
        }
    """.trimIndent()

    fun getSearchableOptionsXml(jar: String) = File(getSearchableOptions(), "/$jar.jar/search/$jar.jar.searchableOptions.xml")

    private fun getSearchableOptions() = File(buildDirectory, IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME)
}
