/*
 * Copyright 2013-2014 must-be.org
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

package consulo.gwt.jakartaee.module.extension;

import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.gwt.base.module.extension.GwtModuleExtensionPanel;
import consulo.gwt.module.extension.GoogleGwtMutableModuleExtension;
import consulo.gwt.module.extension.path.GwtLibraryPathProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.module.extension.swing.SwingMutableModuleExtension;
import consulo.module.ui.extension.ModuleExtensionSdkBoxBuilder;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;

/**
 * @author VISTALL
 * @since 14.07.14
 */
public class JavaEEGoogleGwtMutableModuleExtension extends JavaEEGoogleGwtModuleExtension implements GoogleGwtMutableModuleExtension<JavaEEGoogleGwtModuleExtension>, SwingMutableModuleExtension
{
	public JavaEEGoogleGwtMutableModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer rootModel)
	{
		super(id, rootModel);
	}

	@Nonnull
	@Override
	public MutableModuleInheritableNamedPointer<Sdk> getInheritableSdk()
	{
		return (MutableModuleInheritableNamedPointer<Sdk>) super.getInheritableSdk();
	}

	@RequiredUIAccess
	@Nullable
	@Override
	public Component createConfigurationComponent(@Nonnull Disposable disposable, @Nonnull Runnable runnable)
	{
		return VerticalLayout.create().add(Label.create("Unsupported platform"));
	}

	@RequiredUIAccess
	@Nullable
	@Override
	public JComponent createConfigurablePanel(@Nonnull Disposable disposable, @Nonnull Runnable runnable)
	{
		JPanel panel = new JPanel(new VerticalFlowLayout(true, false));
		if(GwtLibraryPathProvider.EP_NAME.findFirstSafe(it -> it.canChooseBundle(getModuleRootLayer())) != null)
		{
			panel.add(ModuleExtensionSdkBoxBuilder.createAndDefine(this, runnable).build());
		}
		panel.add(new GwtModuleExtensionPanel(this));
		return panel;
	}

	@Override
	public void setEnabled(boolean b)
	{
		myIsEnabled = b;
	}

	@Override
	public boolean isModified(@Nonnull JavaEEGoogleGwtModuleExtension originExtension)
	{
		if(isModifiedImpl(originExtension))
		{
			return true;
		}
		if(!Comparing.equal(myPackagingPaths, originExtension.myPackagingPaths))
		{
			return true;
		}
		return false;
	}
}
