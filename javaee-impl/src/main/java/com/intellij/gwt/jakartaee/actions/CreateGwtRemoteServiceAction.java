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

package com.intellij.gwt.jakartaee.actions;

import com.intellij.gwt.GwtBundle;
import com.intellij.gwt.base.actions.GwtCreateActionBase;
import com.intellij.gwt.module.model.GwtModule;
import com.intellij.gwt.module.model.GwtServlet;
import com.intellij.gwt.jakartaee.rpc.GwtServletUtil;
import com.intellij.gwt.base.rpc.RemoteServiceUtil;
import com.intellij.gwt.sdk.GwtVersion;
import com.intellij.gwt.base.templates.GwtTemplates;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.gwt.base.module.extension.GwtModuleExtensionUtil;
import consulo.gwt.jakartaee.module.extension.JavaEEGoogleGwtModuleExtension;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.xml.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.StringTokenizer;

@ActionImpl(id = "GWT.NewRemoteService", parents = @ActionParentRef(@ActionRef(id = "GWT")))
public class CreateGwtRemoteServiceAction extends GwtCreateActionBase
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.gwt.actions.CreateGwtRemoteServiceAction");
	@NonNls
	private static final String QUALIFIED_SERVICE_NAME_PROPERTY = "QUALIFIED_SERVICE_NAME";
	@NonNls
	private static final String SERVICE_NAME_PROPERTY = "SERVICE_NAME";
	@NonNls
	private static final String SERVLET_PATH_PROPERTY = "SERVLET_PATH";
	@NonNls
	private static final String RELATIVE_PATH_PROPERTY = "RELATIVE_SERVLET_PATH";
	@NonNls
	private static final String SERVER_PACKAGE = "server";

	public CreateGwtRemoteServiceAction()
	{
		super(GwtBundle.message("newservice.menu.action.text"), GwtBundle.message("newservice.menu.action.description"));
	}

	@Override
	protected boolean requireGwtModule()
	{
		return true;
	}

	@Override
	protected String getDialogPrompt()
	{
		return GwtBundle.message("newservice.dlg.prompt");
	}

	@Override
	protected String getDialogTitle()
	{
		return GwtBundle.message("newservice.dlg.title");
	}


	@Override
	protected PsiFile[] getAffectedFiles(final GwtModule gwtModule)
	{
		final XmlFile xmlFile = gwtModule.getModuleXmlFile();
		/*if(xmlFile != null)
		{
			final JavaWebModuleExtension webFacet = ModuleUtilCore.getExtension(xmlFile, JavaWebModuleExtension.class);
			if(webFacet != null)
			{
				final WebApp webApp = webFacet.getRoot();
				if(webApp != null)
				{
					return new PsiFile[]{
							xmlFile,
							webApp.getRoot().getFile()
					};
				}
			}
		}  */
		return new PsiFile[]{xmlFile};
	}

	@Override
	@Nonnull
	protected PsiElement[] doCreate(String serviceName, PsiDirectory directory, final GwtModule gwtModule) throws Exception
	{
		ArrayList<PsiElement> res = new ArrayList<PsiElement>(0);

		final String servletPath = GwtServletUtil.getDefaultServletPath(gwtModule, serviceName);
		final JavaEEGoogleGwtModuleExtension extension = ModuleUtilCore.getExtension(gwtModule.getModule(), JavaEEGoogleGwtModuleExtension.class);
		LOG.assertTrue(extension != null);

		GwtVersion version = GwtModuleExtensionUtil.getVersion(extension);
		final String templateName = version.getGwtServiceJavaTemplate();
		final PsiClass serviceClass = createClassFromTemplate(directory, serviceName, templateName, SERVLET_PATH_PROPERTY, servletPath,
				RELATIVE_PATH_PROPERTY, servletPath.substring(1));
		res.add(serviceClass);
		res.add(createClassFromTemplate(directory, serviceName + RemoteServiceUtil.ASYNC_SUFFIX, GwtTemplates.GWT_SERVICE_ASYNC_JAVA));


		final VirtualFile gwtModuleDir = gwtModule.getModuleDirectory();

		PsiClass servletImpl = generateServletClass(serviceName, directory, gwtModuleDir, serviceClass);
		if(servletImpl == null)
		{
			return PsiElement.EMPTY_ARRAY;
		}

		XmlFile xml = gwtModule.getModuleXmlFile();
		if(xml == null)
		{
			return PsiElement.EMPTY_ARRAY;
		}

		final GwtServlet gwtServlet = gwtModule.addServlet();
		gwtServlet.getPath().setValue(servletPath);
		gwtServlet.getServletClass().setValue(servletImpl.getQualifiedName());

	/*	final JavaWebModuleExtension webFacet = ModuleUtilCore.getExtension(xml, JavaWebModuleExtension.class);
		if(webFacet != null)
		{
			final WebApp webApp = webFacet.getRoot();
			if(webApp != null)
			{
				GwtServletUtil.registerServletForService(gwtFacet, gwtModule, webApp, servletImpl, serviceName);
			}
		}
		     */
		return res.toArray(new PsiElement[res.size()]);
	}

	@Override
	protected String getCommandName()
	{
		return GwtBundle.message("newservice.command.name");
	}

	@Override
	protected String getActionName(PsiDirectory directory, String newName)
	{
		return GwtBundle.message("newservice.progress.text", newName);
	}

	@Nullable
	private static PsiClass generateServletClass(String name, PsiDirectory directory, VirtualFile gwtModuleDir,
			final PsiClass serviceClass) throws IncorrectOperationException
	{

		final VirtualFile client = gwtModuleDir.findChild("client");

		String pathFromClient = VirtualFileUtil.getRelativePath(directory.getVirtualFile(), client, '/');

		PsiDirectory serverDest = directory.getManager().findDirectory(gwtModuleDir);
		if(serverDest == null)
		{
			return null;
		}

		final StringTokenizer tokenizer = new StringTokenizer(SERVER_PACKAGE + "/" + pathFromClient, "/");
		while(tokenizer.hasMoreTokens())
		{
			final String dirName = tokenizer.nextToken();
			PsiDirectory nextDir = serverDest.findSubdirectory(dirName);
			if(nextDir == null)
			{
				nextDir = serverDest.createSubdirectory(dirName);
			}
			serverDest = nextDir;
		}

		return createClassFromTemplate(serverDest, name + RemoteServiceUtil.IMPL_SERVICE_SUFFIX, GwtTemplates.GWT_SERVICE_IMPL_JAVA,
				SERVICE_NAME_PROPERTY, serviceClass.getName(), QUALIFIED_SERVICE_NAME_PROPERTY, serviceClass.getQualifiedName());
	}
}