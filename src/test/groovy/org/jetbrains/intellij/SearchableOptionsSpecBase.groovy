package org.jetbrains.intellij

class SearchableOptionsSpecBase extends IntelliJPluginSpecBase {

    @Override
    String getIntellijVersion() {
        return '2019.1'
    }

    protected static String getPluginXmlWithSearchableConfigurable() {
        """<idea-plugin version="2">
               <name>PluginName</name>
               <description>PluginDescription</description>
               <vendor>PluginVendor</vendor>
               <extensions defaultExtensionNs="com.intellij">
                   <projectConfigurable instance="TestSearchableConfigurable"/>
               </extensions>
           </idea-plugin>
        """.stripIndent()
    }

    protected File getTestSearchableConfigurableJava() {
        file('src/main/java/TestSearchableConfigurable.java')
    }

    protected static String getSearchableConfigurableCode() {
        """import com.intellij.openapi.options.ConfigurationException;
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
               public void apply() throws ConfigurationException {
               }
           }
        """.stripIndent()
    }

    protected File getSearchableOptionsXml(String jar) {
        new File(searchableOptions, "/${jar}.jar/search/${jar}.jar.searchableOptions.xml")
    }

    protected File getSearchableOptions() {
        new File(buildDirectory, IntelliJPlugin.SEARCHABLE_OPTIONS_DIR_NAME)
    }
}
