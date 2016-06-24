#!/bin/bash
validacion=0
token="$1"
host="$2"
transaccion="$3"
if [ -z "$1" ]
  then
    validacion=1
elif [ -z "$2" ]
  then
    validacion=2
elif [ -z "$3" ]
  then
    validacion=3
fi

if [ $validacion -gt 0 ]
	then 
		echo "Debe suministrar los parémetros token, host y transacción de la siguiente manera: './file.sh <tokenvalue> <host> <transaccion>'"
else
	curl -X GET -H "Authorization: Bearer $1" -H "Cache-Control: no-cache" -H "Postman-Token: a505801f-c5c0-244e-5e9a-4a423d576939" "$2/mais-fhir-conectathon/debitos/transaccion/$3"
fi

#./curlRequestDebitos.sh localhost:8080 1