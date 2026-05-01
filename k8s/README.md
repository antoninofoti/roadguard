# RoadGuard — Kubernetes Deployment Guide

This document describes how to deploy the RoadGuard cloud infrastructure
on a Kubernetes cluster (tested on Google Kubernetes Engine and minikube).

## Prerequisites

- `kubectl` configured and connected to your cluster
- Docker images built and pushed to a registry (see [Building Images](#building-images))
- Nginx Ingress Controller installed in the cluster

## Directory Structure

```
k8s/
├── namespace.yaml               # roadguard namespace
├── ingress.yaml                 # Nginx Ingress (path-based routing)
├── web-portal/
│   ├── deployment.yaml          # 2→6 replicas, zero-downtime rolling update
│   ├── service.yaml             # ClusterIP
│   └── hpa.yaml                 # HorizontalPodAutoscaler
└── analytics-api/
    ├── deployment.yaml          # 1→4 replicas
    ├── service.yaml             # ClusterIP
    └── hpa.yaml                 # HPA
```

## Building & Pushing Images

```bash
# From repository root
docker build -t roadguard/web-portal:latest ./web-portal \
  --build-arg VITE_FIREBASE_API_KEY=<key> \
  --build-arg VITE_FIREBASE_PROJECT_ID=<project>
  # ... other VITE_FIREBASE_* args

docker build -t roadguard/analytics-api:latest ./analytics-api

# Push to Google Container Registry (GCR)
docker tag roadguard/web-portal:latest gcr.io/<PROJECT_ID>/web-portal:latest
docker tag roadguard/analytics-api:latest gcr.io/<PROJECT_ID>/analytics-api:latest
docker push gcr.io/<PROJECT_ID>/web-portal:latest
docker push gcr.io/<PROJECT_ID>/analytics-api:latest
```

## Deploying to Kubernetes

```bash
# 1. Create namespace
kubectl apply -f k8s/namespace.yaml

# 2. Deploy services
kubectl apply -f k8s/web-portal/
kubectl apply -f k8s/analytics-api/

# 3. Configure Ingress
#    Edit k8s/ingress.yaml and set host: your-domain.com
kubectl apply -f k8s/ingress.yaml

# 4. Verify
kubectl get pods -n roadguard
kubectl get hpa -n roadguard
kubectl get ingress -n roadguard
```

## Deploying to GKE (Google Kubernetes Engine)

```bash
# Create a minimal GKE cluster (Autopilot — managed, cost-efficient)
gcloud container clusters create-auto roadguard-cluster \
  --region europe-west1 \
  --project <PROJECT_ID>

# Get credentials
gcloud container clusters get-credentials roadguard-cluster \
  --region europe-west1 \
  --project <PROJECT_ID>

# Apply all manifests
kubectl apply -f k8s/
```

## Verifying the Deployment

```bash
# Check pod status
kubectl get pods -n roadguard -w

# Check HPA scaling
kubectl describe hpa -n roadguard

# Check Ingress external IP
kubectl get ingress -n roadguard
```

## Local Development with minikube

```bash
minikube start
minikube addons enable ingress
kubectl apply -f k8s/
minikube service web-portal -n roadguard --url
```

## Teardown

```bash
kubectl delete namespace roadguard   # removes all resources in the namespace
```

