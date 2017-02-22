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

**Login**
```bash
curl -v -X POST -H "Accept: application/json" --data "id=peter.banda&password=xxx" http://10.79.2.192:8080/login
```

Response
< HTTP/1.1 200 OK
* Server Apache-Coyote/1.1 is not blacklisted
< Server: Apache-Coyote/1.1
< Vary: Accept
< Set-Cookie: PLAY2AUTH_SESS_ID=xxx; Expires=Wed, 22-Feb-2017 12:19:28 GMT; Path=/; HttpOnly
< Content-Type: text/plain;charset=utf-8
< Content-Length: 89
< Date: Wed, 22 Feb 2017 11:19:28 GMT
< 
User 'peter.banda' successfully logged in. Check the header for a 'PLAY_SESSION' cookie.

**Find Data**
```bash
curl -X GET -v -G http://localhost:9000/studies/records/findCustom -H "Accept: application/json" -d "dataSet=ml.iris&orderBy=class&projection=class&projection=sepal-length" --cookie "PLAY2AUTH_SESS_ID=xxx" | jq .
```
