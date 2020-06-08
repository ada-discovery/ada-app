# Development

Before you start here is a list of required dependencies:

* jdk 8
* scala 2.11.12
* sbt 0.13
* Elasticsearch 5.6
* mongo DB 4.0

## Linux (Ubuntu) guidelines

### JDK

```
sudo apt install openjdk-8-jdk
```

### scala
```
sudo apt-get install scala
```

### sbt
```
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
sudo apt-get update
sudo apt-get install sbt
```

### elastic
The easiest way is to start docker image:
```
docker run -p 9200:9200 elasticsearch:5.6
```

### mongo
The easiest way is to start docker image:
```
docker run -p 27017:27017 mongo:4
```


## Run project

After elastic and mongo are up and running start sbt:
 ```
export ADA_MONGO_DB_URI=mongodb://127.0.0.1:27017/ada
sbt -jvm-debug 5005 "project web" "~run"
```

Now ada should be running locally: http://localhost:9000/ 
