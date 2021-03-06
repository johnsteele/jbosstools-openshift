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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.reddeer.common.condition.WaitCondition;
import org.eclipse.reddeer.common.exception.RedDeerException;
import org.eclipse.reddeer.common.exception.WaitTimeoutExpiredException;
import org.eclipse.reddeer.common.logging.Logger;
import org.eclipse.reddeer.common.util.Display;
import org.eclipse.reddeer.common.wait.TimePeriod;
import org.eclipse.reddeer.common.wait.WaitUntil;
import org.eclipse.reddeer.common.wait.WaitWhile;
import org.eclipse.reddeer.eclipse.selectionwizard.NewMenuWizard;
import org.eclipse.reddeer.eclipse.wst.server.ui.cnf.ServersView2;
import org.eclipse.reddeer.swt.condition.ControlIsEnabled;
import org.eclipse.reddeer.swt.condition.ShellIsAvailable;
import org.eclipse.reddeer.swt.impl.button.CancelButton;
import org.eclipse.reddeer.swt.impl.button.FinishButton;
import org.eclipse.reddeer.swt.impl.button.OkButton;
import org.eclipse.reddeer.swt.impl.shell.DefaultShell;
import org.eclipse.reddeer.swt.impl.text.LabeledText;
import org.eclipse.reddeer.workbench.core.condition.JobIsRunning;
import org.eclipse.reddeer.workbench.handler.EditorHandler;
import org.eclipse.ui.IEditorPart;
import org.jboss.tools.cdk.reddeer.core.condition.SystemJobIsRunning;
import org.jboss.tools.cdk.reddeer.server.exception.CDKServerException;
import org.jboss.tools.cdk.reddeer.server.ui.editor.CDEServerEditor;
import org.jboss.tools.cdk.reddeer.server.ui.editor.CDK3ServerEditor;
import org.jboss.tools.cdk.reddeer.server.ui.wizard.NewCDKServerWizard;
import org.jboss.tools.cdk.ui.bot.test.server.wizard.CDKServerWizardAbstractTest;
import org.jboss.tools.cdk.ui.bot.test.utils.CDKTestUtils;
import org.junit.After;

/**
 * Abstract test class for CDK 3.2+ server editor 
 * @author odockal
 *
 */
public abstract class CDKServerEditorAbstractTest extends CDKServerWizardAbstractTest {
	
	protected ServersView2 serversView;

	protected CDEServerEditor editor;

	protected static final String ANOTHER_HYPERVISOR = "virtualbox";
	
	private static Logger log = Logger.getLogger(CDKServerEditorAbstractTest.class);

	public abstract void setServerEditor();
	
	@After
	public void tearDown() {
		cleanUp();
	}
	
	protected abstract void setupServerWizardPage(NewMenuWizard dialog);
	
	public void cleanUp() {
		if (editor != null) {
			if (!editor.isActive()) {
				editor.activate();
			}
			editor.save();
			editor.close();
			editor = null;
		}
		if (serversView != null) {
			if (serversView.isOpen()) {
				serversView.close();
				serversView = null;
			}
		}
	}

	public void addCDKServer() {
		NewCDKServerWizard dialog = (NewCDKServerWizard)CDKTestUtils.openNewServerWizardDialog();
		
		try {
			setupServerWizardPage(dialog);
			new WaitUntil(new ControlIsEnabled(new FinishButton()), TimePeriod.MEDIUM, false);
			dialog.finish(TimePeriod.MEDIUM);
		} catch (RedDeerException coreExc) {
			new CancelButton().click();
			throw new CDKServerException("Exception occured in CDK server wizard, wizard was canceled", coreExc);
		}
	}
	
	protected void assertCDKServerWizardFinished() {
		try {
			addCDKServer();
		} catch (CDKServerException exc) {
			exc.printStackTrace();
			fail("Fails to create " + getServerAdapter() + " Server via New Server Wizard due to " + exc.getMessage());
		}
	}
	
	protected void checkEditorStateAfterSave(String location, boolean canSave) {
		LabeledText label = ((CDK3ServerEditor) editor).getMinishiftBinaryLabel();
		label.setText(location);
		new WaitUntil(new SystemJobIsRunning(getJobMatcher(MINISHIFT_VALIDATION_JOB)), TimePeriod.SHORT, false);
		new WaitWhile(new SystemJobIsRunning(getJobMatcher(MINISHIFT_VALIDATION_JOB)), TimePeriod.DEFAULT, false);
		if (canSave) {
			verifyEditorCanSave();
		} else {
			verifyEditorCannotSave();
		}
	}

	/**
	 * We need to override save method from EditorHandler to be executed in async
	 * thread in order to be able to work with message dialog from invalid server
	 * editor location
	 * 
	 * @param editor
	 *            IEditorPart to work with during saving
	 */
	private void performSave(final IEditorPart editor) {
		EditorHandler.getInstance().activate(editor);
		Display.asyncExec(new Runnable() {

			@Override
			public void run() {
				editor.doSave(new NullProgressMonitor());

			}
		});
		new WaitUntil(new WaitCondition() {

			@Override
			public boolean test() {
				return !editor.isDirty();
			}

			@Override
			public String description() {
				return " editor is not dirty...";
			}

			@Override
			public <T> T getResult() {
				return null;
			}

			@Override
			public String errorMessageWhile() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String errorMessageUntil() {
				// TODO Auto-generated method stub
				return null;
			}
		}, TimePeriod.MEDIUM);
	}

	private void verifyEditorCannotSave() {
		assertTrue(editor.isDirty());
		try {
			performSave(editor.getEditorPart());
			new WaitWhile(new JobIsRunning());
			fail("Editor was saved successfully but exception was expected");
		} catch (WaitTimeoutExpiredException exc) {
			log.info("WaitTimeoutExpiredException occured, editor was not saved as expected");
		}
		errorDialogAppeared();
		assertTrue(editor.isDirty());
	}

	private void verifyEditorCanSave() {
		assertTrue(editor.isDirty());
		try {
			performSave(editor.getEditorPart());
			log.info("Editor was saved as expected");
		} catch (WaitTimeoutExpiredException exc) {
			fail("Editor was not saved successfully but exception was thrown");
		}
		assertFalse(editor.isDirty());
	}

	private void errorDialogAppeared() {
		try {
			new WaitUntil(new ShellIsAvailable(new DefaultShell(getServerAdapter())), TimePeriod.MEDIUM);
			log.info("Error Message Dialog appeared as expected");
		} catch (WaitTimeoutExpiredException exc) {
			log.error(exc.getMessage());
			fail("Error Message Dialog did not appear while trying to save editor");
		}
		new OkButton().click();
	}
	
}
