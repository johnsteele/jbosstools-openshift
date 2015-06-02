/*******************************************************************************
 * Copyright (c) 2015 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.openshift.internal.ui.wizard.connection;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.jface.databinding.fieldassist.ControlDecorationSupport;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.databinding.viewers.ViewerProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.jboss.tools.common.databinding.ObservablePojo;
import org.jboss.tools.common.ui.databinding.ValueBindingBuilder;
import org.jboss.tools.openshift.common.core.connection.IConnection;
import org.jboss.tools.openshift.core.connection.Connection;
import org.jboss.tools.openshift.core.connection.ConnectionFactory;
import org.jboss.tools.openshift.internal.common.ui.connection.BaseConnectionEditor;
import org.jboss.tools.openshift.internal.common.ui.connection.ConnectionWizardPageModel;
import org.jboss.tools.openshift.internal.common.ui.connection.ConnectionWizardPageModel.IConnectionAuthenticationProvider;
import org.jboss.tools.openshift.internal.common.ui.databinding.IsNotNullValidator;
import org.jboss.tools.openshift.internal.common.ui.databinding.RequiredControlDecorationUpdater;
import org.jboss.tools.openshift.internal.common.ui.detailviews.AbstractStackedDetailViews;
import org.jboss.tools.openshift.internal.common.ui.detailviews.AbstractStackedDetailViews.IDetailView;
import org.jboss.tools.openshift.internal.common.ui.utils.DataBindingUtils;

import com.openshift.restclient.authorization.IAuthorizationContext;

/**
 * Detail view used in the common Connection Wizard to
 * support establishing connections to v3 instance of OpenShift
 * 
 * @author Jeff Cantrill
 * @author Andre Dietisheim
 *
 */
public class ConnectionEditor extends BaseConnectionEditor {


	private static final String PROPERTY_SELECTED_DETAIL_VIEW = "selectedDetailView";
	
	private Map<String,IConnectionEditorDetailView> detailViews = new HashMap<String, IConnectionEditorDetailView>();
	private ConnectionEditorStackedDetailViews stackedViews ;
	private DetailViewModel detailViewModel = new DetailViewModel();
	private IConnectionEditorDetailView defaultDetailView;

	private Button chkRememberToken;
	private IObservableValue rememberTokenObservable;

	private Binding rememberTokenBinding;

	private IObservableValue authTypeObservable;

	private Binding selectedAuthTypeBinding;

	private ComboViewer cmbViewerAuthType;
	
	private class DetailViewModel extends ObservablePojo{
		private IConnectionEditorDetailView selectedDetailView;
		
		public IConnectionEditorDetailView getSelectedDetailView() {
			return this.selectedDetailView;
		}
		public void  setSelectedDetailView(IConnectionEditorDetailView view) {
			this.selectedDetailView = view;
		}
		public void setSelectedConnection(IObservableValue selectedConnection) {
			Connection connection = (Connection) selectedConnection.getValue();
			rememberTokenObservable.setValue(connection.isRememberPassword());
			setSelectedDetailView(detailViews.get(connection.getAuthScheme()));
		}
	}
	
	private class ConnectionEditorStackedDetailViews extends AbstractStackedDetailViews{

		public ConnectionEditorStackedDetailViews(IObservableValue detailViewModel, Object context, Composite parent,
				DataBindingContext dbc) {
			super(detailViewModel, context, parent, dbc);
		}

		@Override
		protected IDetailView[] getDetailViews() {
			return detailViews.values().toArray(new IConnectionEditorDetailView[detailViews.size()]);
		}
		
	}
	
