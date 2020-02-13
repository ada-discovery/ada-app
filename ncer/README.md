# Ada-Web - NCER-PD Project
[![version](https://img.shields.io/badge/version-0.8.1-green.svg)](https://ada.parkinson.lu) [![License: CC BY-NC 3.0](https://img.shields.io/badge/License-CC%20BY--NC%203.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/3.0/)

## Application Server (Netty)

* Root folder: `/opt/ada-web-ncer/`

**Start**
```
sudo ./bin/runme
```

**Stop**
```
sudo ./bin/stopme
```

**Config**
```
./bin/set_env.sh
./conf/custom.conf
```

**Log**
```
./bin/logs/application.log
````

**Cron Jobs**
```
/etc/cron.daily/ada-backup
/etc/cron.daily/sftp_ibbl_to_ada
```

**Monit Config**
```
/etc/monit/monitrc
```

<br/>

## Database (Mongo)

* IP: 10.240.6.124

**Start**
```
sudo service mongod start
```

**Stop**
```
sudo service mongod stop
```

**Config**
```
/etc/mongod.conf
```

**Log**
```
/var/log/mongodb/mongod.log
```

**Backup script**
```bash
/etc/cron.weekly/ada-db-backup
```

<br/>

## Database (Elastic Search)


**Start**
```
sudo service elasticsearch start
```

**Stop**
```
sudo service elasticsearch stop
```

**Config(s)**
```
/etc/elasticsearch/elasticsearch.yml
/etc/init.d/elasticsearch
/etc/default/elasticsearch
/usr/lib/systemd/system/elasticsearch.service
```

**Log**
```
/var/log/elasticsearch/ada-cluster.log
```

<br/>

## Spark Grid
* Root folder: `/home/peter.banda/spark-2.2.0-bin-hadoop2.7`

**Start Master**

```
 ./sbin/start-master.sh --webui-port 8081
```

**Stop Master**

```
./sbin/stop-master.sh 
```

**Start Slave**

```
./sbin/start-slave.sh spark://10.240.6.121:7077
```

**Stop Slave**

```
./sbin/stop-slaves.sh
```

**Monitoring Web UI**


<br/>

# API

Use **https://ada.parkinson.lu** as the API's core url.

Since html and json service types share the same end points you need to specify the **"Accept: application/json"** header to get JSONs back.

<br/>

**Login**
```bash
curl -v -X POST -H "Accept: application/json" --data "id=userxxx&password=yyy" https://ada.parkinson.lu/login
```

__Response__

```
< HTTP/1.1 200 OK
* Server Apache-Coyote/1.1 is not blacklisted
< Server: Apache-Coyote/1.1
< Vary: Accept
< Set-Cookie: PLAY2AUTH_SESS_ID=xxx; Expires=Wed, 22-Feb-2017 12:19:28 GMT; Path=/; HttpOnly
< Content-Type: text/plain;charset=utf-8
< Content-Length: 89
< Date: Wed, 22 Feb 2017 11:19:28 GMT
< 
User 'userxxx' successfully logged in. Check the header for a 'PLAY_SESSION' cookie.
```
<br/>

**Find Data**

The following parameters are supported:

 Param Name    | Description   | Required 
 ------------- | ------------- | -------------
 dataSet       | Data set id, such as __denopa.clinical_baseline__. | true 
 orderBy       | The name of the field to sort by.   | 
 projection    | The field names to retrieve. If not specified all fields are returned.    |
 filterOrId    | The id of filter (if saved) or the filter's conditions to satisfy.     |
 limit         | The number of items to retrieve. |
 skip          | The number of items to skip. |

<br/>

***All***

```bash
curl -X GET -G https://ada.parkinson.lu/dataSets/records/findCustom -H "Accept: application/json"
            -d "dataSet=denopa.clinical_baseline" --cookie "PLAY2AUTH_SESS_ID=xxx"
```

<br/>

***All with JSON formatted***

```bash
curl -X GET -G https://ada.parkinson.lu/dataSets/records/findCustom -H "Accept: application/json"
            -d "dataSet=denopa.clinical_baseline" --cookie "PLAY2AUTH_SESS_ID=xxx" | jq .
```

<br/>

***With Filter, OrderBy, and Projections***

The following query returns JSONs with two fields: __Geschlecht__ (gender), and __a_Alter__ (age), ordered by __a_Alter__ from the __denopa.clinical_baseline__ data set for all the items with __a_Alter__ greater than 70.

```bash
curl -X GET -G https://ada.parkinson.lu/dataSets/records/findCustom -H "Accept: application/json" 
                -d "dataSet=denopa.clinical_baseline&orderBy=a_Alter&projection=Geschlecht&projection=a_Alter&filterOrId=[{\"fieldName\":\"a_Alter\",\"conditionType\":\">\",\"value\":\"70\"}]" --cookie "PLAY2AUTH_SESS_ID=xxx"
```

<br/>

**Get Dictionary**

***Full Dictionary***

```bash
curl -X GET -G https://ada.parkinson.lu/dataSets/dictionary/listAll -H "Accept: application/json"
            -d "dataSet=denopa.clinical_baseline" --cookie "PLAY2AUTH_SESS_ID=xxx"
```

***Get a Specific Field***

E.g. "Geschlecht"

```bash
curl -v -X GET -G https://ada.parkinson.lu/dataSets/dictionary/get/Geschlecht -H "Accept: application/json"
            -d "dataSet=denopa.clinical_baseline" --cookie "PLAY2AUTH_SESS_ID=xxx"
```
