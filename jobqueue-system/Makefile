# ============================================================
# Makefile — Distributed Job Scheduler
# ============================================================

SHELL := /bin/bash
ROOT  := $(shell pwd)

.PHONY: help build test start stop status smoke clean infra-up infra-down

help:
	@echo ""
	@echo "  Distributed Job Scheduler — Make Targets"
	@echo "  ─────────────────────────────────────────"
	@echo "  make build       Build all modules (skip tests)"
	@echo "  make test        Run all unit tests"
	@echo "  make infra-up    Start Docker infrastructure (PG, Redis, Kafka)"
	@echo "  make infra-down  Stop Docker infrastructure"
	@echo "  make start       Kill stale ports → build → start all services"
	@echo "  make stop        Stop all running services"
	@echo "  make status      Health check all services"
	@echo "  make smoke       Run end-to-end smoke tests"
	@echo "  make clean       Maven clean"
	@echo ""

build:
	@echo "── Building all modules..."
	mvn clean install -DskipTests -q

test:
	@echo "── Running tests..."
	mvn test

infra-up:
	@echo "── Starting infrastructure containers..."
	docker-compose up -d postgres redis zookeeper kafka
	@echo "   Waiting for infrastructure to be ready..."
	@sleep 10
	@echo "   Infrastructure UP"

infra-down:
	@echo "── Stopping infrastructure containers..."
	docker-compose down

start:
	@chmod +x start-all.sh
	@./start-all.sh

stop:
	@chmod +x start-all.sh
	@./start-all.sh --stop

status:
	@chmod +x start-all.sh
	@./start-all.sh --status

smoke:
	@chmod +x scripts/smoke-test.sh
	@./scripts/smoke-test.sh

clean:
	mvn clean
