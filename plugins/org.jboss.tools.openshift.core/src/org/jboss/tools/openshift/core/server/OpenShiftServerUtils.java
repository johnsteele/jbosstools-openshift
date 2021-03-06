/*******************************************************************************
 * Copyright (c) 2015-2017 Red Hat Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Incorporated - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.openshift.core.server;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerAttributes;
import org.eclipse.wst.server.core.IServerType;
import org.eclipse.wst.server.core.IServerWorkingCopy;
import org.eclipse.wst.server.core.ServerCore;
import org.eclipse.wst.server.core.ServerUtil;
import org.eclipse.wst.server.core.internal.Server;
import org.eclipse.wst.server.core.internal.ServerWorkingCopy;
import org.eclipse.wst.server.core.model.ModuleDelegate;
import org.eclipse.wst.server.core.util.ProjectModule;
import org.jboss.ide.eclipse.as.core.server.IDeployableServer;
import org.jboss.ide.eclipse.as.core.util.IJBossToolingConstants;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.IControllableServerBehavior;
import org.jboss.tools.foundation.core.plugin.log.StatusFactory;
import org.jboss.tools.openshift.common.core.connection.ConnectionURL;
import org.jboss.tools.openshift.common.core.connection.ConnectionsRegistrySingleton;
import org.jboss.tools.openshift.common.core.connection.IConnection;
import org.jboss.tools.openshift.common.core.server.ServerUtils;
import org.jboss.tools.openshift.common.core.utils.ProjectUtils;
import org.jboss.tools.openshift.common.core.utils.StringUtils;
import org.jboss.tools.openshift.common.core.utils.UrlUtils;
import org.jboss.tools.openshift.common.core.utils.VariablesHelper;
import org.jboss.tools.openshift.core.connection.Connection;
import org.jboss.tools.openshift.core.util.OpenShiftResourceUniqueId;
import org.jboss.tools.openshift.internal.core.OpenShiftCoreActivator;
import org.jboss.tools.openshift.internal.core.WatchManager;
import org.jboss.tools.openshift.internal.core.preferences.OCBinary;
import org.jboss.tools.openshift.internal.core.util.ResourceUtils;
import org.osgi.service.prefs.BackingStoreException;

import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IPod;
import com.openshift.restclient.model.IResource;
import com.openshift.restclient.model.IService;

/**
 * @author Andre Dietisheim
 */
public class OpenShiftServerUtils {

	private static final String LIVERELOAD_PORT_KEY = "port";//Key to the port # of the host the LiveReload server need to proxy

	public static final String SERVER_PROJECT_QUALIFIER = "org.jboss.tools.openshift.core"; //$NON-NLS-1$

	public static final String ATTR_SERVICE = "org.jboss.tools.openshift.Service"; //$NON-NLS-1$
	public static final String ATTR_DEPLOYPROJECT = "org.jboss.tools.openshift.DeployProject"; //$NON-NLS-1$
	public static final String ATTR_SOURCE_PATH = "org.jboss.tools.openshift.SourcePath"; //$NON-NLS-1$
	public static final String ATTR_POD_PATH = "org.jboss.tools.openshift.PodPath"; //$NON-NLS-1$
	public static final String ATTR_ROUTE = "org.jboss.tools.openshift.Route"; //$NON-NLS-1$
	public static final String ATTR_DEVMODE_KEY = "org.jboss.tools.openshift.DevmodeKey"; //$NON-NLS-1$
	public static final String ATTR_DEBUG_PORT_KEY = "org.jboss.tools.openshift.DebugPortKey"; //$NON-NLS-1$
	public static final String ATTR_DEBUG_PORT_VALUE = "org.jboss.tools.openshift.DebugPortValue"; //$NON-NLS-1$

	public static final String ATTR_IGNORE_CONTEXT_ROOT = "org.jboss.tools.openshift.IgnoreContextRoot";//$NON-NLS-1$
	public static final String ATTR_OVERRIDE_PROJECT_SETTINGS = "org.jboss.tools.openshift.project.Override";//$NON-NLS-1$

	public static final String ATTR_CONNECTIONURL = "org.jboss.tools.openshift.Connection";//$NON-NLS-1$
	
	/** the OpensHift Server Type as defined in the plugin.xml. */
	public static final String OPENSHIFT_SERVER_TYPE = "org.jboss.tools.openshift.openshift.server.type";//$NON-NLS-1$

