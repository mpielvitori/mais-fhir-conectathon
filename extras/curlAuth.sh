#!/bin/bash
host="$1"
if [ -z "$1" ]
  then
	echo "Debe suministrar el parémetro host de la siguiente manera: './file.sh <host>'"
else
	curl -X POST -H "Authorization: Bearer" -H "Cache-Control: no-cache" -H "Postman-Token: 6b26f5da-fb6b-771a-75a5-f6f7274f762b" -H "Content-Type: application/x-www-form-urlencoded" -d 'grant_type=password&username=usuario_prestador&password=passwd_prestador&client_secret=oauth2_client_secret&client_id=oauth2_client_id' "$1/mais-fhir-conectathon/auth"
fi

#./curlAuth.sh localhost:8080