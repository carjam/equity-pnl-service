# Docker Containerization Specification

## Objective
Create production-ready multi-stage Docker builds, optimize image size, configure proper health checks, and prepare for Kubernetes deployment.

## Current State

### Issues
- Incomplete Dockerfile (most commented out)
- Uses development setup in docker-compose (`mvn spring-boot:run`)
- No health checks in containers
- No resource limits
- Large image size (includes build tools)
- No security scanning
- Development and production use same image

## Target State

- Multi-stage Docker build
- Optimized production image (<200MB)
- Proper health checks and readiness probes
- Resource limits configured
- Security-scanned images
- Separate dev and prod Dockerfiles
- Kubernetes-ready

## Implementation Plan

### Step 1: Production Dockerfile

**File: `Dockerfile`**

```dockerfile
# Multi-stage build for production

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy POM and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

# Create non-root user
RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -S appuser -G appuser

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/equity-*.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for container
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Step 2: Development Dockerfile

**File: `Dockerfile.dev`**

```dockerfile
# Development Dockerfile with hot reload

FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

# Install utilities
RUN apt-get update && apt-get install -y curl wget && rm -rf /var/lib/apt/lists/*

# Copy POM for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Expose ports
EXPOSE 8080 5005

# JVM debug options
ENV MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Run with spring-boot:run for hot reload
CMD ["mvn", "spring-boot:run"]
```

### Step 3: Production docker-compose.yml

**File: `docker-compose.prod.yml`**

```yaml
version: '3.8'

services:
  equity-db:
    image: mysql:8.0
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    volumes:
      - equity-db-data:/var/lib/mysql
    networks:
      - equity-network
    ports:
      - "60333:3306"
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --max_connections=200
      - --innodb_buffer_pool_size=256M
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p$$MYSQL_ROOT_PASSWORD"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M

  redis:
    image: redis:7-alpine
    restart: always
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis-data:/data
    networks:
      - equity-network
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M
        reservations:
          cpus: '0.25'
          memory: 128M

  app:
    build:
      context: .
      dockerfile: Dockerfile
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:mysql://equity-db:3306/${MYSQL_DATABASE}
      DATABASE_USERNAME: ${MYSQL_USER}
      DATABASE_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      FINHUB_KEY: ${FINHUB_KEY}
      FINHUB_URL: ${FINHUB_URL}
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION: ${JWT_EXPIRATION}
      JAVA_OPTS: ${JAVA_OPTS:--XX:MaxRAMPercentage=75.0}
    ports:
      - "8080:8080"
    networks:
      - equity-network
    depends_on:
      equity-db:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 1G
        reservations:
          cpus: '1.0'
          memory: 512M
      replicas: 1

networks:
  equity-network:
    driver: bridge

volumes:
  equity-db-data:
    driver: local
  redis-data:
    driver: local
```

### Step 4: Development docker-compose.yml

**File: `docker-compose.yml`** (update existing)

```yaml
version: '3.8'

services:
  equity-db:
    image: mysql:8.0
    restart: always
    env_file:
      - .env
    ports:
      - "60333:3306"
    volumes:
      - equity-db-data:/var/lib/mysql
      - ./src/main/resources/db/migration:/docker-entrypoint-initdb.d:ro
    networks:
      - equity-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - equity-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3

  app:
    build:
      context: .
      dockerfile: Dockerfile.dev
    restart: unless-stopped
    env_file:
      - .env
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DATABASE_URL: jdbc:mysql://equity-db:3306/equity
    ports:
      - "8080:8080"
      - "5005:5005" # Debug port
    volumes:
      - .:/app
      - maven-cache:/root/.m2
    networks:
      - equity-network
    depends_on:
      equity-db:
        condition: service_healthy
      redis:
        condition: service_healthy

networks:
  equity-network:
    driver: bridge

volumes:
  equity-db-data:
  redis-data:
  maven-cache:
```

### Step 5: Build Scripts

**File: `build.sh`** (update existing)

```bash
#!/bin/bash
set -e

echo "Building Equity PnL Service..."

# Parse arguments
BUILD_TYPE=${1:-dev}

if [ "$BUILD_TYPE" = "prod" ]; then
    echo "Building production image..."
    docker build -t equity-pnl-service:latest -f Dockerfile .
    docker tag equity-pnl-service:latest equity-pnl-service:$(git rev-parse --short HEAD)
    echo "Production image built: equity-pnl-service:latest"
elif [ "$BUILD_TYPE" = "dev" ]; then
    echo "Building development image..."
    docker build -t equity-pnl-service:dev -f Dockerfile.dev .
    echo "Development image built: equity-pnl-service:dev"
else
    echo "Unknown build type: $BUILD_TYPE"
    echo "Usage: ./build.sh [dev|prod]"
    exit 1
fi

echo "Build complete!"
```

**File: `run.sh`**

```bash
#!/bin/bash
set -e

# Check for .env file
if [ ! -f .env ]; then
    echo "Error: .env file not found"
    echo "Please create .env from .env.template"
    exit 1
fi

# Parse arguments
ENV=${1:-dev}

if [ "$ENV" = "prod" ]; then
    echo "Starting production environment..."
    docker-compose -f docker-compose.prod.yml up -d
elif [ "$ENV" = "dev" ]; then
    echo "Starting development environment..."
    docker-compose up -d
else
    echo "Unknown environment: $ENV"
    echo "Usage: ./run.sh [dev|prod]"
    exit 1
fi

echo "Waiting for services to be healthy..."
sleep 10

echo "Services started!"
echo "Application: http://localhost:8080"
echo "Health check: http://localhost:8080/actuator/health"
```

### Step 6: .dockerignore

**File: `.dockerignore`**

```
# Git
.git
.gitignore

# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties

# IDE
.idea/
*.iml
.vscode/
.project
.classpath
.settings/

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Environment
.env
.env.local
.env.*.local

# Documentation
*.md
docs/
spec/

# Docker
Dockerfile*
docker-compose*.yml
.dockerignore

# CI/CD
.github/
.gitlab-ci.yml
Jenkinsfile

# Test
src/test/
```

### Step 7: Kubernetes Deployment

**File: `k8s/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: equity-pnl-service
  labels:
    app: equity-pnl-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: equity-pnl-service
  template:
    metadata:
      labels:
        app: equity-pnl-service
    spec:
      containers:
      - name: app
        image: equity-pnl-service:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: equity-pnl-secrets
              key: database-url
        - name: DATABASE_USERNAME
          valueFrom:
            secretKeyRef:
              name: equity-pnl-secrets
              key: database-username
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: equity-pnl-secrets
              key: database-password
        - name: REDIS_HOST
          value: "redis-service"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: equity-pnl-secrets
              key: redis-password
        - name: FINHUB_KEY
          valueFrom:
            secretKeyRef:
              name: equity-pnl-secrets
              key: finhub-key
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: equity-pnl-secrets
              key: jwt-secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: equity-pnl-service
spec:
  selector:
    app: equity-pnl-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

### Step 8: Security Scanning

**File: `.github/workflows/docker-scan.yml`**

```yaml
name: Docker Security Scan

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Docker image
        run: docker build -t equity-pnl-service:${{ github.sha }} .
      
      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: equity-pnl-service:${{ github.sha }}
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'
      
      - name: Upload Trivy results to GitHub Security
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: 'trivy-results.sarif'
      
      - name: Fail on critical vulnerabilities
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: equity-pnl-service:${{ github.sha }}
          format: 'table'
          exit-code: '1'
          severity: 'CRITICAL'
```

### Step 9: Image Size Optimization

Current approach already optimizes size through:
1. Multi-stage build (build artifacts not in final image)
2. JRE instead of JDK (smaller runtime)
3. Alpine base images (minimal OS)
4. .dockerignore (exclude unnecessary files)

**Expected sizes:**
- Build stage: ~800MB (temporary)
- Final image: ~180MB

### Step 10: Docker Commands Reference

**File: `DOCKER.md`**

```markdown
# Docker Commands Reference

## Development

# Build development image
./build.sh dev

# Start development environment
./run.sh dev
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop environment
docker-compose down

# Rebuild and restart
docker-compose up -d --build

## Production

# Build production image
./build.sh prod

# Start production environment
./run.sh prod
docker-compose -f docker-compose.prod.yml up -d

# Check health
docker-compose -f docker-compose.prod.yml ps
curl http://localhost:8080/actuator/health

# View resource usage
docker stats

## Maintenance

# Remove all containers and volumes
docker-compose down -v

# Clean up images
docker image prune -a

# Security scan
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy image equity-pnl-service:latest

# Inspect image
docker inspect equity-pnl-service:latest

# Check image size
docker images equity-pnl-service
```

## Testing

```bash
# Build and test locally
./build.sh prod

# Run container
docker run -d --name equity-test \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  equity-pnl-service:latest

# Check logs
docker logs -f equity-test

# Test health endpoint
curl http://localhost:8080/actuator/health

# Stop and remove
docker stop equity-test && docker rm equity-test
```

## Acceptance Criteria

- [ ] Multi-stage Dockerfile completed
- [ ] Production image <200MB
- [ ] Development Dockerfile with hot reload
- [ ] Health checks configured
- [ ] Resource limits set
- [ ] Non-root user in container
- [ ] Security scanning integrated
- [ ] Kubernetes manifests created
- [ ] Documentation complete
- [ ] Build scripts functional
- [ ] All images pass security scan

## Performance Benchmarks

### Image Sizes
- Development image: ~800MB (includes build tools)
- Production image: ~180MB
- MySQL: ~500MB
- Redis: ~35MB

### Startup Times
- Container start: <5 seconds
- Application ready: <40 seconds
- Health check passing: <60 seconds

## Dependencies

- Phase 1 complete (modern Spring Boot)
- Phase 2 complete (health checks configured)

## Estimated Effort

- Dockerfile creation: 1 day
- Docker Compose configuration: 1 day
- Kubernetes manifests: 1 day
- Security scanning setup: 0.5 days
- Testing and documentation: 0.5 days
- **Total: 4 days**

## References

- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [Spring Boot Docker](https://spring.io/guides/topicals/spring-boot-docker/)
- [Kubernetes Documentation](https://kubernetes.io/docs/home/)
