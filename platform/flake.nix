{
  description = "Blueshell website NixOS + k3s platform (single VPS)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    deploy-rs.url = "github:serokell/deploy-rs";
    disko.url = "github:nix-community/disko";
    nixos-anywhere.url = "github:nix-community/nixos-anywhere";
  };

  outputs =
    inputs@{ self, nixpkgs, deploy-rs, disko, nixos-anywhere, ... }:
    let
      lib = nixpkgs.lib;
      supportedNixosAnywhereSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      mkHost =
        {
          system,
          hostModule,
          extraModules ? [ ],
          extraSpecialArgs ? { },
        }:
        lib.nixosSystem {
          inherit system;
          specialArgs = { inherit inputs; } // extraSpecialArgs;
          modules =
            [
              disko.nixosModules.disko
              hostModule
            ]
            ++ extraModules;
        };
    in
    {
      nixosConfigurations = {
        frankfurt-contabo-1 = mkHost {
          system = "x86_64-linux";
          hostModule = ./nix/hosts/frankfurt-contabo-1/default.nix;
        };
      };

      deploy.nodes.frankfurt-contabo-1 = {
        # Contabo VPS 20 public IPv4. The address is permanent for the
        # lifetime of this VPS; using the IP directly avoids any chance
        # of a DNS flip (e.g. during the PR 10 apex cutover) pointing
        # deploy-rs at a stale box.
        hostname = "157.173.115.164";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.x86_64-linux.activate.nixos self.nixosConfigurations.frankfurt-contabo-1;
        };
      };

      packages = lib.genAttrs supportedNixosAnywhereSystems (
        system:
        {
          nixos-anywhere = nixos-anywhere.packages.${system}.default;
        }
      );

      apps = lib.genAttrs supportedNixosAnywhereSystems (
        system:
        {
          nixos-anywhere = {
            type = "app";
            program = "${self.packages.${system}.nixos-anywhere}/bin/nixos-anywhere";
          };
        }
      );

      checks = lib.genAttrs [ "x86_64-linux" "aarch64-linux" ] (
        system:
        deploy-rs.lib.${system}.deployChecks self.deploy
      );
    };
}
