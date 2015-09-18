

shop.product.Get() {
  shop.api.get product/$1
}

shop.product.List() {
  shop.api.get products
}

shop.product.Create() {
	cat << EOF > last.json
{
  "name": "$2",
  "description": "$3",
  "price": $4,
  "_type": "Product.Create"
}
EOF
    shop.api.put product/$1
}

shop.product.ChangeName() {
cat << EOF > last.json
{
  "name": "$2",
  "_type": "Product.ChangeName"
}
EOF

  shop.api.patch product/$1
}

shop.product.ChangePrice() {
cat << EOF > last.json
{
  "price": $2,
  "_type": "Product.ChangePrice"
}
EOF

  shop.api.patch product/$1
}

