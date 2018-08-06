
# MAX_MAP_COUNT = 262144
export MAX_MAP_COUNT = 262144
sudo docker run -p 5601:5601 -p 9200:9200 -p 5044:5044 -it --name elk sebp/elk
sudo docker start elk