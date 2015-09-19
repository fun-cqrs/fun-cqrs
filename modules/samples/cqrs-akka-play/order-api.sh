

get.Order() {
  shop.api.get order/$1
}


list.Orders() {
  shop.api.get orders
}

create.Order() {
	cat << EOF > last.json
{
  "customerId": { "uuid" : "$1" },
  "_type": "Order.Create"
}
EOF
    shop.api.post order
}

order.AddProduct() {
cat << EOF > last.json
{
  "productNumber": { "number": "$2" },
  "_type": "Order.AddProduct"
}
EOF

  shop.api.patch order/$1
}

order.RemoveProduct() {
cat << EOF > last.json
{
  "productNumber": { "number": "$2" },
  "_type": "Order.RemoveProduct"
}
EOF

  shop.api.patch order/$1
}

