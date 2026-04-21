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
    hostName = "blueshell-fra-1";
    domain = "blueshell.nl";
    # Contabo does not run DHCP — the cidata ISO ships static config that
    # Debian picks up on first boot. Replace the placeholder v4/v6 values
    # with the real ones from the cidata file once the VPS is provisioned.
    useDHCP = lib.mkForce false;
    interfaces.ens18 = {
      useDHCP = false;
      ipv4.addresses = [
        {
          address = "REPLACE_WITH_VPS_IPV4";
          prefixLength = 24;
        }
      ];
      ipv6.addresses = [
        {
          address = "REPLACE_WITH_VPS_IPV6";
          prefixLength = 64;
        }
      ];
    };
    defaultGateway = {
      address = "REPLACE_WITH_VPS_GATEWAY_V4";
      interface = "ens18";
    };
    defaultGateway6 = {
      address = "fe80::1";
      interface = "ens18";
    };
  };

  system.stateVersion = "25.05";
}
