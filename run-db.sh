#!/usr/bin/env bash

docker build -t funcqrs/db ./samples/lottery/src/main/resources/db

docker run --name funcqrs-postgres -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=changeit -p 5433:5432 -d funcqrs/db
