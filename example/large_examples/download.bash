select file in shiporder_100.xml shiporder_1000.xml shiporder_10000.xml shiporder_100000.xml shiporder_1000000.xml shiporder_10000000.xml
do
   echo "Downloading $file"
   wget "https://simplex24.de/vpex/examples/$file"
done