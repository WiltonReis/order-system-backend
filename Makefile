dCOMPOSE = docker compose

.PHONY: up down logs dev

## up: start all services
up:
	$(COMPOSE) up -d

## down: stop and remove containers
down:
	$(COMPOSE) down

## logs: tail logs from all services
logs:
	$(COMPOSE) logs -f

## dev: start only infrastructure (postgres + pgadmin) for local Spring Boot dev
dev:
	$(COMPOSE) up -d postgres pgadmin

build:
    $(COMPOSE) up -d --build
