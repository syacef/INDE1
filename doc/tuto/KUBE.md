# KUBE

## Setup

```bash
apt-get update && apt-get upgrade -y
curl -sfL https://get.k3s.io | sh -
ln -s /usr/local/bin/kubectl /usr/bin/kubectl || true
sleep 30 # just for waiting cluster to be ready
kubectl apply -k k8s
```

## Forwarding

```bash
# TODO: ssh forward command
kubectl port-forward --address 0.0.0.0  svc/argocd-server -n argocd 8080:443
```