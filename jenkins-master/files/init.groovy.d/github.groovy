import jenkins.*
import jenkins.install.InstallState
import jenkins.model.*
import hudson.*
import hudson.model.*
import hudson.security.*
import hudson.security.csrf.DefaultCrumbIssuer
import jenkins.security.s2m.*
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.impl.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import org.jenkinsci.plugins.github.GitHubPlugin
import org.jenkinsci.plugins.github.config.*
import com.amazonaws.services.secretsmanager.AWSSecretsManager
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.*
import groovy.json.JsonSlurper

println("[Running] GitHub script")

def getGitHubWebhookSecret(client) {
   GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
           .withSecretId("jenkins_master")
   try {
       GetSecretValueResult getSecretValueResult = client.getSecretValue(getSecretValueRequest)
       return new JsonSlurper().parseText(getSecretValueResult.getSecretString()).github_webhook_secret
   } catch(ResourceNotFoundException | InvalidRequestException | InvalidParameterException e) {
       System.err.println("An error occurred retrieving the secret: " + e.getMessage())
       throw e
   }
}

def jenkins = Jenkins.getInstance()

final AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
    .build();

// Create GitHub Webhook Credentials
githubWebhookSecret = new StringCredentialsImpl(CredentialsScope.GLOBAL, "github-webhook-secret", "GitHub Webhook Secret", Secret.fromString(getGitHubWebhookSecret(client)))
globalDomain = Domain.global()
credentials_store = Jenkins.instance.getExtensionList("com.cloudbees.plugins.credentials.SystemCredentialsProvider")[0].getStore()
credentials_store.addCredentials(globalDomain, githubWebhookSecret)
jenkins.save()

// Configure github plugin
githubConfig = GitHubPlugin.configuration()
githubConfig.setHookSecretConfig(new HookSecretConfig("github-webhook-secret"))
githubConfig.save()
jenkins.save()

println("[Done] GitHub script")