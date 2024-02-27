// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.platform.gradle.Constants.SEARCHABLE_OPTIONS_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.SEARCHABLE_OPTIONS_SUFFIX

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

    fun getTestSearchableConfigurableJava() = dir.resolve("src/main/java/TestSearchableConfigurable.java")

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

    fun getSearchableOptionsXml(jar: String) = getSearchableOptions().resolve("$jar.jar/search/$jar.jar$SEARCHABLE_OPTIONS_SUFFIX")

    private fun getSearchableOptions() = buildDirectory.resolve(SEARCHABLE_OPTIONS_DIRECTORY)
}
