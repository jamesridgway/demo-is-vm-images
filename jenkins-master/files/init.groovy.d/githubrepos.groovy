import com.amazonaws.services.secretsmanager.AWSSecretsManager
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*
import groovy.json.JsonSlurper
import hudson.model.*
import jenkins.*
import jenkins.branch.BranchSource
import jenkins.model.*
import jenkins.scm.api.SCMHead
import org.jenkinsci.plugins.github_branch_source.BranchDiscoveryTrait
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSourceBuilder
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import org.kohsuke.github.GHMyself
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub


println("[Running] GitHub Repository Script")

def getJenkinsMasterCredentials(client) {
   GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
           .withSecretId("jenkins_master")
   try {
       GetSecretValueResult getSecretValueResult = client.getSecretValue(getSecretValueRequest)
       return new JsonSlurper().parseText(getSecretValueResult.getSecretString())
   } catch(ResourceNotFoundException | InvalidRequestException | InvalidParameterException e) {
       System.err.println("An error occurred retrieving the secret: " + e.getMessage())
       throw e
   }
}

def jenkins = Jenkins.getInstance()

def client = AWSSecretsManagerClientBuilder.standard().build()
def creds = getJenkinsMasterCredentials(client)

def repositoriesToClone = creds.github_repos.split(",")

def githubTokenCreds = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "github-token", "GitHub account token", creds.github_username, creds.github_token)
credentials_store = Jenkins.instance.getExtensionList("com.cloudbees.plugins.credentials.SystemCredentialsProvider")[0].getStore()
credentials_store.addCredentials(Domain.global(), githubTokenCreds)
jenkins.save()

def existingJobs = jenkins.items.fullName

def github = GitHub.connect(creds.github_username, creds.github_token)
def githubMyself = github.getMyself()

repositoriesToClone.each{ repositoryToClone ->
  if (repositoryToClone in existingJobs) {
    println "Job already exists for repository: " + repositoryToClone
  } else {
    repository = githubMyself.getRepository(repositoryToClone)
    println "Creating job for repository: " + repository.name

    project = jenkins.createProject(WorkflowMultiBranchProject.class, repository.name)

    githubsource = new GitHubSCMSourceBuilder("github-" + repository.name, GitHubSCMSource.GITHUB_URL, "github-token", creds.github_username, repository.name).build()
    githubsource.setTraits([new BranchDiscoveryTrait(3)])

    project.setSourcesList([new BranchSource(githubsource)])
  }
}

println("[Done] GitHub Repository Script")
