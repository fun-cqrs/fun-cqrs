

get.Customer() {
  shop.api.get customer/$1
}

list.Customers() {
  shop.api.get customers
}

create.Customer() {
	cat << EOF > last.json
{
  "name": "$1",
  "_type": "Customer.Create"
}
EOF
    shop.api.post customer
}

customer.AddVatNumber() {
cat << EOF > last.json
{
  "vat": { "number" : "$2" },
  "_type": "Customer.AddVatNumber"
}
EOF

  shop.api.patch customer/$1
}

customer.AddAddress() {
cat << EOF > last.json
{
  "address" : {
    "street"  : { "name": "$2" } ,
    "city"    : { "name": "$3" },
    "country" : { "name": "$4" }
  },
  "_type": "Customer.AddAddress"
}
EOF

  shop.api.patch customer/$1
}
