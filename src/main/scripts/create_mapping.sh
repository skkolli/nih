#!/bin/sh

INDEX_NAME=nih_`date +'%Y%m%d'`

# Delete index if it already exists
echo "Deleting index $INDEX_NAME (if it exists)"
curl -XDELETE "http://localhost:9200/$INDEX_NAME"
echo 
echo 

# Create NIH index with our mapping
echo "Creating index $INDEX_NAME"
curl -XPUT "http://localhost:9200/$INDEX_NAME" -d @mapping.json
echo 
echo 

# Delete and then re-create our alias
echo "Moving alias 'nih' to index $INDEX_NAME"
curl -XPOST "http://localhost:9200/_aliases" -d '
{
    "actions" : [
	{ "remove" : { "index" : "*", "alias" : "nih" } }
        , { "add" : { "index" : "'"$INDEX_NAME"'", "alias" : "nih" } }
    ]
}'
echo 
echo 