	public static final String SERVER_START_ON_CREATION = "org.jboss.tools.openshift.SERVER_START_ON_CREATION";

	private static final Collection<String> SERVER_ADAPTER_ALLOWED_RESOURCE_TYPES = Collections.unmodifiableCollection(
			Arrays.asList(
					ResourceKind.ROUTE,
					ResourceKind.SERVICE,
	                ResourceKind.REPLICATION_CONTROLLER,
	                ResourceKind.DEPLOYMENT_CONFIG,
	                ResourceKind.POD));
	
	/**
	 * Checks if the resource is allowed for OpenShift server adapter.
	 * 
	 * @param resource the OpenShift resource
	 * @return true if allowed
	 */
	public static boolean isAllowedForServerAdapter(IResource resource) {
	    return SERVER_ADAPTER_ALLOWED_RESOURCE_TYPES.contains(resource.getKind());
	}
	
	/**
	 * Returns the first openshift 3 server in the current workspace
	 * that matches the given OpenShift resource (service,
	 * deployment config, replication controller) name.
	 * 
	 * @see #ATTR_SERVICE
	 */
	public static IServer findServerForResource(String serviceName) {
		return findServerForResource(serviceName, ServerCore.getServers());
	}

	/**
	 * Returns the first openshift 3 server within the given list of servers
	 * that matches the given OpenShift resource (service,
     * deployment config, replication controller) name.
	 * 
	 * @see #ATTR_SERVICE
	 */
	public static IServer findServerForResource(String serviceName, IServer[] servers) {
		if (StringUtils.isEmpty(serviceName)
				|| servers == null
				|| servers.length == 0) {
			return null;
		}

		final IServerType serverType = getServerType();
		if (serverType == null) {
			return null;
		}

		return Stream.of(servers)
				.filter(server -> serverType.equals(server.getServerType())
						&& server.getAttribute(ATTR_SERVICE, "").equals(serviceName))
				.findFirst().orElse(null);
	}
	
	public static IServerType getServerType() {
		return ServerCore.findServerType(OpenShiftServer.SERVER_TYPE_ID);
	}
	
	public static String getServerName(IResource resource, IConnection connection) {
		if (resource == null) {
			return null;
		}

		String baseName = new StringBuilder(resource.getName())
		        .append(" (")
		        .append(resource.getKind())
				.append(") at OpenShift 3 (")
				.append(UrlUtils.cutPort(UrlUtils.cutScheme(connection.getHost())))
				.append(")")
				.toString();
		return ServerUtils.getServerName(baseName);
	}

	public static void updateServer(String serverName, String host, String connectionUrl, IResource resource, String sourcePath, 
			String podPath, IProject deployProject, String routeURL, String devmodeKey, String debugPortKey, String debugPortValue,
			IServerWorkingCopy server) {
		String deployProjectName = ProjectUtils.getName(deployProject);
		updateServer(serverName, host, connectionUrl, deployProjectName, OpenShiftResourceUniqueId.get(resource), 
				sourcePath, podPath, routeURL, devmodeKey, debugPortKey, debugPortValue, server);
	}

	public static void updateServer(String serverName, String host, String connectionUrl, String deployProjectName, String serviceId, 
			String sourcePath, String podPath, String routeURL, String devmodeKey, String debugPortKey, String debugPortValue, 
			IServerWorkingCopy server) {
		updateServer(server);

		server.setName(serverName);
		server.setHost(host);

		server.setAttribute(ATTR_CONNECTIONURL, connectionUrl);
		server.setAttribute(ATTR_DEPLOYPROJECT, deployProjectName);
		server.setAttribute(ATTR_SOURCE_PATH, sourcePath);
		server.setAttribute(ATTR_POD_PATH, podPath);
		server.setAttribute(ATTR_SERVICE, serviceId);
		server.setAttribute(ATTR_ROUTE, routeURL);
		server.setAttribute(ATTR_DEVMODE_KEY, devmodeKey);
		server.setAttribute(ATTR_DEBUG_PORT_KEY, debugPortKey);
		server.setAttribute(ATTR_DEBUG_PORT_VALUE, debugPortValue);
	}

