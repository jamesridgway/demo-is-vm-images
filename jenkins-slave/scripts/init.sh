set -e

echo "The user is: $SUDO_USER"

# System Updates
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get dist-upgrade -yq

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get upgrade -yq
apt-get install -y curl htop language-pack-en jq ruby
apt-get autoremove -y

gem install bundler

# Hostname
echo -n "jenkins-slave-01" > /etc/hostname
hostname -F /etc/hostname

# Configure salt
curl -o bootstrap-salt.sh -L https://bootstrap.saltstack.com
sh bootstrap-salt.sh
sed -i -e 's/#file_client:.*/file_client: local/g' /etc/salt/minion
ln -sf "/home/$SUDO_USER/salt" "/srv/salt"
ln -sf "/home/$SUDO_USER/pillar" "/srv/pillar"

# Run masterless salt
echo -n "jenkins-slave-01" > /etc/salt/minion_id
systemctl restart salt-minion
salt-call --local state.apply

# Clean up salt
rm -rf "/home/$SUDO_USER/salt" /srv/salt "/home/$SUDO_USER/pillar" /srv/pillar 
sed -i -e '/^file_client:/ d' /etc/salt/minion

# Install packer
PACKER_DOWNLOAD_URL=$(curl -s https://www.packer.io/downloads.html | grep -oP "https://releases.hashicorp.com/packer/.*_linux_amd64.zip")
PACKER_ZIP_FILENAME=$(echo "${PACKER_DOWNLOAD_URL}" | grep -oP "packer_.*_linux_amd64.zip")
wget "${PACKER_DOWNLOAD_URL}"
unzip "${PACKER_ZIP_FILENAME}" -d packer
mv ./packer/packer /usr/local/bin
rm -r ./packer "${PACKER_ZIP_FILENAME}"