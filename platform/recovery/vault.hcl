ui            = true
disable_mlock = true
api_addr      = "http://127.0.0.1:8200"
cluster_addr  = "http://127.0.0.1:8201"

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}

storage "raft" {
  path    = "/vault/data"
  node_id = "vault-0"
}

# Step 7c makes a root token from the shares, which Vault 2.0 allows only when asked.
enable_unauthenticated_access = ["generate-root"]
