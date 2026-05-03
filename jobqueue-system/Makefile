.PHONY: build start stop restart logs status clean

## ── Infrastructure ─────────────────────────────────────────────────────────
infra-up:
	docker compose up -d
	@echo "✅  Infrastructure up (PostgreSQL :5435, Redis :6379, Kafka :9092)"

infra-down:
	docker compose down
	@echo "✅  Infrastructure stopped"

## ── Build ───────────────────────────────────────────────────────────────────
build:
	mvn clean install -DskipTests
	@echo "✅  All modules built"

test:
	mvn clean test
	@echo "✅  All tests executed"

## ── Run ─────────────────────────────────────────────────────────────────────
start:
	chmod +x start-all.sh && ./start-all.sh
	@echo "✅  All services started"

stop:
	./start-all.sh --stop

restart: stop start

## ── Logs ────────────────────────────────────────────────────────────────────
logs-submission:
	tail -f logs/submission-service.log

logs-worker:
	tail -f logs/worker-service.log

## ── Status ──────────────────────────────────────────────────────────────────
status:
	@echo "--- submission-service ---"
	@curl -s http://localhost:8081/actuator/health | python3 -m json.tool || echo "DOWN"
	@echo "--- worker-service ---"
	@curl -s http://localhost:8082/actuator/health | python3 -m json.tool || echo "DOWN"

## ── Clean ───────────────────────────────────────────────────────────────────
clean:
	mvn clean
	rm -f logs/*.log .running_pids
