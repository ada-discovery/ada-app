# Ada (NCER-PD Reporting System)

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

## API

Use **http://10.79.2.192:8080** as the API's core url. Note that it's planned to change the protocol to https so pls. check this site for future announcements.

Since html and json service types share the end points you need to specify the **"Accept: application/json"** header to get JSON back.


**Login**
```bash
curl -v -X POST -H "Accept: application/json" --data "id=userxxx&password=yyy" http://10.79.2.192:8080/login
```

***Response***

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
<br/>
<br/>

**Find Data**

The following parameters are supported:

 Param Name    | Description   | Required 
 ------------- | ------------- | -------------
 dataSet       | Data set id, such as __denopa.clinical_baseline__. | true 
 orderBy       | The name of the field to sort by.   | 
 projection    | The field names to retrieve. If not specified all fields are returned.    |
 filterOrId    | The id of filter (if saved) or the filter's conditions to satisfy.     |

***All***

```bash
curl -X GET -G http://10.79.2.192:8080/studies/records/findCustom -H "Accept: application/json"
            -d "dataSet=denopa.clinical_baseline" --cookie "PLAY2AUTH_SESS_ID=xxx"
```

with JSON formatting

```bash
curl -X GET -G http://10.79.2.192:8080/studies/records/findCustom -H "Accept: application/json"
            -d "dataSet=denopa.clinical_baseline" --cookie "PLAY2AUTH_SESS_ID=xxx" | jq .
```

***With Filter, OrderBy, and Projections***

```bash
curl -X GET -v -G http://10.79.2.192:8080/studies/records/findCustom -H "Accept: application/json"
               -d "dataSet=denopa.clinical_baseline&orderBy=class&projection=class&projection=sepal-length" --cookie "PLAY2AUTH_SESS_ID=xxx" | jq .
```
