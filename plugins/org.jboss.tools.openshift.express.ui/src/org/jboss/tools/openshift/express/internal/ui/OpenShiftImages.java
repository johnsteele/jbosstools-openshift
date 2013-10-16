/*******************************************************************************
 * Copyright (c) 2011 Red Hat Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Incorporated - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.openshift.express.internal.ui;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.jboss.tools.openshift.express.internal.ui.utils.ImageRepository;

/**
 * @author Andre Dietisheim
 */
public class OpenShiftImages {

	private static final String ICONS_FOLDER = "icons/";

	private static final ImageRepository repo =
			new ImageRepository(
					ICONS_FOLDER, OpenShiftUIActivator.getDefault(), OpenShiftUIActivator.getDefault().getImageRegistry());

	public static final ImageDescriptor OPENSHIFT_LOGO_DARK = repo.create("openshift-logo-dark.png"); //$NON-NLS-1$ 
	public static final ImageDescriptor OPENSHIFT_LOGO_WHITE_ICON = repo.create("openshift-logo-white-icon.png"); //$NON-NLS-1$ 
	public static final Image OPENSHIFT_LOGO_WHITE_ICON_IMG = repo.getImage("openshift-logo-white-icon.png"); //$NON-NLS-1$ 
	public static final ImageDescriptor OPENSHIFT_LOGO_WHITE_MEDIUM = repo.create("openshift-logo-white-medium.png"); //$NON-NLS-1$ 	
	public static final Image OPENSHIFT_LOGO_WHITE_MEDIUM_IMG = repo.getImage("openshift-logo-white-medium.png"); //$NON-NLS-1$ 	

	public static final ImageDescriptor OPEN_BROWSER = repo.create("open-browser.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor REFRESH = repo.create("refresh.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor GO_INTO = repo.create("go-into.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor TASK_REPO_NEW = repo.create("task-repository-new.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor EDIT = repo.create("edit.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor QUERY_NEW = repo.create("query-new.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor REPO_MIDDLE = repo.create("repository-middle.gif"); //$NON-NLS-1$ 
	public static final Image REPO_MIDDLE_IMG = repo.getImage("repository-middle.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor QUERY = repo.create("query.gif"); //$NON-NLS-1$ 
	public static final Image QUERY_IMG = repo.getImage("query.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor PROJECTS = repo.create("projects.gif"); //$NON-NLS-1$ 
	public static final Image PROJECTS_IMG = repo.getImage("projects.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor TASK_REPO = repo.create("task-repository.gif"); //$NON-NLS-1$ 
	public static final Image TASK_REPO_IMG = repo.getImage("task-repository.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor SYSTEM_PROCESS = repo.create("systemprocess.gif"); //$NON-NLS-1$ 
	public static final Image SYSTEM_PROCESS_IMG = repo.getImage("systemprocess.gif"); //$NON-NLS-1$ 
	public static final ImageDescriptor GLOBE = repo.create("globe.png"); //$NON-NLS-1$ 
	public static final Image GLOBE_IMG = repo.getImage("globe.png"); //$NON-NLS-1$ 
}
