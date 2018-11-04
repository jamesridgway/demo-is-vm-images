import jenkins.*
import jenkins.install.InstallState
import jenkins.model.*
import hudson.*
import hudson.model.*
import hudson.security.*
import hudson.security.csrf.DefaultCrumbIssuer
import jenkins.security.s2m.*
import java.nio.file.Files
import java.nio.file.Paths


println("[Running] startup script")


def installPlugins(jenkins, plugins) {
  def pluginManager = jenkins.getPluginManager()
  def updateCenter = jenkins.getUpdateCenter()

  pluginManager.doCheckUpdatesServer()

  def installed = false
  def deploys = []
  plugins.each {
    if (pluginManager.getPlugin(it)) {
      println("Plugin '${it}' already installed")
    } else {
      def plugin = updateCenter.getPlugin(it)
      if (plugin && (!plugin.installed || (!plugin.installed.isPinned() && plugin.installed.hasUpdate()))) {
        println("Installing plugin: ${it}")
        deploys << plugin.deploy(true)
        installed = true
      } else {
        println("Could not find plugin with name: ${it}")
      }
    }
  }
  deploys*.get()
  jenkins.save()
  return installed
}

def setupSecurityRealm(jenkins) {
  def hudsonRealm = new HudsonPrivateSecurityRealm(false)

  def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
  strategy.setAllowAnonymousRead(false)

  jenkins.setSecurityRealm(hudsonRealm)
  jenkins.setAuthorizationStrategy(strategy)
  jenkins.save()
}

// --------------------------------------------------

// Setup
// --
def plugins = ["ace-editor", "ant", "antisamy-markup-formatter", "apache-httpcomponents-client-4-api", "authentication-tokens", "aws-credentials", "aws-java-sdk", "blueocean", "bouncycastle-api", "branch-api", "build-timeout", "cloudbees-folder", "command-launcher", "credentials", "credentials-binding", "display-url-api", "docker-commons", "docker-workflow", "durable-task", "ec2", "email-ext", "git", "git-client", "git-server", "github", "github-api", "github-branch-source", "gradle", "handlebars", "jackson2-api", "jdk-tool", "jquery-detached", "jsch", "junit", "ldap", "mailer", "mapdb-api", "matrix-auth", "matrix-project", "momentjs", "pam-auth", "pipeline-build-step", "pipeline-github-lib", "pipeline-graph-analysis", "pipeline-input-step", "pipeline-milestone-step", "pipeline-model-api", "pipeline-model-declarative-agent", "pipeline-model-definition", "pipeline-model-extensions", "pipeline-rest-api", "pipeline-stage-step", "pipeline-stage-tags-metadata", "pipeline-stage-view", "plain-credentials", "resource-disposer", "scm-api", "script-security", "ssh-credentials", "ssh-slaves", "structs", "subversion", "timestamper", "token-macro", "workflow-aggregator", "workflow-api", "workflow-basic-steps", "workflow-cps", "workflow-cps-global-lib", "workflow-durable-task-step", "workflow-job", "workflow-multibranch", "workflow-scm-step", "workflow-step-api", "workflow-support", "ws-cleanup"]
// --

def jenkins = Jenkins.getInstance()

setupSecurityRealm(jenkins)

// Disable Jenkins CLI remoting
jenkins.setInstallState(InstallState.INITIAL_SETUP_COMPLETED)
jenkins.getDescriptor("jenkins.CLI").get().setEnabled(false)
jenkins.save()

// Enable CSRF
if (jenkins.getCrumbIssuer() == null) {
  jenkins.setCrumbIssuer(new DefaultCrumbIssuer(true))
  jenkins.save()
}

// Only use secure protocols
Set<String> agentProtocolsList = ['JNLP4-connect', 'Ping']
if(!jenkins.getAgentProtocols().equals(agentProtocolsList)) {
    jenkins.setAgentProtocols(agentProtocolsList)
    jenkins.save()
}

// Enable Agent to Master security subsystem
jenkins.injector.getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false);
jenkins.save()

// Set location
jlc = JenkinsLocationConfiguration.get()
//jlc.setUrl("https://jenkins") 
jlc.save() 

// Install Plugins
def installed = installPlugins(jenkins, plugins)
if (installed) {
  jenkins.doSafeRestart()
}

// No executors on master
jenkins.setNumExecutors(0)
jenkins.save()

if (!new File("/var/lib/jenkins/init.groovy.d/aws.groovy").exists()) {
  Files.copy(Paths.get("/var/lib/jenkins/init.groovy.d/aws.txt"), Paths.get("/var/lib/jenkins/init.groovy.d/aws.groovy"))
  jenkins.doSafeRestart()
}

if (!new File("/var/lib/jenkins/init.groovy.d/github.groovy").exists()) {
  Files.copy(Paths.get("/var/lib/jenkins/init.groovy.d/github.txt"), Paths.get("/var/lib/jenkins/init.groovy.d/github.groovy"))
  jenkins.doSafeRestart()
}
if (!new File("/var/lib/jenkins/init.groovy.d/githubrepos.groovy").exists()) {
  Files.copy(Paths.get("/var/lib/jenkins/init.groovy.d/githubrepos.txt"), Paths.get("/var/lib/jenkins/init.groovy.d/githubrepos.groovy"))
  jenkins.doSafeRestart()
}

println("[Done] startup script")