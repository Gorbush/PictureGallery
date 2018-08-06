echo "From https://elk-docker.readthedocs.io/"
echo "http://www.baeldung.com/java-application-logs-to-elastic-stack"
echo "Kibana local: http://localhost:5601/app/kibana#/home/tutorial_directory/logging?_g=()"
sudo docker pull sebp/elk
export MAX_MAP_COUNT=262144
#sudo docker run -p 5601:5601 -p 9200:9200 -p 5044:5044 -it --name elk sebp/elk
docker build . -t elkgallerymine
sudo docker-compose up elk