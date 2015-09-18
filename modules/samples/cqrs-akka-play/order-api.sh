

shop.order.Get() {
  shop.api.get order/$1
}

shop.order.List() {
  shop.api.get orders
}

shop.order.Create() {
	cat << EOF > last.json
{
  "customerId": { "uuid" : "$1" },
  "_type": "Order.Create"
}
EOF
    shop.api.post order
}

shop.order.AddProduct() {
cat << EOF > last.json
{
  "productNumber": { "number": "$2" },
  "_type": "Order.AddProduct"
}
EOF

  shop.api.patch order/$1
}

shop.order.RemoveProduct() {
cat << EOF > last.json
{
  "productNumber": { "number": "$2" },
  "_type": "Order.RemoveProduct"
}
EOF

  shop.api.patch order/$1
}

