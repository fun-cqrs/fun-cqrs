

shop.api.put() {
  http PUT http://localhost:9000/$1 --verbose < last.json
}

shop.api.post() {
  http POST http://localhost:9000/$1 --verbose < last.json
}

shop.api.patch() {
  http PATCH http://localhost:9000/$1 --verbose < last.json
}


shop.api.get() {
  http GET http://localhost:9000/$1 --verbose
}


. product-api.sh
. customer-api.sh
. order-api.sh