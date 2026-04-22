{ lib, modulesPath, ... }:
{
  imports = [
    # Provides virtio_scsi / virtio_blk / virtio_net in the initrd plus the
    # qemu-guest agent. Without this the initrd cannot mount /dev/sda on
    # Contabo's virtio-scsi host, the kernel panics on "unable to mount root"
    # before serial console or journald are up, and the machine appears alive
    # to the hypervisor but never comes back on the network.
    (modulesPath + "/profiles/qemu-guest.nix")
    ../../modules/base
    ../../modules/k3s/bootstrap.nix
    ../../modules/roles/single-node.nix
    ./disko.nix
  ];

  # Contabo's KVM firmware is BIOS-only (no /sys/firmware/efi in rescue),
  # so the base module's systemd-boot can't be executed by the firmware.
  # Use GRUB in BIOS mode against the MBR/GPT+bios_grub disko layout.
  boot.loader = {
    systemd-boot.enable = lib.mkForce false;
    efi.canTouchEfiVariables = lib.mkForce false;
    timeout = 1;
    grub = {
      enable = true;
      efiSupport = false;
      copyKernels = true;
      forceInstall = true;
    };
  };

  networking = {
    hostName = "frankfurt-contabo-1";
    domain = "v2.esa-blueshell.nl";
    # Contabo does not run DHCP — the cidata ISO ships static config that
    # Debian picks up on first boot. We bake the static values directly
    # here; the public IP is permanent for the lifetime of this VPS.
    useDHCP = lib.mkForce false;
    interfaces.ens18 = {
      useDHCP = false;
      ipv4.addresses = [
        {
          address = "157.173.115.164";
          prefixLength = 24;
        }
      ];
      ipv6.addresses = [
        {
          address = "2a02:c207:2316:2642::1";
          prefixLength = 64;
        }
      ];
    };
    defaultGateway = {
      address = "157.173.115.1";
      interface = "ens18";
    };
    # Contabo's standard IPv6 default gateway is the link-local fe80::1
    # (same as the sister personal-stack Frankfurt host).
    defaultGateway6 = {
      address = "fe80::1";
      interface = "ens18";
    };
  };

  system.stateVersion = "25.05";
}
