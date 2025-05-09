/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.gwt.jakartaee.run;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.google.gwt.base.icon.GwtIconGroup;
import consulo.google.gwt.localize.GwtLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GwtRunConfigurationType implements ConfigurationType {
    @Nonnull
    public static GwtRunConfigurationType getInstance() {
        return Application.get().getExtensionPoint(ConfigurationType.class)
            .findExtensionOrFail(GwtRunConfigurationType.class);
    }

    private GwtRunConfigurationFactory myConfigurationFactory;

    @Inject
    GwtRunConfigurationType() {
        myConfigurationFactory = new GwtRunConfigurationFactory(this);
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GwtLocalize.runGwtConfigurationDisplayName();
    }

    @Nonnull
    @Override
    public LocalizeValue getConfigurationTypeDescription() {
        return GwtLocalize.runGwtConfigurationDescription();
    }

    @Override
    public Image getIcon() {
        return GwtIconGroup.gwt();
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{myConfigurationFactory};
    }

    @Override
    @Nonnull
    public String getId() {
        return "GWT.ConfigurationType";
    }
}

