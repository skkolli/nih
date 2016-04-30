#!/bin/sh

curl -XPUT 'http://localhost:9200/nih_20160430' -d @mapping.json