	@Override
	public Composite createControls(Composite parent, ConnectionWizardPageModel pageModel, DataBindingContext dbc) {
		Composite composite = setControl(new Composite(parent, SWT.None));
		GridLayoutFactory.fillDefaults()
				.numColumns(2).margins(10, 10).spacing(10, 10).applyTo(composite);
		
		//remember token
		this.rememberTokenObservable = new WritableValue(Boolean.FALSE, Boolean.class);
		rememberTokenObservable.addValueChangeListener(changeListener);

		//detail views
		detailViews.put(IAuthorizationContext.AUTHSCHEME_OAUTH, new OAuthDetailView(pageModel, changeListener, pageModel.getContext(), rememberTokenObservable));
		detailViews.put(IAuthorizationContext.AUTHSCHEME_BASIC, new BasicAuthenticationDetailView(changeListener, pageModel.getContext(), rememberTokenObservable));
		defaultDetailView = detailViews.get(IAuthorizationContext.AUTHSCHEME_OAUTH);
		
		// auth type
		Label lblAuthType = new Label(composite, SWT.NONE);
		lblAuthType.setText("Protocol:");
		GridDataFactory.fillDefaults()
				.align(SWT.LEFT, SWT.CENTER).applyTo(lblAuthType);
		Combo cmbAuthType = new Combo(composite, SWT.DEFAULT);
		GridDataFactory.fillDefaults()
				.align(SWT.LEFT, SWT.CENTER).grab(true, false).applyTo(cmbAuthType);
		cmbViewerAuthType = new ComboViewer(cmbAuthType);
		cmbViewerAuthType.setContentProvider(ArrayContentProvider.getInstance());
		cmbViewerAuthType.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return element.toString();
			}
			
		});
		cmbViewerAuthType.setInput(detailViews.values());
		authTypeObservable = BeanProperties.value(PROPERTY_SELECTED_DETAIL_VIEW, IConnectionEditorDetailView.class)
				.observe(detailViewModel);
	

		//connection detail views
		final Composite detailsContainer = new Composite(composite, SWT.None);
		GridDataFactory.fillDefaults()
			.align(SWT.FILL, SWT.FILL).span(3,1).applyTo(detailsContainer);
		stackedViews = new ConnectionEditorStackedDetailViews(
				authTypeObservable,
				pageModel, 
				detailsContainer, 
				dbc);
		stackedViews.createControls(false);
		
		//remember token
		this.chkRememberToken = new Button(composite, SWT.CHECK);
		chkRememberToken.setText("&Save Token (could trigger secure storage login)");
		GridDataFactory.fillDefaults()
				.align(SWT.FILL, SWT.CENTER).span(2, 1).grab(true, false).applyTo(chkRememberToken);
		
		return composite;
	}
	
	@Override
	public void onVisible(IObservableValue detailViewModelObservable, ConnectionWizardPageModel pageModel, DataBindingContext dbc) {
		bindWidgetsToInternalModel(dbc);
		authTypeObservable.setValue(defaultDetailView);
	}
	
	@Override
	public void onInVisible(IObservableValue detailViewModelObservable, DataBindingContext dbc) {
		detailViewModel.getSelectedDetailView().onInVisible(detailViewModelObservable, dbc);
		disposeBindings();
	}

	private void bindWidgetsToInternalModel(DataBindingContext dbc) {
		//auth protocol
		selectedAuthTypeBinding = ValueBindingBuilder
				.bind(ViewerProperties.singleSelection().observe(cmbViewerAuthType))
				.validatingAfterGet(
						new IsNotNullValidator(
								ValidationStatus.cancel("Please select an authorization protocol.")))
				.to(authTypeObservable)
				.in(dbc);
		ControlDecorationSupport
				.create(selectedAuthTypeBinding, SWT.LEFT | SWT.TOP, null, new RequiredControlDecorationUpdater());
		
		// remember password
		this.rememberTokenBinding = ValueBindingBuilder
				.bind(WidgetProperties.selection().observe(chkRememberToken))
				.to(rememberTokenObservable)
				.in(dbc);
		ControlDecorationSupport.create(
				rememberTokenBinding, SWT.LEFT | SWT.TOP, null, new RequiredControlDecorationUpdater());
	}

	@Override
	public void onInVisible(IObservableValue detailsViewModelObservable, ConnectionWizardPageModel pageModel, DataBindingContext dbc) {
		detailViewModel.getSelectedDetailView().onInVisible(detailsViewModelObservable, dbc);
		disposeBindings();
	}

	private void disposeBindings() {
		DataBindingUtils.dispose(rememberTokenBinding);
		DataBindingUtils.dispose(selectedAuthTypeBinding);
		for (IDetailView view : stackedViews.getDetailViews()) {
			view.dispose();
		}			
		
	}
	
	@Override
	protected void onSelectedConnectionChanged(IObservableValue selectedConnection) {
		IConnection conn = (IConnection) selectedConnection.getValue();
		if(!(conn instanceof Connection)) return;
		detailViewModel.setSelectedConnection(selectedConnection);
	}

	@Override
	public boolean isViewFor(Object object) {
		return object instanceof ConnectionFactory;
	}
	
	private IConnectionEditorDetailView getDetailView() {
		return detailViewModel.getSelectedDetailView();
	}
	
	@Override
	protected IConnectionAuthenticationProvider createConnectionAuthenticationProvider(ConnectionWizardPageModel pageModel) {
		return new ConnectionAuthenticationProviderProxy();
	}
	
	private class  ConnectionAuthenticationProviderProxy implements IConnectionAuthenticationProvider {
		@Override
		public IConnection update(IConnection connection) {
			return getDetailView().getConnectionAuthenticationProvider().update(connection);
		}
		
	}

}