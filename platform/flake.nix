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
        blueshell-fra-1 = mkHost {
          system = "x86_64-linux";
          hostModule = ./nix/hosts/blueshell-fra-1/default.nix;
        };
      };

      deploy.nodes.blueshell-fra-1 = {
        # Placeholder — replace with the provisioned Contabo VPS public IP
        # before running `deploy-rs activate`. nixos-anywhere takes the IP
        # as a CLI argument so it does not need this value during initial
        # bootstrap.
        hostname = "blueshell-fra-1.blueshell.nl";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.x86_64-linux.activate.nixos self.nixosConfigurations.blueshell-fra-1;
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
