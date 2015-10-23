

get.Raffle() {
  raffle.api.get raffle/$1
}

list.Raffles() {
  raffle.api.get raffles
}

create.Raffle() {
	cat << EOF > last.json
{
  "name": "$2",
  "_type": "Raffle.Create"
}
EOF
  raffle.api.put raffle/$1
}

raffle.AddParticipant() {
cat << EOF > last.json
{
  "name": "$2",
  "_type": "Raffle.AddParticipant"
}
EOF

  raffle.api.patch raffle/$1
}

raffle.Run() {
cat << EOF > last.json
{
  "_type": "Raffle.Run"
}
EOF

  raffle.api.patch raffle/$1
}

