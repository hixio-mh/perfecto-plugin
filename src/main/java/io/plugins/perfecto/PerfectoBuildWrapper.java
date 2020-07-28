package io.plugins.perfecto;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.*;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import io.plugins.perfecto.credentials.PerfectoCredentials;
import jenkins.model.Jenkins;
import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;


/**
 * {@link BuildWrapper} that sets up the Perfecto connect tunnel and populates environment variable.
 *
 * @author Dushyantan Satike
 */
public class PerfectoBuildWrapper extends BuildWrapper implements Serializable {

	/**
	 * Logger instance.
	 */
	private static final Logger logger = Logger.getLogger(PerfectoBuildWrapper.class.getName());

	//	/**
	//	 * Environment variable key which contains the Perfecto user name.
	//	 */
	//	public static final String PERFECTO_USER = "perfectoUser";
	/**
	 * Environment variable key which contains the Perfecto Security token.
	 */
	public static final String PERFECTO_SECURITY_TOKEN = "perfectoSecurityToken";
	/**
	 * Environment variable key which contains the Perfecto Cloud name.
	 */
	public static final String PERFECTO_CLOUD_NAME = "perfectoCloudName";

	public static final Pattern ENVIRONMENT_VARIABLE_PATTERN = Pattern.compile("[$|%][{]?([a-zA-Z_][a-zA-Z0-9_]+)[}]?");

	/**
	 * The custom Tunnel Id name.
	 */
	private String tunnelIdCustomName = "tunnelId";
	/**
	 * Indicates whether Perfecto Connect should be started as part of the build.
	 */
	private boolean enablePerfectoConnect;
	/**
	 * The path of the Perfecto connect location.
	 */
	private String perfectoConnectLocation;
	/**
	 * The path of the Perfecto security token.
	 */
	private String perfectoSecurityToken;

	/**
	 * The Perfecto Connect parameters.
	 */
	private String pcParameters;

	private String reuseTunnelId;
	
	private PerfectoCredentials credentials;

	@SuppressFBWarnings("SE_BAD_FIELD")
	private RunCondition condition;
	/**
	 * @see CredentialsProvider
	 */
	private String credentialId;

	private static String tunnelId;

	private String perfectoConnectFile;

	private String pcLocation = "";

