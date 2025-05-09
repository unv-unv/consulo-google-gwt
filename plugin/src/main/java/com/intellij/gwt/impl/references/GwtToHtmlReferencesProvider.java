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

package com.intellij.gwt.impl.references;

import com.intellij.java.language.psi.PsiLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.gwt.module.extension.GoogleGwtModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ModuleUtilCore;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;

/**
 * @author nik
 */
public class GwtToHtmlReferencesProvider extends PsiReferenceProvider
{

	@Override
	@Nonnull
	@RequiredReadAction
	public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull final ProcessingContext context)
	{
		if(element instanceof PsiLiteralExpression)
		{
			GoogleGwtModuleExtension extension = ModuleUtilCore.getExtension(element, GoogleGwtModuleExtension.class);
			if(extension == null)
			{
				return PsiReference.EMPTY_ARRAY;
			}
			final PsiLiteralExpression literalExpression = (PsiLiteralExpression) element;
			if(literalExpression.getValue() instanceof String)
			{
				return new PsiReference[]{new GwtToHtmlTagReference(literalExpression)};
			}
		}
		return PsiReference.EMPTY_ARRAY;
	}

}
