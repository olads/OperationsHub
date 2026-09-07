# Architecture

```mermaid
flowchart TD
    Client[Client] --> Nginx[Nginx / HTTPS]
    Nginx --> API[Spring Boot API]
    API --> DB[(PostgreSQL)]
    API --> Redis[(Redis)]
    API --> RabbitMQ[RabbitMQ]
    API --> Metrics[Prometheus/Grafana]
```
