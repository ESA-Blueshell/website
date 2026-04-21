{ config, lib, ... }:
# Single-node k3s bootstrap. Every pod and the Traefik ingress controller
# (via hostPort) run on this one VPS, so there is no worker/agent join
# protocol to configure and no cross-site overlay to tunnel — flannel
# runs over the default ethernet interface.
let
  isK3sServer = config.services.k3s.enable && config.services.k3s.role == "server";
in
{
  config = lib.mkIf config.services.k3s.enable {
    networking.firewall.allowedTCPPorts =
      [
        # Kubelet.
        10250
        # Traefik public-ingress hostPorts — bound directly on the node
        # because the cluster has no LoadBalancer backend.
        80
        443
        # Mail — Stalwart binds these with hostPort in the mail apps.
        25
        465
        587
        993
        995
        110
        143
        4190
      ]
      ++ lib.optionals isK3sServer [ 6443 ];
    # Flannel VXLAN backend.
    networking.firewall.allowedUDPPorts = [ 8472 ];
  };
}
