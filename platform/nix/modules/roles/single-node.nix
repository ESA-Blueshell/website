{ ... }:
# Collapsed control-plane + worker role. The cluster is intentionally
# single-node; Traefik and ServiceLB are disabled because the platform
# ships its own Traefik via Flux and uses hostPorts rather than a
# LoadBalancer.
{
  services.k3s = {
    enable = true;
    role = "server";
    extraFlags = [
      "--disable=traefik"
      "--disable=servicelb"
      "--write-kubeconfig-mode=0644"
      # Trust OIDC tokens issued by the website api so Headlamp (and
      # anything else with an id-token) can authenticate against the
      # kube-apiserver without a static kubeconfig. The audience is the
      # OAuth2 clientId the api registers for Headlamp; the groups claim
      # drives per-user RBAC (see
      # platform/cluster/flux/apps/core/headlamp/oidc-admin-binding.yaml
      # when that lands).
      #
      # The oidc: prefix keeps these identities disjoint from the
      # built-in system:* groups so a human-issued token is never
      # implicitly bound to a system role.
      "--kube-apiserver-arg=oidc-issuer-url=https://esa-blueshell.nl/api"
      "--kube-apiserver-arg=oidc-client-id=headlamp"
      "--kube-apiserver-arg=oidc-username-claim=preferred_username"
      "--kube-apiserver-arg=oidc-username-prefix=oidc:"
      "--kube-apiserver-arg=oidc-groups-claim=groups"
      "--kube-apiserver-arg=oidc-groups-prefix=oidc:"
    ];
  };
}
