# Retail Platform

Enterprise retail platform CI/CD demonstration.

## Application

- Technology: Java 17 + Spring Boot + Maven
- Application port: 8081
- Production baseline: 4.2.0

## Endpoints

- `/` - application version and environment
- `/health` - application health
- `/payment` - payment functionality
- `/version` - application version

## Docker

Docker images are version tagged:

- `retail-app:4.2.0`
- `retail-app:4.2.1`
- `retail-app:4.2.2`

The application uses a Docker health check and an isolated application network.

## Git Branches

- `main` - production-ready
- `develop` - ongoing development
- `release/4.3.0` - release preparation
- `hotfix/payment-4.2.1` - emergency production fix

## CI/CD

Jenkins supports:

- DEPLOY
- ROLLBACK
- UAT deployment
- Production deployment
- Production confirmation
- Version validation
- Health-check based deployment
- Automatic rollback