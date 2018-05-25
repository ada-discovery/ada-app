# Ada (NCER-PD Reporting System)
<br/>

## Application Server (Apache Tomcat)

**Start**
```bash
ssh -p 8022 yourusername@10.79.2.192
cd /home/peter.banda/apache-tomcat-7.0.64/bin
source set_env.sh
./startup.sh
```

**Stop**
```bash
ssh -p 8022 yourusername@10.79.2.192
cd /home/peter.banda/apache-tomcat-7.0.64/bin
./shutdown.sh
ps -A | grep java (to check if still running, if yes do: 'kill -s kill pid')
```

**Config**
```bash
/home/peter.banda/apache-tomcat-7.0.64/bin/catalina.sh
````

**Log**
```bash
/home/peter.banda/apache-tomcat-7.0.64/logs/catalina.out
```

**Backup script**
```bash
/etc/cron.daily/ada-backup
```

<br/>

## Database (Mongo)

**Start**
```bash
ssh -p 8022 yourusername@10.79.2.71
sudo service mongod start
```

**Stop**
```bash
ssh -p 8022 yourusername@10.79.2.71
sudo service mongod stop
```

**Config**
```bash
/etc/mongod.conf
```

**Log**
```bash
/var/log/mongodb/mongod.log
```

**Backup script**
```bash
/etc/cron.daily/ada-db-backup
```

<br/>

## Database (Elastic Search)

**Start**
```bash
ssh -p 8022 yourusername@10.79.2.235
sudo service elasticsearch start
```

**Stop**
```bash
ssh -p 8022 yourusername@10.79.2.235
sudo service elasticsearch stop
```

**Config(s)**
```bash
/etc/elasticsearch/elasticsearch.yml
/etc/init.d/elasticsearch
/etc/default/elasticsearch
/usr/lib/systemd/system/elasticsearch.service
```

**Log**
```bash
/var/log/elasticsearch/ada-cluster.log
```

**Backup script**
```bash
TODO
```

<br/>

## API

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
