/******************************************************************************* 
 * Copyright (c) 2017 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.cdk.ui.bot.test.server.editor;

import static org.junit.Assert.assertTrue;

import org.eclipse.reddeer.common.logging.Logger;
import org.eclipse.reddeer.common.wait.TimePeriod;
import org.eclipse.reddeer.common.wait.WaitUntil;
import org.eclipse.reddeer.common.wait.WaitWhile;
import org.eclipse.reddeer.eclipse.selectionwizard.NewMenuWizard;
import org.eclipse.reddeer.eclipse.wst.server.ui.wizard.NewServerWizardPage;
import org.eclipse.reddeer.junit.runner.RedDeerSuite;
import org.eclipse.reddeer.workbench.core.condition.JobIsRunning;
import org.jboss.tools.cdk.reddeer.core.condition.SystemJobIsRunning;
import org.jboss.tools.cdk.reddeer.server.ui.CDEServersView;
import org.jboss.tools.cdk.reddeer.server.ui.editor.CDK3ServerEditor;
import org.jboss.tools.cdk.reddeer.server.ui.wizard.NewCDK3ServerContainerWizardPage;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Class tests CDK3 server editor page
 * 
 * @author odockal
 *
 */
@RunWith(RedDeerSuite.class)
public class CDK3ServerEditorTest extends CDKServerEditorAbstractTest {

	private static final Logger log = Logger.getLogger(CDK3ServerEditorTest.class);
	
	private String hypervisor = MINISHIFT_HYPERVISOR;

	@BeforeClass
	public static void setUpEnvironment() {
		checkMinishiftParameters();
		checkMinishiftProfileParameters();
	}
	
	@Before
	public void setup() {
		this.hypervisor = MINISHIFT_HYPERVISOR;
	}
	
	@Override
	protected void setupServerWizardPage(NewMenuWizard dialog) {
		NewServerWizardPage page = new NewServerWizardPage(dialog);
		page.selectType(SERVER_TYPE_GROUP, CDK3_SERVER_NAME);
		page.setName(SERVER_ADAPTER_3);
		dialog.next();
		NewCDK3ServerContainerWizardPage containerPage = new NewCDK3ServerContainerWizardPage();
		containerPage.setCredentials(USERNAME, PASSWORD);
		log.info("Setting hypervisor to: " + hypervisor);
		containerPage.setHypervisor(hypervisor);
		log.info("Setting binary to " + MINISHIFT);
		containerPage.setMinishiftBinary(MINISHIFT);
		new WaitWhile(new SystemJobIsRunning(getJobMatcher(MINISHIFT_VALIDATION_JOB)), TimePeriod.MEDIUM, false);
	}
	
	public void setServerEditor() {
		serversView = new CDEServersView();
		serversView.open();
		serversView.getServer(getServerAdapter()).open();
		editor = new CDK3ServerEditor(getServerAdapter());
		editor.activate();
		new WaitUntil(new JobIsRunning(), TimePeriod.getCustom(1), false);
	}

	@Override
	protected String getServerAdapter() {
		return SERVER_ADAPTER_3;
	}

	@Test
	public void testCDK3ServerEditor() {
		assertCDKServerWizardFinished();
		setServerEditor();

		assertTrue(editor.getUsernameLabel().getText().equalsIgnoreCase("minishift_username"));
		assertTrue(editor.getPasswordLabel().getText().equalsIgnoreCase("minishift_password"));
		assertTrue(editor.getDomainCombo().getSelection().equalsIgnoreCase(CREDENTIALS_DOMAIN));
		assertTrue(editor.getHostnameLabel().getText().equalsIgnoreCase(SERVER_HOST));
		assertTrue(
				((CDK3ServerEditor) editor).getHypervisorCombo().getSelection().equalsIgnoreCase(MINISHIFT_HYPERVISOR));
		assertTrue(editor.getServernameLabel().getText().equals(getServerAdapter()));
		assertTrue(((CDK3ServerEditor) editor).getMinishiftBinaryLabel().getText().equals(MINISHIFT));
		assertTrue(((CDK3ServerEditor) editor).getMinishiftHomeLabel().getText().contains(".minishift"));
	}

	@Test
	public void testCDK3Hypervisor() {
		// change the hypervisor
		this.hypervisor = ANOTHER_HYPERVISOR;
		assertCDKServerWizardFinished();
		setServerEditor();

		assertTrue(
				((CDK3ServerEditor) editor).getHypervisorCombo().getSelection().equalsIgnoreCase(ANOTHER_HYPERVISOR));
	}

	@Test
	public void testInvalidMinishiftLocation() {
		assertCDKServerWizardFinished();
		setServerEditor();

		checkEditorStateAfterSave(EXISTING_PATH, false);
		checkEditorStateAfterSave(NON_EXECUTABLE_FILE, false);
		checkEditorStateAfterSave(NON_EXISTING_PATH, false);
		checkEditorStateAfterSave(EXECUTABLE_FILE, false);
		checkEditorStateAfterSave(MINISHIFT_PROFILE, false);
		checkEditorStateAfterSave(MINISHIFT, true);
	}
	
}