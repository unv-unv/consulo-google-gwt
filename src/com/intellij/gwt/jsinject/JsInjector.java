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

package com.intellij.gwt.jsinject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class JsInjector implements MultiHostInjector {
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement host) {
    if (((PsiComment)host).getTokenType() == JavaTokenType.C_STYLE_COMMENT) {
      PsiComment comment = (PsiComment)host;
      String text = comment.getText();

      if (!text.startsWith("/*-{") || !text.endsWith("}-*/")) return;

      final PsiElement parent = host.getParent();
      if (parent != null && parent instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)parent;
        if (method.getModifierList().hasExplicitModifier("native")) {
          @NonNls StringBuilder prefix = new StringBuilder();
          prefix.append("function ");
          prefix.append(method.getName());
          prefix.append(" ( ");
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          for (int i = 0; i != parameters.length; ++i) {
            prefix.append(parameters[i].getName());
            prefix.append(",");
          }

          prefix.append("$wnd");
          prefix.append(",");
          prefix.append("$doc");

          prefix.append(") {");

          String suffix = "}";
          Language language = JavaScriptSupportLoader.GWT_DIALECT;
          TextRange range = new TextRange(4, text.length() - 4);
          registrar.startInjecting(language)
            .addPlace(prefix.toString(), suffix, (PsiLanguageInjectionHost)host, range)
            .doneInjecting();
        }
      }
    }
  }

  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PsiComment.class);
  }
}
