Vagrant.configure("2") do |config|
    config.vm.define "foohost"
    config.vm.box = "generic/ubuntu2204"
    config.vm.provider "libvirt" do |libvirt|
      libvirt.memory = 7000
      libvirt.cpus = 6
    end

    config.vm.synced_folder "k8s", "/vagrant", type: "rsync"

    config.vm.provision "shell", inline: <<-SHELL
      set -e
      apt-get update && apt-get upgrade -y
      curl -sfL https://get.k3s.io | sh -
      ln -s /usr/local/bin/kubectl /usr/bin/kubectl || true
      sleep 30
      kubectl apply -k /vagrant/
    SHELL
  end
