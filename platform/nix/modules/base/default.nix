{ config, lib, pkgs, ... }:
let
  authorizedKeysDir = ../../authorized-keys;
  deployAuthorizedKeysPath = authorizedKeysDir + "/deploy.pub";
  deployAuthorizedKeys =
    if builtins.pathExists deployAuthorizedKeysPath then
      lib.filter (line: line != "" && !(lib.hasPrefix "#" line)) (
        lib.splitString "\n" (builtins.readFile deployAuthorizedKeysPath)
      )
    else
      [ ];
in
{
  boot.loader.systemd-boot.enable = true;
  boot.loader.efi.canTouchEfiVariables = true;

  networking.useDHCP = true;
  networking.firewall = {
    enable = true;
    # 2222 — SSH. Web (80/443) and mail (25/465/587/993/995/110/143/4190)
    # are opened by the k3s bootstrap module when Traefik / Stalwart bind
    # hostPorts. Keeping them out of the base module means a host that
    # only runs the base role stays firewalled to SSH only.
    allowedTCPPorts = [ 2222 ];
  };

  # Write /etc/resolv.conf statically from NixOS and disable openresolv
  # entirely: keep kubelet's resolv.conf under three entries so
  # DNSConfigForming doesn't fire. Three upstreams: Cloudflare primary
  # + secondary for speed, Quad9 for operator diversity.
  networking.resolvconf.enable = false;
  environment.etc."resolv.conf" = {
    mode = "0644";
    text = ''
      nameserver 1.1.1.1
      nameserver 1.0.0.1
      nameserver 9.9.9.9
      options timeout:1 attempts:2 rotate
    '';
  };

  services.openssh = {
    enable = true;
    ports = [ 2222 ];
    settings = {
      AllowUsers = [ "deploy" ];
      KbdInteractiveAuthentication = false;
      PasswordAuthentication = false;
      PermitRootLogin = "no";
      PubkeyAuthentication = true;
      X11Forwarding = false;
    };
  };

  services.fail2ban.enable = true;

  environment.systemPackages = with pkgs; [
    curl
    git
    jq
    vim
  ];

  # Durable home for api user uploads. The api Deployment mounts
  # /srv/blueshell/storage via a static hostPath PV (see
  # platform/cluster/flux/apps/stateless/api/pvc.yaml); kubelet would
  # auto-create the path with DirectoryOrCreate, but seeding it here
  # keeps ownership + perms in NixOS's hands across reboots. An
  # initContainer in the api pod loosens the inner mount to 0777 at
  # pod start so the pod's dynamic Alpine uid can write.
  systemd.tmpfiles.rules = [
    "d /srv                   0755 root root -"
    "d /srv/blueshell         0755 root root -"
    "d /srv/blueshell/storage 0755 root root -"
  ];

  # A missing deploy.pub is caught loudly at install/deploy time by the
  # bash guards in platform/scripts. Surface it here as a warning too,
  # but do not fail the build — otherwise `nix flake check` blows up on
  # a clean checkout (deploy.pub is gitignored per design).
  warnings = lib.optional (deployAuthorizedKeys == [ ]) ''
    No deploy SSH public keys configured in ${toString deployAuthorizedKeysPath}.
    The `deploy` user on this host will have no authorized keys. Create the
    file locally (see platform/nix/authorized-keys/README.md) before the
    next install or deploy-rs activation.
  '';

  users.users.deploy = {
    isNormalUser = true;
    extraGroups = [ "wheel" ];
    openssh.authorizedKeys.keys = deployAuthorizedKeys;
  };
  security.sudo.wheelNeedsPassword = false;

  time.timeZone = "Europe/Amsterdam";
  i18n.defaultLocale = "en_US.UTF-8";

  # Reclaim nix store space weekly so a single-node VPS with a modest disk
  # does not fill up from accumulated deploy-rs profiles.
  nix.gc = {
    automatic = true;
    dates = "weekly";
    options = "--delete-older-than 30d";
  };
  nix.settings.auto-optimise-store = true;
  nix.settings.experimental-features = [ "nix-command" "flakes" ];
}
