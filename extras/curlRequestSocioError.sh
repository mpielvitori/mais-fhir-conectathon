#!/bin/bash
validacion=0
token="$1"
host="$2"
file="$3"
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
		echo "Debe suministrar los parémetros token, host y file de la siguiente manera: './file.sh <tokenvalue> <host> <file>'"
else
	#for i in {1..100}
	#do
	   inicio=$(date +"%T")
	   curl -X POST -H "Authorization: Bearer $1" -H "Cache-Control: no-cache" -H "Postman-Token: 9d6cdb50-2a6b-35e9-54f8-d54a7243960d" -d @$3 "$2/mais-fhir-conectathon/facturacion?inputFormat=json&outputFormat=xml"
	   fin==$(date +"%T")
	   	echo ""
		echo "Inicia: $inicio"
		echo "Finaliza: $fin"
	#done	
fi

#./curlRequestSocioError.sh ec7abf97517ce7144120cc57642a6afb localhost:8080 MAISFacturacionRequestSocio1Error.json