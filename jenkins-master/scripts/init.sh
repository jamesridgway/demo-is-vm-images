set -e

echo "The user is: $SUDO_USER"

mv ~/rc.local /etc/rc.local

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get upgrade -yq
apt-get install -y curl htop language-pack-en
apt-get autoremove -y

# Hostname
echo -n "jenkins-01" > /etc/hostname
hostname -F /etc/hostname

# Configure salt
curl -o bootstrap-salt.sh -L https://bootstrap.saltstack.com
sh bootstrap-salt.sh
sed -i -e 's/#file_client:.*/file_client: local/g' /etc/salt/minion
ln -sf "/home/$SUDO_USER/salt" "/srv/salt"
ln -sf "/home/$SUDO_USER/pillar" "/srv/pillar"

# Run masterless salt
echo -n "jenkins-01" > /etc/salt/minion_id
systemctl restart salt-minion
salt-call --local state.apply

# Clean up salt
rm -rf "/home/$SUDO_USER/salt" /srv/salt "/home/$SUDO_USER/pillar" /srv/pillar 
sed -i -e '/^file_client:/ d' /etc/salt/minion

# Java
apt-get install -y openjdk-8-jdk-headless

# Jenkins
wget -q -O - https://pkg.jenkins.io/debian/jenkins-ci.org.key | apt-key add -
sh -c 'echo deb http://pkg.jenkins.io/debian-stable binary/ > /etc/apt/sources.list.d/jenkins.list'
apt-get update
apt-get install -y jenkins

# Disable setup
cat >> /etc/default/jenkins << 'EOF'
JAVA_ARGS="${JAVA_ARGS} -Djenkins.install.runSetupWizard=false"
EOF

# Write init.groovy.d files
mkdir -p /var/lib/jenkins/init.groovy.d/
mv ~/init.groovy.d/init.groovy /var/lib/jenkins/init.groovy.d/init.groovy
mv ~/init.groovy.d/aws.groovy /var/lib/jenkins/init.groovy.d/aws.txt
mv ~/init.groovy.d/github.groovy /var/lib/jenkins/init.groovy.d/github.txt
mv ~/init.groovy.d/githubrepos.groovy /var/lib/jenkins/init.groovy.d/githubrepos.txt
rm -rf ~/init.groovy.d
chown -R jenkins:jenkins /var/lib/jenkins/init.groovy.d