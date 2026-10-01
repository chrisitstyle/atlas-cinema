
up:
	docker compose -f infra/compose.yml build
	docker compose -f infra/compose.yml up
up-nc:
	docker compose -f infra/compose.yml build --no-cache
	docker compose -f infra/compose.yml up

up-detached:
	docker compose -f infra/compose.yml build
	docker compose -f infra/compose.yml up -d

up-detached-nc:
	docker compose -f infra/compose.yml build --no-cache
	docker compose -f infra/compose.yml up -d