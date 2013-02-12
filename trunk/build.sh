#!/bin/bash
set -e
rm -fv Warehouse.jar warehouse/*.class
javac -bootclasspath ${WTK}/jar/cldcapi10.jar:${WTK}/jar/midpapi10.jar -source 1.2 -target 1.1 warehouse/*.java
${WTK}/bin/preverify1.0 -d . -classpath ${WTK}/jar/cldcapi10.jar:${WTK}/jar/midpapi10.jar warehouse.Warehouse warehouse.WarehouseCanvas
jar cvfm Warehouse.jar warehouse.mf warehouse