	/**
	 * Sets the given value for the given attribute in the given server and saves it.
	 * 
	 * @param attribute
	 * @param value
	 * @param server
	 * @throws CoreException
	 */
	public static void updateServer(String attribute, String value, IServerWorkingCopy server) throws CoreException {
		if (!StringUtils.isEmpty(attribute)) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					"Could not update server project, setting name missing."));
		}
		if (!StringUtils.isEmpty(value)) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					NLS.bind("Could not update server project, value for setting {0} is missing.", attribute)));
		}
		server.setAttribute(attribute, value);
		server.save(true, new NullProgressMonitor());
	}
	
	private static void updateServer(IServerWorkingCopy server) {
		server.setAttribute(IDeployableServer.SERVER_MODE, OpenShiftServer.OPENSHIFT3_MODE_ID);
		((ServerWorkingCopy) server).setAutoPublishSetting(Server.AUTO_PUBLISH_RESOURCE);
		server.setAttribute(IJBossToolingConstants.IGNORE_LAUNCH_COMMANDS, String.valueOf(Boolean.TRUE));
		int webPort = 80;//TODO should we determine the webPort from the route?
		server.setAttribute(IJBossToolingConstants.WEB_PORT, webPort);
		server.setAttribute(LIVERELOAD_PORT_KEY, webPort);//So that we can open via LiveReload
		server.setAttribute(IJBossToolingConstants.WEB_PORT_DETECT, Boolean.FALSE.toString());
		server.setAttribute(IDeployableServer.DEPLOY_DIRECTORY_TYPE, IDeployableServer.DEPLOY_CUSTOM);
		server.setAttribute(IDeployableServer.ZIP_DEPLOYMENTS_PREF, true);
	}

	public static void updateServerProject(String connectionUrl, IResource resource, String sourcePath, String podPath, String routeURL, 
			String devmodeKey, String debugPortKey, String debugPortValue, IProject project) {
		updateServerProject(connectionUrl, OpenShiftResourceUniqueId.get(resource), sourcePath, podPath, routeURL, 
				devmodeKey, debugPortKey, debugPortValue, project);
	}

	public static void updateServerProject(String connectionUrl, String serviceId, String sourcePath, String podPath, String routeURL, 
			String devmodeKey, String debugPortKey, String debugPortValue, IProject project) {
		IEclipsePreferences node = ServerUtils.getProjectNode(SERVER_PROJECT_QUALIFIER, project);
		node.put(ATTR_CONNECTIONURL, connectionUrl);
		node.put(ATTR_DEPLOYPROJECT, project.getName());
		node.put(ATTR_SOURCE_PATH, sourcePath);
		node.put(ATTR_SERVICE, serviceId);
		updateProjectNode(ATTR_POD_PATH, podPath, node);
		updateProjectNode(ATTR_ROUTE, routeURL, node);
		updateProjectNode(ATTR_DEVMODE_KEY, devmodeKey, node);
		updateProjectNode(ATTR_DEBUG_PORT_KEY, debugPortKey, node);
		updateProjectNode(ATTR_DEBUG_PORT_VALUE, debugPortValue, node);
		saveProject(node);
	}

	private static void updateProjectNode(String attribute, String value, IEclipsePreferences node) {
		if (value != null) {
			node.put(attribute, value);
		} else {
			node.remove(attribute);
		}
	}
	
	public static void updateServerProject(String attribute, String value, IProject project) throws CoreException {
		if (!StringUtils.isEmpty(attribute)) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					"Could not update server project, setting name missing."));
		}
		if (!StringUtils.isEmpty(value)) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					NLS.bind("Could not update server project, value for setting {0} is missing.", attribute)));
		}
		IEclipsePreferences node = ServerUtils.getProjectNode(SERVER_PROJECT_QUALIFIER, project);
		node.put(attribute, value);
		saveProject(node);
	}

	private static void saveProject(IEclipsePreferences node) {
		try {
			node.flush();
		} catch (BackingStoreException e) {
			// TODO: throw, dont swallow
			OpenShiftCoreActivator.pluginLog().logError(e);
		}
	}

	public static IModule findProjectModule(IProject p) {
		IModule[] all = org.eclipse.wst.server.core.ServerUtil.getModules(p);
		for( int i = 0; i < all.length; i++ ) {
			ModuleDelegate md = (ModuleDelegate)all[i].loadAdapter(ModuleDelegate.class, new NullProgressMonitor());
			if( md instanceof ProjectModule 
					&& !(md instanceof org.eclipse.jst.j2ee.internal.deployables.BinaryFileModuleDelegate)) {
				return all[i];
			}
		}
		return null;
	}
	
	public static IProject getDeployProject(IServerAttributes server) {
		// TODO: implement override project settings with server settings
		return ProjectUtils.getProject(getDeployProjectName(server));
	}

	public static String getDeployProjectName(IServerAttributes server) {
		if (server == null) {
			return null;
		}
		return server.getAttribute(ATTR_DEPLOYPROJECT, (String) null);
	}

	public static boolean isIgnoresContextRoot(IServerAttributes server) {
		return server.getAttribute(ATTR_IGNORE_CONTEXT_ROOT, true);
	}

	/**
	 * Returns true if the given server is an OpenShift one, false otherwise.
	 * 
	 * @param server
	 *            the server adapter to check
	 * @return true or false
	 */
	public static boolean isOpenShiftRuntime(IServerAttributes server) {
		return OPENSHIFT_SERVER_TYPE.equals(server.getServerType().getId());
	}

	public static IServerWorkingCopy create(String name) throws CoreException {
		final IServerWorkingCopy serverWorkingCopy = 
				(IServerWorkingCopy) getServerType().createServer(name, null, null);
		return serverWorkingCopy;
	}

	/**
	 * Returns the {@link Connection} for the given server. Throws a
	 * {@link CoreException} it none was found.
	 * 
	 * @param server
	 * @return
	 * @throws CoreException
	 */
	public static Connection getConnectionChecked(IServerAttributes server) throws CoreException {
		Connection connection = getConnection(server);
		if (connection == null) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					NLS.bind("Could not find the connection for server {0}."
							+ " Your server adapter might refer to an inexistant connection."
							, server == null? "" : server.getName())));
		}
		return connection;
	}

	/**
	 * Returns the connection for the given server. Returns {@code null} if none was
	 * found.
	 * 
	 * @param server
	 * @return
	 */
	public static Connection getConnection(IServerAttributes server) {
		if (server == null) {
			return null;
		}
		
		Connection connection = null;
		try {
			String url = getConnectionURL(server);
			if (StringUtils.isEmpty(url)) {
				return null;
			}
			ConnectionURL connectionUrl = ConnectionURL.forURL(url);
			if (connectionUrl != null) {
				connection = ConnectionsRegistrySingleton.getInstance().getByUrl(connectionUrl, Connection.class);
			}
			if (connection == null) {
				OpenShiftCoreActivator.pluginLog().logError(NLS.bind(
						"Could not find an existing OpenShift connection to host {0} with user {1} for server {2}",
						new String[] { connectionUrl.getHost(), connectionUrl.getUsername(), server.getName() }));
			}
		} catch (UnsupportedEncodingException | MalformedURLException e) {
			OpenShiftCoreActivator.pluginLog()
					.logError(NLS.bind("Connection url stored in server {0} is malformed.", server.getName()), e);
		}

		return connection;
	}

	public static String getConnectionURL(IServerAttributes server) {
		return getAttribute(ATTR_CONNECTIONURL, server);
	}

	public static IResource getResource(IServerAttributes attributes, IProgressMonitor monitor) {
		return getResource(attributes, getConnection(attributes), monitor);
	}

	/**
	 * Returns the {@link IResource} that's stored in the given server. Throws a
	 * {@link CoreException} if none was found.
	 * 
	 * @param server
	 * @param connection
	 * @param monitor
	 * @return
	 * @throws CoreException
	 */
	public static IResource getResourceChecked(IServerAttributes server, Connection connection, IProgressMonitor monitor) 
			throws CoreException {
		IResource resource = getResource(server, connection, monitor);
		if (resource == null) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					NLS.bind("Could not find the resource for server {0}." 
							+ " Your server adapter might refer to an inexistant resource.",
							server == null? "" : server.getName())));
		}
		return resource;
	}

	/**
	 * Returns the OpenShift resource (service, replication controller) for the
	 * given server. Returns {@code null} if none was found. 
	 * It gets the resource name and type from server settings and requests the
	 * resource from the OpenShit server. It should thus <strong>NOT</strong> be called from the 
	 * UI thread.
	 * 
	 * @param server
	 *            the server (attributes) to get the resource name from
	 * @param connection
	 *            the connection (to the OpenShift server) to retrieve the resource
	 *            from
	 * @return the OpenShift resource
	 */
	public static IResource getResource(IServerAttributes server, Connection connection, IProgressMonitor monitor) {
		// TODO: implement override project settings with server settings
		String uniqueId = getAttribute(ATTR_SERVICE, server);
		if (StringUtils.isEmpty(uniqueId)) {
			return null;
		}
		if (connection == null) {
			return null;
		}
		String projectName = OpenShiftResourceUniqueId.getProjectName(uniqueId);
		String kind = OpenShiftResourceUniqueId.getKind(uniqueId);
		List<IResource> resources = connection.getResources(kind, projectName);
		IResource resource = OpenShiftResourceUniqueId.getByUniqueId(uniqueId, resources);
		if (resource != null) {
			WatchManager.getInstance().startWatch(resource.getProject(), connection);
		}
		return resource;
	}

	public static String getRouteURL(IServerAttributes server) {
		return getAttribute(ATTR_ROUTE, server);
	}

	/**
	 * Returns the pod path from the given server. The value is fetched from the
	 * persistent server attributes, no loading from remote server is done.
	 * 
	 * @param server the server to get the pod path attribute from
	 * @return
	 */	
	public static String getPodPath(IServerAttributes server) {
		// TODO: implement override project settings with server settings
		return getAttribute(OpenShiftServerUtils.ATTR_POD_PATH, server);
	}
	
	public static String getDevmodeKey(IServerAttributes server) {
		return getAttribute(ATTR_DEVMODE_KEY, server);
	}

	public static String getDebugPortKey(IServerAttributes server) {
		return getAttribute(ATTR_DEBUG_PORT_KEY, server);
	}

	public static String getDebugPort(IServerAttributes server) {
		return getAttribute(ATTR_DEBUG_PORT_VALUE, server);
	}

	/**
	 * Loads the pod path from docker image metadata for the given resource and server.
	 * Should <strong>NOT</strong> be executed from display thread.
	 * 
	 * @param resource the resource to derive the pod and docker image from
	 * @param server the server to derive the openshift connection from
	 * @return
	 */
	public static String loadPodPath(IResource resource, IServer server) {
		DockerImageLabels metaData = DockerImageLabels.getInstance(resource, getBehaviour(server));
		return metaData.getPodPath();
	}

	public static IControllableServerBehavior getBehaviour(IServer server) {
		return server.getAdapter(IControllableServerBehavior.class);
	}
	
	public static OpenShiftServerBehaviour getOpenShiftServerBehaviour(IServer server) throws CoreException {
		IControllableServerBehavior serverBehavior = getBehaviour(server);
		if (!(serverBehavior instanceof OpenShiftServerBehaviour)) {
			throw toCoreException("Unable to find a OpenShiftServerBehaviour instance");
		}
		return (OpenShiftServerBehaviour) serverBehavior;
	}

	public static OpenShiftServerBehaviour getOpenShiftServerBehaviour(ILaunchConfiguration configuration) throws CoreException {
		return getOpenShiftServerBehaviour(ServerUtil.getServer(configuration));
	}

	/**
	 * Creates an {@link RSync}
	 * @param server the {@link IServer} on which the {@code rsync} operation will be performed
	 * @return the {@link RSync} to be used to execute the command.
	 * @throws CoreException
	 */
	public static RSync createRSync(final IServer server, IProgressMonitor monitor) throws CoreException {
		assertServerNotNull(server);

		final String location = OCBinary.getInstance().getLocation();
		if (location == null) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					"Binary for oc-tools could not be found. Please open the OpenShift 3 Preference Page and set the location of the oc binary."));
		}

		final IResource resource = getResource(server, monitor);
		if (resource == null) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					NLS.bind("Server {0} could not determine the service to publish to.", server.getName())));
		}

		String podPath = loadPodPathIfEmpty(server, resource);

		return new RSync(resource, podPath, server);
	}

	private static String loadPodPathIfEmpty(final IServer server, final IResource resource) throws CoreException {
		String podPath = getPodPath(server);
		if (StringUtils.isEmpty(podPath)) {
			podPath = loadPodPath(resource, server);
			if (StringUtils.isEmpty(podPath)) {
				throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
					NLS.bind("Server {0} could not determine the destination directory to publish to.", server.getName())));
			}
		}
		return podPath;
	}
	
	public static String getSourcePath(IServerAttributes server) {
		// TODO: implement override project settings with server settings
		String rawSourcePath = getAttribute(ATTR_SOURCE_PATH, server);
		if (org.apache.commons.lang.StringUtils.isBlank(rawSourcePath)) {
			return rawSourcePath;
		}
		return VariablesHelper.replaceVariables(rawSourcePath);
	}

	public static boolean isOverridesProject(IServerAttributes server) {
		return server.getAttribute(ATTR_OVERRIDE_PROJECT_SETTINGS, false);
	}

	/**
	 * Returns the attribute value for the given name and project. The given
	 * default value is return if the value doesnt exist or cannot be retrieved.
	 * 
	 * @param project
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	private static String getProjectAttribute(String name, String defaultValue, IProject project) {
		return ServerUtils.getProjectAttribute(name, defaultValue, SERVER_PROJECT_QUALIFIER, project);
	}
	
	/**
	 * Returns the deployment config for the given server (attributes). The
	 * match is done by the service that the given (openshift server) is bound
	 * to. 
	 * This method does remote calls to the OpenShift server and thus should
	 * never be called from the UI thread.
	 * 
	 * @param server
	 * @return the replication controller for the given server
	 * 
	 * @see #getResource(IServerAttributes)
	 * @see ResourceUtils#getPodsFor(IService, Collection)
	 */
	public static IDeploymentConfig getDeploymentConfig(IServerAttributes server, IProgressMonitor monitor) throws CoreException {
		assertServerNotNull(server);
		
		Connection connection = getConnectionChecked(server);
		IResource resource = getResourceChecked(server, connection, monitor);
		IDeploymentConfig dc = ResourceUtils.getDeploymentConfigFor(resource, connection);
		if (dc == null) {
			throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
		            NLS.bind("Could not find deployment config for {0}. "
		                    + "Your build might be still running and pods not created yet or "
		                    + "there might be no labels on your pods pointing to the wanted deployment config.", 
							server.getName())));
		}

		return dc;
	}

	/**
	 * Returns the value for the given key and server. Will first query the
	 * server and if no value was found the deploy project is queried.
	 * 
	 * @param key
	 * @param server
	 * @return
	 */
	protected static String getAttribute(String key, IServerAttributes server) {
		if (server == null) {
			return null;
		}
		String attribute = server.getAttribute(key, (String) null);
		if (attribute == null) {
			attribute = getProjectAttribute(key, null, getDeployProject(server));
		}
		return attribute;
	}

	/**
	 * Return {@code true} if the given server has a deploy project that is a
	 * java project.
	 * 
	 * @param server
	 * @return
	 * 
	 * @see #getDeployProject(IServerAttributes)
	 */
	public static boolean isJavaProject(IServerAttributes server) {
		IProject p = getDeployProject(server);
		try {
			return ProjectUtils.isAccessible(p) && p.hasNature(JavaCore.NATURE_ID);
		} catch (CoreException e) {
			OpenShiftCoreActivator.pluginLog().logError(e);
		}
		return false;
	}
	
    private static void assertServerNotNull(IServerAttributes server) throws CoreException {
        if (server == null) {
            throw new CoreException(OpenShiftCoreActivator.statusFactory().errorStatus(
                    "Could not determine the server to use."));
        }
    }
    
	public static CoreException toCoreException(String msg, Exception e) {
		return new CoreException(StatusFactory.errorStatus(OpenShiftCoreActivator.PLUGIN_ID, msg, e));
	}

	public static CoreException toCoreException(String msg) {
		return toCoreException(msg, null);
	}
	
	/**
	 * Returns all pods for the given server. Returns an empty list otherwise.
	 * 
	 * @param server
	 * @return
	 */
	public static Collection<IPod> getAllPods(IServer server, IProgressMonitor monitor) {
		Connection connection = getConnection(server);
		if (connection == null) {
			return Collections.emptyList();
		}
		IResource resource = getResource(server, connection, monitor);
		if (resource == null) {
			return Collections.emptyList();
		}
		List<IPod> collection = new ArrayList<>();
		List<IPod> pods = connection.getResources(ResourceKind.POD, resource.getNamespace());
		List<IPod> servicePods = ResourceUtils.getPodsFor(resource, pods);
		collection.addAll(pods);
		collection.addAll(servicePods);
		return collection;
	}

}
