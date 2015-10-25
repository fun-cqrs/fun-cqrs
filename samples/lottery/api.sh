

lottery.api.put() {
  http PUT http://localhost:9000/$1 --verbose < last.json
}

lottery.api.post() {
  http POST http://localhost:9000/$1 --verbose < last.json
}

lottery.api.patch() {
  http PATCH http://localhost:9000/$1 --verbose < last.json
}


lottery.api.get() {
  http GET http://localhost:9000/$1 --verbose
}




get.Lottery() {
  lottery.api.get lottery/$1
}

list.Lotteries() {
  lottery.api.get lotteries
}

create.Lottery() {
	cat << EOF > last.json
{
  "name": "$2",
  "_type": "Lottery.Create"
}
EOF
  lottery.api.put lottery/$1
}

lottery.AddParticipant() {
cat << EOF > last.json
{
  "name": "$2",
  "_type": "Lottery.AddParticipant"
}
EOF

  lottery.api.patch lottery/$1
}



lottery.RemoveParticipant() {
cat << EOF > last.json
{
  "name": "$2",
  "_type": "Lottery.RemoveParticipant"
}
EOF

  lottery.api.patch lottery/$1
}

lottery.Run() {
cat << EOF > last.json
{
  "_type": "Lottery.Run"
}
EOF

  lottery.api.patch lottery/$1
}
