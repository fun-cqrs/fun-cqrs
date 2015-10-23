

raffle.api.put() {
  http PUT http://localhost:9000/$1 --verbose < last.json
}

raffle.api.post() {
  http POST http://localhost:9000/$1 --verbose < last.json
}

raffle.api.patch() {
  http PATCH http://localhost:9000/$1 --verbose < last.json
}


raffle.api.get() {
  http GET http://localhost:9000/$1 --verbose
}


. run-api.sh