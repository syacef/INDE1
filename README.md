# INDE1 Final Project

Distributed resilient architecture handling parking events in real time.

## Table of Contents
- [Project Structure](#project-structure)
- [Documentation](#documentation)
- [Architecture](#architecture)
- [Testing](#testing)
- [Deployment](#deployment)
- [Deployment Specifications](#deployment-specifications)

## Project Structure

```
.
├── apps
│   ├── repo-account
│   ├── srvc-alert
│   ├── srvc-io
│   ├── srvc-notifier
│   └── srvc-stats
├── ci                           # ci for each app
├── doc
├── scripts
├── k8s
│   ├── apps                     # deployment of each apps
│   ├── argocd                   # deployment of argocd
|   |   └── argocd-apps
│   ├── kustomization.yml
│   ├── grafana                  # CRD definition for the kafka operator + topics
│   ├── kafka                    # CRD definition for the kafka operator + topics
│   ├── minio                    # Additional custom files for minio
│   ├── redis                    # CRD definition for the redis failover
│   ├── redis-insight            # Chart helm for redis insight
│   └── spark                    # CRD definition for the spark operator (including scheduled jobs)
├── .gitlab-ci
├── docker-compose.yml           # Docker compose for testing
└── README.md
```

## Architecture

The project is built with a microservices architecture including the following components :

- `repo-account`: User account and login management
- `srvc-store`: Kafka connect sink to store all kafka events to S3 datalake
- `srvc-notifier`: Notification management to handle alerts
- `srvc-io`: IO event generator
- `srvc-stats`: Spark scheduled batch job to compute model aggregations

![Architecture Diagram](doc/arch/infra_v1.2.png)

## Testing

Individual service tests can be run by navigating to the specific service directory and executing:

```bash
cd apps/<service-name>
docker compose up -d
sbt test
docker compose down
```

## Deployment

Our Project is deployed on Kubernetes. The deployment process is handled through our CI/CD pipeline.

To deploy the whole stack, just use:

```bash
kubectl apply -k k8s/argocd
```

## Deployment Specifications

We provide a sample of good preset for our architecture.

### Development Environment

**1 K8S node**

| Service | Replicas | CPU (cores) | Memory | Storage |
|---------|----------|-------------|--------|---------|
| **Apps** |
| repo-account  | 1 | 0.5 | 512MB | - |
| srvc-alert    | 1 | 0.5 | 512MB | - |
| srvc-io       | 1 | 0.5 | 512MB | - |
| srvc-notifier | 1 | 0.5 | 512MB | - |
| **Misc** |
| Spark | 1 | 1 | 1GB | 10GB | 3-node replica set |
| Kafka | 1 | 1 | 1GB | 5GB | Single instance |
| Redis | 3 | 0.5 | 512MB | 1GB | 3 node Cluster |
| MinIO | 3 | 0.5 | 512MB | 20GB | Cluster |