	@Override
	public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
		super.makeSensitiveBuildVariables(build, sensitiveVariables);
		sensitiveVariables.add(PERFECTO_SECURITY_TOKEN);
	}

	/**
	 * Constructs a new instance using data entered on the job configuration screen.
	 * @param condition                 allows users to define rules which enable Perfecto Connect
	 * @param credentialId              Which credential a build should use
	 * @param cloudName            		perfecto's cloud name
	 * @param tunnelIdCustomName        Custom value for tunnel Id environment variable name.
	 * @param pcParameters              Perfecto Connect parameters
	 * @param perfectoConnectLocation   Perfecto connect location
	 * @param perfectoSecurityToken     Perfecto Security token.
	 * @param perfectoConnectFile       Perfecto connect file name.
	 * @param reuseTunnelId				Tunnel ID to reuse.
	 */
	@DataBoundConstructor
	public PerfectoBuildWrapper(
			RunCondition condition,
			String credentialId,
			String tunnelIdCustomName,
			String pcParameters,
			String perfectoConnectLocation,
			String perfectoSecurityToken,
			String perfectoConnectFile,
			String reuseTunnelId
			) {
		this.perfectoConnectLocation = perfectoConnectLocation;
		this.condition = condition;
		if(tunnelIdCustomName.length()>1)
			this.tunnelIdCustomName = tunnelIdCustomName;
		this.credentialId = credentialId;
		this.perfectoSecurityToken = perfectoSecurityToken;
		this.pcParameters = pcParameters;
		this.perfectoConnectFile = perfectoConnectFile;
		this.reuseTunnelId = reuseTunnelId;
	}

	private String getTunnelId(String perfectoConnectLocation, String cloudName, String apiKey, BuildListener listener) throws IOException {
		String tunnelId;
		boolean isWindows = System.getProperty("os.name")
				.toLowerCase().startsWith("windows");
		Process process;
		if (isWindows) {

			if(perfectoConnectLocation.endsWith("/")||perfectoConnectLocation.endsWith("\\")) {
				pcLocation = perfectoConnectLocation+perfectoConnectFile;
			}else {
				pcLocation = perfectoConnectLocation+File.separator+perfectoConnectFile;
			}
			String baseCommand = pcLocation.trim()+" start -c "+cloudName.trim()+".perfectomobile.com -s "+apiKey.trim();
			listener.getLogger().println(pcLocation.trim()+" start -c "+cloudName.trim()+".perfectomobile.com -s <<TOKEN>>");
			String cmdArgs[] = {"cmd.exe", "/c", baseCommand+" "+pcParameters.trim()};
			process = new ProcessBuilder(cmdArgs).redirectErrorStream(true).start();
			InputStream is = process.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			tunnelId = reader.readLine();
			if(tunnelId == null) {
				throw new RuntimeException("Unable to create tunnel ID. Kindly cross check your parameters or raise a Perfecto support case.");
			}
			if(tunnelId.contains("not recognized as an internal or external command")) {
				throw new RuntimeException("Perfecto Connect Path and Name is not Correct. Path Provided : '"+pcLocation+"'");
			}
			if(tunnelId.contains("Can't start Perfecto Connect")||tunnelId.contains("failed to start")) {
				throw new RuntimeException(tunnelId);
			}
		} else {

			if(perfectoConnectLocation.endsWith("/")||perfectoConnectLocation.endsWith("\\")) {
				pcLocation = perfectoConnectLocation+perfectoConnectFile;
			}else {
				pcLocation = perfectoConnectLocation+File.separator+perfectoConnectFile;
			}
			String baseCommand = pcLocation.trim()+" start -c "+cloudName.trim()+".perfectomobile.com -s "+apiKey.trim();
			listener.getLogger().println(pcLocation.trim()+" start -c "+cloudName.trim()+".perfectomobile.com -s <<TOKEN>>");
			String cmdArgs[] = {"bash", "-c", baseCommand+" "+pcParameters.trim()};
			process = new ProcessBuilder(cmdArgs).redirectErrorStream(true).start();
			InputStream is = process.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			tunnelId = reader.readLine();
			listener.getLogger().println("Tunnel Id : "+tunnelId);
			if(tunnelId == null) {
				throw new RuntimeException("Unable to create tunnel ID. Kindly cross check your parameters or raise a Perfecto support case.");
			}
			if(tunnelId.contains("bash: ")) {
				throw new RuntimeException("Perfecto Connect Path and Name is not Correct. Path Provided : '"+pcLocation+"'");
			}
			if(tunnelId.contains("Can't start Perfecto Connect")) {
				throw new RuntimeException(tunnelId);
			}
		}
		return tunnelId;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Invoked prior to the running of a Jenkins build.  Populates the Perfecto specific environment variables and launches Perfecto Connect.
	 *
	 * @return a new {@link hudson.model.Environment} instance populated with the Perfecto environment variables
	 */
	@Override
	public Environment setUp(final AbstractBuild build, Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
		listener.getLogger().println("Creating Tunnel Id........!");
		logger.fine("Setting Perfecto Build Wrapper");

		credentials = PerfectoCredentials.getPerfectoCredentials(build, this);
		CredentialsProvider.track(build, credentials);
		
		if(credentials==null && !reuseTunnelId.contains("-"))
			throw new RuntimeException("Credentials missing......");

		if(!reuseTunnelId.contains("-")) {
			final String apiKey = credentials.getPassword().getPlainText();
			tunnelId = getTunnelId(perfectoConnectLocation, credentials.getCloudName(), apiKey, listener);
		}
		else
			tunnelId = reuseTunnelId;
		listener.getLogger().println("Tunnel Id created succesfully.");

		return new Environment() {

			/**
			 * Updates the environment variable map to include the Perfecto specific environment variables applicable to the build.
			 * @param env existing environment variables
			 */
			@Override
			public void buildEnvVars(Map<String, String> env) {
				logger.fine("Creating Perfecto environment variables");
				/* New standard env name */
				//				PerfectoEnvironmentUtil.outputEnvironmentVariable(env, PERFECTO_USER, username, true);
				/* New standard env name */
				//				PerfectoEnvironmentUtil.outputEnvironmentVariable(env, PERFECTO_SECURITY_TOKEN, apiKey, true);
				//
				//				PerfectoEnvironmentUtil.outputEnvironmentVariable(env, PERFECTO_CLOUD_NAME, getCloudName(), true);

				PerfectoEnvironmentUtil.outputEnvironmentVariable(env, tunnelIdCustomName, tunnelId, true);
			}
			/**
			 * {@inheritDoc}
			 *
			 * Tear down method
			 * @param build
			 *      The build in progress for which an {@link Environment} object is created.
			 *      Never null.
			 * @param listener
			 *      Can be used to send any message.
			 *
			 */
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
				return true;
			}
		};
	}

	public boolean isEnablePerfectoConnect() {
		return enablePerfectoConnect;
	}

	public void setEnablePerfectoConnect(boolean enablePerfectoConnect) {
		this.enablePerfectoConnect = enablePerfectoConnect;
	}

	public String getTunnelIdCustomName() {
		return tunnelIdCustomName;
	}

	public void setTunnelIdCustomName(String tunnelIdCustomName) {
		this.tunnelIdCustomName = tunnelIdCustomName;
	}

	public String getPerfectoConnectFile() {
		return perfectoConnectFile;
	}

	public void setPerfectoConnectFile(String perfectoConnectFile) {
		this.perfectoConnectFile = perfectoConnectFile;
	}

	public String getPerfectoConnectLocation() {
		return perfectoConnectLocation;
	}

	public void setPerfectoConnectLocation(String perfectoConnectLocation) {
		this.perfectoConnectLocation = perfectoConnectLocation;
	}

	public String getSecurityToken() {
		return perfectoSecurityToken;
	}

	public void setSecurityToken(String perfectoSecurityToken) {
		this.perfectoSecurityToken = perfectoSecurityToken;
	}

	public RunCondition getCondition() {
		return condition;
	}

	public String getCredentialId() {
		return credentialId;
	}

	public void setCredentialId(String credentialId) {
		this.credentialId = credentialId;
	}

	public String getPcParameters() {
		return pcParameters;
	}

	public void setPcParameters(String pcParameters) {
		this.pcParameters = pcParameters;
	}

	  @Extension(ordinal = 1.0D)
	    public static class DescriptorImpl extends BuildWrapperDescriptor {
		/**
		 * @return text to be displayed within Jenkins job configuration
		 */
		@Override
		public String getDisplayName() {
			return "Perfecto Connect";
		}

		/**
		 * @param context    Project/parent
		 * @return the list of supported credentials
		 */
		public ListBoxModel doFillCredentialIdItems(final @AncestorInPath ItemGroup<?> context) {
			return new StandardUsernameListBoxModel()
					.withAll(PerfectoCredentials.all(context));
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			// TODO Auto-generated method stub
			return true;
		}

	}

	protected boolean migrateCredentials(AbstractProject project) {
		if (Strings.isNullOrEmpty(credentialId)) {
			if (credentials != null) {
				try {
					credentialId = PerfectoCredentials.migrateToCredentials(
							credentials.getUsername(),
							credentials.getCloudName(),
							credentials.getApiKey().getPlainText(),
							project == null ? "Unknown" : project.getDisplayName()
							);
					return true;
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try {
				this.credentialId = PerfectoCredentials.migrateToCredentials(
						credentials.getUsername(),
						credentials.getCloudName(),
						credentials.getApiKey().getPlainText(),
						"Global"
						);
				return true;
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	@Extension
	static final public class ItemListenerImpl extends ItemListener {
		public void onLoaded() {
			Jenkins instance = Jenkins.getInstance();
			if (instance == null) { return; }
			for (BuildableItemWithBuildWrappers item : instance.getItems(BuildableItemWithBuildWrappers.class))
			{
				AbstractProject p = item.asProject();
				for (PerfectoBuildWrapper bw : ((BuildableItemWithBuildWrappers)p).getBuildWrappersList().getAll(PerfectoBuildWrapper.class))
				{
					if (bw.migrateCredentials(p)) {
						try {
							p.save();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
}