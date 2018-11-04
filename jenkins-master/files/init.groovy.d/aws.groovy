import jenkins.*
import jenkins.install.InstallState
import jenkins.model.*
import hudson.*
import hudson.model.*
import hudson.security.*
import hudson.security.csrf.DefaultCrumbIssuer
import jenkins.security.s2m.*
import hudson.plugins.ec2.*
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetInstanceProfileRequest;
import com.amazonaws.services.secretsmanager.AWSSecretsManager
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.*
import groovy.json.JsonSlurper;

println("[Running] AWS script")


def createUser(jenkins, username, password) {
  def userAlreadyExists = User.getAll()*.getId().contains(username)
  if (userAlreadyExists) {
    println("User '${username}' already exists");
    return false;
  }
  if (password == "" || password == null) {
    println("User '${username}' cannot have an empty password!")
    return false; 
  }
  def hudsonRealm = jenkins.getSecurityRealm()
  hudsonRealm.createAccount(username, password)
  jenkins.save()
  return true
}


def getJenkinsSlavePrivateKey(client) {
   GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
           .withSecretId("jenkins_slave_private_key")
   try {
       GetSecretValueResult getSecretValueResult = client.getSecretValue(getSecretValueRequest)
       return getSecretValueResult.getSecretString()
   } catch(ResourceNotFoundException | InvalidRequestException | InvalidParameterException e) {
       System.err.println("An error occurred retrieving the secret: " + e.getMessage())
       throw e
   }
}

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

final AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
    .build();

def jenkins = Jenkins.getInstance()

def masterCredentials = getJenkinsMasterCredentials(client);

// Set location
jlc = JenkinsLocationConfiguration.get()
jlc.setUrl(masterCredentials.jenkins_url)
jlc.save()

// Setup user
if (createUser(jenkins, masterCredentials.admin_username, masterCredentials.admin_password)) {
  println("Created admin user!")
}
if (createUser(jenkins, masterCredentials.github_webhook_username, masterCredentials.github_webhook_password)) {
  println("Created github user!")
}


def ec2_tags = [
  new EC2Tag('Project', 'Jenkins CI Slaves'),
  new EC2Tag('Name','Jenkins CI Slave')
]

def slaveUserData = """\
#!/bin/bash
set -e

INSTANCE_ID=\$(curl -s http://169.254.169.254/latest/meta-data/instance-id)
HOSTNAME="jenkins-slave-\${INSTANCE_ID}"

echo -n "\${HOSTNAME}" > /etc/hostname
hostname -F /etc/hostname
echo -n "\${HOSTNAME}" > /etc/salt/minion_id'
"""

AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient()
DescribeImagesResult describeImagesResult = ec2.describeImages(new DescribeImagesRequest()
  .withFilters(new Filter("name").withValues("jenkins-slave*"))
  .withOwners("self"))

Image jenkinsSlaveAmi = describeImagesResult.getImages().stream()
  .sorted { i1, i2 -> i2.getCreationDate().compareTo(i1.getCreationDate())}
  .findFirst()
  .get()


String sshSgName = ec2.describeSecurityGroups(new DescribeSecurityGroupsRequest()
    .withFilters(new Filter("tag:Name").withValues("SSH")))
    .getSecurityGroups().get(0)
    .getGroupId()

String defaultSgName = ec2.describeSecurityGroups(new DescribeSecurityGroupsRequest()
    .withFilters(new Filter("description").withValues("default VPC security group")))
    .getSecurityGroups().get(0)
    .getGroupId()

AmazonIdentityManagement iam = AmazonIdentityManagementClient.builder().build();

String jenkinsCiSlaveInstanceProfile = iam.getInstanceProfile(new GetInstanceProfileRequest()
    .withInstanceProfileName("jenkins_ci_slave"))
    .getInstanceProfile()
    .getArn()

String subnetId = ec2.describeSubnets().getSubnets().get(0).getSubnetId()

def workerAmi = new SlaveTemplate(
  jenkinsSlaveAmi.getImageId(),                                   // String ami
  '',                                                             // String zone
  new SpotConfiguration("0.02"),                                  // SpotConfiguration spotConfig
  defaultSgName + "," + sshSgName,                                // String securityGroups
  '/home/jenkins',                                                // String remoteFS
  InstanceType.fromValue("t3.small"),                             // InstanceType type
  false,                                                          // boolean ebsOptimized
  "EC2 Jenkins Slave",                                            // String labelString
  Node.Mode.NORMAL,                                               // Node.Mode mode
  "Jenkins Slave AMI",                                            // String description
  '',                                                             // String initScript
  '',                                                             // String tmpDir
  slaveUserData,                                                  // String userData
  "1",                                                            // String numExecutors
  'jenkins',                                                      // String remoteAdmin
  new UnixData(null, null, null, '22'),                           // AMITypeData amiType
  '',                                                             // String jvmopts
  false,                                                          // boolean stopOnTerminate
  subnetId,                                                       // String subnetId
  ec2_tags,                                                       // List<EC2Tag> tags
  '30',                                                           // String idleTerminationMinutes
  false,                                                          // boolean usePrivateDnsName
  '4',                                                            // String instanceCapStr
  jenkinsCiSlaveInstanceProfile,                                  // String iamInstanceProfile
  false,                                                          // boolean deleteRootOnTermination
  true,                                                           // boolean useEphemeralDevices
  false,                                                          // boolean useDedicatedTenancy
  '600',                                                          // String launchTimeoutStr
  false,                                                          // boolean associatePublicIp
  '',                                                             // String customDeviceMapping
  false,                                                          // boolean connectBySSHProcess
  false                                                           // boolean connectUsingPublicIp
)


def new_cloud = new AmazonEC2Cloud(
  "EC2 Spot Slaves",                  // String cloudName
  true,                               // boolean useInstanceProfileForCredentials
  '',                                 // String credentialsId
  'eu-west-1',                        // String region
  getJenkinsSlavePrivateKey(client),  // String privateKey
  "4",                                // String instanceCapStr
  [workerAmi],                        // List<? extends SlaveTemplate> templates
  "",                                 // String roleArn
  ""                                  // String roleSessionName
)

jenkins.clouds.clear()
jenkins.clouds.add(new_cloud)
jenkins.save()


println("[Done] AWS script")