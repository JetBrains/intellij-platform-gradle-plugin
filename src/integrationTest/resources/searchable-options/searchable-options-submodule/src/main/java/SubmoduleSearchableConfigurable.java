// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SubmoduleSearchableConfigurable implements SearchableConfigurable {

    @NotNull
    @Override
    public String getId() {
        return "submodule.searchable.configurable";
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Submodule Searchable Configurable";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return new JLabel("Label for Submodule Searchable Configurable");
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() {
    }
}
