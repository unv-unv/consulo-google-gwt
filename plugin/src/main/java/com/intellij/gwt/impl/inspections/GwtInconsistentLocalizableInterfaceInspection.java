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

package com.intellij.gwt.impl.inspections;

import com.intellij.gwt.GwtBundle;
import com.intellij.gwt.base.inspections.BaseGwtInspection;
import com.intellij.gwt.impl.i18n.GwtI18nManager;
import com.intellij.gwt.base.i18n.GwtI18nUtil;
import com.intellij.gwt.impl.i18n.PropertiesFilesListCellRenderer;
import com.intellij.gwt.sdk.GwtVersion;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.dataContext.DataManager;
import consulo.gwt.module.extension.GoogleGwtModuleExtension;
import consulo.gwt.base.module.extension.GwtModuleExtensionUtil;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.popup.IPopupChooserBuilder;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@ExtensionImpl
public class GwtInconsistentLocalizableInterfaceInspection extends BaseGwtInspection
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.gwt.inspections.GwtInconsistentLocalizableInterfaceInspection");

	@Override
	@Nls
	@Nonnull
	public String getDisplayName()
	{
		return GwtBundle.message("inspection.name.inconsistent.gwt.localizable.interface");
	}

	@Override
	@NonNls
	@Nonnull
	public String getShortName()
	{
		return "GwtInconsistentI18nInterface";
	}


	@Override
	@Nullable
	public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state)
	{
		GoogleGwtModuleExtension extension = getExtension(file);
		if(extension == null)
		{
			return null;
		}

		GwtVersion version = GwtModuleExtensionUtil.getVersion(extension);

		if(file instanceof PropertiesFile)
		{
			PropertiesFile propertiesFile = (PropertiesFile) file;
			return checkPropertiesFile(manager, propertiesFile, extension, version);
		}

		if(file instanceof PsiJavaFile)
		{
			final PsiClass[] psiClasses = ((PsiJavaFile) file).getClasses();
			for(PsiClass psiClass : psiClasses)
			{
				final ProblemDescriptor[] descriptors = checkPsiClass(manager, psiClass);
				if(descriptors != null)
				{
					return descriptors;
				}
			}
		}
		return null;
	}

	@Nullable
	private static ProblemDescriptor[] checkPsiClass(final InspectionManager manager, final PsiClass psiClass)
	{
		final GwtI18nManager i18nManager = GwtI18nManager.getInstance(manager.getProject());
		final PropertiesFile[] files = i18nManager.getPropertiesFiles(psiClass);
		if(files.length == 0)
		{
			return null;
		}

		List<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();
		for(PsiMethod psiMethod : psiClass.getMethods())
		{
			final IProperty[] properties = i18nManager.getProperties(psiMethod);
			if(properties.length == 0)
			{
				final String description = GwtBundle.message("problem.description.method.0.doesn.t.have.corresponding.property", psiMethod.getName());
				final LocalQuickFix quickFix = new DefinePropertyQuickfix(GwtI18nUtil.getPropertyName(psiMethod), files);
				descriptors.add(manager.createProblemDescriptor(getElementToHighlight(psiMethod), description, quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
			}
		}
		return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
	}

	@Nullable
	private static ProblemDescriptor[] checkPropertiesFile(final InspectionManager manager, final PropertiesFile propertiesFile, final GoogleGwtModuleExtension extension, GwtVersion version)
	{
		final GwtI18nManager i18nManager = GwtI18nManager.getInstance(manager.getProject());
		final PsiClass anInterface = i18nManager.getPropertiesInterface(propertiesFile);
		if(anInterface == null)
		{
			return null;
		}

		List<IProperty> propertiesWithoutMethods = new ArrayList<IProperty>();
		final List<IProperty> properties = propertiesFile.getProperties();
		for(IProperty property : properties)
		{
			final PsiMethod method = i18nManager.getMethod(property);
			if(method == null && property.getUnescapedKey() != null)
			{
				propertiesWithoutMethods.add(property);
			}
		}

		if(propertiesWithoutMethods.isEmpty())
		{
			return null;
		}

		SynchronizeInterfaceQuickFix synchAllQuickfix;
		if(propertiesWithoutMethods.size() > 1)
		{
			synchAllQuickfix = new SynchronizeInterfaceQuickFix(anInterface, propertiesWithoutMethods, version);
		}
		else
		{
			synchAllQuickfix = null;
		}

		List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
		for(IProperty property : propertiesWithoutMethods)
		{
			final String key = property.getUnescapedKey();
			final AddMethodToInterfaceQuickFix quickFix = new AddMethodToInterfaceQuickFix(anInterface, key, property.getValue(), version);
			LocalQuickFix[] fixes = synchAllQuickfix == null ? new LocalQuickFix[]{quickFix} : new LocalQuickFix[]{
					synchAllQuickfix,
					quickFix
			};

			final String description = GwtBundle.message("problem.description.property.0.doesn.t.have.corresponding.method.in.1", key, anInterface.getName());
			problems.add(manager.createProblemDescriptor(property.getPsiElement(), description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
		}
		return problems.toArray(new ProblemDescriptor[problems.size()]);
	}

	private static void defineProperty(final PropertiesFile propertiesFile, final String propertyName)
	{
		final Property property = new WriteCommandAction<Property>(propertiesFile.getProject(), propertiesFile.getContainingFile())
		{
			@Override
			protected void run(final Result<Property> result) throws Throwable
			{
				result.setResult(definePropertyImpl(propertiesFile, propertyName));
			}
		}.execute().getResultObject();

		if(property != null)
		{
			GwtI18nUtil.navigateToProperty(property);
		}
	}

	@Nullable
	private static Property definePropertyImpl(final PropertiesFile propertiesFile, final String propertyName)
	{
		try
		{
			final PsiElement element = propertiesFile.addProperty(PropertiesElementFactory.createProperty(propertiesFile.getProject(), propertyName, ""));
			return element instanceof Property ? (Property) element : null;
		}
		catch(IncorrectOperationException e)
		{
			LOG.error(e);
			return null;
		}
	}

	private static boolean ensureWritable(final PsiElement element)
	{
		return !ReadonlyStatusHandler.getInstance(element.getProject()).ensureFilesWritable(element.getContainingFile().getVirtualFile()).hasReadonlyFiles();
	}

	private static class AddMethodToInterfaceQuickFix extends BaseGwtLocalQuickFix
	{
		private final PsiClass myInterface;
		private final String myPropertyName;
		private final String myPropertyValue;
		private final GwtVersion myGwtVersion;

		public AddMethodToInterfaceQuickFix(final PsiClass anInterface, final String propertyName, final String value, final GwtVersion gwtVersion)
		{
			super(GwtBundle.message("quickfix.name.create.method.0.in.1", propertyName, anInterface.getName()));
			myInterface = anInterface;
			myPropertyName = propertyName;
			myPropertyValue = value;
			myGwtVersion = gwtVersion;
		}

		@Override
		public void applyFix(final @Nonnull Project project, @Nonnull final ProblemDescriptor descriptor)
		{
			if(!ensureWritable(myInterface))
			{
				return;
			}
			GwtI18nUtil.addMethod(myInterface, myPropertyName, myPropertyValue, myGwtVersion);
		}
	}

	private static class SynchronizeInterfaceQuickFix extends BaseGwtLocalQuickFix
	{
		private final PsiClass myInterface;
		private final GwtVersion myGwtVersion;
		private final List<IProperty> myProperties;

		public SynchronizeInterfaceQuickFix(final PsiClass anInterface, final List<IProperty> properties, final GwtVersion gwtVersion)
		{
			super(GwtBundle.message("quickfix.name.synchronize.all.methods.in.0", anInterface.getName()));
			myProperties = properties;
			myInterface = anInterface;
			myGwtVersion = gwtVersion;
		}

		@Override
		public void applyFix(final @Nonnull Project project, @Nonnull final ProblemDescriptor descriptor)
		{
			if(!ensureWritable(myInterface))
			{
				return;
			}

			for(IProperty property : myProperties)
			{
				GwtI18nUtil.addMethod(myInterface, property.getUnescapedKey(), property.getValue(), myGwtVersion);
			}
		}
	}

	private static class DefinePropertyQuickfix extends BaseGwtLocalQuickFix
	{
		private final String myPropertyName;
		private final PropertiesFile[] myPropertiesFiles;

		public DefinePropertyQuickfix(final String propertyName, final PropertiesFile[] propertiesFiles)
		{
			super(GwtBundle.message("quickfix.name.create.property.0", propertyName));
			myPropertyName = propertyName;
			myPropertiesFiles = propertiesFiles;
		}

		@Override
		public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
		{
			if(myPropertiesFiles.length == 1)
			{
				final PropertiesFile file = myPropertiesFiles[0];
				if(ensureWritable(file.getContainingFile()))
				{
					final Property property = definePropertyImpl(file, myPropertyName);
					if(property != null)
					{
						GwtI18nUtil.navigateToProperty(property);
					}
				}
				return;
			}

			IPopupChooserBuilder<PropertiesFile> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(List.of(myPropertiesFiles));
			builder.setRenderer(new PropertiesFilesListCellRenderer());
			builder.setTitle(GwtBundle.message("quickfix.popup.title.choose.properties.file")).setItemChosenCallback(e ->
			{
				defineProperty(e, myPropertyName);
			}).createPopup().showInBestPositionFor(DataManager.getInstance().getDataContext());
		}
	}
}
