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

package com.intellij.gwt.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.gwt.GwtBundle;
import com.intellij.gwt.facet.GwtFacet;
import com.intellij.gwt.module.GwtModulesManager;
import com.intellij.gwt.module.model.GwtModule;
import com.intellij.gwt.rpc.GwtSerializableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class GwtInconsistentSerializableClassInspection extends BaseGwtInspection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.gwt.inspections.GwtInconsistentSerializableClassInspection");

  @Nls
  @NotNull
  public String getDisplayName() {
    return GwtBundle.message("inspection.name.incorrect.serializable.class");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "GwtInconsistentSerializableClass";
  }


  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    GwtFacet gwtFacet = getFacet(aClass);
    if (gwtFacet == null) return null;

    PsiFile containingFile = aClass.getContainingFile();
    if (containingFile == null) return null;
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;
    List<GwtModule> gwtModules = GwtModulesManager.getInstance(manager.getProject()).findGwtModulesByClientSourceFile(virtualFile);
    if (gwtModules.isEmpty()) return null;

    GwtSerializableUtil.SerializableChecker serializableChecker = GwtSerializableUtil.createSerializableChecker(gwtFacet, true);
    if (!serializableChecker.isMarkedSerializable(aClass)) return null;

    List<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();
    final PsiField[] psiFields = aClass.getFields();
    for (PsiField psiField : psiFields) {
      if (!psiField.hasModifierProperty(PsiModifier.TRANSIENT)) {
        final PsiType type = psiField.getType();
        if (!serializableChecker.isSerializable(type)) {
          final String description = GwtBundle.message("problem.description.field.0.is.not.serializable", type.getPresentableText());
          PsiElement element = psiField.getTypeElement();
          if (element == null) {
            element = psiField;
          }
          descriptors.add(manager.createProblemDescriptor(element, description, LocalQuickFix.EMPTY_ARRAY,
                                                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
    }

    if (serializableChecker.isGwtSerializable(aClass) && !GwtSerializableUtil.hasPublicNoArgConstructor(aClass)) {
      PsiMethod constructor = GwtSerializableUtil.findNoArgConstructor(aClass);
      final String description = GwtBundle.message("problem.description.serializable.class.should.provide.public.no.args.constructor");
      if (constructor == null) {
        final LocalQuickFix quickfix = new CreateDefaultConstructorQuickFix(aClass);
        descriptors.add(manager.createProblemDescriptor(getElementToHighlight(aClass), description, quickfix,
                                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
      else if (!gwtFacet.getSdkVersion().isPrivateNoArgConstructorInSerializableClassAllowed()) {
        LocalQuickFix quickfix = new MakeConstructorPublicQuickFix(constructor);
        descriptors.add(manager.createProblemDescriptor(getElementToHighlight(constructor), description, quickfix,
                                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static class CreateDefaultConstructorQuickFix extends BaseGwtLocalQuickFix {
    private final PsiClass myClass;

    public CreateDefaultConstructorQuickFix(final PsiClass aClass) {
      super(GwtBundle.message("quickfix.name.create.public.no.args.constructor.in.0", aClass.getName()));
      myClass = aClass;
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      try {
        myClass.add(JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory().createConstructor());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static class MakeConstructorPublicQuickFix extends BaseGwtLocalQuickFix {
    private final PsiMethod myConstructor;

    private MakeConstructorPublicQuickFix(final PsiMethod constructor) {
      super(GwtBundle.message("quickfix.name.make.0.public", constructor.getContainingClass().getName()));
      myConstructor = constructor;
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      try {
        myConstructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
