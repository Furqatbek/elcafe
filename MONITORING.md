# ElCafe System Monitoring

This document describes the monitoring stack setup for the ElCafe Restaurant Delivery Service.

## Overview

The monitoring stack consists of:
- **Prometheus**: Metrics collection and storage
- **Grafana**: Metrics visualization and dashboarding
- **cAdvisor**: Container metrics
- **Spring Boot Actuator**: Application health and metrics
- **Micrometer**: Application metrics instrumentation

## Services

### Prometheus
- **URL**: http://localhost:9090
- **Purpose**: Collect and store metrics from all services
- **Scrape Interval**: 15 seconds

### Grafana
- **URL**: http://localhost:3001
- **Username**: `admin`
- **Password**: `admin`
- **Purpose**: Visualize metrics with dashboards

### cAdvisor
- **URL**: http://localhost:8081
- **Purpose**: Monitor Docker container resource usage

### Spring Boot Actuator
- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus Metrics**: http://localhost:8080/actuator/prometheus

## Available Metrics

### Application Metrics
- **JVM Metrics**: Memory usage, garbage collection, thread count
- **HTTP Metrics**: Request count, duration, status codes
- **Database Metrics**: Connection pool status, query performance
- **Redis Metrics**: Connection status, cache hit/miss rates
- **Tomcat Metrics**: Request threads, sessions
- **Logback Metrics**: Log events by level

### Container Metrics (cAdvisor)
- CPU usage per container
- Memory usage per container
- Network I/O
- Disk I/O
- Container restarts

## Actuator Endpoints

All Actuator endpoints are available at `/actuator`:

### Public Endpoints
- `GET /actuator/health` - Overall application health
- `GET /actuator/health/liveness` - Liveness probe
- `GET /actuator/health/readiness` - Readiness probe
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - List of available metrics
- `GET /actuator/metrics/{metric}` - Specific metric details
- `GET /actuator/prometheus` - Prometheus-formatted metrics

### Additional Endpoints (require authentication)
- `GET /actuator/env` - Environment properties
- `GET /actuator/loggers` - Logger configurations
- `GET /actuator/threaddump` - Thread dump
- `GET /actuator/heapdump` - Heap dump

## Grafana Dashboards

### Importing Dashboards

1. Access Grafana at http://localhost:3001
2. Login with `admin` / `admin`
3. Navigate to **Dashboards** → **Import**
4. Use these dashboard IDs:
   - **4701**: JVM (Micrometer)
   - **12900**: Spring Boot Statistics
   - **193**: Docker Containers (cAdvisor)
   - **11074**: Node Exporter Full

### Custom Dashboards

Custom dashboards are located in `./monitoring/grafana/dashboards/`.

## Prometheus Queries Examples

### Application Performance

```promql
# Request rate per second
rate(http_server_requests_seconds_count[5m])

# Average response time
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status="500"}[5m])

# 95th percentile response time
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

### JVM Metrics

```promql
# JVM memory used
jvm_memory_used_bytes{area="heap"}

# Garbage collection time
rate(jvm_gc_pause_seconds_sum[5m])

# Thread count
jvm_threads_live_threads
```

### Database Metrics

```promql
# Active database connections
hikaricp_connections_active

# Database connection pool usage
hikaricp_connections{pool="HikariPool-1"}
```

### Container Metrics

```promql
# Container CPU usage
rate(container_cpu_usage_seconds_total{name=~"elcafe.*"}[5m])

# Container memory usage
container_memory_usage_bytes{name=~"elcafe.*"}

# Container network received
rate(container_network_receive_bytes_total{name=~"elcafe.*"}[5m])
```

## Alerts Configuration

### Critical Alerts

```yaml
# Add to prometheus.yml under rule_files
groups:
  - name: elcafe_alerts
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status="500"}[5m]) > 0.05
        for: 5m
        annotations:
          summary: "High error rate detected"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        annotations:
          summary: "Database connection pool near limit"

      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9
        for: 5m
        annotations:
          summary: "JVM heap memory usage above 90%"

      - alert: ServiceDown
        expr: up{job="spring-boot-app"} == 0
        for: 1m
        annotations:
          summary: "Spring Boot application is down"
```

## Health Checks

The application provides Kubernetes-style health probes:

### Liveness Probe
```bash
curl http://localhost:8080/actuator/health/liveness
```
Returns `{"status":"UP"}` if the application is running.

### Readiness Probe
```bash
curl http://localhost:8080/actuator/health/readiness
```
Returns `{"status":"UP"}` if the application is ready to accept traffic.

## Troubleshooting

### Prometheus Not Scraping Metrics

1. Check if Prometheus can reach the application:
   ```bash
   docker exec elcafe-prometheus wget -O- http://app:8080/actuator/prometheus
   ```

2. Check Prometheus targets:
   - Visit http://localhost:9090/targets
   - Ensure all targets show "UP" status

### Grafana Not Showing Data

1. Verify Prometheus datasource:
   - Go to **Configuration** → **Data Sources**
   - Test the Prometheus connection

2. Check if Prometheus has data:
   - Visit http://localhost:9090/graph
   - Try a simple query like `up`

### High Memory Usage

1. Check JVM memory:
   ```bash
   curl http://localhost:8080/actuator/metrics/jvm.memory.used
   ```

2. Get heap dump for analysis:
   ```bash
   curl http://localhost:8080/actuator/heapdump -O
   ```

3. Analyze with tools like Eclipse MAT or VisualVM

## Best Practices

1. **Regular Monitoring**: Check dashboards daily
2. **Set Up Alerts**: Configure alerts for critical metrics
3. **Capacity Planning**: Monitor trends to predict resource needs
4. **Performance Baselines**: Establish normal metric ranges
5. **Log Correlation**: Correlate metrics with logs for debugging

## Starting the Monitoring Stack

```bash
# Start all services including monitoring
docker-compose up -d

# View logs
docker-compose logs -f prometheus grafana

# Check service status
docker-compose ps
```

## Accessing Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Prometheus | http://localhost:9090 | N/A |
| Grafana | http://localhost:3001 | admin / admin |
| cAdvisor | http://localhost:8081 | N/A |
| Spring Boot App | http://localhost:8080 | N/A |
| Actuator Health | http://localhost:8080/actuator/health | N/A |
| Actuator Metrics | http://localhost:8080/actuator/prometheus | N/A |

## Metrics Retention

- **Prometheus**: Retains metrics for 15 days by default
- **Grafana**: No data retention (queries Prometheus)

To adjust Prometheus retention:
```yaml
# In docker-compose.yaml, add to prometheus command:
- '--storage.tsdb.retention.time=30d'
```

## Security Considerations

1. **Production**: Change Grafana admin password
2. **Production**: Restrict Actuator endpoints to authenticated users only
3. **Production**: Enable HTTPS for Prometheus and Grafana
4. **Production**: Use authentication for Prometheus remote write/read

## Additional Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
