set -e

echo "The user is: $SUDO_USER"

mkdir -p /etc/systemd/system/apt-daily.timer.d
cat > /etc/systemd/system/apt-daily.timer.d/apt-daily.timer.conf << 'EOF'
[Timer]
Persistent=false
EOF

# System Updates
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get dist-upgrade -yq

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get upgrade -yq
apt-get install -y curl htop language-pack-en
apt-get autoremove -y

# Hostname
echo -n "core-01" > /etc/hostname
hostname -F /etc/hostname

# Configure salt
curl -o bootstrap-salt.sh -L https://bootstrap.saltstack.com
sh bootstrap-salt.sh
sed -i -e 's/#file_client:.*/file_client: local/g' /etc/salt/minion
ln -sf "/home/$SUDO_USER/salt" "/srv/salt"
ln -sf "/home/$SUDO_USER/pillar" "/srv/pillar"

# Run masterless salt
echo -n "core-01" > /etc/salt/minion_id
systemctl restart salt-minion
salt-call --local state.apply

# Clean up salt
rm -rf "/home/$SUDO_USER/salt" /srv/salt "/home/$SUDO_USER/pillar" /srv/pillar 
sed -i -e '/^file_client:/ d' /etc/salt/minion
