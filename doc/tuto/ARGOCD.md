# ARGOCD

https://argo-cd.readthedocs.io/en/stable/getting_started/

## Setup admin password

```bash
argocd admin initial-password -n argocd
```

## Adding repo



```bash
ssh-keyscan gitlab.cri.epita.fr | 
argocd cert add-ssh --batch
```

## Install argocd cli

```bash
VERSION=$(curl -L -s https://raw.githubusercontent.com/argoproj/argo-cd/stable/VERSION)
curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/download/v$VERSION/argocd-linux-amd64
sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd
rm argocd-linux-amd64
```

https://argo-cd.readthedocs.io/en/stable/cli_installation/